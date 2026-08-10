package com.ghealth.tools.feature.settings

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Gh3220VersionResolverTest {

    @Test
    fun `parseGh3220VersionText extracts utf8 text`() {
        val raw = byteArrayOf(0x01, 0x07) + "GHealth".toByteArray()
        assertEquals("GHealth", parseGh3220VersionText(raw))
    }

    @Test
    fun `parseGh3220VersionText returns null on blank or truncated`() {
        assertNull(parseGh3220VersionText(byteArrayOf(0x01, 0x00)))
        assertNull(parseGh3220VersionText(byteArrayOf(0x01)))
        assertNull(parseGh3220VersionText(byteArrayOf(0x01, 0x02, 0x41)))
    }

    @Test
    fun `gh3220 version queries match protocol doc version types`() {
        val byLabel = GH3220_VERSION_QUERIES.associateBy { it.label }
        assertEquals(0x01, byLabel.getValue("固件版本").verType.toInt())
        assertEquals(0x0B, byLabel.getValue("虚拟寄存器版本").verType.toInt())
        assertEquals(0x0C, byLabel.getValue("Bootloader版本").verType.toInt())
        assertEquals(0x0D, byLabel.getValue("BLE版本").verType.toInt())
        assertEquals(0x0E, byLabel.getValue("协议版本").verType.toInt())
        assertEquals(0x0F, byLabel.getValue("支持功能").verType.toInt())
        assertEquals(0x10, byLabel.getValue("驱动库版本").verType.toInt())
        assertEquals(0x11, byLabel.getValue("芯片版本").verType.toInt())
        assertEquals(0x12, byLabel.getValue("ADT").verType.toInt())
        assertEquals(0x13, byLabel.getValue("HR").verType.toInt())
        assertEquals(0x14, byLabel.getValue("HRV").verType.toInt())
        assertEquals(0x15, byLabel.getValue("HSM").verType.toInt())
        assertEquals(0x16, byLabel.getValue("FPBP").verType.toInt())
        assertEquals(0x19, byLabel.getValue("PWA").verType.toInt())
        assertEquals(0x1A, byLabel.getValue("SpO2").verType.toInt())
        assertEquals(0x1B, byLabel.getValue("ECG").verType.toInt())
        assertEquals(0x1C, byLabel.getValue("PWTT").verType.toInt())
        assertEquals(0x1D, byLabel.getValue("SOFTADT").verType.toInt())
        assertEquals(0x1E, byLabel.getValue("BT").verType.toInt())
    }

    @Test
    fun `versionPlan returns gh3220 queries and no algo queries for gh3220`() {
        val (basicQueries, algoQueries) = versionPlan(isGh3220 = true)
        assertEquals(GH3220_VERSION_QUERIES, basicQueries)
        assertTrue(algoQueries.isEmpty())
    }

    @Test
    fun `versionPlan returns basic and algo queries for gh3036`() {
        val (basicQueries, algoQueries) = versionPlan(isGh3220 = false)
        assertEquals(BASIC_VERSION_QUERIES, basicQueries)
        assertEquals(ALGO_VERSION_QUERIES, algoQueries)
    }
}
