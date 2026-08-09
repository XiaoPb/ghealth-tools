package com.ghealth.tools.ble.itlvc.codec

import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class ItlvcFrameCodecTest {

    private val codec = ItlvcFrameCodec()

    @Test
    fun `encode gh3220 frame with checksum`() {
        // 0x1A 无 payload：AA 11 1A 00 + CRC(AE)
        val frame = ItlvcFrame(type = byteArrayOf(0x1A), value = ByteArray(0))
        assertContentEquals(
            byteArrayOf(0xAA.toByte(), 0x11, 0x1A, 0x00, 0xAE.toByte()),
            codec.encode(frame),
        )
    }

    @Test
    fun `encode gh3220 version query`() {
        val frame = ItlvcFrame(type = byteArrayOf(0x19), value = byteArrayOf(0x01))
        assertContentEquals(
            byteArrayOf(0xAA.toByte(), 0x11, 0x19, 0x01, 0x01, 0xEC.toByte()),
            codec.encode(frame),
        )
    }

    @Test
    fun `encode without checksum omits C`() {
        val noChecksum = ItlvcFrameCodec(
            FrameLayout(idBytes = byteArrayOf(0xAA.toByte(), 0x11.toByte()), checksum = null),
        )
        val frame = ItlvcFrame(type = byteArrayOf(0x1A), value = ByteArray(0))
        assertContentEquals(
            byteArrayOf(0xAA.toByte(), 0x11, 0x1A, 0x00),
            noChecksum.encode(frame),
        )
    }

    @Test
    fun `encode multi-byte L field`() {
        val codec2 = ItlvcFrameCodec(
            FrameLayout(idBytes = byteArrayOf(0xAA.toByte(), 0x11.toByte()), lenBytes = 2, checksum = null),
        )
        val frame = ItlvcFrame(type = byteArrayOf(0x01), value = byteArrayOf(1, 2, 3, 4, 5))
        assertContentEquals(
            byteArrayOf(0xAA.toByte(), 0x11, 0x01, 0x00, 0x05, 1, 2, 3, 4, 5),
            codec2.encode(frame),
        )
    }

    @Test
    fun `encode rejects invalid type size and oversized value`() {
        assertFailsWith<IllegalArgumentException> { codec.encode(ItlvcFrame(byteArrayOf(1, 2), ByteArray(0))) }
        assertFailsWith<IllegalArgumentException> {
            codec.encode(ItlvcFrame(byteArrayOf(1), ByteArray(239)))
        }
    }
}
