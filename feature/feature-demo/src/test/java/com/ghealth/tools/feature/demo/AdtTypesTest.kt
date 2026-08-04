package com.ghealth.tools.feature.demo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AdtTypesTest {

    @Test
    fun `wear event IDLE returns Idle`() {
        assertEquals("Idle", AdtWearEvent.labels(0))
    }

    @Test
    fun `wear event ON returns Wear`() {
        assertEquals("Wear", AdtWearEvent.labels(AdtWearEvent.ON))
    }

    @Test
    fun `wear event OFF returns Off`() {
        assertEquals("Off", AdtWearEvent.labels(AdtWearEvent.OFF))
    }

    @Test
    fun `wear event MOVE returns Move`() {
        assertEquals("Move", AdtWearEvent.labels(AdtWearEvent.MOVE))
    }

    @Test
    fun `wear event MOVE_TIME_OUT returns MoveTO`() {
        assertEquals("MoveTO", AdtWearEvent.labels(AdtWearEvent.MOVE_TIME_OUT))
    }

    @Test
    fun `wear event combined bits are joined with pipe`() {
        // ON | MOVE → "Wear|Move"
        assertEquals("Wear|Move", AdtWearEvent.labels(AdtWearEvent.ON or AdtWearEvent.MOVE))
    }

    @Test
    fun `wear event unknown high bit appended as hex`() {
        // ON | 0x10 → "Wear|0x10"
        assertEquals("Wear|0x10", AdtWearEvent.labels(AdtWearEvent.ON or 0x10))
    }

    @Test
    fun `wear event only unknown bit returns hex only`() {
        // 仅未知位（无已知位）时直接输出 "0x<hex>"，不带前导 "|"
        assertEquals("0x10", AdtWearEvent.labels(0x10))
    }

    @Test
    fun `det state maps raw values`() {
        assertEquals(AdtDetState.DET_ON, AdtDetState.fromValue(0))
        assertEquals(AdtDetState.DET_OFF, AdtDetState.fromValue(1))
        assertEquals(AdtDetState.UNKNOWN, AdtDetState.fromValue(2))
    }

    @Test
    fun `det state unknown raw falls back to UNKNOWN`() {
        assertEquals(AdtDetState.UNKNOWN, AdtDetState.fromValue(99))
    }

    @Test
    fun `det state labels match spec`() {
        assertEquals("Det-On", AdtDetState.DET_ON.label)
        assertEquals("Det-Off", AdtDetState.DET_OFF.label)
        assertEquals("Unknown", AdtDetState.UNKNOWN.label)
    }
}
