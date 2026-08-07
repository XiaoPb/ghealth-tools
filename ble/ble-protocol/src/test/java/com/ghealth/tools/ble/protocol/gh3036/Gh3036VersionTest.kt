package com.ghealth.tools.ble.protocol.gh3036

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Gh3036VersionTest {

    @Test
    fun `parse valid version string`() {
        // len=5 (LE), "1.2.3"
        val data = byteArrayOf(0x05, 0x00, 0x31, 0x2E, 0x32, 0x2E, 0x33)
        assertEquals("1.2.3", parseGh3036VersionString(data))
    }

    @Test
    fun `parse returns no_ver when data shorter than two bytes`() {
        assertEquals("no_ver", parseGh3036VersionString(byteArrayOf()))
        assertEquals("no_ver", parseGh3036VersionString(byteArrayOf(0x05)))
    }

    @Test
    fun `parse returns no_ver when declared length is zero`() {
        assertEquals("no_ver", parseGh3036VersionString(byteArrayOf(0x00, 0x00)))
    }

    @Test
    fun `parse returns no_ver when declared length exceeds available bytes`() {
        // len=10 but only 3 payload bytes
        val data = byteArrayOf(0x0A, 0x00, 0x31, 0x2E, 0x32)
        assertEquals("no_ver", parseGh3036VersionString(data))
    }

    @Test
    fun `parse trims trailing whitespace`() {
        // "1.0 " (trailing space 0x20)
        val data = byteArrayOf(0x04, 0x00, 0x31, 0x2E, 0x30, 0x20)
        assertEquals("1.0", parseGh3036VersionString(data))
    }

    @Test
    fun `parse returns no_ver when payload is whitespace only`() {
        // single space
        val data = byteArrayOf(0x01, 0x00, 0x20)
        assertEquals("no_ver", parseGh3036VersionString(data))
    }

    @Test
    fun `parse returns no_ver when payload is NUL only`() {
        // len=1，payload=0x00（设备返回空版本缓冲区，等价于空字符串）
        val data = byteArrayOf(0x01, 0x00, 0x00)
        assertEquals("no_ver", parseGh3036VersionString(data))
    }

    @Test
    fun `parse returns no_ver when payload is NULs and whitespace`() {
        // "\u0000 \u0000"
        val data = byteArrayOf(0x03, 0x00, 0x00, 0x20, 0x00)
        assertEquals("no_ver", parseGh3036VersionString(data))
    }

    @Test
    fun `parse trims trailing NUL bytes`() {
        // "1.0\0" -> "1.0"
        val data = byteArrayOf(0x04, 0x00, 0x31, 0x2E, 0x30, 0x00)
        assertEquals("1.0", parseGh3036VersionString(data))
    }

    @Test
    fun `parse interprets length as little-endian`() {
        // len=3 LE = [0x03, 0x00]；若误用 BE 则 len=768 越界返回 no_ver，可区分方向
        val data = byteArrayOf(0x03, 0x00, 0x41, 0x42, 0x43)  // "ABC"
        assertEquals("ABC", parseGh3036VersionString(data))
    }
}
