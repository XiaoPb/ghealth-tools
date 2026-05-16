package com.ghealth.tools.ble.protocol.rpccore

const val FRAME_HEADER_0: Byte = 0xAA.toByte()
const val FRAME_HEADER_1: Byte = 0x11
const val MAX_FRAME_SIZE = 240
const val MAX_KEY_SIZE = 32
const val LAST_FRAME_INDEX: Byte = 0xFF.toByte()

enum class ParseState {
    FrameHeader,
    CheckLength,
    CheckTypeKey,
    CheckKey,
    CheckIndex,
    CheckParam,
    CheckCrc
}

@JvmInline
value class TypeKey(val raw: Byte) {
    val packType: Int get() = (raw.toInt() and 0x03)
    val isArray: Boolean get() = ((raw.toInt() shr 2) and 0x01) != 0
    val width: Int get() = ((raw.toInt() shr 3) and 0x07)
    val isSecure: Boolean get() = ((raw.toInt() shr 6) and 0x01) != 0
    val isFin: Boolean get() = ((raw.toInt() shr 7) and 0x01) != 0

    companion object {
        fun build(
            packType: Int = 1,
            isArray: Boolean = false,
            width: Int = 0,
            secure: Boolean = false,
            fin: Boolean = false
        ): TypeKey {
            var b = packType and 0x03
            if (isArray) b = b or 0x04
            b = b or ((width and 0x07) shl 3)
            if (secure) b = b or 0x40
            if (fin) b = b or 0x80
            return TypeKey(b.toByte())
        }
    }
}

data class FrameIndex(
    var invokeIdx: Byte = 0,
    var frameIdx: Byte = 0
)

data class ParseResult(
    val key: String,
    val param: ByteArray,
    val isSecure: Boolean,
    val isFin: Boolean,
    val invokeIdx: Byte,
    val frameIdx: Byte
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ParseResult) return false
        return key == other.key && param.contentEquals(other.param) &&
            isSecure == other.isSecure && isFin == other.isFin &&
            invokeIdx == other.invokeIdx && frameIdx == other.frameIdx
    }

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + param.contentHashCode()
        return result
    }
}
