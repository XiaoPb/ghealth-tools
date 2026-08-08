package com.ghealth.tools.feature.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UpdateSchedulerTest {

    @Test
    fun `整点时刻距离下一个整点为整整一小时`() {
        assertEquals(3_600_000L, UpdateScheduler.delayUntilNextHourMillis(3_600_000L))
    }

    @Test
    fun `整点前一毫秒距离下一整点为一毫秒`() {
        assertEquals(1L, UpdateScheduler.delayUntilNextHourMillis(7_199_999L))
    }

    @Test
    fun `零点三十分距离下一整点为三十分钟`() {
        assertEquals(1_800_000L, UpdateScheduler.delayUntilNextHourMillis(1_800_000L))
    }

    @Test
    fun `跨天仍按整点计算`() {
        val twoDaysInMillis = 2L * 24 * 3_600_000
        assertEquals(3_600_000L, UpdateScheduler.delayUntilNextHourMillis(twoDaysInMillis))
    }
}
