package com.ghealth.tools.feature.settings

object UpdateScheduler {

    private const val HOUR_MILLIS = 60 * 60 * 1000L

    /** 从 nowMillis 到下一个整点 0 分时刻的毫秒数（范围为 1..3_600_000）。 */
    fun delayUntilNextHourMillis(nowMillis: Long): Long {
        val nextHourBoundary = (nowMillis / HOUR_MILLIS + 1) * HOUR_MILLIS
        return nextHourBoundary - nowMillis
    }
}
