package com.ghealth.tools.ble.itlvc.transport

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotifyTransportTest {

    @Test
    fun `receive forwards every notify chunk`() = runTest {
        val notify = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
        val transport = NotifyTransport(notify, { Result.success(Unit) }, mtu = 240)
        val received = mutableListOf<ByteArray>()
        val collect = launch { transport.receive.collect { received.add(it) } }
        testScheduler.runCurrent()
        notify.emit(byteArrayOf(0x01))
        notify.emit(byteArrayOf(0x02, 0x03))
        testScheduler.runCurrent()
        collect.cancel()
        assertEquals(2, received.size)
        assertContentEquals(byteArrayOf(0x01), received[0])
        assertContentEquals(byteArrayOf(0x02, 0x03), received[1])
    }

    @Test
    fun `send delegates to writer and propagates result`() = runTest {
        val written = mutableListOf<ByteArray>()
        val transport = NotifyTransport(
            MutableSharedFlow(),
            { data -> written.add(data); Result.success(Unit) },
            mtu = 20,
        )
        assertTrue(transport.send(byteArrayOf(0xAA.toByte())).isSuccess)
        assertContentEquals(byteArrayOf(0xAA.toByte()), written.single())
        assertEquals(20, transport.mtu)
    }
}
