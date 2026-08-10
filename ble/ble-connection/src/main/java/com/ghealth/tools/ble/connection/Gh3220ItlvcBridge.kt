package com.ghealth.tools.ble.connection

import com.ghealth.tools.ble.gh3220.Gh3220Layout
import com.ghealth.tools.ble.gh3220.Gh3220ProtocolClient
import com.ghealth.tools.ble.gh3220.rawdata.RawDataDecoder
import com.ghealth.tools.ble.gh3220.rawdata.SamplingConfig
import com.ghealth.tools.ble.itlvc.codec.ItlvcFrameCodec
import com.ghealth.tools.ble.itlvc.core.ItlvcConfig
import com.ghealth.tools.ble.itlvc.core.ItlvcSession
import com.ghealth.tools.ble.itlvc.transport.NotifyTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * GH3220 新协议（ITLVC 0xAA11）连接桥：把 BLE 连接层的 Notify 流与写委托装配为
 * [ItlvcSession] + [Gh3220ProtocolClient]。一个设备一个实例，随连接创建、断开销毁。
 *
 * - 接收：Kable `peripheral.observe(notifyChar)` 的字节流经 [NotifyTransport] 原样进入会话接收协程；
 * - 发送：帧字节经 [writer] 写 TX 特征（由 `BleConnectionManager.writeToDevice` 提供）；
 * - 生命周期：`attach(scope)` 启动接收协程并注册上报处理器；`detach()` 停止。
 */
class Gh3220ItlvcBridge(
    notifyFlow: Flow<ByteArray>,
    writer: (ByteArray) -> Result<Unit>,
    mtu: Int,
    samplingConfig: SamplingConfig = SamplingConfig(),
) {
    val session = ItlvcSession(ItlvcFrameCodec(Gh3220Layout.layout), ItlvcConfig())
    val client = Gh3220ProtocolClient(session, RawDataDecoder(samplingConfig))
    private val transport = NotifyTransport(notifyFlow, writer, mtu)

    /** 启动接收协程并注册上报处理器；重复调用安全（ItlvcSession.attach 先 detach，client.attach 幂等）。 */
    fun attach(scope: CoroutineScope) {
        session.attach(transport, scope)
        client.attach()
    }

    fun detach() {
        session.detach()
    }
}
