package com.ghealth.tools.ble.gh3220

import com.ghealth.tools.ble.gh3220.event.Gh3220CurrentBattery
import com.ghealth.tools.ble.gh3220.rawdata.Gh3220RawDataFrame
import com.ghealth.tools.ble.itlvc.codec.ItlvcFrame
import com.ghealth.tools.ble.itlvc.codec.ItlvcFrameCodec
import com.ghealth.tools.ble.itlvc.core.ItlvcConfig
import com.ghealth.tools.ble.itlvc.core.ItlvcSession
import com.ghealth.tools.ble.itlvc.state.SessionState
import com.ghealth.tools.ble.itlvc.transport.InMemoryTransport
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class Gh3220ProtocolClientTest {

    private val codec = ItlvcFrameCodec()

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private fun responseFrame(type: Int, value: ByteArray): ByteArray =
        codec.encode(ItlvcFrame(bytes(type), value))

    @Test
    fun `rawdata reports are decoded and routed to flows`() = runTest {
        val transport = InMemoryTransport()
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)
        val client = Gh3220ProtocolClient(session)
        client.attach()

        val frames = mutableListOf<Gh3220RawDataFrame>()
        val collect = launch {
            client.rawdataFrames.collect { frames.add(it) }
        }
        // runTest 虚拟时间下 receiver 与 collect 协程尚未订阅；replay=0 的 SharedFlow 会丢弃
        // 无订阅者时的 emit 数据，故先 runCurrent() 完成订阅再发射（与 InMemoryTransportTest 同款约定）。
        testScheduler.runCurrent()
        // 0x08：dataType=0x00，frame = [frameId=0][0x01020304]
        transport.emit(responseFrame(0x08, bytes(0x00, 0x05, 0x00, 0x01, 0x02, 0x03, 0x04)))
        delay(10)
        collect.cancel()

        assertEquals(1, frames.size)
        assertContentEquals(intArrayOf(0x01020304), frames[0].rawdata)
        session.detach()
    }

    @Test
    fun `fifo and event reports are routed`() = runTest {
        val transport = InMemoryTransport()
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)
        val client = Gh3220ProtocolClient(session)
        client.attach()

        val fifo = mutableListOf<Any>()
        val events = mutableListOf<Any>()
        val collect = launch {
            client.fifoReports.collect { fifo.add(it) }
        }
        val collect2 = launch {
            client.cardiffEvents.collect { events.add(it) }
        }
        testScheduler.runCurrent()
        transport.emit(responseFrame(0x2A, bytes(0x03, 0x04, 0x00, 0x00, 0x00, 0xDE, 0xAD, 0xBE, 0xEF)))
        transport.emit(responseFrame(0x16, bytes(0x00, 0x02, 0x03)))
        delay(10)
        collect.cancel()
        collect2.cancel()

        assertEquals(1, fifo.size)
        assertEquals(3, (fifo[0] as com.ghealth.tools.ble.gh3220.rawdata.Gh3220FifoReport).fifoId)
        assertEquals(1, events.size)
        assertEquals(3, (events[0] as com.ghealth.tools.ble.gh3220.event.Gh3220CardiffEvent).eventReportId)
        // 0x16 自动 ACK
        assertContentEquals(
            codec.encode(ItlvcFrame(bytes(0x16), bytes(0x03))),
            transport.sent.single(),
        )
        session.detach()
    }

    @Test
    fun `command apis encode payloads and parse responses`() = runTest {
        val transport = InMemoryTransport()
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)
        val client = Gh3220ProtocolClient(session)
        client.attach()

        val responder = launch {
            transport.emit(responseFrame(0x1A, bytes(0x00)))
        }
        val status = client.getConnectionStatus()
        responder.join()
        assertTrue(status.isSuccess, "status: $status")
        assertEquals(0, status.getOrThrow())
        // 请求帧：AA 11 1A 00 ...
        assertEquals(0x1A, transport.sent[0][2].toInt() and 0xFF)
        session.detach()
    }

    @Test
    fun `getVersion parses typed response`() = runTest {
        val transport = InMemoryTransport()
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)
        val client = Gh3220ProtocolClient(session)
        client.attach()

        val responder = launch {
            transport.emit(responseFrame(0x19, bytes(0x01, 0x02, 0x41, 0x42)))
        }
        val version = client.getVersion(0x01)
        responder.join()
        assertTrue(version.isSuccess, "version: $version")
        assertEquals("AB", version.getOrThrow().text)
        session.detach()
    }

    @Test
    fun `malformed report emits decode error instead of crashing`() = runTest {
        val transport = InMemoryTransport()
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)
        val client = Gh3220ProtocolClient(session)
        client.attach()

        val frames = mutableListOf<Gh3220RawDataFrame>()
        val errors = mutableListOf<Throwable>()
        val collectFrames = launch { client.rawdataFrames.collect { frames.add(it) } }
        val collect = launch { client.decodeErrors.collect { errors.add(it) } }
        testScheduler.runCurrent()

        // 0x08 dataLen=10 但只有 1 字节数据 → 解码失败进 decodeErrors
        transport.emit(responseFrame(0x08, bytes(0x00, 0x0A, 0x00)))
        delay(10)
        // 随后合法帧仍被路由 → 接收协程存活
        transport.emit(responseFrame(0x08, bytes(0x00, 0x05, 0x00, 0x01, 0x02, 0x03, 0x04)))
        delay(10)
        collect.cancel()
        collectFrames.cancel()

        assertEquals(1, errors.size)
        assertIs<com.ghealth.tools.ble.itlvc.core.ItlvcError.ParseError>(errors[0])
        assertEquals(1, frames.size)
        assertContentEquals(intArrayOf(0x01020304), frames[0].rawdata)
        session.detach()
    }

    @Test
    fun `session state is exposed`() = runTest {
        val transport = InMemoryTransport()
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)
        val client = Gh3220ProtocolClient(session)
        assertEquals(SessionState.CONNECTED, client.sessionState)
        session.detach()
    }

    @Test
    fun `regArrayWrite rejects status 1`() = runTest {
        val transport = InMemoryTransport()
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)
        val client = Gh3220ProtocolClient(session)
        client.attach()

        val responder = launch {
            while (transport.sent.isEmpty()) delay(1)
            transport.emit(responseFrame(0xA1, bytes(0x01)))
        }
        val result = client.regArrayWrite(listOf(intArrayOf(0x00, 0x01, 0x00, 0x02)))
        responder.join()

        assertTrue(result.isFailure)
        assertIs<com.ghealth.tools.ble.itlvc.core.ItlvcError.CommandError.DeviceError>(result.exceptionOrNull())
        session.detach()
    }

    @Test
    fun `sendRaw sends raw payload and returns raw response bytes`() = runTest {
        val transport = InMemoryTransport()
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)
        val client = Gh3220ProtocolClient(session)
        client.attach()

        val job = async { client.sendRaw(0x23, bytes(0xDE, 0xAD)) }
        testScheduler.runCurrent()
        // 0x23 无格式说明：原始字节收发
        assertContentEquals(responseFrame(0x23, bytes(0xDE, 0xAD)), transport.sent.single())
        transport.emit(responseFrame(0x23, bytes(0xBE, 0xEF)))
        val result = job.await()
        assertTrue(result.isSuccess)
        assertContentEquals(bytes(0xBE, 0xEF), result.getOrThrow())
        session.detach()
    }

    @Test
    fun `sendRaw respects pass-through whitelist`() = runTest {
        val transport = InMemoryTransport()
        val session = ItlvcSession(codec, ItlvcConfig(passThroughMode = true))
        session.attach(transport, this)
        val client = Gh3220ProtocolClient(session)
        client.attach()

        // 白名单 0x19 放行（无响应则超时，但已写入传输）
        val allowed = launch { client.sendRaw(0x19, bytes(0x01)) }
        testScheduler.runCurrent()
        assertEquals(1, transport.sent.size)
        allowed.cancel()
        session.detach()

        // 非白名单 0x23 拒绝且不写入传输
        val blocked = client.sendRaw(0x23, bytes(0xDE))
        assertTrue(blocked.isFailure)
        assertIs<com.ghealth.tools.ble.itlvc.core.ItlvcError.CommandError.Unsupported>(blocked.exceptionOrNull())
        assertEquals(1, transport.sent.size)
    }

    @Test
    fun `0x28 time data command constant is defined`() {
        assertEquals(0x28, Gh3220Cmd.ECG_PATCH_TIME)
    }

    @Test
    fun `attach is idempotent and handlers stay registered`() = runTest {
        val transport = InMemoryTransport()
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)
        val client = Gh3220ProtocolClient(session)
        client.attach()
        client.attach() // 第二次调用不得重复注册/重置解码基准导致异常

        val frames = mutableListOf<Gh3220RawDataFrame>()
        val collect = launch { client.rawdataFrames.collect { frames.add(it) } }
        testScheduler.runCurrent()
        transport.emit(responseFrame(0x08, bytes(0x00, 0x05, 0x00, 0x01, 0x02, 0x03, 0x04)))
        delay(10)
        collect.cancel()
        assertEquals(1, frames.size) // 每帧恰好一条上报，无重复路由
        session.detach()
    }

    @Test
    fun `startHbd surfaces device status 1 as DeviceError`() = runTest {
        val transport = InMemoryTransport()
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)
        val client = Gh3220ProtocolClient(session)
        client.attach()

        val responder = launch {
            while (transport.sent.isEmpty()) delay(1)
            transport.emit(responseFrame(0x0C, bytes(0x01)))
        }
        val result = client.startHbd(on = true, mode = 0, function = 2)
        responder.join()

        assertTrue(result.isFailure)
        assertIs<com.ghealth.tools.ble.itlvc.core.ItlvcError.CommandError.DeviceError>(result.exceptionOrNull())
        session.detach()
    }

    @Test
    fun `current battery report is decoded and routed`() = runTest {
        val transport = InMemoryTransport()
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)
        val client = Gh3220ProtocolClient(session)
        client.attach()

        val batteries = mutableListOf<Gh3220CurrentBattery>()
        val collect = launch {
            client.currentBattery.collect { batteries.add(it) }
        }
        testScheduler.runCurrent()
        // 0x0D：[cardiff u16le][battery 1B][tx u16le][count u16le]
        transport.emit(responseFrame(0x0D, bytes(0x34, 0x12, 0x3C, 0x01, 0x00, 0x0A, 0x00)))
        delay(10)
        collect.cancel()

        assertEquals(1, batteries.size)
        assertEquals(0x1234, batteries[0].cardiffCurrent)
        assertEquals(0x3C, batteries[0].batteryPercent)
        assertEquals(1, batteries[0].txCurrent)
        assertEquals(10, batteries[0].bleSendPackageCount)
        session.detach()
    }
}
