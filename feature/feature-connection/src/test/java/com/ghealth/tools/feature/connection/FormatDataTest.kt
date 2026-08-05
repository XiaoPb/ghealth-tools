package com.ghealth.tools.feature.connection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FormatDataTest {

    @Test
    fun `hex format without line break joins bytes with single space`() {
        val data = byteArrayOf(0x00, 0x0F, 0xFF.toByte(), 0xAB.toByte())
        assertEquals("00 0F FF AB", formatData(data, asHex = true))
    }

    @Test
    fun `decimal format without line break joins decimal values`() {
        val data = byteArrayOf(0x00, 0x0F, 0xFF.toByte())
        assertEquals("0 15 255", formatData(data, asHex = false))
    }

    @Test
    fun `hex format with bytesPerLine wraps every N bytes with newline`() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06)
        assertEquals("01 02\n03 04\n05 06", formatData(data, asHex = true, bytesPerLine = 2))
    }

    @Test
    fun `hex format with bytesPerLine 16 stays single line when bytes fewer than 16`() {
        val data = byteArrayOf(0x01, 0x02, 0x03)
        assertEquals("01 02 03", formatData(data, asHex = true, bytesPerLine = 16))
    }

    @Test
    fun `bytesPerLine zero means no wrapping`() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        assertEquals("01 02 03 04", formatData(data, asHex = true, bytesPerLine = 0))
    }

    @Test
    fun `empty byte array returns empty string`() {
        assertEquals("", formatData(byteArrayOf(), asHex = true, bytesPerLine = 16))
    }

    @Test
    fun `decimal format also respects bytesPerLine wrapping`() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        assertEquals("1 2\n3 4", formatData(data, asHex = false, bytesPerLine = 2))
    }

    // ── formatU16Array / registerReadCount ─────────────────────────

    @Test
    fun `u16 array hex format joins all elements padded to 4 hex digits`() {
        // U16 小端：0x0123 -> bytes [0x23, 0x01]
        val data = byteArrayOf(0x23, 0x01, 0xFF.toByte(), 0x00)
        assertEquals("0x0123, 0x00FF", formatU16Array(data, asHex = true))
    }

    @Test
    fun `u16 array decimal format joins decimal values`() {
        val data = byteArrayOf(0x23, 0x01, 0xFF.toByte(), 0x00)
        assertEquals("291, 255", formatU16Array(data, asHex = false))
    }

    @Test
    fun `u16 array with skipFirstAsCount drops first element`() {
        // 首项 = 0x0002 (数量 2)，后跟两个寄存器值
        val data = byteArrayOf(0x02, 0x00, 0x23, 0x01, 0xFF.toByte(), 0x00)
        assertEquals("0x0123, 0x00FF", formatU16Array(data, asHex = true, skipFirstAsCount = true))
    }

    @Test
    fun `u16 array without skipFirstAsCount keeps all elements`() {
        val data = byteArrayOf(0x02, 0x00, 0x23, 0x01)
        assertEquals("0x0002, 0x0123", formatU16Array(data, asHex = true, skipFirstAsCount = false))
    }

    @Test
    fun `u16 array with only count and skip returns empty string`() {
        // 仅 1 个 U16（数量字段），skip 后无数据
        val data = byteArrayOf(0x01, 0x00)
        assertEquals("", formatU16Array(data, asHex = true, skipFirstAsCount = true))
    }

    @Test
    fun `u16 array empty data returns empty string`() {
        assertEquals("", formatU16Array(byteArrayOf(), asHex = true))
    }

    @Test
    fun `u16 array odd byte count ignores trailing byte`() {
        // 3 字节 -> 1 个完整 U16 + 1 个孤立字节（被忽略）
        val data = byteArrayOf(0x23, 0x01, 0xFF.toByte())
        assertEquals("0x0123", formatU16Array(data, asHex = true))
    }

    @Test
    fun `registerReadCount returns first u16 as count`() {
        val data = byteArrayOf(0x02, 0x00, 0x23, 0x01, 0xFF.toByte(), 0x00)
        assertEquals(2, registerReadCount(data))
    }

    @Test
    fun `registerReadCount returns null for empty data`() {
        assertNull(registerReadCount(byteArrayOf()))
    }

    @Test
    fun `registerReadCount returns null for single byte`() {
        assertNull(registerReadCount(byteArrayOf(0x02)))
    }
}
