package com.ghealth.tools.ble.gh3220.event

import com.ghealth.tools.ble.gh3220.Gh3220Cmd
import com.ghealth.tools.ble.itlvc.codec.ItlvcFrame
import com.ghealth.tools.ble.itlvc.codec.ItlvcFrameCodec
import com.ghealth.tools.ble.itlvc.core.ItlvcConfig
import com.ghealth.tools.ble.itlvc.core.ItlvcSession
import com.ghealth.tools.ble.itlvc.transport.InMemoryTransport
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventAckHandlerTest {

    private val codec = ItlvcFrameCodec()
    private val session = ItlvcSession(codec, ItlvcConfig())

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private fun responseFrame(type: Int, value: ByteArray): ByteArray =
        codec.encode(ItlvcFrame(bytes(type), value))

    @Test
    fun `0x16 report is acked and forwarded`() = runTest {
        val transport = InMemoryTransport()
        session.attach(transport, this)
        val handler = EventAckHandler(session)
        handler.attach()

        // runTest 虚拟时间下 receiver 协程尚未订阅；replay=0 的 SharedFlow 会丢弃无订阅者时的
        // emit 数据，故先 runCurrent() 完成订阅再发射（与 InMemoryTransportTest 同款约定）。
        testScheduler.runCurrent()
        transport.emit(responseFrame(Gh3220Cmd.CHIP_EVENT_REPORT, bytes(0x00, 0x02, 0x03)))

        val event = handler.events.first()
        assertEquals(0x0002, event.event)
        assertEquals(3, event.eventReportId)
        // 自动 ACK：0x16 + [EventReportID]
        assertEquals(1, transport.sent.size)
        assertContentEquals(
            codec.encode(ItlvcFrame(bytes(Gh3220Cmd.CHIP_EVENT_REPORT), bytes(0x03))),
            transport.sent[0],
        )
        session.detach()
    }

    @Test
    fun `report decoder parses 0x14 device event`() {
        val result = ReportDecoder.decodeDeviceEvent(bytes(0x05, 0x78, 0x56, 0x34, 0x12))
        assertTrue(result.isSuccess)
        val event = result.getOrThrow()
        assertEquals(0x05, event.eventId)
        assertEquals(0x12345678L, event.info)
    }

    @Test
    fun `report decoder parses 0x0D current battery`() {
        val result = ReportDecoder.decodeCurrentBattery(bytes(0x34, 0x12, 0x64, 0x78, 0x56, 0x03, 0x00))
        assertTrue(result.isSuccess)
        val info = result.getOrThrow()
        assertEquals(0x1234, info.cardiffCurrent)
        assertEquals(100, info.batteryPercent)
        assertEquals(0x5678, info.txCurrent)
        assertEquals(0x0003, info.bleSendPackageCount)
    }

    @Test
    fun `report decoder parses 0x21 slave log`() {
        val result = ReportDecoder.decodeSlaveLog("ABC".toByteArray(Charsets.UTF_8))
        assertTrue(result.isSuccess)
        assertEquals("ABC", result.getOrThrow().text)
    }

    @Test
    fun `malformed 0x16 report does not kill session`() = runTest {
        val transport = InMemoryTransport()
        session.attach(transport, this)
        val handler = EventAckHandler(session)
        handler.attach()
        testScheduler.runCurrent()

        // 畸形 0x16：payload 只有 2 字节（<3），应被丢弃且不发送 ACK
        transport.emit(responseFrame(Gh3220Cmd.CHIP_EVENT_REPORT, bytes(0x00, 0x02)))
        testScheduler.runCurrent()
        assertEquals(0, transport.sent.size)

        // 订阅事件流后再发合法帧：接收协程必须存活
        val deferred = async { handler.events.first() }
        testScheduler.runCurrent()
        transport.emit(responseFrame(Gh3220Cmd.CHIP_EVENT_REPORT, bytes(0x00, 0x02, 0x03)))
        val event = deferred.await()
        assertEquals(0x0002, event.event)
        assertEquals(3, event.eventReportId)
        assertEquals(1, transport.sent.size)
        session.detach()
    }
}
