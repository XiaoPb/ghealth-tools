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

    // ── reduceDetState ──────────────────────────────────────────────

    @Test
    fun `det state non-unknown updates last and returns itself as effective`() {
        val (last, effective) = AdtWearStateReducer.reduceDetState(
            lastNonUnknown = null,
            detStatus = AdtDetState.DET_ON.raw
        )
        assertEquals(AdtDetState.DET_ON.raw, last)
        assertEquals(AdtDetState.DET_ON.raw, effective)
    }

    @Test
    fun `det state unknown with history falls back to last non-unknown`() {
        val (last, effective) = AdtWearStateReducer.reduceDetState(
            lastNonUnknown = AdtDetState.DET_OFF.raw,
            detStatus = AdtDetState.UNKNOWN.raw
        )
        assertEquals(AdtDetState.DET_OFF.raw, last)
        assertEquals(AdtDetState.DET_OFF.raw, effective)
    }

    @Test
    fun `det state unknown without history stays unknown and keeps null`() {
        val (last, effective) = AdtWearStateReducer.reduceDetState(
            lastNonUnknown = null,
            detStatus = AdtDetState.UNKNOWN.raw
        )
        assertNull(last)
        assertEquals(AdtDetState.UNKNOWN.raw, effective)
    }

    @Test
    fun `det state non-unknown after unknown updates history to new value`() {
        val (last, effective) = AdtWearStateReducer.reduceDetState(
            lastNonUnknown = AdtDetState.DET_OFF.raw,
            detStatus = AdtDetState.DET_ON.raw
        )
        assertEquals(AdtDetState.DET_ON.raw, last)
        assertEquals(AdtDetState.DET_ON.raw, effective)
    }

    @Test
    fun `det state unrecognized raw value treated as unknown`() {
        // 99 不在 {0,1} 中，fromValue 回落 UNKNOWN
        val (last, effective) = AdtWearStateReducer.reduceDetState(
            lastNonUnknown = AdtDetState.DET_ON.raw,
            detStatus = 99
        )
        assertEquals(AdtDetState.DET_ON.raw, last)
        assertEquals(AdtDetState.DET_ON.raw, effective)
    }
}
