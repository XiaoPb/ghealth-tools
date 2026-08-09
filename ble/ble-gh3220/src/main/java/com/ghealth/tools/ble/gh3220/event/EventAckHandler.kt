package com.ghealth.tools.ble.gh3220.event

import com.ghealth.tools.ble.gh3220.Gh3220Cmd
import com.ghealth.tools.ble.itlvc.core.ItlvcSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 0x16 Cardiff 事件上报处理：收到即解析 → 回 `0x16 + [EventReportID]` ACK → 事件上抛到 [events]。
 * 通过 `ItlvcSession.registerReportHandler` 挂接，不占用请求队列（ACK 用单向 `session.send`）。
 *
 * 畸形 0x16 载荷直接丢弃不中断接收循环；事件流在无订阅或缓冲满时 tryEmit 丢弃，不阻塞接收协程。
 */
class EventAckHandler(private val session: ItlvcSession) {

    private val _events = MutableSharedFlow<Gh3220CardiffEvent>(extraBufferCapacity = 64)
    val events: Flow<Gh3220CardiffEvent> = _events.asSharedFlow()

    /** 注册 0x16 上报处理器；需在 `session.attach(...)` 之后调用。 */
    fun attach() {
        session.registerReportHandler(byteArrayOf(Gh3220Cmd.CHIP_EVENT_REPORT.toByte())) { frame ->
            val parsed = ReportDecoder.decodeCardiffEvent(frame.value).getOrNull() ?: return@registerReportHandler
            session.send(byteArrayOf(Gh3220Cmd.CHIP_EVENT_REPORT.toByte()), byteArrayOf(parsed.eventReportId.toByte()))
            _events.tryEmit(parsed)
        }
    }
}
