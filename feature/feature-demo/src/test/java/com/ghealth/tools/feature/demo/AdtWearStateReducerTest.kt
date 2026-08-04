package com.ghealth.tools.feature.demo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AdtWearStateReducerTest {

    @Test
    fun `non-idle event updates last and returns itself as effective`() {
        val (last, effective) = AdtWearStateReducer.reduce(lastNonIdle = null, wearEvent = AdtWearEvent.ON)
        assertEquals(AdtWearEvent.ON, last)
        assertEquals(AdtWearEvent.ON, effective)
    }

    @Test
    fun `idle with history falls back to last non-idle and keeps history`() {
        val (last, effective) = AdtWearStateReducer.reduce(
            lastNonIdle = AdtWearEvent.OFF,
            wearEvent = AdtWearEvent.IDLE
        )
        assertEquals(AdtWearEvent.OFF, last)
        assertEquals(AdtWearEvent.OFF, effective)
    }

    @Test
    fun `idle without history stays idle and keeps null`() {
        val (last, effective) = AdtWearStateReducer.reduce(
            lastNonIdle = null,
            wearEvent = AdtWearEvent.IDLE
        )
        assertNull(last)
        assertEquals(AdtWearEvent.IDLE, effective)
    }

    @Test
    fun `non-idle after idle updates history to new value`() {
        val (last, effective) = AdtWearStateReducer.reduce(
            lastNonIdle = AdtWearEvent.OFF,
            wearEvent = AdtWearEvent.MOVE
        )
        assertEquals(AdtWearEvent.MOVE, last)
        assertEquals(AdtWearEvent.MOVE, effective)
    }

    @Test
    fun `combined non-idle bits update history and return as effective`() {
        val combined = AdtWearEvent.ON or AdtWearEvent.MOVE
        val (last, effective) = AdtWearStateReducer.reduce(lastNonIdle = null, wearEvent = combined)
        assertEquals(combined, last)
        assertEquals(combined, effective)
    }
}
