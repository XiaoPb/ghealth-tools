package com.ghealth.tools.feature.factory.engine

import com.ghealth.tools.feature.factory.compute.HighPassFilter
import com.ghealth.tools.feature.factory.compute.PpgMetricsCalculator
import com.ghealth.tools.feature.factory.model.AppComputeConfig
import com.ghealth.tools.feature.factory.model.ChipAdcParams
import com.ghealth.tools.feature.factory.model.TestDef
import com.ghealth.tools.feature.factory.model.TestResult
import com.ghealth.tools.feature.factory.model.TestType
import com.ghealth.tools.feature.factory.model.ThresholdOperator
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * F_GetMode 返回空数据（对端固件未实现产测计算）时，基于 TEST1 原始帧数据
 * 按「PPG数据采集通用公式与配置说明」在 App 端计算各项指标并判定。
 *
 * - 噪声测试（BASE_NOISE/PPG_NOISE）：Noise（μV）= σ_filter / full_scale × V_ref × 10^6
 * - CTR 测试（LPCTR/LPLCTR）：优先用 GH3036 帧 Ipd pA（公式2），否则用 rawdata + compute.gain_k（公式1）
 * - Iled：GH3036 系优先取 AGC 帧 led_current_sum(0.1mA)/10；GH3220/GH3300 优先取 compute.led_current_ma，
 *   可互相回退（非 GH3036 使用 AGC 解码时记录 WARN 日志）
 */
@Singleton
class AppSideTestEvaluator @Inject constructor() {

    fun evaluate(
        testType: TestType,
        testDef: TestDef,
        data: CollectedRawData,
        chip: String,
        log: (LogLevel, String) -> Unit
    ): List<TestResult>? {
        if (data.isEmpty) {
            log(LogLevel.ERROR, "${testType.displayName}: 未采集到 TEST1 原始数据，App端计算失败")
            return null
        }

        val params = ChipAdcParams.forChip(chip)
        val compute = testDef.compute
        val filter = HighPassFilter(sampleRateHz = compute?.sampleRateHz ?: DEFAULT_SAMPLE_RATE_HZ)
        val thresholdDef = testDef.globalThreshold
        val results = mutableListOf<TestResult>()

        for (ch in 0 until data.channelCount) {
            val rawSeries = data.rawdataByChannel[ch]?.map { it.toDouble() }?.toDoubleArray()
            val rawAvg = rawSeries?.let { PpgMetricsCalculator.average(it) } ?: Double.NaN

            val computed: Double
            when {
                testType == TestType.BASE_NOISE || testType == TestType.PPG_NOISE -> {
                    if (rawSeries == null || rawSeries.isEmpty()) {
                        results += failedResult(testType, ch, testDef)
                        log(LogLevel.WARN, "${testType.displayName}: 通道$ch 无原始数据，标记 FAIL")
                        continue
                    }
                    // 以均值为滤波器初态，消除直流阶跃瞬态
                    val sigma = PpgMetricsCalculator.stddev(filter.filter(rawSeries, initialX = rawAvg))
                    if (sigma <= 0.0) {
                        results += failedResult(testType, ch, testDef)
                        log(LogLevel.WARN, "${testType.displayName}: 通道$ch 滤波后标准差为 0（样本过少或数据异常），标记 FAIL")
                        continue
                    }
                    computed = PpgMetricsCalculator.noise(sigma, params)
                    val snr = PpgMetricsCalculator.snr(rawAvg, params.offset, sigma)
                    log(LogLevel.INFO,
                        "${testType.displayName}: 通道$ch Noise=${TestResult.formatComputed(computed)}μV SNR=${TestResult.formatComputed(snr)}dB")
                }
                testType == TestType.LPCTR || testType == TestType.LPLCTR -> {
                    val ipdPaSeries = data.ipdPaByChannel[ch]
                    val ipdNa = if (!ipdPaSeries.isNullOrEmpty()) {
                        PpgMetricsCalculator.ipdFromPa(
                            PpgMetricsCalculator.average(ipdPaSeries.map { it.toDouble() }.toDoubleArray())
                        )
                    } else if (rawSeries != null && rawSeries.isNotEmpty() && compute != null && compute.gainK != null && compute.gainK > 0) {
                        PpgMetricsCalculator.ipdFromRaw(rawAvg, params, compute.gainK)
                    } else {
                        val reason = if (rawSeries == null || rawSeries.isEmpty()) "无原始数据"
                        else "缺少有效的 compute.gain_k 配置，无法用 rawdata 计算 Ipd"
                        results += failedResult(testType, ch, testDef)
                        log(LogLevel.WARN, "${testType.displayName}: 通道$ch $reason，标记 FAIL")
                        continue
                    }
                    val agcLedMa = data.ledCurrentSumMaByChannel[ch]
                    val ledMa = if (params.offset == 0.0) {
                        // GH3036 系：AGC 位域已文档化，优先 AGC，其次配置
                        agcLedMa ?: compute?.ledCurrentMa ?: 0.0
                    } else {
                        // GH3220/GH3300：AGC 位域未文档化，优先配置，其次 AGC（并告警）
                        compute?.ledCurrentMa ?: agcLedMa ?: 0.0
                    }
                    if (params.offset != 0.0 && compute?.ledCurrentMa == null && agcLedMa != null) {
                        log(LogLevel.WARN, "${testType.displayName}: 通道$ch LED 电流来自 AGC 解码（非 GH3036 位域未文档化），建议配置 compute.led_current_ma")
                    }
                    computed = PpgMetricsCalculator.ctr(ipdNa, ledMa)
                    if (computed.isNaN()) {
                        results += failedResult(testType, ch, testDef)
                        log(LogLevel.WARN, "${testType.displayName}: 通道$ch LED 电流为 0，无法计算 CTR，标记 FAIL")
                        continue
                    }
                }
                else -> {
                    results += failedResult(testType, ch, testDef)
                    log(LogLevel.WARN, "${testType.displayName}: 通道$ch 不支持 App端计算，标记 FAIL")
                    continue
                }
            }

            val passed = thresholdDef == null ||
                ThresholdOperator.fromKey(thresholdDef.operator).evaluate(computed, thresholdDef)
            results += TestResult(
                testType = testType,
                channelIndex = ch,
                value = computed.roundToInt(),
                unit = testDef.unit,
                threshold = thresholdDef?.let { ThresholdOperator.fromKey(it.operator).formatThresholdDouble(it) } ?: "-",
                passed = passed,
                computedValue = computed
            )
        }

        val passCount = results.count { it.passed }
        log(LogLevel.INFO, "${testType.displayName}: App端计算完成，$passCount/${results.size} 通道通过")
        return results
    }

    private fun failedResult(testType: TestType, channel: Int, testDef: TestDef): TestResult =
        TestResult(
            testType = testType,
            channelIndex = channel,
            value = 0,
            unit = testDef.unit,
            threshold = "-",
            passed = false
        )

    companion object {
        const val DEFAULT_SAMPLE_RATE_HZ = 100
    }
}
