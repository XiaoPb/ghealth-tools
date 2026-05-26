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
        val typeKey = TypeKey.build(packType = 2, isArray = isArray, width = 3, secure = secure, fin = fin)
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

    fun buildMultiFrame(
        key: String,
        param: ByteArray,
        secure: Boolean = false,
        invokeIdx: Byte = 0
    ): List<ByteArray> {
        val isArray = key.length > 1
        val keyBytes = if (isArray) byteArrayOf(key.length.toByte()) + key.toByteArray(Charsets.UTF_8)
        else key.toByteArray(Charsets.UTF_8)

        // Overhead for intermediate frames (with frameIdx):
        //   2 (headers) + 1 (length) + 1 (typeKey) + keyBytes + 1 (CRC) + frameIdx
        //   + (secure ? 1 : 0) for invokeIdx
        val intOverhead = 6 + keyBytes.size + (if (secure) 1 else 0)
        // Final frame: no frameIdx, but offset may differ
        val finalOverhead = 5 + keyBytes.size + (if (secure) 1 else 0)

        val maxPayloadInt = MAX_FRAME_SIZE - intOverhead
        val maxPayloadFinal = MAX_FRAME_SIZE - finalOverhead

        if (maxPayloadInt <= 0 || maxPayloadFinal <= 0) {
            // Key too long to fit in a single frame — fall back to single-frame attempt
            return listOf(build(key, param, secure, fin = true, invokeIdx))
        }

        if (param.size <= maxPayloadFinal) {
            return listOf(build(key, param, secure, fin = true, invokeIdx))
        }

        val frames = mutableListOf<ByteArray>()
        var offset = 0
        var frameIdx: Byte = 0

        while (offset < param.size) {
            val remaining = param.size - offset
            val isLast = remaining <= maxPayloadFinal
            val maxForThisFrame = if (isLast) maxPayloadFinal else maxPayloadInt
            val chunkSize = minOf(remaining, maxForThisFrame)
            val chunk = param.copyOfRange(offset, offset + chunkSize)

            frames.add(
                build(
                    key = key,
                    param = chunk,
                    secure = secure,
                    fin = isLast,
                    invokeIdx = invokeIdx,
                    frameIdx = if (isLast) LAST_FRAME_INDEX else frameIdx
                )
            )

            offset += chunkSize
            frameIdx = (frameIdx + 1).toByte()
        }

        return frames
    }
}
