package com.ghealth.tools.feature.demo

import com.ghealth.tools.core.model.FunctionMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DisplayWidthConfigTest {

    @Test
    fun `选项列表为 50 100 125 250 500`() {
        assertEquals(listOf(50, 100, 125, 250, 500), DisplayWidthConfig.OPTIONS)
    }

    @Test
    fun `ADT 默认宽度为 50`() {
        assertEquals(50, DisplayWidthConfig.defaultFor(FunctionMode.ADT))
    }

    @Test
    fun `HR SPO2 HRV NADT_GREEN NADT_IR 默认宽度为 125`() {
        assertEquals(125, DisplayWidthConfig.defaultFor(FunctionMode.HR))
        assertEquals(125, DisplayWidthConfig.defaultFor(FunctionMode.SPO2))
        assertEquals(125, DisplayWidthConfig.defaultFor(FunctionMode.HRV))
        assertEquals(125, DisplayWidthConfig.defaultFor(FunctionMode.NADT_GREEN))
        assertEquals(125, DisplayWidthConfig.defaultFor(FunctionMode.NADT_IR))
    }

    @Test
    fun `TEST1 TEST2 默认宽度为 500`() {
        assertEquals(500, DisplayWidthConfig.defaultFor(FunctionMode.TEST1))
        assertEquals(500, DisplayWidthConfig.defaultFor(FunctionMode.TEST2))
    }

    @Test
    fun `其余模式默认宽度为 125`() {
        assertEquals(125, DisplayWidthConfig.defaultFor(FunctionMode.ECG))
        assertEquals(125, DisplayWidthConfig.defaultFor(FunctionMode.RESP))
        assertEquals(125, DisplayWidthConfig.defaultFor(FunctionMode.BIA))
    }

    @Test
    fun `每个默认宽度都必须在选项列表内`() {
        for (mode in FunctionMode.entries) {
            val w = DisplayWidthConfig.defaultFor(mode)
            assertTrue(
                DisplayWidthConfig.OPTIONS.contains(w),
                "模式 $mode 的默认宽度 $w 不在 OPTIONS 中"
            )
        }
    }
}
