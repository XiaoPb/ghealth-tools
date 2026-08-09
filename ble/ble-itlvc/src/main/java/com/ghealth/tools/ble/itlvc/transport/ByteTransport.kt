package com.ghealth.tools.ble.itlvc.transport

import kotlinx.coroutines.flow.Flow

/**
 * 传输抽象。上位机侧接收由 Notify 回调驱动：适配器把每个数据块发射到 [receive]，
 * 会话在单一接收协程中 collect 并喂入接收状态机；发送走 [send]（调用方保证串行）。
 *
 * [send] 契约：
 * - [send] 调用顺序必须保留：调用方串行发起，适配器/底层按序提交，不得乱序；
 * - 返回成功仅表示字节已被接受/进入发送队列，不代表对端已收到；
 * - 异步写错误（如 BLE 写回调失败）不得通过同步 [send] 结果返回，而应由适配器
 *   通过 [receive] 或会话事件向上层暴露。
 */
interface ByteTransport {
    /** MTU 提示：单次 [send] 可携带的最大负载字节数（写负载，而非原始 ATT MTU），供上层分片参考。 */
    val mtu: Int

    fun send(data: ByteArray): Result<Unit>

    val receive: Flow<ByteArray>
}
