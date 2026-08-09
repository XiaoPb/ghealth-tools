package com.ghealth.tools.core.storage

import com.ghealth.tools.core.model.LogLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LogLevelTest {

    @Test
    fun `fromKey maps all five levels`() {
        assertEquals(LogLevel.ERROR, LogLevel.fromKey("E"))
        assertEquals(LogLevel.WARN, LogLevel.fromKey("W"))
        assertEquals(LogLevel.INFO, LogLevel.fromKey("I"))
        assertEquals(LogLevel.DEBUG, LogLevel.fromKey("D"))
        assertEquals(LogLevel.VERBOSE, LogLevel.fromKey("V"))
    }

    @Test
    fun `fromKey falls back to debug for unknown key`() {
        assertEquals(LogLevel.DEBUG, LogLevel.fromKey(""))
        assertEquals(LogLevel.DEBUG, LogLevel.fromKey("X"))
    }

    @Test
    fun `priorities match android log constants`() {
        assertEquals(2, LogLevel.VERBOSE.priority)
        assertEquals(3, LogLevel.DEBUG.priority)
        assertEquals(4, LogLevel.INFO.priority)
        assertEquals(5, LogLevel.WARN.priority)
        assertEquals(6, LogLevel.ERROR.priority)
    }
}
