package com.ghealth.tools.ble.itlvc.codec

/** 帧编解码接口。decode 由 ReceiveStateMachine 按 layout 增量完成。 */
interface FrameCodec {
    val layout: FrameLayout
    fun encode(frame: ItlvcFrame): ByteArray
}
