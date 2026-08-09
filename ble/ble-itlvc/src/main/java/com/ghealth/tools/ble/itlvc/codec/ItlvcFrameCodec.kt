package com.ghealth.tools.ble.itlvc.codec

/** 按 [FrameLayout] 编码 ITLVC 帧：I + T + L + V (+ C)。默认布局即 GH3220 布局。 */
class ItlvcFrameCodec(
    override val layout: FrameLayout = FrameLayout.GH3220,
) : FrameCodec {
    override fun encode(frame: ItlvcFrame): ByteArray {
        require(frame.type.size == layout.typeBytes) { "type size ${frame.type.size} != ${layout.typeBytes}" }
        require(frame.value.size <= layout.maxValueLen) { "value size ${frame.value.size} exceeds max ${layout.maxValueLen}" }
        val body = layout.idBytes + frame.type + layout.encodeLen(frame.value.size) + frame.value
        val checksum = layout.checksum?.compute(body) ?: ByteArray(0)
        return body + checksum
    }
}
