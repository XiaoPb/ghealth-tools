package com.ghealth.tools.ble.itlvc.transport

import kotlinx.coroutines.flow.Flow

/**
 * 传输抽象。上位机侧接收由 Notify 回调驱动：适配器把每个数据块发射到 [receive]，
 * 会话在单一接收协程中 collect 并喂入接收状态机；发送走 [send]（调用方保证串行）。
 */
interface ByteTransport {
    /** MTU 提示（字节），供上层分片参考。 */
    val mtu: Int

    fun send(data: ByteArray): Result<Unit>

    val receive: Flow<ByteArray>
}
