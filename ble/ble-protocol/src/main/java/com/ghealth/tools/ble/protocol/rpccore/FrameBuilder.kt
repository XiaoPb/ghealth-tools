package com.ghealth.tools.ble.protocol.rpccore

class FrameBuilder {
    private val buffer = ByteArray(MAX_FRAME_SIZE)
    private var pos = 0

    fun reset(): FrameBuilder { pos = 0; return this }

    fun build(
        key: String,
        param: ByteArray = ByteArray(0),
        secure: Boolean = false,
        fin: Boolean = true,
        invokeIdx: Byte = 0,
        frameIdx: Byte = LAST_FRAME_INDEX
    ): ByteArray {
        reset()
        val isArray = key.length > 1
        val typeKey = TypeKey.build(packType = 3, isArray = isArray, width = 0, secure = secure, fin = fin)
        val keyBytes = if (isArray) byteArrayOf(key.length.toByte()) + key.toByteArray(Charsets.UTF_8)
        else key.toByteArray(Charsets.UTF_8)
        val hasInvokeIdx = secure
        val hasFrameIdx = !fin || (secure && !fin)
        var contentLen = 1 + keyBytes.size + param.size
        if (hasInvokeIdx) contentLen++
        if (hasFrameIdx && frameIdx != LAST_FRAME_INDEX) contentLen++

        buffer[pos++] = FRAME_HEADER_0
        buffer[pos++] = FRAME_HEADER_1
        buffer[pos++] = contentLen.toByte()
        buffer[pos++] = typeKey.raw
        var crc: Byte = typeKey.raw
        for (b in keyBytes) { buffer[pos++] = b; crc = (crc + b).toByte() }
        if (hasInvokeIdx) { buffer[pos++] = invokeIdx; crc = (crc + invokeIdx).toByte() }
        if (hasFrameIdx && frameIdx != LAST_FRAME_INDEX) { buffer[pos++] = frameIdx; crc = (crc + frameIdx).toByte() }
        for (b in param) { buffer[pos++] = b; crc = (crc + b).toByte() }
        buffer[pos++] = crc
        return buffer.copyOf(pos)
    }
}
