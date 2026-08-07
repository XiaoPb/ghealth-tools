package com.ghealth.tools.feature.factory.compute

import com.ghealth.tools.feature.factory.model.ChipAdcParams
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * PPG 产测指标计算公式（依据「PPG数据采集通用公式与配置说明」）。
 *
 * - Noise（μV）= σ_filter / full_scale × V_ref × 10^6，σ_filter 为 0.5Hz 高通滤波后总体标准差
 * - Ipd 公式1（nA）= (rawdata_avg - offset) / full_scale × V_ref × 10^6 / (tia_ratio × G_k)
 * - Ipd 公式2（nA）= Ipd_pa / 1000（GH3036 帧 phyValue 直接提供 pA）
 * - CTR（nA/mA）= Ipd / Iled
 * - SNR（dB）= 20·log10((rawdata_avg - offset) / σ_filter)
 */
object PpgMetricsCalculator {

    fun average(values: DoubleArray): Double =
        if (values.isEmpty()) Double.NaN else values.sum() / values.size

    /** 总体标准差（分母 N，与信号处理惯例一致）。 */
    fun stddev(values: DoubleArray): Double {
        if (values.isEmpty()) return Double.NaN
        val mean = average(values)
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(variance)
    }

    /** Noise（μV）= σ_filter / full_scale × V_ref × 10^6 */
    fun noise(sigmaFilter: Double, params: ChipAdcParams): Double =
        sigmaFilter / params.fullScale * params.vref * 1e6

    /** Ipd（nA）= (rawdata_avg - offset) / full_scale × V_ref × 10^6 / (tia_ratio × G_k) */
    fun ipdFromRaw(rawAvg: Double, params: ChipAdcParams, gainKOhm: Double): Double {
        require(gainKOhm > 0) { "跨阻增益 gainKOhm 必须为正" }
        return (rawAvg - params.offset) / params.fullScale *
            params.vref * 1e6 / (params.tiaRatio * gainKOhm)
    }

    /** Ipd（nA）= Ipd_pa / 1000（GH3036 帧 phyValue 直接提供 pA） */
    fun ipdFromPa(ipdPaAvg: Double): Double = ipdPaAvg / 1000.0

    /** CTR（nA/mA）= Ipd / Iled；Iled ≤ 0 时返回 NaN */
    fun ctr(ipdNa: Double, ledMa: Double): Double =
        if (ledMa > 0.0) ipdNa / ledMa else Double.NaN

    /** SNR（dB）= 20·log10((rawdata_avg - offset) / σ_filter)；σ_filter ≤ 0 时返回 NaN */
    fun snr(rawAvg: Double, offset: Double, sigmaFilter: Double): Double =
        if (sigmaFilter > 0.0) 20.0 * log10((rawAvg - offset) / sigmaFilter) else Double.NaN
}
