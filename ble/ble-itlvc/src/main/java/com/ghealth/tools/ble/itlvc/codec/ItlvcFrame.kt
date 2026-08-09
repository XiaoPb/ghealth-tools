package com.ghealth.tools.ble.itlvc.codec

/** ITLVC 通用帧模型：T（类型）+ V（载荷）。I/L/C 由 FrameLayout 负责。 */
data class ItlvcFrame(
    val type: ByteArray,
    val value: ByteArray,
) {
    /** T 字段按大端序解释为 Int（多字节 T 时有用）。 */
    val typeValue: Int get() = type.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }
}
