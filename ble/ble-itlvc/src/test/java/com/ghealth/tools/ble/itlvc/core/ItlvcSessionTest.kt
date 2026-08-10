package com.ghealth.tools.ble.itlvc.core

import com.ghealth.tools.ble.itlvc.codec.ItlvcFrame
import com.ghealth.tools.ble.itlvc.codec.ItlvcFrameCodec
import com.ghealth.tools.ble.itlvc.state.SessionState
import com.ghealth.tools.ble.itlvc.transport.ByteTransport
import com.ghealth.tools.ble.itlvc.transport.InMemoryTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ItlvcSessionTest {

    private val codec = ItlvcFrameCodec()
    private val connSpec = CommandSpec(type = byteArrayOf(0x1A), timeoutMs = 200)

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private fun responseFrame(type: Int, value: ByteArray): ByteArray =
        codec.encode(ItlvcFrame(bytes(type), value))

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `response arriving during send is matched to pending command`() = runTest(UnconfinedTestDispatcher()) {
        // 真机复现：设备响应 notify 先于 onCharacteristicWrite 回调到达（写入完成前），
        // send() 内部直接注入响应帧，接收协程在 awaiting 挂载前就能处理到它。
        val transport = object : ByteTransport {
            private val rx = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
            override val receive: Flow<ByteArray> = rx
            override val mtu: Int = 240
            override fun send(data: ByteArray): Result<Unit> {
                rx.tryEmit(responseFrame(0x1A, bytes(0x00)))
                return Result.success(Unit)
            }
        }
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)

        val result = session.execute(connSpec, ByteArray(0))

        assertTrue(result.isSuccess, "result: $result")
        assertTrue(result.getOrThrow().contentEquals(bytes(0x00)))
        session.detach()
    }

    @Test
    fun `command roundtrip over in-memory transport`() = runTest {
        val transport = InMemoryTransport()
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)

        val job = launch {
            transport.emit(responseFrame(0x1A, bytes(0x00)))
        }
        val result = session.execute(connSpec, ByteArray(0))
        job.join()

        assertTrue(result.isSuccess, "result: $result")
        assertTrue(result.getOrThrow().contentEquals(bytes(0x00)))
        assertEquals(SessionState.CONNECTED, session.sessionState)
        session.detach()
    }

    @Test
    fun `response timeout fails with CommandError Timeout`() = runTest {
        val transport = InMemoryTransport()
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)

        val result = session.execute(connSpec, ByteArray(0))
        assertTrue(result.isFailure)
        assertIs<ItlvcError.CommandError.Timeout>(result.exceptionOrNull())
        session.detach()
    }

    @Test
    fun `timeout then retry succeeds`() = runTest {
        val transport = InMemoryTransport()
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)

        val spec = CommandSpec(type = byteArrayOf(0x19), timeoutMs = 50, retryCount = 1, retryDelayMs = 10)
        val responder = launch {
            // 第一次超时后重试，第二次回复
            kotlinx.coroutines.delay(80)
            transport.emit(responseFrame(0x19, bytes(0x01, 0x02, 0x41, 0x42)))
        }
        val result = session.execute(spec, bytes(0x01))
        responder.join()

        assertTrue(result.isSuccess, "result: $result")
        assertEquals(2, transport.sent.size)
        session.detach()
    }

    @Test
    fun `pass-through mode rejects non-whitelisted command`() = runTest {
        val transport = InMemoryTransport()
        val session = ItlvcSession(codec, ItlvcConfig(passThroughMode = true))
        session.attach(transport, this)

        val result = session.execute(connSpec, ByteArray(0))
        assertTrue(result.isFailure)
        assertIs<ItlvcError.CommandError.Unsupported>(result.exceptionOrNull())
        assertEquals(0, transport.sent.size)
        session.detach()
    }

    @Test
    fun `send failure surfaces TransportError`() = runTest {
        val transport = InMemoryTransport()
        transport.failSends = true
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)

        val result = session.execute(connSpec, ByteArray(0))
        assertTrue(result.isFailure)
        assertIs<ItlvcError.TransportError>(result.exceptionOrNull())
        session.detach()
    }

    @Test
    fun `report frame routes to registered handler and skips pending match`() = runTest {
        val transport = InMemoryTransport()
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)

        val reports = mutableListOf<Int>()
        session.registerReportHandler(bytes(0x21)) { frame ->
            reports.add(frame.value.size)
        }
        // 先发一个 0x1A 请求，设备先回一条 0x21 日志，再回 0x1A 响应
        val responder = launch {
            transport.emit(responseFrame(0x21, bytes(0x41)))
            transport.emit(responseFrame(0x1A, bytes(0x00)))
        }
        val result = session.execute(connSpec, ByteArray(0))
        responder.join()

        assertTrue(result.isSuccess)
        assertEquals(listOf(1), reports)
        session.detach()
    }

    @Test
    fun `late response after timeout routes to report handler`() = runTest {
        val transport = InMemoryTransport()
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)

        val lateValues = mutableListOf<ByteArray>()
        session.registerReportHandler(bytes(0x1A)) { frame ->
            lateValues.add(frame.value)
        }

        val spec = CommandSpec(type = byteArrayOf(0x1A), timeoutMs = 50)
        val result = session.execute(spec, ByteArray(0))
        assertTrue(result.isFailure)
        assertIs<ItlvcError.CommandError.Timeout>(result.exceptionOrNull())

        transport.emit(responseFrame(0x1A, bytes(0x00)))
        testScheduler.runCurrent()

        assertEquals(1, lateValues.size)
        assertTrue(lateValues[0].contentEquals(bytes(0x00)))
        session.detach()
    }

    @Test
    fun `detach while awaiting returns timeout without hang`() = runTest {
        val transport = InMemoryTransport()
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)

        var result: Result<ByteArray>? = null
        val job = launch {
            result = session.execute(connSpec, ByteArray(0))
        }
        testScheduler.runCurrent()
        session.detach()
        job.join()

        assertIs<ItlvcError.CommandError.Timeout>(result?.exceptionOrNull())
    }

    @Test
    fun `re-attach switches transport`() = runTest {
        val transport1 = InMemoryTransport()
        val transport2 = InMemoryTransport()
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport1, this)
        session.attach(transport2, this)

        val responder = launch {
            transport2.emit(responseFrame(0x19, bytes(0x41)))
        }
        val spec = CommandSpec(type = byteArrayOf(0x19), timeoutMs = 200)
        val result = session.execute(spec, bytes(0x01))
        responder.join()

        assertTrue(result.isSuccess, "result: $result")
        assertEquals(1, transport2.sent.size)
        assertEquals(0, transport1.sent.size)
        session.detach()
    }

    @Test
    fun `cancelled execute rethrows CancellationException`() = runTest {
        val transport = InMemoryTransport()
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)

        var cancelled = false
        val job = launch {
            try {
                session.execute(connSpec, ByteArray(0))
            } catch (e: kotlinx.coroutines.CancellationException) {
                cancelled = true
            }
        }
        testScheduler.runCurrent()
        job.cancel()
        job.join()

        assertTrue(cancelled)
        session.detach()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `cancellation during send clears awaiting so same-type frame routes to report handler`() =
        runTest(UnconfinedTestDispatcher()) {
            val rx = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
            val transport = object : ByteTransport {
                override val receive: Flow<ByteArray> = rx
                override val mtu: Int = 240
                override fun send(data: ByteArray): Result<Unit> {
                    throw CancellationException("send cancelled (test)")
                }
            }
            val session = ItlvcSession(codec, ItlvcConfig())
            session.attach(transport, this)
            val reports = mutableListOf<Int>()
            session.registerReportHandler(bytes(0x1A)) { frame -> reports.add(frame.value.size) }

            val outcome = try {
                session.execute(connSpec, ByteArray(0))
                "no-throw"
            } catch (e: CancellationException) {
                "cancelled"
            }

            assertEquals("cancelled", outcome)
            // 取消后 awaiting 必须已被清理：同类型帧应路由到 report handler，而不是被吞掉
            rx.tryEmit(responseFrame(0x1A, bytes(0x00)))
            assertEquals(listOf(1), reports)
            session.detach()
        }

}
