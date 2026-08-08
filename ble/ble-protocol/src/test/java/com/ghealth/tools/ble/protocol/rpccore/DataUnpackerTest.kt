package com.ghealth.tools.ble.protocol.rpccore

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DataUnpackerTest {

    // 对齐 C 端 gh_package.c packageArray：u8 数组 header = pack_type(01)|is_array(1)|width(3)|end(1)
    // 0x5D = split 0，0xDD = split 1
    private fun u8Header(split: Boolean): Byte =
        (0x5D or if (split) 0x80 else 0x00).toByte()

    @Test
    fun `unpackU8Array concatenates split chunks over 255 elements`() {
        val total = 300
        val firstLen = 255
        val secondLen = total - firstLen
        val payload = byteArrayOf(u8Header(true), firstLen.toByte()) +
            ByteArray(firstLen) { it.toByte() } +
            byteArrayOf(u8Header(false), secondLen.toByte()) +
            ByteArray(secondLen) { (firstLen + it).toByte() }

        val unpacked = unpackU8Array(payload).toByteArray()

        assertEquals(total, unpacked.size)
        assertArrayEquals(ByteArray(total) { it.toByte() }, unpacked)
    }

    @Test
    fun `unpackU8Array keeps single chunk arrays unchanged`() {
        val payload = byteArrayOf(u8Header(false), 3) + byteArrayOf(1, 2, 3)
        assertArrayEquals(byteArrayOf(1, 2, 3), unpackU8Array(payload).toByteArray())
    }

    @Test
    fun `unpackU8Array rejects split chunk with mismatched header`() {
        // 第二块头 0x5F 与首块 0xDD 除 split 位外不一致（pack_type/width 不同）
        val payload = byteArrayOf(u8Header(true), 1, 42, 0x5F, 1, 43)
        assertTrue(unpackU8Array(payload).isEmpty())
    }

    @Test
    fun `unpackU8Array rejects truncated split chunk`() {
        val payload = byteArrayOf(u8Header(true), 255.toByte()) + ByteArray(100)
        assertTrue(unpackU8Array(payload).isEmpty())
    }

    @Test
    fun `unpackU16Array concatenates split chunks over 255 elements`() {
        // u16: width=4 -> header 0x65(end=1)，split -> 0xE5
        val total = 300
        val firstLen = 255
        val secondLen = total - firstLen
        val data = ByteArray(total * 2)
        for (i in 0 until total) {
            data[i * 2] = (i and 0xFF).toByte()
            data[i * 2 + 1] = ((i shr 8) and 0xFF).toByte()
        }
        val payload = byteArrayOf(0xE5.toByte(), firstLen.toByte()) +
            data.copyOfRange(0, firstLen * 2) +
            byteArrayOf(0x65.toByte(), secondLen.toByte()) +
            data.copyOfRange(firstLen * 2, total * 2)

        val unpacked = unpackU16Array(payload)

        assertEquals(total, unpacked.size)
        for (i in 0 until total) {
            assertEquals(i.toUShort(), unpacked[i])
        }
    }
}