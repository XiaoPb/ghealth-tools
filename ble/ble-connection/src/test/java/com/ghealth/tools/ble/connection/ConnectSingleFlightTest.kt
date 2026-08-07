package com.ghealth.tools.ble.connection

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConnectSingleFlightTest {

    @Test
    fun `second connect for same address is rejected while first is active`() {
        val singleFlight = ConnectSingleFlight()
        assertTrue(singleFlight.tryAcquire("AA:BB:CC:DD:EE:FF"))
        assertFalse(singleFlight.tryAcquire("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `different addresses can connect concurrently`() {
        val singleFlight = ConnectSingleFlight()
        assertTrue(singleFlight.tryAcquire("AA:BB:CC:DD:EE:FF"))
        assertTrue(singleFlight.tryAcquire("11:22:33:44:55:66"))
    }

    @Test
    fun `release frees the slot so reconnect is allowed`() {
        val singleFlight = ConnectSingleFlight()
        assertTrue(singleFlight.tryAcquire("AA:BB:CC:DD:EE:FF"))
        singleFlight.release("AA:BB:CC:DD:EE:FF")
        assertTrue(singleFlight.tryAcquire("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `address matching is case insensitive`() {
        val singleFlight = ConnectSingleFlight()
        assertTrue(singleFlight.tryAcquire("AA:BB:CC:DD:EE:FF"))
        assertFalse(singleFlight.tryAcquire("aa:bb:cc:dd:ee:ff"))
        singleFlight.release("aa:bb:cc:dd:ee:ff")
        assertTrue(singleFlight.tryAcquire("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `isActive reflects slot state`() {
        val singleFlight = ConnectSingleFlight()
        assertFalse(singleFlight.isActive("AA:BB:CC:DD:EE:FF"))
        singleFlight.tryAcquire("AA:BB:CC:DD:EE:FF")
        assertTrue(singleFlight.isActive("AA:BB:CC:DD:EE:FF"))
        singleFlight.release("AA:BB:CC:DD:EE:FF")
        assertFalse(singleFlight.isActive("AA:BB:CC:DD:EE:FF"))
    }
}