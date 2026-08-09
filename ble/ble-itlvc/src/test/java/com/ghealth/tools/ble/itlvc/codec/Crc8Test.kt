package com.ghealth.tools.ble.itlvc.codec

import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class Crc8Test {

    @Test
    fun `crc8 table first 16 entries match device C table`() {
        // 设备端 gh_uprotocol.c g_uchCrc8TabArr 前 16 项
        val expected = intArrayOf(
            0x00, 0x07, 0x0E, 0x09, 0x1C, 0x1B, 0x12, 0x15,
            0x38, 0x3F, 0x36, 0x31, 0x24, 0x23, 0x2A, 0x2D,
        )
        val actual = IntArray(16) { seed ->
            var crc = seed
            repeat(8) {
                crc = if (crc and 0x80 != 0) ((crc shl 1) xor 0x07) and 0xFF else (crc shl 1) and 0xFF
            }
            crc
        }
        assertContentEquals(expected, actual)
    }

    @Test
    fun `crc8 known vectors`() {
        val vectors = listOf(
            byteArrayOf(0xAA.toByte()) to 0xAC,
            byteArrayOf(0xAA.toByte(), 0x11) to 0x3A,
            byteArrayOf(0xAA.toByte(), 0x11, 0x1A, 0x00) to 0xAE,
            byteArrayOf(0xAA.toByte(), 0x11, 0x19, 0x01, 0x01) to 0xEC,
            byteArrayOf(0xAA.toByte(), 0x11, 0x05, 0x04, 0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()) to 0x70,
            byteArrayOf(0xAA.toByte(), 0x11, 0x03, 0x04, 0x00, 0x01, 0x00, 0x00) to 0x27,
            byteArrayOf(0xAA.toByte(), 0x11, 0x16, 0x03, 0x00, 0x02, 0x03) to 0xEB,
        )
        vectors.forEach { (data, expected) ->
            assertEquals(expected, Crc8.compute(data)[0].toInt() and 0xFF, "crc8 of ${data.toHex()}")
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }
}
