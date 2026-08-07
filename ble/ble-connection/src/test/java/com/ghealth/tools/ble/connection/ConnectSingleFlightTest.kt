package com.ghealth.tools.ble.connection

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConnectSingleFlightTest {

    @Test
    fun `second connect for same address is rejected while first is active`() {
        val singleFlight = ConnectSingleFlight()
        val ownerA = Any()
        val ownerB = Any()
        assertTrue(singleFlight.tryAcquire("AA:BB:CC:DD:EE:FF", ownerA))
        assertFalse(singleFlight.tryAcquire("AA:BB:CC:DD:EE:FF", ownerB))
    }

    @Test
    fun `different addresses can connect concurrently`() {
        val singleFlight = ConnectSingleFlight()
        val ownerA = Any()
        val ownerB = Any()
        assertTrue(singleFlight.tryAcquire("AA:BB:CC:DD:EE:FF", ownerA))
        assertTrue(singleFlight.tryAcquire("11:22:33:44:55:66", ownerB))
    }

    @Test
    fun `release by wrong owner does not free the slot`() {
        val singleFlight = ConnectSingleFlight()
        val ownerA = Any()
        val ownerB = Any()
        assertTrue(singleFlight.tryAcquire("AA:BB:CC:DD:EE:FF", ownerA))
        singleFlight.release("AA:BB:CC:DD:EE:FF", ownerB)
        assertTrue(singleFlight.isActive("AA:BB:CC:DD:EE:FF"))
        assertFalse(singleFlight.tryAcquire("AA:BB:CC:DD:EE:FF", ownerB))
        singleFlight.release("AA:BB:CC:DD:EE:FF", ownerA)
        assertTrue(singleFlight.tryAcquire("AA:BB:CC:DD:EE:FF", ownerA))
    }

    @Test
    fun `address matching is case insensitive`() {
        val singleFlight = ConnectSingleFlight()
        val ownerA = Any()
        val ownerB = Any()
        assertTrue(singleFlight.tryAcquire("AA:BB:CC:DD:EE:FF", ownerA))
        assertFalse(singleFlight.tryAcquire("aa:bb:cc:dd:ee:ff", ownerB))
        singleFlight.release("aa:bb:cc:dd:ee:ff", ownerA)
        assertTrue(singleFlight.tryAcquire("AA:BB:CC:DD:EE:FF", ownerA))
    }

    @Test
    fun `isActive reflects slot state`() {
        val singleFlight = ConnectSingleFlight()
        val ownerA = Any()
        assertFalse(singleFlight.isActive("AA:BB:CC:DD:EE:FF"))
        singleFlight.tryAcquire("AA:BB:CC:DD:EE:FF", ownerA)
        assertTrue(singleFlight.isActive("AA:BB:CC:DD:EE:FF"))
        singleFlight.release("AA:BB:CC:DD:EE:FF", ownerA)
        assertFalse(singleFlight.isActive("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `double release is idempotent and allows re-acquire`() {
        val singleFlight = ConnectSingleFlight()
        val ownerA = Any()
        assertTrue(singleFlight.tryAcquire("AA:BB:CC:DD:EE:FF", ownerA))
        singleFlight.release("AA:BB:CC:DD:EE:FF", ownerA)
        singleFlight.release("AA:BB:CC:DD:EE:FF", ownerA)
        assertTrue(singleFlight.tryAcquire("AA:BB:CC:DD:EE:FF", ownerA))
    }
}
