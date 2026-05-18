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
     * Fields per gh_hr_alg_e:
     * - heartRate: hba_out (BPM)
     * - validScore: signal quality score
     * - snr: signal-to-noise ratio
     * - accInfo: accelerometer info
     */
    data class HR(
        val heartRate: Int = 0,
        val validScore: Int = 0,
        val snr: Int = 0,
        val accInfo: Int = 0
    ) : AlgorithmResult() {
        override val hasData: Boolean get() = heartRate > 0
        override val display: String get() = if (hasData) "$heartRate BPM" else "--"
    }

    /**
     * SPO2 (Blood Oxygen) algorithm result.
     *
     * Fields per gh_spo2_alg_e:
     * - spo2: final_spo2 (%)
     * - rValue: R value
     * - confiCoeff: confidence coefficient
     * - validLevel: validity level
     * - hbMean: mean hemoglobin
     */
    data class SPO2(
        val spo2: Int = 0,
        val rValue: Int = 0,
        val confiCoeff: Int = 0,
        val validLevel: Int = 0,
        val hbMean: Int = 0
    ) : AlgorithmResult() {
        override val hasData: Boolean get() = spo2 > 0
        override val display: String get() = if (hasData) {
            val rv = if (rValue > 0) " / R:${rValue / 1000.0}" else ""
            "$spo2%$rv"
        } else "--"
    }

    /**
     * HRV (Heart Rate Variability) algorithm result.
     *
     * Fields per gh_spo2_hrv_e:
     * - rri: R-R intervals [0..3] (ms)
     * - confidence: detection confidence
     * - validNum: number of valid RRI values
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
     * - wearEvent: wear status event
     * - detStatus: detection status
     * - ctr: counter
     */
    data class ADT(
        val wearEvent: Int = 0,
        val detStatus: Int = 0,
        val ctr: Int = 0
    ) : AlgorithmResult() {
        override val hasData: Boolean get() = wearEvent > 0 || detStatus > 0
        override val display: String get() = when {
            wearEvent == 1 -> "Wear"
            wearEvent == 2 -> "Off"
            detStatus == 1 -> "Detecting"
            detStatus == 2 -> "Detected"
            else -> "--"
        }
    }

    /**
     * NADT (Non-Auto Detect) algorithm result.
     *
     * Fields per gh_spo2_nadt_e:
     * - wearOffDetectRes: wear-off detection result
     * - liveBodyConf: live body confidence
     */
    data class NADT(
        val wearOffDetectRes: Int = 0,
        val liveBodyConf: Int = 0
    ) : AlgorithmResult() {
        override val hasData: Boolean get() = wearOffDetectRes > 0 || liveBodyConf > 0
        override val display: String get() = when {
            liveBodyConf > 0 -> "Live:$liveBodyConf"
            wearOffDetectRes > 0 -> "Off:$wearOffDetectRes"
            else -> "--"
        }
    }
}

/** Parse algoData into a typed [AlgorithmResult] for the given [FunctionMode]. */
fun parseAlgorithmResult(mode: com.ghealth.tools.core.model.FunctionMode, algoData: IntArray): AlgorithmResult {
    if (algoData.isEmpty()) return AlgorithmResult.None

    fun a(i: Int) = if (i < algoData.size) algoData[i] else 0

    return when (mode) {
        com.ghealth.tools.core.model.FunctionMode.HR -> AlgorithmResult.HR(
            heartRate = a(0),
            validScore = a(1),
            snr = a(2),
            accInfo = a(4)
        )
        com.ghealth.tools.core.model.FunctionMode.SPO2 -> AlgorithmResult.SPO2(
            spo2 = a(0),
            rValue = a(1),
            confiCoeff = a(2),
            validLevel = a(3),
            hbMean = a(4)
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
            wearOffDetectRes = a(0),
            liveBodyConf = a(1)
        )
        else -> AlgorithmResult.None
    }
}
