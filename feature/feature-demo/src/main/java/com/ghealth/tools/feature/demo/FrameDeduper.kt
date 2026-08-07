package com.ghealth.tools.feature.demo

import com.ghealth.tools.core.model.FunctionMode

/**
 * 帧级去重：同一设备同一功能模式下，最近 [recentSize] 帧内出现
 * (frameCnt, timestamp) 完全相同的帧时，判定为设备滚动窗口重发的重复帧，
 * 应跳过（不进波形缓冲、不写 CSV）。
 *
 * 设备每次通知携带最近 5 帧窗口、相邻窗口重叠 2~3 帧，重发帧的
 * (frameCnt, timestamp) 与首次出现时完全相同；正常新帧二者唯一，
 * 因此不会误杀帧号回绕/重置的合法新帧。
 *
 * 注意：此类非线程安全，调用方需保证单线程访问（当前由 ghFrameFlow 的 Main 收集器串行调用）。
 */
class FrameDeduper(private val recentSize: Int = 16) {
    private val recentByKey =
        mutableMapOf<Pair<String, FunctionMode>, ArrayDeque<Pair<Int, Long>>>()

    /**
     * @return true 表示该帧与近期收到的帧重复，应丢弃；false 表示正常新帧。
     */
    fun isDuplicate(
        address: String,
        funcMode: FunctionMode,
        frameCnt: Int,
        timestamp: Long
    ): Boolean {
        val key = address to funcMode
        val deque = recentByKey.getOrPut(key) { ArrayDeque() }
        val stamp = frameCnt to timestamp
        val duplicate = stamp in deque
        if (!duplicate) {
            deque.addLast(stamp)
            if (deque.size > recentSize) deque.removeFirst()
        }
        return duplicate
    }

    fun clear() {
        recentByKey.clear()
    }

    /** 设备断开/移除后清理该地址的所有去重状态，避免旧窗口误杀重连设备的合法新帧。 */
    fun removeAddress(address: String) {
        recentByKey.keys.removeAll { key -> key.first == address }
    }
}
