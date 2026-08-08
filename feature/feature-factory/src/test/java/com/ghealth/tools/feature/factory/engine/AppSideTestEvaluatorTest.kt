package com.ghealth.tools.feature.factory.engine

import com.ghealth.tools.feature.factory.model.AppComputeConfig
import com.ghealth.tools.feature.factory.model.TestDef
import com.ghealth.tools.feature.factory.model.TestType
import com.ghealth.tools.feature.factory.model.ThresholdDef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin

class AppSideTestEvaluatorTest {

    private val evaluator = AppSideTestEvaluator()
    private val logs = mutableListOf<Pair<LogLevel, String>>()
    private fun log(level: LogLevel, message: String) { logs += level to message }

    private val noiseDef = TestDef(
        enabled = true, mode = 4, channels = 2, unit = "uV",
        globalThreshold = ThresholdDef("le", 300.0)
    )
    private val ctrDef = TestDef(
        enabled = true, mode = 16, channels = 2, unit = "nA/mA",
        globalThreshold = ThresholdDef("ge", 100.0),
        compute = AppComputeConfig(gainK = 100.0)
    )

    // 直流 2^23 + 5Hz 正弦（幅值 1000），采样率 100Hz，共 2000 点
    private fun noiseSeries(): List<Int> {
        val fs = 100.0
        return (0 until 2000).map { i ->
            (8_388_608.0 + 1000.0 * sin(2 * PI * 5.0 * i / fs)).toInt()
        }
    }

    private fun collectedRaw(series: List<Int> = noiseSeries()) = CollectedRawData(
        rawdataByChannel = mapOf(0 to series, 1 to series),
        ipdPaByChannel = emptyMap(),
        ledCurrentSumMaByChannel = emptyMap(),
        frameCnts = series.indices.toList()
    )

    @Test
    fun `噪声测试按公式计算 Noise 并与阈值判定`() {
        val results = evaluator.evaluate(TestType.BASE_NOISE, noiseDef, collectedRaw(), "gh3036", ::log)!!
        assertEquals(2, results.size)
        assertTrue(results.all { it.passed })
        // 5Hz 正弦滤波后 stddev ≈ 707，Noise ≈ 707/8388608*1.8e6 ≈ 151.8 μV ≤ 300
        assertEquals(151.8, results[0].computedValue!!, 5.0)
    }

    @Test
    fun `噪声超过阈值时 FAIL 且错误码正确`() {
        val def = noiseDef.copy(globalThreshold = ThresholdDef("le", 50.0))
        val results = evaluator.evaluate(TestType.BASE_NOISE, def, collectedRaw(), "gh3036", ::log)!!
        assertFalse(results[0].passed)
        assertEquals(TestType.BASE_NOISE.errorBase, results[0].errorCodeComputed)
    }

    @Test
    fun `CTR 测试在 GH3036 使用 Ipd pA 路径`() {
        // phyValue = 1_000_000 pA → 1000 nA；LED sum = 510 mA → CTR ≈ 1.96 nA/mA
        val data = CollectedRawData(
            rawdataByChannel = mapOf(0 to noiseSeries()),
            ipdPaByChannel = mapOf(0 to List(100) { 1_000_000 }),
            ledCurrentSumMaByChannel = mapOf(0 to 510.0),
            frameCnts = (0 until 100).toList()
        )
        val results = evaluator.evaluate(TestType.LPCTR, ctrDef, data, "gh3036", ::log)!!
        assertEquals(1.96, results[0].computedValue!!, 0.01)
        assertFalse(results[0].passed) // CTR < 100（阈值 ge 100）→ FAIL
    }

    @Test
    fun `CTR 测试 rawdata 回退使用 gain_k`() {
        // GH3220: rawdata=2^24, offset=2^23 → (rawAvg-offset)/full_scale=1 → Ipd=9000nA；LED=20mA → CTR=450
        val data = CollectedRawData(
            rawdataByChannel = mapOf(0 to List(200) { 16_777_216 }),
            ipdPaByChannel = emptyMap(),
            ledCurrentSumMaByChannel = mapOf(0 to 20.0),
            frameCnts = (0 until 200).toList()
        )
        val results = evaluator.evaluate(TestType.LPCTR, ctrDef, data, "gh3220", ::log)!!
        assertEquals(450.0, results[0].computedValue!!, 1e-6)
        assertTrue(results[0].passed)
    }

    @Test
    fun `无 gain_k 且无 phyValue 时通道 FAIL`() {
        val data = CollectedRawData(
            rawdataByChannel = mapOf(0 to List(100) { 100 }),
            ipdPaByChannel = emptyMap(),
            ledCurrentSumMaByChannel = mapOf(0 to 20.0),
            frameCnts = (0 until 100).toList()
        )
        val def = ctrDef.copy(compute = null)
        val results = evaluator.evaluate(TestType.LPCTR, def, data, "gh3036", ::log)!!
        assertFalse(results[0].passed)
    }

