package com.ghealth.tools.ble.gh3220.flow

import com.ghealth.tools.ble.gh3220.Gh3220Cmd
import com.ghealth.tools.ble.itlvc.codec.ItlvcFrame
import com.ghealth.tools.ble.itlvc.codec.ItlvcFrameCodec
import com.ghealth.tools.ble.itlvc.core.CommandSpec
import com.ghealth.tools.ble.itlvc.core.ItlvcConfig
import com.ghealth.tools.ble.itlvc.core.ItlvcError
import com.ghealth.tools.ble.itlvc.core.ItlvcSession
import com.ghealth.tools.ble.itlvc.transport.InMemoryTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FwUpgradeFlowTest {

    private val codec = ItlvcFrameCodec()

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private fun responseFrame(type: Int, value: ByteArray): ByteArray =
        codec.encode(ItlvcFrame(bytes(type), value))

    /** 从完整帧中提取 payload（AA 11 T L payload CRC）。 */
    private fun payloadOf(frame: ByteArray): ByteArray =
        frame.copyOfRange(4, 4 + (frame[3].toInt() and 0xFF))

    private fun newSession(transport: InMemoryTransport, testScope: kotlinx.coroutines.CoroutineScope): ItlvcSession {
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, testScope)
        return session
    }

    @Test
    fun `getFirmwareVersion parses response`() = runTest {
        val transport = InMemoryTransport()
        val session = newSession(transport, this)
        val flow = FwUpgradeFlow(session, CommandSpec(type = bytes(Gh3220Cmd.FW_UPGRADE), timeoutMs = 200))

        val responder = launch {
            transport.emit(responseFrame(Gh3220Cmd.FW_UPGRADE, bytes(0x01, 0x02, 0x31, 0x30)))
        }
        val result = flow.getFirmwareVersion()
        responder.join()

        assertTrue(result.isSuccess, "result: $result")
        assertEquals("10", result.getOrThrow())
        session.detach()
    }

    @Test
    fun `setTransferParams encodes payload and checks status`() = runTest {
        val transport = InMemoryTransport()
        val session = newSession(transport, this)
        val flow = FwUpgradeFlow(session, CommandSpec(type = bytes(Gh3220Cmd.FW_UPGRADE), timeoutMs = 200))

        val responder = launch {
            transport.emit(responseFrame(Gh3220Cmd.FW_UPGRADE, bytes(0x02, 0x01)))
        }
        val result = flow.setTransferParams(fileSize = 0x12345678L, blockSize = 0x0200)
        responder.join()

        assertTrue(result.isSuccess, "result: $result")
        assertEquals(1, transport.sent.size)
        assertContentEquals(bytes(0x02, 0x78, 0x56, 0x34, 0x12, 0x00, 0x02), payloadOf(transport.sent[0]))
        session.detach()
    }

    @Test
    fun `transferFirmware splits blocks and packages with progress`() = runTest {
        val transport = InMemoryTransport()
        val session = newSession(transport, this)
        val flow = FwUpgradeFlow(session, CommandSpec(type = bytes(Gh3220Cmd.FW_UPGRADE), timeoutMs = 200))

        val firmware = ByteArray(120) { it.toByte() }
        val responder = launch {
            for (i in 0 until 3) {
                while (transport.sent.size <= i) delay(1)
                val req = payloadOf(transport.sent[i])
                // 回显 [0x03][status=1][total 2B][index 2B][len 1B]
                val resp = bytes(0x03, 0x01) + req.copyOfRange(1, 6)
                transport.emit(responseFrame(Gh3220Cmd.FW_UPGRADE, resp))
            }
        }
        val progress = mutableListOf<Int>()
        val result = flow.transferFirmware(firmware, blockSize = 100) { sent, total ->
            progress.add(sent)
            assertEquals(120, total)
        }
        responder.join()

        assertTrue(result.isSuccess, "result: $result")
        assertEquals(listOf(56, 100, 120), progress)
        assertEquals(3, transport.sent.size)

        // 包 1：total=100 index=0 len=56；包 2：total=100 index=56 len=44；包 3：total=20 index=0 len=20
        val p0 = payloadOf(transport.sent[0])
        assertContentEquals(bytes(0x03, 0x64, 0x00, 0x00, 0x00, 0x38), p0.copyOfRange(0, 6))
        assertEquals(56, p0.size - 6)
        assertContentEquals(firmware.copyOfRange(0, 56), p0.copyOfRange(6, p0.size))

        val p2 = payloadOf(transport.sent[2])
        assertContentEquals(bytes(0x03, 0x14, 0x00, 0x00, 0x00, 0x14), p2.copyOfRange(0, 6))
        assertEquals(20, p2.size - 6)
        assertContentEquals(firmware.copyOfRange(100, 120), p2.copyOfRange(6, p2.size))
        session.detach()
    }

    @Test
    fun `transferFirmware fails fast on device status 2`() = runTest {
        val transport = InMemoryTransport()
        val session = newSession(transport, this)
        val flow = FwUpgradeFlow(session, CommandSpec(type = bytes(Gh3220Cmd.FW_UPGRADE), timeoutMs = 200))

        val firmware = ByteArray(60) { it.toByte() }
        val responder = launch {
            while (transport.sent.isEmpty()) delay(1)
            // 完整长度响应（[0x03][status=2][total][index][len]）才能通过结构校验命中 DeviceError
            transport.emit(responseFrame(Gh3220Cmd.FW_UPGRADE, bytes(0x03, 0x02, 0x64, 0x00, 0x00, 0x00, 0x38)))
        }
        val result = flow.transferFirmware(firmware, blockSize = 100)
        responder.join()

        assertTrue(result.isFailure)
        assertIs<ItlvcError.CommandError.DeviceError>(result.exceptionOrNull())
        assertEquals(1, transport.sent.size)
        session.detach()
    }
}
