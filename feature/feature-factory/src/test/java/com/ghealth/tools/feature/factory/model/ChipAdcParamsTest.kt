package com.ghealth.tools.feature.factory.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChipAdcParamsTest {

    @Test
    fun `gh3036 使用零偏移`() {
        val p = ChipAdcParams.forChip("gh3036")
        assertEquals(8_388_608.0, p.fullScale)
        assertEquals(0.0, p.offset)
        assertEquals(1.8, p.vref)
        assertEquals(2.0, p.tiaRatio)
    }

    @Test
    fun `gh3038q 归入 gh3036 参数`() {
        assertEquals(0.0, ChipAdcParams.forChip("gh3038q").offset)
    }

    @Test
    fun `gh3220 与 gh3300 使用 2^23 偏移`() {
        for (chip in listOf("gh3220", "gh3300")) {
            val p = ChipAdcParams.forChip(chip)
            assertEquals(8_388_608.0, p.fullScale)
            assertEquals(8_388_608.0, p.offset)
            assertEquals(1.8, p.vref)
            assertEquals(2.0, p.tiaRatio)
        }
    }

    @Test
    fun `gh3020 归入 gh3220 系列 2^23 偏移`() {
        assertEquals(8_388_608.0, ChipAdcParams.forChip("gh3020").offset)
    }

    @Test
    fun `大写输入归入 gh3220 参数`() {
        assertEquals(8_388_608.0, ChipAdcParams.forChip("GH3220").offset)
    }

    @Test
    fun `未知芯片回退为 gh3036 参数`() {
        assertEquals(0.0, ChipAdcParams.forChip("unknown").offset)
    }
}
