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
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RegArrayConfigFlowTest {

    private val codec = ItlvcFrameCodec()

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private fun responseFrame(type: Int, value: ByteArray): ByteArray =
        codec.encode(ItlvcFrame(bytes(type), value))

    /** 从完整帧中提取 payload（AA 11 T L payload CRC）。 */
    private fun payloadOf(frame: ByteArray): ByteArray =
        frame.copyOfRange(4, 4 + (frame[3].toInt() and 0xFF))

    @Test
    fun `sendRegArrayConfig splits 176 blocks into 3 frames`() = runTest {
        val transport = InMemoryTransport()
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)
        val flow = RegArrayConfigFlow(session, CommandSpec(type = bytes(Gh3220Cmd.REG_ARRAY_WRITE), timeoutMs = 200))

        val data = ByteArray(176 * 4) { (it * 7 + 1).toByte() } // 704B = 176 block
        val responder = launch {
            for (i in 0 until 3) {
                while (transport.sent.size <= i) delay(1)
                transport.emit(responseFrame(Gh3220Cmd.REG_ARRAY_WRITE, bytes(0x00)))
            }
        }
        val progress = mutableListOf<Int>()
        val result = flow.sendRegArrayConfig(data) { sent, total ->
            progress.add(sent)
            assertEquals(704, total)
        }
        responder.join()

        assertTrue(result.isSuccess, "result: $result")
        assertEquals(listOf(236, 472, 704), progress)
        assertEquals(3, transport.sent.size)
        assertContentEquals(data.copyOfRange(0, 236), payloadOf(transport.sent[0]))
        assertContentEquals(data.copyOfRange(236, 472), payloadOf(transport.sent[1]))
        assertContentEquals(data.copyOfRange(472, 704), payloadOf(transport.sent[2]))
        session.detach()
    }

    @Test
    fun `sendRegArrayConfig surfaces device status 1 as DeviceError`() = runTest {
        val transport = InMemoryTransport()
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)
        val flow = RegArrayConfigFlow(session, CommandSpec(type = bytes(Gh3220Cmd.REG_ARRAY_WRITE), timeoutMs = 200))

        val data = ByteArray(480) { (it * 3 + 1).toByte() } // 120 block → 3 帧（236/236/8）
        val responder = launch {
            while (transport.sent.isEmpty()) delay(1)
            transport.emit(responseFrame(Gh3220Cmd.REG_ARRAY_WRITE, bytes(0x01)))
        }
        val progress = mutableListOf<Int>()
        val result = flow.sendRegArrayConfig(data) { sent, _ -> progress.add(sent) }
        responder.join()

        assertTrue(result.isFailure)
        assertIs<ItlvcError.CommandError.DeviceError>(result.exceptionOrNull())
        assertEquals(1, transport.sent.size, "第一帧失败后必须立即停止，不能继续下发剩余帧")
        assertTrue(progress.isEmpty(), "失败帧不应计入进度回调（先判失败再回调）")
        session.detach()
    }

    @Test
    fun `sendRegArrayConfig rejects non-multiple-of-4 data`() = runTest {
        val transport = InMemoryTransport()
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)
        val flow = RegArrayConfigFlow(session)

        assertFailsWith<IllegalArgumentException> { flow.sendRegArrayConfig(ByteArray(5)) }
        session.detach()
    }

    @Test
    fun `sendRegArrayConfig with exactly 236 bytes sends single frame`() = runTest {
        val transport = InMemoryTransport()
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)
        val flow = RegArrayConfigFlow(session, CommandSpec(type = bytes(Gh3220Cmd.REG_ARRAY_WRITE), timeoutMs = 200))

        val data = ByteArray(236) { (it * 5 + 2).toByte() }
        val responder = launch {
            while (transport.sent.isEmpty()) delay(1)
            transport.emit(responseFrame(Gh3220Cmd.REG_ARRAY_WRITE, bytes(0x00)))
        }
        val result = flow.sendRegArrayConfig(data)
        responder.join()

        assertTrue(result.isSuccess, "result: $result")
        assertEquals(1, transport.sent.size)
        assertContentEquals(data, payloadOf(transport.sent[0]))
        session.detach()
    }

    @Test
    fun `sendRegArrayConfig surfaces empty response as ParseError`() = runTest {
        val transport = InMemoryTransport()
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)
        val flow = RegArrayConfigFlow(session, CommandSpec(type = bytes(Gh3220Cmd.REG_ARRAY_WRITE), timeoutMs = 200))

        val responder = launch {
            while (transport.sent.isEmpty()) delay(1)
            transport.emit(responseFrame(Gh3220Cmd.REG_ARRAY_WRITE, ByteArray(0)))
        }
        val result = flow.sendRegArrayConfig(ByteArray(4) { it.toByte() })
        responder.join()

        assertTrue(result.isFailure)
        assertIs<ItlvcError.ParseError>(result.exceptionOrNull())
        session.detach()
    }

    @Test
    fun `sendRegArrayConfig surfaces unknown status as ParseError`() = runTest {
        val transport = InMemoryTransport()
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)
        val flow = RegArrayConfigFlow(session, CommandSpec(type = bytes(Gh3220Cmd.REG_ARRAY_WRITE), timeoutMs = 200))

        val responder = launch {
            while (transport.sent.isEmpty()) delay(1)
            transport.emit(responseFrame(Gh3220Cmd.REG_ARRAY_WRITE, bytes(0x02)))
        }
        val result = flow.sendRegArrayConfig(ByteArray(4) { it.toByte() })
        responder.join()

        assertTrue(result.isFailure)
        assertIs<ItlvcError.ParseError>(result.exceptionOrNull())
        session.detach()
    }

    @Test
    fun `sendRegArrayConfig rejects blocksPerFrame out of range`() = runTest {
        val transport = InMemoryTransport()
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)
        val flow = RegArrayConfigFlow(session)

        assertFailsWith<IllegalArgumentException> { flow.sendRegArrayConfig(ByteArray(4), blocksPerFrame = 0) }
        assertFailsWith<IllegalArgumentException> { flow.sendRegArrayConfig(ByteArray(4), blocksPerFrame = 60) }
        session.detach()
    }
}
