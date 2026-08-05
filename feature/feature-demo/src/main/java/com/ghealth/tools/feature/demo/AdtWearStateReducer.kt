package com.ghealth.tools.feature.demo

/**
 * ADT 佩戴事件 IDLE 与检测状态 UNKNOWN 回退状态归约。
 *
 * - 固件在无事件时上报 `wearEvent = IDLE(0)`。为避免界面在 Idle 与上一次有效事件之间频繁闪烁，
 *   收到 IDLE 时回退显示该 role 上一次的非 IDLE 事件（见 [reduce]）。
 * - 固件在准备中上报 `detStatus = UNKNOWN(2)`。为避免界面在 Unknown 与上一次有效检测状态之间频繁闪烁，
 *   收到 UNKNOWN 时回退显示该 role 上一次的非 UNKNOWN 检测状态（见 [reduceDetState]）。
 *
 * 该对象为纯函数，不持有状态；调用方（如 [com.ghealth.tools.feature.demo.DemoViewModel]）
 * 按 role 自行保存 `lastNonIdle` / `lastNonUnknown` 并在每帧调用对应函数。
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

    /**
     * detStatus UNKNOWN 回退：当 [AdtDetState.fromValue]`([detStatus])` 为 [AdtDetState.UNKNOWN] 时，
     * 用该 role 上一次非 UNKNOWN 的 detStatus 替换，便于界面持续显示有效检测状态。
     * 非 UNKNOWN 帧更新历史。
     *
     * @param lastNonUnknown 该 role 上一次非 UNKNOWN 的 detStatus；`null` 表示尚未出现过。
     * @param detStatus 当前帧的 detStatus 原始值。
     * @return `(新的 lastNonUnknown, 用于显示的 detStatus)`：
     *   - 非 UNKNOWN 帧：更新历史并以本身作为显示值；
     *   - UNKNOWN 帧且有历史：保持历史，以历史值作为显示值；
     *   - UNKNOWN 帧且无历史：保持 `null`，以 UNKNOWN 作为显示值。
     */
    fun reduceDetState(lastNonUnknown: Int?, detStatus: Int): Pair<Int?, Int> {
        val isUnknown = AdtDetState.fromValue(detStatus) == AdtDetState.UNKNOWN
        return when {
            !isUnknown -> detStatus to detStatus
            lastNonUnknown != null -> lastNonUnknown to lastNonUnknown
            else -> null to detStatus
        }
    }
}
