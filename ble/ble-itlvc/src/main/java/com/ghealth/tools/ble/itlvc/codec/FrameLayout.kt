package com.ghealth.tools.ble.itlvc.codec

/**
 * ITLVC 帧布局配置。所有字段宽度可配置，C（校验）可选。
 *
 * @param idBytes   固定标识前缀 I（如 GH3220 的 0xAA 0x11）
 * @param typeBytes T 字段宽度（Byte）
 * @param lenBytes  L 字段宽度（Byte），大端序
 * @param maxValueLen V 字段最大长度（Byte）
 * @param checksum  校验算法；null 表示无校验
 */
data class FrameLayout(
    val idBytes: ByteArray,
    val typeBytes: Int = 1,
    val lenBytes: Int = 1,
    val maxValueLen: Int = 238,
    val checksum: Checksum? = Crc8,
) {
    val checksumLen: Int get() = checksum?.size ?: 0
    val headerLen: Int get() = idBytes.size + typeBytes + lenBytes

    fun encodeLen(len: Int): ByteArray {
        require(len >= 0) { "negative length: $len" }
        require(len <= (1L shl (lenBytes * 8)) - 1) { "length $len exceeds $lenBytes-byte L field" }
        return ByteArray(lenBytes) { i -> ((len shr ((lenBytes - 1 - i) * 8)) and 0xFF).toByte() }
    }

    companion object {
        /** GH3220 固定布局：I=0xAA 0x11，T=1，L=1，V≤238，C=CRC8。 */
        val GH3220 = FrameLayout(
            idBytes = byteArrayOf(0xAA.toByte(), 0x11.toByte()),
            typeBytes = 1,
            lenBytes = 1,
            maxValueLen = 238,
            checksum = Crc8,
        )
    }
}
