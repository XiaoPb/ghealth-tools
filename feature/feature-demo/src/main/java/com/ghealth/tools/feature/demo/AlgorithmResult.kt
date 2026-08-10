package com.ghealth.tools.feature.demo

sealed class AlgorithmResult {
    abstract val display: String
    abstract val hasData: Boolean

    /** No algorithm data received yet. */
    data object None : AlgorithmResult() {
        override val display: String get() = "--"
        override val hasData: Boolean get() = false
    }

    /**
     * HR (Heart Rate) algorithm result.
     *
     * Fields per GH3220 算法映射（snResult 下标）：
     * - snResult[0]: 心率值（单位 bpm）
     * - snResult[1]: 置信度分数（0~100）
     * - snResult[2]: 信噪比：滤波后频谱主峰能量与总能量之比
     */
    data class HR(
        val heartRate: Int = 0,
        val confidence: Int = 0,
        val snr: Int = 0
    ) : AlgorithmResult() {
        override val hasData: Boolean get() = heartRate > 0
        override val display: String get() = if (hasData) "$heartRate BPM" else "--"
    }

    /**
     * SPO2 (Blood Oxygen) algorithm result.
     *
     * Fields per GH3220 算法映射（snResult 下标）：
     * - snResult[0]: 相对血氧饱和度（单位 %）
     * - snResult[1]: 综合 R 值 * 10000
     * - snResult[2]: 实时置信度（出值策略处理后），范围 0~100
     * - snResult[3]: 置信等级 -2~5，值越大输出值依赖策略卡控的成分越少，负值表示异常
     * - snResult[4]: 心率值（暂无用）
     * - snResult[5]: 数据无效标记，无效时 bit 位为 1（bit0 acc 运动 / bit1 acc 倾斜 / bit2 ppg 瞬时强干扰 /
     *   bit3 ppg 周期性 / bit4 ppg 强噪声 / bit5 ppg 两路一致性）
     */
    data class SPO2(
        val spo2: Int = 0,
        val rValue: Int = 0,
        val confidence: Int = 0,
        val confidenceLevel: Int = 0,
        val heartRate: Int = 0,
        val invalidFlag: Int = 0
    ) : AlgorithmResult() {
        override val hasData: Boolean get() = spo2 > 0
        override val display: String get() = if (hasData) {
            val rv = if (rValue > 0) " / R:${rValue / 10000.0}" else ""
            val spo2_val = if (spo2 > 10000) spo2 / 10000.0 else spo2
            "$spo2_val%$rv"
        } else "--"
    }

    /**
     * HRV (Heart Rate Variability) algorithm result.
     *
     * Fields per GH3220 算法映射（snResult 下标）：
     * - snResult[0..3]: RRI/PPI 值（单位 ms）
     * - snResult[4]: 置信度：0 不可信，25 低置信度，75 高置信度，100 可信
     * - snResult[5]: RRI/PPI 数量
     */
    data class HRV(
        val rri: List<Int> = emptyList(),
        val confidence: Int = 0,
        val validNum: Int = 0
    ) : AlgorithmResult() {
        override val hasData: Boolean get() = rri.any { it > 0 }
        override val display: String get() {
            val valid = rri.filter { it > 0 }
            return if (valid.isNotEmpty()) "RRI: ${valid.joinToString(", ")}ms" else "--"
        }
    }

    /**
     * ADT (Auto Detect Wear) algorithm result.
     *
     * Fields per gh_adt_alg_e:
     * - wearEvent: wear status event（位掩码，见 [AdtWearEvent]）
     * - detStatus: detection state（见 [AdtDetState]）
     * - ctr: counter
     */
    data class ADT(
        val wearEvent: Int = 0,
        val detStatus: Int = 0,
        val ctr: Int = 0
    ) : AlgorithmResult() {
        // ADT 帧到达即视为有数据：DET_ON(0) 与 IDLE(0) 都是合法状态，
        // 不能用 > 0 判断，否则佩戴检测中状态会被丢弃。
        override val hasData: Boolean get() = true
        override val display: String get() =
            "${AdtWearEvent.labels(wearEvent)} / ${AdtDetState.fromValue(detStatus).label}"
    }

