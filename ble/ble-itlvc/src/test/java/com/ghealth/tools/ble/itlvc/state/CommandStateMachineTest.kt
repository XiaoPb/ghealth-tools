package com.ghealth.tools.ble.itlvc.state

import com.ghealth.tools.ble.itlvc.core.CommandSpec
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandStateMachineTest {

    @Test
    fun `command lifecycle transitions`() {
        val sm = CommandStateMachine()
        assertEquals(CommandState.CREATED, sm.state)
        sm.onEnqueued(); assertEquals(CommandState.PENDING_SEND, sm.state)
        sm.onSent(); assertEquals(CommandState.AWAITING_RESPONSE, sm.state); assertEquals(1, sm.attempts)
        sm.onResponse(); assertEquals(CommandState.COMPLETED, sm.state)
    }

    @Test
    fun `timeout then retry then success`() {
        val sm = CommandStateMachine()
        sm.onEnqueued(); sm.onSent()
        sm.onTimeout(); assertEquals(CommandState.TIMED_OUT, sm.state)
        sm.onSent(); assertEquals(2, sm.attempts)
        sm.onResponse(); assertEquals(CommandState.COMPLETED, sm.state)
    }

    @Test
    fun `failure is terminal`() {
        val sm = CommandStateMachine()
        sm.onEnqueued(); sm.onSent()
        sm.onFailure(); assertEquals(CommandState.FAILED, sm.state)
    }

    @Test
    fun `command spec defaults and pass-through flag`() {
        val spec = CommandSpec(type = byteArrayOf(0x1A))
        assertContentEquals(byteArrayOf(0x1A), spec.type)
        assertEquals(1000L, spec.timeoutMs)
        assertEquals(0, spec.retryCount)
        assertFalse(spec.allowedInPassThrough)

        val pt = CommandSpec(type = byteArrayOf(0x19), allowedInPassThrough = true)
        assertTrue(pt.allowedInPassThrough)
    }
}

