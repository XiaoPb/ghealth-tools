package com.ghealth.tools.feature.factory.model
import java.util.Locale

enum class TestType(
    val displayName: String,
    val defaultCollapsed: Boolean,
    val mode: Int,
    val errorBase: Int
) {
    CHIP_INIT("芯片初始化", false, 0x01, 0x1001),
    CHIP_UID("设备UUID", false, 0x02, 0x2001),
    BASE_NOISE("底噪测试", true, 0x04, 0x3000),
    PPG_NOISE("PPG噪声测试", true, 0x08, 0x4000),
    LPCTR("LPCTR测试", true, 0x10, 0x5000),
    LPLCTR("LPLCTR测试", true, 0x20, 0x6000);

    companion object {
        fun fromMode(mode: Int): TestType? = entries.find { it.mode == mode }
    }
}

data class TestResult(
    val testType: TestType,
    val channelIndex: Int,
    val value: Int,
    val unit: String,
    val threshold: String,
    val passed: Boolean,
    val errorCode: Int = 0,
    val displayValue: String? = null,
    val computedValue: Double? = null
) {
    val errorCodeComputed: Int
        get() = if (passed) 0 else testType.errorBase + channelIndex

    val formattedValue: String
        get() = displayValue ?: computedValue?.let { formatComputed(it) } ?: value.toString()

    companion object {
        /** 计算值格式化：最多 3 位小数，去掉末尾 0；NaN/Inf 显示 N/A。 */
        fun formatComputed(value: Double): String =
            if (value.isNaN() || value.isInfinite()) "N/A"
            else String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')
    }
}
