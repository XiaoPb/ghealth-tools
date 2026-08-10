package com.ghealth.tools.ble.connection

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BatterySourceSelectorTest {

    @Test
    fun `protocol battery allowed when no battery service marked`() {
        val selector = BatterySourceSelector()
        assertTrue(selector.shouldUseProtocolBattery("AA:BB:CC:00:00:01"))
    }

    @Test
    fun `battery service takes priority over protocol report`() {
        val selector = BatterySourceSelector()
        selector.markBatteryService("AA:BB:CC:00:00:01")
        assertFalse(selector.shouldUseProtocolBattery("AA:BB:CC:00:00:01"))
    }

    @Test
    fun `mark is idempotent and scoped per address`() {
        val selector = BatterySourceSelector()
        selector.markBatteryService("AA:BB:CC:00:00:01")
        selector.markBatteryService("AA:BB:CC:00:00:01")
        assertFalse(selector.shouldUseProtocolBattery("AA:BB:CC:00:00:01"))
        assertTrue(selector.shouldUseProtocolBattery("AA:BB:CC:00:00:02"))
    }

    @Test
    fun `remove clears marker so reconnect can use protocol battery again`() {
        val selector = BatterySourceSelector()
        selector.markBatteryService("AA:BB:CC:00:00:01")
        selector.remove("AA:BB:CC:00:00:01")
        assertTrue(selector.shouldUseProtocolBattery("AA:BB:CC:00:00:01"))
    }
}
