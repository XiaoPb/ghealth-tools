package com.ghealth.tools.ble.connection

import com.ghealth.tools.ble.connection.BatteryStatus.ChargeState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BatteryLevelStatusParserTest {

    // ── parseLevel (0x2A19) ──────────────────────────────────────────

    @Test
    fun `level 0 returns 0`() {
        assertEquals(0, BatteryLevelStatusParser.parseLevel(byteArrayOf(0)))
    }

    @Test
    fun `level 100 returns 100`() {
        assertEquals(100, BatteryLevelStatusParser.parseLevel(byteArrayOf(100)))
    }

    @Test
    fun `level 50 returns 50`() {
        assertEquals(50, BatteryLevelStatusParser.parseLevel(byteArrayOf(50)))
    }

    @Test
    fun `level above 100 returns null`() {
        assertNull(BatteryLevelStatusParser.parseLevel(byteArrayOf(101)))
        assertNull(BatteryLevelStatusParser.parseLevel(byteArrayOf(255.toByte())))
    }

    @Test
    fun `empty level data returns null`() {
        assertNull(BatteryLevelStatusParser.parseLevel(byteArrayOf()))
    }

    // ── parseChargeState (0x2A1E) ────────────────────────────────────

    @Test
    fun `status field charging returns Charging`() {
        // flags: bit5 Battery Status Present (0x20)
        // no bit6/3/4 → status 字段在 offset 1
        val data = byteArrayOf(0x20, 0x02)
        assertEquals(ChargeState.Charging, BatteryLevelStatusParser.parseChargeState(data))
    }

    @Test
    fun `status field discharging not full returns Discharging`() {
        val data = byteArrayOf(0x20, 0x03)
        assertEquals(ChargeState.Discharging, BatteryLevelStatusParser.parseChargeState(data))
    }

    @Test
    fun `status field full returns Full`() {
        val data = byteArrayOf(0x20, 0x04)
        assertEquals(ChargeState.Full, BatteryLevelStatusParser.parseChargeState(data))
    }

    @Test
    fun `status field not charging returns NotCharging`() {
        val data = byteArrayOf(0x20, 0x01)
        assertEquals(ChargeState.NotCharging, BatteryLevelStatusParser.parseChargeState(data))
    }

    @Test
    fun `status field unknown enum returns Unknown`() {
        val data = byteArrayOf(0x20, 0x00)
        assertEquals(ChargeState.Unknown, BatteryLevelStatusParser.parseChargeState(data))
    }

    @Test
    fun `status field reserved enum value returns Unknown`() {
        val data = byteArrayOf(0x20, 0x05)
        assertEquals(ChargeState.Unknown, BatteryLevelStatusParser.parseChargeState(data))
    }

    @Test
    fun `status present with level and charge level and charge type before status offsets correctly`() {
        // flags: bit6 Battery Level (1B) + bit3 Charge Level (2B) + bit4 Charge Type (1B) + bit5 Status (1B)
        // = 0x40 | 0x08 | 0x10 | 0x20 = 0x78
        // offset = 1 + 1 + 2 + 1 = 5
        val data = byteArrayOf(
            0x78,        // flags
            80.toByte(), // battery level
            50, 0,       // charge level (uint16 LE)
            1,           // charge type
            0x02,        // status = Charging
        )
        assertEquals(ChargeState.Charging, BatteryLevelStatusParser.parseChargeState(data))
    }

    @Test
    fun `status field absent but wired external power connected infers Charging`() {
        // flags: bit1 Wired External Power (0x02)，无 bit5
        val data = byteArrayOf(0x02)
        assertEquals(ChargeState.Charging, BatteryLevelStatusParser.parseChargeState(data))
    }

    @Test
    fun `status field absent but wireless external power connected infers Charging`() {
        // flags: bit2 Wireless External Power (0x04)，无 bit5
        val data = byteArrayOf(0x04)
        assertEquals(ChargeState.Charging, BatteryLevelStatusParser.parseChargeState(data))
    }

    @Test
    fun `status field absent and no external power returns Unknown`() {
        // flags: 仅 bit0 Battery Present (0x01)
        val data = byteArrayOf(0x01)
        assertEquals(ChargeState.Unknown, BatteryLevelStatusParser.parseChargeState(data))
    }

    @Test
    fun `status present but data truncated returns Unknown`() {
        // flags 标记 status present，但实际无 status 字节
        val data = byteArrayOf(0x20)
        assertEquals(ChargeState.Unknown, BatteryLevelStatusParser.parseChargeState(data))
    }

    @Test
    fun `empty status data returns Unknown`() {
        assertEquals(ChargeState.Unknown, BatteryLevelStatusParser.parseChargeState(byteArrayOf()))
    }
}