    @Test
    fun `LED 电流为 0 时通道 FAIL`() {
        val data = CollectedRawData(
            rawdataByChannel = mapOf(0 to List(100) { 16_777_216 }),
            ipdPaByChannel = emptyMap(),
            ledCurrentSumMaByChannel = emptyMap(),
            frameCnts = (0 until 100).toList()
        )
        val results = evaluator.evaluate(TestType.LPCTR, ctrDef, data, "gh3220", ::log)!!
        assertFalse(results[0].passed)
    }

    @Test
    fun `无原始数据时返回 null`() {
        val results = evaluator.evaluate(TestType.BASE_NOISE, noiseDef, CollectedRawData.EMPTY, "gh3036", ::log)
        assertNull(results)
    }

    @Test
    fun `噪声数据滤波后标准差为 0 时 FAIL 而非给出 0 噪声`() {
        val data = CollectedRawData(
            rawdataByChannel = mapOf(0 to List(100) { 8_388_608 }),
            ipdPaByChannel = emptyMap(),
            ledCurrentSumMaByChannel = emptyMap(),
            frameCnts = (0 until 100).toList()
        )
        val results = evaluator.evaluate(TestType.BASE_NOISE, noiseDef, data, "gh3036", ::log)!!
        assertFalse(results[0].passed)
    }

    @Test
    fun `GH3220 优先使用配置 LED 电流而非 AGC 解码值`() {
        // AGC 若被误用：9000/500=18 → FAIL；正确用配置 20mA：9000/20=450 → PASS
        val data = CollectedRawData(
            rawdataByChannel = mapOf(0 to List(200) { 16_777_216 }),
            ipdPaByChannel = emptyMap(),
            ledCurrentSumMaByChannel = mapOf(0 to 500.0),
            frameCnts = (0 until 200).toList()
        )
        val def = ctrDef.copy(compute = AppComputeConfig(gainK = 100.0, ledCurrentMa = 20.0))
        val results = evaluator.evaluate(TestType.LPCTR, def, data, "gh3220", ::log)!!
        assertEquals(450.0, results[0].computedValue!!, 1e-6)
        assertTrue(results[0].passed)
    }

    @Test
    fun `gain_k 为 0 时通道 FAIL 而非抛异常`() {
        val data = CollectedRawData(
            rawdataByChannel = mapOf(0 to List(100) { 16_777_216 }),
            ipdPaByChannel = emptyMap(),
            ledCurrentSumMaByChannel = mapOf(0 to 20.0),
            frameCnts = (0 until 100).toList()
        )
        val def = ctrDef.copy(compute = AppComputeConfig(gainK = 0.0))
        val results = evaluator.evaluate(TestType.LPCTR, def, data, "gh3220", ::log)!!
        assertFalse(results[0].passed)
    }

    @Test
    fun `操作失败通道 computedValue 为 null`() {
        val data = CollectedRawData(
            rawdataByChannel = mapOf(0 to List(100) { 100 }),
            ipdPaByChannel = emptyMap(),
            ledCurrentSumMaByChannel = mapOf(0 to 20.0),
            frameCnts = (0 until 100).toList()
        )
        val def = ctrDef.copy(compute = null)
        val results = evaluator.evaluate(TestType.LPCTR, def, data, "gh3036", ::log)!!
        assertFalse(results[0].passed)
        assertNull(results[0].computedValue)
    }

    @Test
    fun `CTR 计算只使用最后 min_number 帧的 Ipd 均值`() {
        // 前 50 帧 Ipd=500000pA，后 100 帧 Ipd=1000000pA；min_number 默认 100 → 均值 1000000pA → CTR 1.96
        val data = CollectedRawData(
            rawdataByChannel = emptyMap(),
            ipdPaByChannel = mapOf(0 to List(50) { 500_000 } + List(100) { 1_000_000 }),
            ledCurrentSumMaByChannel = mapOf(0 to 510.0),
            frameCnts = (0 until 150).toList()
        )
        val results = evaluator.evaluate(TestType.LPCTR, ctrDef, data, "gh3036", ::log)!!
        assertEquals(1.96, results[0].computedValue!!, 0.01)
    }

    @Test
    fun `有效帧数不足 min_number 时返回 null`() {
        val data = CollectedRawData(
            rawdataByChannel = mapOf(0 to noiseSeries().take(50)),
            ipdPaByChannel = emptyMap(),
            ledCurrentSumMaByChannel = emptyMap(),
            frameCnts = (0 until 50).toList()
        )
        val results = evaluator.evaluate(TestType.BASE_NOISE, noiseDef, data, "gh3036", ::log)
        assertNull(results)
    }
}