    /**
     * NADT (Non-Auto Detect) algorithm result.
     *
     * Fields per GH3220 算法映射（snResult 下标）：
     * - snResult[0] bit0-1: 佩戴状态（0 默认，1 佩戴，2 脱落，3 非活体）
     * - snResult[0] bit2: 疑似脱落标记（0 正常，1 疑似脱落）
     * - snResult[1]: 活体置信度（0-100），0-20 活体概率低，80-100 活体概率高
     */
    data class NADT(
        val wearStatus: Int = 0,
        val suspectOff: Int = 0,
        val liveBodyConf: Int = 0
    ) : AlgorithmResult() {
        override val hasData: Boolean get() = wearStatus > 0 || liveBodyConf > 0
        override val display: String get() {
            if (wearStatus == 0 && liveBodyConf == 0) return "--"
            val parts = buildList {
                if (wearStatus > 0) {
                    val marker = if (suspectOff > 0) "(~)" else ""
                    add(nadtWearStatusLabel(wearStatus) + marker)
                }
                if (liveBodyConf > 0) add("Live:$liveBodyConf")
            }
            return parts.joinToString(" / ")
        }
    }

    /**
     * BT (Temperature) algorithm result.
     *
     * Fields per GH3220 算法映射（snResult 下标）：
     * - snResult[0]: NTC0 温度值（单位 0.01℃）
     * - snResult[1]: NTC1 温度值（单位 0.01℃）
     */
    data class BT(
        val ntc0: Int = 0,
        val ntc1: Int = 0
    ) : AlgorithmResult() {
        override val hasData: Boolean get() = ntc0 != 0 || ntc1 != 0
        override val display: String get() {
            val parts = buildList {
                if (ntc0 != 0) add("NTC0:${btTemp(ntc0)}")
                if (ntc1 != 0) add("NTC1:${btTemp(ntc1)}")
            }
            return if (parts.isNotEmpty()) parts.joinToString(" / ") else "--"
        }
    }

    /**
     * ECG (Electrocardiogram) algorithm result.
     *
     * Fields per GH3220 算法映射（snResult 下标）：
     * - snResult[0]: ECG 电压信号（单位 10uV）
     * - snResult[1]: 心率值（单位 bpm）
     * - snResult[2]: 信噪比：滤波后频谱主峰能量与总能量之比
     */
    data class ECG(
        val voltage: Int = 0,
        val heartRate: Int = 0,
        val snr: Int = 0
    ) : AlgorithmResult() {
        override val hasData: Boolean get() = heartRate > 0
        override val display: String get() = if (hasData) "$heartRate BPM" else "--"
    }
}

/** NADT 佩戴状态标签（snResult[0] bit0-1）。 */
fun nadtWearStatusLabel(status: Int): String = when (status) {
    1 -> "Wear"
    2 -> "Off"
    3 -> "Non-live"
    else -> "Default"
}

/** BT 温度格式化：原始值单位为 0.01℃，保留两位小数并带 ℃。 */
fun btTemp(raw: Int): String = String.format(java.util.Locale.US, "%.2f℃", raw / 100.0)

/** Parse algoData into a typed [AlgorithmResult] for the given [FunctionMode]. */
fun parseAlgorithmResult(mode: com.ghealth.tools.core.model.FunctionMode, algoData: IntArray): AlgorithmResult {
    if (algoData.isEmpty()) return AlgorithmResult.None

    fun a(i: Int) = if (i < algoData.size) algoData[i] else 0

    return when (mode) {
        com.ghealth.tools.core.model.FunctionMode.HR -> AlgorithmResult.HR(
            heartRate = a(0),
            confidence = a(1),
            snr = a(2)
        )
        com.ghealth.tools.core.model.FunctionMode.SPO2 -> AlgorithmResult.SPO2(
            spo2 = a(0),
            rValue = a(1),
            confidence = a(2),
            confidenceLevel = a(3),
            heartRate = a(4),
            invalidFlag = a(5)
        )
        com.ghealth.tools.core.model.FunctionMode.HRV -> AlgorithmResult.HRV(
            rri = listOf(a(0), a(1), a(2), a(3)),
            confidence = a(4),
            validNum = a(5)
        )
        com.ghealth.tools.core.model.FunctionMode.ADT -> AlgorithmResult.ADT(
            wearEvent = a(0),
            detStatus = a(1),
            ctr = a(2)
        )
        com.ghealth.tools.core.model.FunctionMode.NADT_GREEN,
        com.ghealth.tools.core.model.FunctionMode.NADT_IR -> AlgorithmResult.NADT(
            wearStatus = a(0) and 0x3,
            suspectOff = (a(0) shr 2) and 0x1,
            liveBodyConf = a(1)
        )
        com.ghealth.tools.core.model.FunctionMode.BT -> AlgorithmResult.BT(
            ntc0 = a(0),
            ntc1 = a(1)
        )
        com.ghealth.tools.core.model.FunctionMode.ECG -> AlgorithmResult.ECG(
            voltage = a(0),
            heartRate = a(1),
            snr = a(2)
        )
        else -> AlgorithmResult.None
    }
}
