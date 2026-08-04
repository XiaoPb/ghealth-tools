package com.ghealth.tools.feature.connection

import org.junit.jupiter.api.Assertions.assertEquals
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
}
