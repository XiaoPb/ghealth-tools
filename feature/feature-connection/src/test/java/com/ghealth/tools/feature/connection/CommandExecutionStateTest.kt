package com.ghealth.tools.feature.connection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class CommandExecutionStateTest {

    @Test
    fun `executing and failed states with same key are not equal`() {
        val executing = CommandExecutionState(isExecuting = true, commandKey = "F_GetMode")
        val failed = CommandExecutionState(isExecuting = false, error = "Timeout", commandKey = "F_GetMode")

        assertNotEquals(executing, failed)
        assertNotEquals(executing.hashCode(), failed.hashCode())
    }

    @Test
    fun `different errors produce different states`() {
        val timeout = CommandExecutionState(error = "Timeout", commandKey = "F_GetMode")
        val notFound = CommandExecutionState(error = "Command not found", commandKey = "F_GetMode")

        assertNotEquals(timeout, notFound)
    }

    @Test
    fun `states with same result content are equal`() {
        val a = CommandExecutionState(result = byteArrayOf(0x01, 0x02), commandKey = "GH3X_GetVersion")
        val b = CommandExecutionState(result = byteArrayOf(0x01, 0x02), commandKey = "GH3X_GetVersion")

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `states with different results are not equal`() {
        val a = CommandExecutionState(result = byteArrayOf(0x01), commandKey = "GH3X_GetVersion")
        val b = CommandExecutionState(result = byteArrayOf(0x02), commandKey = "GH3X_GetVersion")

        assertNotEquals(a, b)
    }

    @Test
    fun `states with different keys are not equal`() {
        val a = CommandExecutionState(result = byteArrayOf(0x01), commandKey = "F_GetMode")
        val b = CommandExecutionState(result = byteArrayOf(0x01), commandKey = "GH3X_GetVersion")

        assertNotEquals(a, b)
    }
}
