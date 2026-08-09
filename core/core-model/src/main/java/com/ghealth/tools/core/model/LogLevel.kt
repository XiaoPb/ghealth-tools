package com.ghealth.tools.core.model

/**
 * 日志保存到文件的等级阈值。
 *
 * priority 与 android.util.Log 常量一致（VERBOSE=2 ... ERROR=6）；
 * 只有 priority >= 当前阈值（LogManager.appLogLevel）的日志才会写入 app_*.log。
 * 声明顺序即设置页下拉框展示顺序（E/W/I/D/V，最严格在前）。
 * 默认等级为 DEBUG（D）。
 */
enum class LogLevel(
    val key: String,
    val displayName: String,
    val priority: Int,
) {
    ERROR("E", "E（Error）", 6),
    WARN("W", "W（Warn）", 5),
    INFO("I", "I（Info）", 4),
    DEBUG("D", "D（Debug）", 3),
    VERBOSE("V", "V（Verbose）", 2);

    companion object {
        fun fromKey(key: String): LogLevel = entries.find { it.key == key } ?: DEBUG
    }
}
