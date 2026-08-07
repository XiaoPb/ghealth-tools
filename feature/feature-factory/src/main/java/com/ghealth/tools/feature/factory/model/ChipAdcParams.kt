package com.ghealth.tools.feature.factory.model

/**
 * 芯片 ADC 参数（来自「PPG数据采集通用公式与配置说明」的芯片型号对比表）。
 */
data class ChipAdcParams(
    val fullScale: Double,   // ADC 满量程
    val offset: Double,      // ADC 偏移量
    val vref: Double,        // ADC 参考电压 (V)
    val tiaRatio: Double     // TIA 比例
) {
    companion object {
        /** 2^23 = 8,388,608 */
        const val FULL_SCALE = 8_388_608.0

        fun forChip(chip: String): ChipAdcParams = when (chip.lowercase()) {
            "gh3220", "gh3020", "gh3026", "gh3228t", "gh3300", "gh3310", "gh3030" -> ChipAdcParams(
                fullScale = FULL_SCALE,
                offset = FULL_SCALE,
                vref = 1.8,
                tiaRatio = 2.0
            )
            // gh3036（含 gh3038/gh3038q）及未知芯片
            else -> ChipAdcParams(
                fullScale = FULL_SCALE,
                offset = 0.0,
                vref = 1.8,
                tiaRatio = 2.0
            )
        }
    }
}
