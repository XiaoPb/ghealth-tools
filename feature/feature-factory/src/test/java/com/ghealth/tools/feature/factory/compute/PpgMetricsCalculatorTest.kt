package com.ghealth.tools.feature.factory.compute

import com.ghealth.tools.feature.factory.model.ChipAdcParams
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.log10

class PpgMetricsCalculatorTest {

    private val gh3036 = ChipAdcParams.forChip("gh3036")

    @Test
    fun `average 与 stddev 计算正确`() {
        val values = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0)
        assertEquals(3.0, PpgMetricsCalculator.average(values))
        assertEquals(1.4142135623730951, PpgMetricsCalculator.stddev(values), 1e-9)
    }

    @Test
    fun `空数组 average 与 stddev 返回 NaN`() {
        assertTrue(PpgMetricsCalculator.average(DoubleArray(0)).isNaN())
        assertTrue(PpgMetricsCalculator.stddev(DoubleArray(0)).isNaN())
    }

    @Test
    fun `noise 公式与量纲一致`() {
        // σ=1000, full_scale=2^23, Vref=1.8 → 1000/8388608*1.8*1e6 ≈ 214.58 μV
        val noise = PpgMetricsCalculator.noise(sigmaFilter = 1000.0, params = gh3036)
        assertEquals(1000.0 / 8_388_608.0 * 1.8 * 1e6, noise, 1e-6)
    }

    @Test
    fun `ipdFromRaw 使用公式 1`() {
        // rawAvg=满量程, offset=0, Vref=1.8, tia=2, Gk=100kΩ → 1.8e6/200 = 9000 nA
        val ipd = PpgMetricsCalculator.ipdFromRaw(8_388_608.0, gh3036, gainKOhm = 100.0)
        assertEquals(9000.0, ipd, 1e-6)
    }

    @Test
    fun `ipdFromPa 使用公式 2`() {
        assertEquals(1000.0, PpgMetricsCalculator.ipdFromPa(1_000_000.0), 1e-9)
    }

    @Test
    fun `ctr 为 Ipd 除以 Iled`() {
        assertEquals(450.0, PpgMetricsCalculator.ctr(ipdNa = 9000.0, ledMa = 20.0), 1e-9)
    }

    @Test
    fun `Iled 为 0 时 ctr 返回 NaN`() {
        assertTrue(PpgMetricsCalculator.ctr(9000.0, 0.0).isNaN())
    }

    @Test
    fun `snr 使用 20log10 公式`() {
        val snr = PpgMetricsCalculator.snr(8_388_608.0, 0.0, 1000.0)
        assertEquals(20.0 * log10(8_388_608.0 / 1000.0), snr, 1e-9)
    }
}
