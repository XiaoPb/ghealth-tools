package com.ghealth.tools.feature.demo

/**
 * ADT 佩戴事件 IDLE 回退状态归约。
 *
 * 固件在无事件时上报 `wearEvent = IDLE(0)`。为避免界面在 Idle 与上一次有效事件之间频繁闪烁，
 * 收到 IDLE 时回退显示该 role 上一次的非 IDLE 事件。
 *
 * 该对象为纯函数，不持有状态；调用方（如 [com.ghealth.tools.feature.demo.DemoViewModel]）
 * 按 role 自行保存 `lastNonIdle` 并在每帧调用 [reduce]。
 */
object AdtWearStateReducer {
    /**
     * @param lastNonIdle 该 role 上一次非 IDLE 的 wearEvent；`null` 表示尚未出现过。
     * @param wearEvent 当前帧的 wearEvent。
     * @return `(新的 lastNonIdle, 用于显示的 wearEvent)`：
     *   - 非 IDLE 帧：更新历史并以其本身作为显示值；
     *   - IDLE 帧且有历史：保持历史，以历史值作为显示值；
     *   - IDLE 帧且无历史：保持 `null`，以 IDLE 作为显示值。
     */
    fun reduce(lastNonIdle: Int?, wearEvent: Int): Pair<Int?, Int> = when {
        wearEvent != AdtWearEvent.IDLE -> wearEvent to wearEvent
        lastNonIdle != null -> lastNonIdle to lastNonIdle
        else -> null to wearEvent
    }
}
