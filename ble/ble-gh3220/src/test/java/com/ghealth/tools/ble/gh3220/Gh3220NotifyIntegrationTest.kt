package com.ghealth.tools.ble.gh3220

import com.ghealth.tools.ble.gh3220.rawdata.Gh3220RawDataFrame
import com.ghealth.tools.ble.itlvc.codec.ItlvcFrame
import com.ghealth.tools.ble.itlvc.codec.ItlvcFrameCodec
import com.ghealth.tools.ble.itlvc.core.ItlvcConfig
import com.ghealth.tools.ble.itlvc.core.ItlvcSession
import com.ghealth.tools.ble.itlvc.transport.NotifyTransport
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 验证 Gh3220ProtocolClient 通过 NotifyTransport 装配的真实接收路径（Notify 分片/粘包）。 */
class Gh3220NotifyIntegrationTest {

    private val codec = ItlvcFrameCodec()

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private fun frame(type: Int, value: ByteArray): ByteArray =
        codec.encode(ItlvcFrame(bytes(type), value))

    @Test
    fun `command response reassembled from fragmented notify chunks`() = runTest {
        val notify = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
        val written = mutableListOf<ByteArray>()
        val transport = NotifyTransport(notify, { data -> written.add(data); Result.success(Unit) }, mtu = 240)
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)
        val client = Gh3220ProtocolClient(session)
        client.attach()

        val job = async { client.getConnectionStatus() }
        testScheduler.runCurrent()
        assertContentEquals(frame(0x1A, ByteArray(0)), written.single())

        val resp = frame(0x1A, bytes(0x00))
        notify.emit(resp.copyOfRange(0, 2))          // 分片 1：AA 11
        notify.emit(resp.copyOfRange(2, resp.size))  // 分片 2：剩余
        val result = job.await()
        assertTrue(result.isSuccess)
        session.detach()
    }

    @Test
    fun `coalesced notify chunk with command response and event report`() = runTest {
        val notify = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
        val written = mutableListOf<ByteArray>()
        val transport = NotifyTransport(notify, { data -> written.add(data); Result.success(Unit) }, mtu = 240)
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)
        val client = Gh3220ProtocolClient(session)
        client.attach()

        val events = mutableListOf<Any>()
        val collect = launch { client.cardiffEvents.collect { events.add(it) } }
        val job = async { client.getConnectionStatus() }
        testScheduler.runCurrent()

        // 一次 Notify 含两帧：0x1A 响应 + 0x16 事件上报
        val concat = frame(0x1A, bytes(0x00)) + frame(0x16, bytes(0x00, 0x02, 0x03))
        notify.emit(concat)

        assertTrue(job.await().isSuccess)
        assertEquals(1, events.size)
        // 0x16 自动 ACK 已写入 TX
        assertTrue(written.any { it.contentEquals(frame(0x16, bytes(0x03))) })
        collect.cancel()
        session.detach()
    }

    @Test
    fun `rawdata report decoded from notify chunks split mid-frame`() = runTest {
        val notify = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
        val transport = NotifyTransport(notify, { Result.success(Unit) }, mtu = 240)
        val session = ItlvcSession(codec, ItlvcConfig())
        session.attach(transport, this)
        val client = Gh3220ProtocolClient(session)
        client.attach()

        val frames = mutableListOf<Gh3220RawDataFrame>()
        val collect = launch { client.rawdataFrames.collect { frames.add(it) } }
        testScheduler.runCurrent()

        val resp = frame(0x08, bytes(0x00, 0x05, 0x00, 0x01, 0x02, 0x03, 0x04))
        notify.emit(resp.copyOfRange(0, 5))
        notify.emit(resp.copyOfRange(5, resp.size))
        testScheduler.runCurrent()
        collect.cancel()

        assertEquals(1, frames.size)
        assertContentEquals(intArrayOf(0x01020304), frames[0].rawdata)
        session.detach()
    }
}
