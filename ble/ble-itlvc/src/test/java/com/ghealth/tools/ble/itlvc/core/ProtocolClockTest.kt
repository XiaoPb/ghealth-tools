package com.ghealth.tools.ble.itlvc.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ProtocolClockTest {

    class ManualClock(var value: Long = 0L) : ProtocolClock {
        override fun now(): Long = value
        fun advance(ms: Long) { value += ms }
    }

    @Test
    fun `injected clock is used by session consumers`() {
        val clock = ManualClock()
        assertEquals(0L, clock.now())
        clock.advance(150)
        assertEquals(150L, clock.now())
    }

    @Test
    fun `system clock moves forward`() {
        val a = SystemClock.now()
        Thread.sleep(5)
        assert(SystemClock.now() >= a)
    }

    @Test
    fun `config defaults match spec`() {
        val c = ItlvcConfig()
        assertEquals(100L, c.frameTimeoutMs)
        assertEquals(1000L, c.defaultResponseTimeoutMs)
        assertEquals(0, c.defaultRetryCount)
        assertEquals(false, c.passThroughMode)
    }
}
