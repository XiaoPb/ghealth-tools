package com.ghealth.tools.feature.factory.model

import java.util.Locale
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class FactoryConfig(
    val project: String,
    val version: String = "1.0",
    val description: String = "",
    val chip: String = "gh3036",
    val global: GlobalConfig = GlobalConfig(),
    val tests: Map<String, TestDef> = emptyMap()
)

@JsonClass(generateAdapter = false)
data class GlobalConfig(
    @Json(name = "default_operator") val defaultOperator: String = "le",
    @Json(name = "fail_action") val failAction: String = "continue"
)

/**
 * App 端计算回退配置（F_GetMode 无数据时按公式计算）。
 */
@JsonClass(generateAdapter = false)
data class AppComputeConfig(
    /** 跨阻增益 kΩ，rawdata 法计算 Ipd（公式1）必需。 */
    @Json(name = "gain_k") val gainK: Double? = null,
    /** LED 驱动电流 mA；缺省从 AGC 帧 led_current_sum(0.1mA)/10 读取。 */
    @Json(name = "led_current_ma") val ledCurrentMa: Double? = null,
    /** 采样率 Hz，用于 0.5Hz 高通滤波系数，默认 100。 */
    @Json(name = "sample_rate_hz") val sampleRateHz: Int = 100,
    /** 采集所需最少帧数（计算窗口 = 最后 min_number 帧）；未配置用默认 100。 */
    @Json(name = "min_number") val minNumber: Int? = null,
    /** 跳过帧数（预热）；总帧数 = skip_number + min_number。 */
    @Json(name = "skip_number") val skipNumber: Int? = null,
    /** 1=要求末尾 min_number 帧帧号连续；0=不要求。 */
    @Json(name = "is_continuous") val isContinuous: Int? = null,
    /** 采集超时 ms。 */
    @Json(name = "timeout") val timeout: Long? = null
)

@JsonClass(generateAdapter = false)
data class TestDef(
    val enabled: Boolean = true,
    val description: String = "",
    val mode: Int = 0,
    val channels: Int = 1,
    val unit: String = "",
    @Json(name = "global_threshold") val globalThreshold: ThresholdDef? = null,
    @Json(name = "compute") val compute: AppComputeConfig? = null
)

@JsonClass(generateAdapter = false)
data class ThresholdDef(
    val operator: String = "le",
    val value: Any? = null
) {
    fun getSingleValue(): Int? = when (value) {
        is Double -> value.toInt()
        is Int -> value
        is String -> value.toIntOrNull()
        else -> null
    }

    fun getRange(): Pair<Int, Int>? {
        val list = value as? List<*> ?: return null
        if (list.size < 2) return null
        val min = when (val v = list[0]) {
            is Double -> v.toInt()
            is Int -> v
            else -> return null
        }
        val max = when (val v = list[1]) {
            is Double -> v.toInt()
            is Int -> v
            else -> return null
        }
        return min to max
    }

    fun getSingleValueDouble(): Double? = when (val v = value) {
        is Double -> v
        is Int -> v.toDouble()
        is String -> v.toDoubleOrNull()
        else -> null
    }

    fun getRangeDouble(): Pair<Double, Double>? {
        val list = value as? List<*> ?: return null
        if (list.size < 2) return null
        val min = when (val v = list[0]) {
            is Double -> v
            is Int -> v.toDouble()
            else -> return null
        }
        val max = when (val v = list[1]) {
            is Double -> v
            is Int -> v.toDouble()
            else -> return null
        }
        return min to max
    }
}

enum class FailAction(val key: String) {
    CONTINUE("continue"),
    ABORT("abort");

    companion object {
        fun fromKey(key: String): FailAction = entries.find { it.key == key } ?: CONTINUE
    }
}

enum class ThresholdOperator(val key: String, val display: String) {
    LT("lt", "<"),
    LE("le", "≤"),
    GT("gt", ">"),
    GE("ge", "≥"),
    EQ("eq", "="),
    NE("ne", "≠"),
    RANGE("range", "∈");

    companion object {
        fun fromKey(key: String): ThresholdOperator = entries.find { it.key == key } ?: LE
    }

    fun evaluate(value: Int, threshold: ThresholdDef): Boolean = when (this) {
        LT -> value < (threshold.getSingleValue() ?: return false)
        LE -> value <= (threshold.getSingleValue() ?: return false)
        GT -> value > (threshold.getSingleValue() ?: return false)
        GE -> value >= (threshold.getSingleValue() ?: return false)
        EQ -> value == (threshold.getSingleValue() ?: return false)
        NE -> value != (threshold.getSingleValue() ?: return false)
        RANGE -> {
            val (min, max) = threshold.getRange() ?: return false
            value in min..max
        }
    }

    fun formatThreshold(threshold: ThresholdDef): String = when (this) {
        RANGE -> {
            val (min, max) = threshold.getRange() ?: return "?"
            "${display}[$min, $max]"
        }
        else -> "${display}${threshold.getSingleValue() ?: "?"}"
    }

    fun evaluate(value: Double, threshold: ThresholdDef): Boolean = when (this) {
        LT -> value < (threshold.getSingleValueDouble() ?: return false)
        LE -> value <= (threshold.getSingleValueDouble() ?: return false)
        GT -> value > (threshold.getSingleValueDouble() ?: return false)
        GE -> value >= (threshold.getSingleValueDouble() ?: return false)
        EQ -> value == (threshold.getSingleValueDouble() ?: return false)
        NE -> value != (threshold.getSingleValueDouble() ?: return false)
        RANGE -> {
            val (min, max) = threshold.getRangeDouble() ?: return false
            value in min..max
        }
    }

    fun formatThresholdDouble(threshold: ThresholdDef): String = when (this) {
        RANGE -> {
            val (min, max) = threshold.getRangeDouble() ?: return "?"
            "${display}[${formatNumber(min)}, ${formatNumber(max)}]"
        }
        else -> "${display}${threshold.getSingleValueDouble()?.let { formatNumber(it) } ?: "?"}"
    }

    private fun formatNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString()
        else String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')
}
