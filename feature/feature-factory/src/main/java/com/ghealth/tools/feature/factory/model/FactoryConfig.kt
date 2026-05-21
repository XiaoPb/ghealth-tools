package com.ghealth.tools.feature.factory.model

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

@JsonClass(generateAdapter = false)
data class TestDef(
    val enabled: Boolean = true,
    val description: String = "",
    val mode: Int = 0,
    val channels: Int = 1,
    val unit: String = "",
    @Json(name = "global_threshold") val globalThreshold: ThresholdDef? = null
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
}
