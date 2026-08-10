package com.ghealth.tools.ble.itlvc.transport

import kotlinx.coroutines.flow.Flow

/**
 * Notify 驱动传输适配器（通用，零 Android 依赖）。
 *
 * BLE 接入期接线：连接层把 RX 特征 Notify 回调发射为 [notifyFlow]（任意线程回调 →
 * channel 缓冲），本类原样暴露为 [ByteTransport.receive]，由会话接收协程消费；
 * [send] 委托 [writer]（TX 特征写）。发送串行化由调用方（[com.ghealth.tools.ble.itlvc.core.ItlvcSession]
 * 内部 sendMutex）保证，本类不做额外互斥。
 */
class NotifyTransport(
    notifyFlow: Flow<ByteArray>,
    private val writer: (ByteArray) -> Result<Unit>,
    override val mtu: Int,
) : ByteTransport {
    override val receive: Flow<ByteArray> = notifyFlow

    override fun send(data: ByteArray): Result<Unit> = writer(data)
}
