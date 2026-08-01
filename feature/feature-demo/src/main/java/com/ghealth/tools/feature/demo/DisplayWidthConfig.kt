package com.ghealth.tools.feature.demo

import com.ghealth.tools.core.model.FunctionMode

/**
 * 波形显示宽度(显示的数据点数)配置。
 *
 * 一帧写入一个采样点,因此"显示宽度"与采样率解耦:
 *   - ADT      5 Hz   → 10s = 50 点(唯一 10 秒默认)
 *   - HR 等   25 Hz   → 5s = 125 点
 *   - TEST    100 Hz   → 5s = 500 点
 * 其余生理信号模式按 25 Hz 处理,默认 125 点。
 */
object DisplayWidthConfig {

    /** 下拉框可选的显示宽度(数据点数)。 */
    val OPTIONS: List<Int> = listOf(50, 100, 125, 250, 500)

    /** 返回指定功能模式的默认显示宽度。 */
    fun defaultFor(funcMode: FunctionMode): Int = when (funcMode) {
        FunctionMode.ADT -> 50                  // 10s × 5Hz
        FunctionMode.HR,
        FunctionMode.HRV,
        FunctionMode.SPO2,
        FunctionMode.NADT_GREEN,
        FunctionMode.NADT_IR -> 125             // 5s × 25Hz
        FunctionMode.TEST1,
        FunctionMode.TEST2 -> 500               // 5s × 100Hz
        else -> 125                            // 其余模式按 5s × 25Hz
    }
}
