package com.ghealth.tools.ble.connection

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotifySubscriptionGuardTest {

    @Test
    fun `first subscription for an address is allowed`() {
        val guard = NotifySubscriptionGuard()
        assertTrue(guard.tryRegister("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `duplicate subscription for same address is rejected`() {
        val guard = NotifySubscriptionGuard()
        assertTrue(guard.tryRegister("AA:BB:CC:DD:EE:FF"))
        assertFalse(guard.tryRegister("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `different addresses subscribe independently`() {
        val guard = NotifySubscriptionGuard()
        assertTrue(guard.tryRegister("AA:BB:CC:DD:EE:FF"))
        assertTrue(guard.tryRegister("11:22:33:44:55:66"))
    }

    @Test
    fun `unregister after disconnect allows subscribing again`() {
        val guard = NotifySubscriptionGuard()
        assertTrue(guard.tryRegister("AA:BB:CC:DD:EE:FF"))
        guard.unregister("AA:BB:CC:DD:EE:FF")
        assertTrue(guard.tryRegister("AA:BB:CC:DD:EE:FF"))
    }
}
