@file:OptIn(ExperimentalUuidApi::class)

package com.ghealth.tools.ble.connection

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BatteryServiceMatcherTest {

    private val batteryService = Uuid.parse("0000180f-0000-1000-8000-00805f9b34fb")
    private val otherService = Uuid.parse("0000180d-0000-1000-8000-00805f9b34fb")
    private val levelChar = BatteryServiceUuids.BATTERY_LEVEL_UUID
    private val statusChar = BatteryServiceUuids.BATTERY_LEVEL_STATUS_UUID
    private val unrelatedChar = Uuid.parse("00002a00-0000-1000-8000-00805f9b34fb")

    private fun ref(service: Uuid, char: Uuid) = DiscoveredCharacteristicRef(service, char)

    @Test
    fun `both battery level and status in battery service return that service uuid`() {
        val refs = listOf(
            ref(batteryService, levelChar),
            ref(batteryService, statusChar),
        )
        val result = BatteryServiceMatcher.match(refs)
        assertEquals(batteryService, result.batteryLevelServiceUuid)
        assertEquals(batteryService, result.batteryLevelStatusServiceUuid)
    }

    @Test
    fun `only battery level present returns level service and null status service`() {
        val refs = listOf(ref(batteryService, levelChar))
        val result = BatteryServiceMatcher.match(refs)
        assertEquals(batteryService, result.batteryLevelServiceUuid)
        assertNull(result.batteryLevelStatusServiceUuid)
    }

    @Test
    fun `only battery level status present returns null level and status service`() {
        val refs = listOf(ref(batteryService, statusChar))
        val result = BatteryServiceMatcher.match(refs)
        assertNull(result.batteryLevelServiceUuid)
        assertEquals(batteryService, result.batteryLevelStatusServiceUuid)
    }

    @Test
    fun `no battery characteristics returns both null`() {
        val refs = listOf(ref(otherService, unrelatedChar))
        val result = BatteryServiceMatcher.match(refs)
        assertNull(result.batteryLevelServiceUuid)
        assertNull(result.batteryLevelStatusServiceUuid)
    }

    @Test
    fun `empty refs returns both null`() {
        val result = BatteryServiceMatcher.match(emptyList())
        assertNull(result.batteryLevelServiceUuid)
        assertNull(result.batteryLevelStatusServiceUuid)
    }

    @Test
    fun `level and status in different services return each respective service`() {
        val refs = listOf(
            ref(otherService, levelChar),
            ref(batteryService, statusChar),
        )
        val result = BatteryServiceMatcher.match(refs)
        assertEquals(otherService, result.batteryLevelServiceUuid)
        assertEquals(batteryService, result.batteryLevelStatusServiceUuid)
    }

    @Test
    fun `duplicate level char across services returns first occurrence`() {
        val refs = listOf(
            ref(otherService, levelChar),
            ref(batteryService, levelChar),
            ref(batteryService, statusChar),
        )
        val result = BatteryServiceMatcher.match(refs)
        assertEquals(otherService, result.batteryLevelServiceUuid)
        assertEquals(batteryService, result.batteryLevelStatusServiceUuid)
    }

    @Test
    fun `matched result has stable equality`() {
        val refs = listOf(
            ref(batteryService, levelChar),
            ref(batteryService, statusChar),
        )
        assertEquals(BatteryServiceMatcher.match(refs), BatteryServiceMatcher.match(refs))
    }
}
