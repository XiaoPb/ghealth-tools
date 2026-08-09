package com.ghealth.tools.ble.gh3220

/** GH3220 payload 小端读写工具（协议文档标注"小端"的字段）。 */
object Gh3220Payload {

    fun u8(value: Int): ByteArray {
        require(value in 0..0xFF) { "value out of u8 range: $value" }
        return byteArrayOf((value and 0xFF).toByte())
    }

    fun u16le(value: Int): ByteArray {
        require(value in 0..0xFFFF) { "value out of u16 range: $value" }
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
        )
    }

    fun u32le(value: Long): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )

    fun u32le(value: Int): ByteArray = u32le(value.toLong() and 0xFFFFFFFFL)

    fun readU8(data: ByteArray, offset: Int): Int = data[offset].toInt() and 0xFF

    fun readU16le(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    fun readU32le(data: ByteArray, offset: Int): Long =
        (data[offset].toLong() and 0xFF) or
            ((data[offset + 1].toLong() and 0xFF) shl 8) or
            ((data[offset + 2].toLong() and 0xFF) shl 16) or
            ((data[offset + 3].toLong() and 0xFF) shl 24)
}
