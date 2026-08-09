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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DriverConfigFlowTest {

    private val codec = ItlvcFrameCodec()

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private fun responseFrame(type: Int, value: ByteArray): ByteArray =
        codec.encode(ItlvcFrame(bytes(type), value))

    /** 从完整帧中提取 payload（AA 11 T L payload CRC）。 */
    private fun payloadOf(frame: ByteArray): ByteArray =
        frame.copyOfRange(4, 4 + (frame[3].toInt() and 0xFF))

    @Test
    fun `sendDriverConfig splits chunks with position and handle flags`() = runTest {
        val transport = InMemoryTransport()
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)
        val flow = DriverConfigFlow(session, CommandSpec(type = bytes(Gh3220Cmd.DRV_CFG), timeoutMs = 200))

        val data = ByteArray(500) { (it % 251).toByte() }
        val responder = launch {
            for (i in 0 until 3) {
                while (transport.sent.size <= i) delay(1)
                transport.emit(responseFrame(Gh3220Cmd.DRV_CFG, bytes(0x00)))
            }
        }
        val progress = mutableListOf<Int>()
        val result = flow.sendDriverConfig(data, save = true, chunkSize = 230) { sent, total ->
            progress.add(sent)
            assertEquals(500, total)
        }
        responder.join()

        assertTrue(result.isSuccess, "result: $result")
        assertEquals(listOf(230, 460, 500), progress)
        assertEquals(3, transport.sent.size)

        // 包 0：pos=0 flag=0（非最后），230 字节
        val p0 = payloadOf(transport.sent[0])
        assertContentEquals(bytes(0x00, 0x00, 0x00), p0.copyOfRange(0, 3))
        assertEquals(230, p0.size - 3)
        // 包 1：pos=230（0x00E6 小端）flag=0
        val p1 = payloadOf(transport.sent[1])
        assertContentEquals(bytes(0xE6, 0x00, 0x00), p1.copyOfRange(0, 3))
        // 包 2：pos=460（0x01CC 小端）flag=2（最后且保存），40 字节
        val p2 = payloadOf(transport.sent[2])
        assertContentEquals(bytes(0xCC, 0x01, 0x02), p2.copyOfRange(0, 3))
        assertEquals(40, p2.size - 3)
        assertContentEquals(data.copyOfRange(460, 500), p2.copyOfRange(3, p2.size))
        session.detach()
    }

    @Test
    fun `sendDriverConfig last flag is 1 when save disabled`() = runTest {
        val transport = InMemoryTransport()
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)
        val flow = DriverConfigFlow(session, CommandSpec(type = bytes(Gh3220Cmd.DRV_CFG), timeoutMs = 200))

        val responder = launch {
            for (i in 0 until 2) {
                while (transport.sent.size <= i) delay(1)
                transport.emit(responseFrame(Gh3220Cmd.DRV_CFG, bytes(0x00)))
            }
        }
        val result = flow.sendDriverConfig(ByteArray(250) { 1 }, save = false, chunkSize = 230)
        responder.join()

        assertTrue(result.isSuccess, "result: $result")
        assertEquals(2, transport.sent.size)
        assertEquals(0, payloadOf(transport.sent[0])[2].toInt() and 0xFF)
        assertEquals(1, payloadOf(transport.sent[1])[2].toInt() and 0xFF)
        session.detach()
    }

    @Test
    fun `sendDriverConfig fails fast on status 1`() = runTest {
        val transport = InMemoryTransport()
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)
        val flow = DriverConfigFlow(session, CommandSpec(type = bytes(Gh3220Cmd.DRV_CFG), timeoutMs = 200))

        val responder = launch {
            while (transport.sent.isEmpty()) delay(1)
            transport.emit(responseFrame(Gh3220Cmd.DRV_CFG, bytes(0x01)))
        }
        val result = flow.sendDriverConfig(ByteArray(500) { 0 })
        responder.join()

        assertTrue(result.isFailure)
        assertIs<ItlvcError.CommandError.DeviceError>(result.exceptionOrNull())
        assertEquals(1, transport.sent.size)
        session.detach()
    }

    @Test
    fun `sendDriverConfig rejects empty response`() = runTest {
        val transport = InMemoryTransport()
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)
        val flow = DriverConfigFlow(session, CommandSpec(type = bytes(Gh3220Cmd.DRV_CFG), timeoutMs = 200))

        val responder = launch {
            while (transport.sent.isEmpty()) delay(1)
            transport.emit(responseFrame(Gh3220Cmd.DRV_CFG, ByteArray(0)))
        }
        val result = flow.sendDriverConfig(ByteArray(10) { 0 })
        responder.join()

        assertTrue(result.isFailure)
        assertIs<ItlvcError.ParseError>(result.exceptionOrNull())
        session.detach()
    }

    @Test
    fun `sendDriverConfig rejects unknown status`() = runTest {
        val transport = InMemoryTransport()
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)
        val flow = DriverConfigFlow(session, CommandSpec(type = bytes(Gh3220Cmd.DRV_CFG), timeoutMs = 200))

        val responder = launch {
            while (transport.sent.isEmpty()) delay(1)
            transport.emit(responseFrame(Gh3220Cmd.DRV_CFG, bytes(0x02)))
        }
        val result = flow.sendDriverConfig(ByteArray(10) { 0 })
        responder.join()

        assertTrue(result.isFailure)
        assertIs<ItlvcError.ParseError>(result.exceptionOrNull())
        session.detach()
    }

    @Test
    fun `sendDriverConfig rejects oversized config`() {
        val session = ItlvcSession(codec, ItlvcConfig())
        val flow = DriverConfigFlow(session, CommandSpec(type = bytes(Gh3220Cmd.DRV_CFG), timeoutMs = 200))
        assertFailsWith<IllegalArgumentException> {
            runBlocking { flow.sendDriverConfig(ByteArray(0x10001) { 0 }) }
        }
    }
}
