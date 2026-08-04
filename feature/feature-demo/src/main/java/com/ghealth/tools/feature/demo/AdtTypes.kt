package com.ghealth.tools.feature.demo

/**
 * ADT (Auto Detect Wear) 佩戴事件位掩码解析。
 *
 * 对应固件 `gh_adt_wear_e`（位掩码，多个事件可同时置位）：
 * - GH_ADT_WEAR_IDLE          = 0
 * - GH_ADT_WEAR_ON            = 1 << 0   佩戴
 * - GH_ADT_WEAR_OFF           = 1 << 1   摘下
 * - GH_ADT_WEAR_MOVE          = 1 << 2   移动
 * - GH_ADT_WEAR_MOVE_TIME_OUT = 1 << 3   移动超时
 *
 * OFFSET 依据枚举声明顺序推断（与现有 wearEvent==1→Wear / ==2→Off 行为一致）；
 * 若固件实际 OFFSET 不同，调整下方位掩码常量即可。
 */
object AdtWearEvent {
    const val IDLE: Int = 0
    const val ON: Int = 1 shl 0            // 1
    const val OFF: Int = 1 shl 1           // 2
    const val MOVE: Int = 1 shl 2          // 4
    const val MOVE_TIME_OUT: Int = 1 shl 3 // 8

    private val BITS: List<Pair<Int, String>> = listOf(
        ON to "Wear",
        OFF to "Off",
        MOVE to "Move",
        MOVE_TIME_OUT to "MoveTO"
    )

    /**
     * 返回 [value] 中所有已置位事件的可读标签，按固件枚举顺序拼接。
     *
     * - 0 → "Idle"
     * - 单事件 → 如 "Wear"
     * - 多事件叠加 → 如 "Wear|Move"
     * - 含未知位 → 追加 "0x<hex>"，如 "Wear|0x10"
     */
    fun labels(value: Int): String {
        if (value == IDLE) return "Idle"
        val sb = StringBuilder()
        var known = 0
        for ((bit, label) in BITS) {
            if (value and bit != 0) {
                if (sb.isNotEmpty()) sb.append('|')
                sb.append(label)
                known = known or bit
            }
        }
        val unknown = value and known.inv() and 0xFFFF
        if (unknown != 0) {
            if (sb.isNotEmpty()) sb.append('|')
            sb.append("0x${unknown.toString(16)}")
        }
        return if (sb.isEmpty()) "Idle" else sb.toString()
    }
}

/**
 * ADT 佩戴检测状态。对应固件 `gh_adt_det_state_e`：
 * - GH_ADT_WEAR_DET_ON     = 0  佩戴检测中
 * - GH_ADT_WEAR_DET_OFF    = 1  摘下检测中
 * - GH_ADT_WEAR_DET_UNKONW = 2  默认状态，准备中
 */
enum class AdtDetState(val raw: Int, val label: String) {
    DET_ON(0, "Det-On"),
    DET_OFF(1, "Det-Off"),
    UNKNOWN(2, "Unknown");

    companion object {
        /** 将原始 [raw] 值映射为枚举；未匹配值回落到 [UNKNOWN]。 */
        fun fromValue(raw: Int): AdtDetState =
            entries.firstOrNull { it.raw == raw } ?: UNKNOWN
    }
}
