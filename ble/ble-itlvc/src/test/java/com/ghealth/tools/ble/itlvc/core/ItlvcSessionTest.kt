package com.ghealth.tools.ble.itlvc.core

import com.ghealth.tools.ble.itlvc.codec.ItlvcFrame
import com.ghealth.tools.ble.itlvc.codec.ItlvcFrameCodec
import com.ghealth.tools.ble.itlvc.state.SessionState
import com.ghealth.tools.ble.itlvc.transport.InMemoryTransport
import kotlinx.coroutines.launch
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

}
