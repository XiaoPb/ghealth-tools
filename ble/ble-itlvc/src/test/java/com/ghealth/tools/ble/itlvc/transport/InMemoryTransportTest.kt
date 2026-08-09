package com.ghealth.tools.ble.itlvc.transport

import com.ghealth.tools.ble.itlvc.core.ItlvcError
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InMemoryTransportTest {

    @Test
    fun `send records bytes`() = runTest {
        val t = InMemoryTransport()
        assertTrue(t.send(byteArrayOf(1, 2, 3)).isSuccess)
        assertEquals(1, t.sent.size)
        assertTrue(t.sent[0].contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `failSends returns TransportError`() = runTest {
        val t = InMemoryTransport()
        t.failSends = true
        val result = t.send(byteArrayOf(1))
        assertTrue(result.isFailure)
        assertIs<ItlvcError.TransportError>(result.exceptionOrNull())
    }

    @Test
    fun `emit delivers bytes to receive flow`() = runTest {
        val t = InMemoryTransport()
        val received = mutableListOf<ByteArray>()
        val job = launch {
            t.receive.collect { received.add(it) }
        }
        // runTest 虚拟时间下收集协程尚未启动；replay=0 的 SharedFlow 在无订阅者时
        // 会丢弃 emit 的数据，故先 runCurrent() 让收集协程完成订阅再发射。
        testScheduler.runCurrent()
        t.emit(byteArrayOf(0xAA.toByte(), 0x11))
        t.emitBytes(0x1A, 0x00)
        delay(10)
        job.cancel()
        assertEquals(2, received.size)
        assertTrue(received[0].contentEquals(byteArrayOf(0xAA.toByte(), 0x11)))
        assertTrue(received[1].contentEquals(byteArrayOf(0x1A, 0x00)))
    }
}
