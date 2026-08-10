package com.ghealth.tools.ble.connection

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FirmwareVersionResolverTest {

    @Test
    fun `BLE 版本 0x09 成功时返回其版本且不回退到 0x01`() = runBlocking {
        val calls = mutableListOf<Byte>()
        val fetchRaw: suspend (Byte) -> ByteArray? = { verType ->
            calls.add(verType)
            if (verType == 0x09.toByte()) encodeVersion("BLE_1.0") else null
        }

        val result = resolveFirmwareVersion(fetchRaw)

        assertEquals("BLE_1.0", result)
        assertEquals(listOf<Byte>(0x09.toByte()), calls)
    }

    @Test
    fun `0x09 返回 null 时回退到 0x01 并返回 0x01 的版本`() = runBlocking {
        val calls = mutableListOf<Byte>()
        val fetchRaw: suspend (Byte) -> ByteArray? = { verType ->
            calls.add(verType)
            when (verType) {
                0x09.toByte() -> null
                0x01.toByte() -> encodeVersion("FW_2.0")
                else -> null
            }
        }

        val result = resolveFirmwareVersion(fetchRaw)

        assertEquals("FW_2.0", result)
        assertEquals(listOf<Byte>(0x09.toByte(), 0x01.toByte()), calls)
    }

    @Test
    fun `0x09 响应解析为 no_ver 时回退到 0x01`() = runBlocking {
        val fetchRaw: suspend (Byte) -> ByteArray? = { verType ->
            when (verType) {
                0x09.toByte() -> encodeVersion("") // len=0 -> parseGh3036VersionString 返回 "no_ver"
                0x01.toByte() -> encodeVersion("FW_2.0")
                else -> null
            }
        }

        val result = resolveFirmwareVersion(fetchRaw)

        assertEquals("FW_2.0", result)
    }

    @Test
    fun `0x09 与 0x01 都返回 null 时返回 null`() = runBlocking {
        val fetchRaw: suspend (Byte) -> ByteArray? = { null }

        val result = resolveFirmwareVersion(fetchRaw)

        assertNull(result)
    }

    @Test
    fun `0x09 与 0x01 都解析为 no_ver 时返回 null`() = runBlocking {
        val fetchRaw: suspend (Byte) -> ByteArray? = { encodeVersion("") }

        val result = resolveFirmwareVersion(fetchRaw)

        assertNull(result)
    }

    @Test
    fun `0x09 响应为 NUL 空字符时回退到 0x01`() = runBlocking {
        val calls = mutableListOf<Byte>()
        val fetchRaw: suspend (Byte) -> ByteArray? = { verType ->
            calls.add(verType)
            when (verType) {
                0x09.toByte() -> byteArrayOf(0x01, 0x00, 0x00) // 空版本（NUL）-> parseGh3036VersionString 返回 "no_ver"
                0x01.toByte() -> encodeVersion("FW_2.0")
                else -> null
            }
        }

        val result = resolveFirmwareVersion(fetchRaw)

        assertEquals("FW_2.0", result)
        assertEquals(listOf<Byte>(0x09.toByte(), 0x01.toByte()), calls)
    }

    /** 构造 GH3X_GetVersion 响应字节：[len_lo, len_hi, ...UTF-8 字节]，与 parseGh3036VersionString 约定一致。 */
    private fun encodeVersion(text: String): ByteArray {
        val bytes = text.toByteArray(Charsets.UTF_8)
        return byteArrayOf(
            (bytes.size and 0xFF).toByte(),
            ((bytes.size shr 8) and 0xFF).toByte()
        ) + bytes
    }

    @Test
    fun `GH3220 版本解析 0x19 verType len text`() = runBlocking {
        val raw = byteArrayOf(0x01, 0x06) + "EVK_12".toByteArray()
        val fetchRaw: suspend (Byte) -> ByteArray? = { if (it == 0x01.toByte()) raw else null }
        assertEquals("EVK_12", resolveGh3220Version(fetchRaw))
    }

    @Test
    fun `GH3220 版本读取失败返回 null`() = runBlocking {
        val fetchRaw: suspend (Byte) -> ByteArray? = { null }
        assertNull(resolveGh3220Version(fetchRaw))
    }
}
