package com.ghealth.tools.ble.connection

import com.ghealth.tools.ble.gh3220.event.Gh3220CurrentBattery
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class Gh3220BatteryMapperTest {

    private fun battery(percent: Int) = Gh3220CurrentBattery(
        cardiffCurrent = 0,
        batteryPercent = percent,
        txCurrent = 0,
        bleSendPackageCount = 0,
    )

    @Test
    fun `maps protocol percent to battery level`() {
        val status = Gh3220BatteryMapper.toBatteryStatus(battery(63))
        assertEquals(63, status.level)
        assertEquals(BatteryStatus.ChargeState.Unknown, status.chargeState)
    }

    @Test
    fun `clamps out of range percent`() {
        assertEquals(100, Gh3220BatteryMapper.toBatteryStatus(battery(255)).level)
        assertEquals(0, Gh3220BatteryMapper.toBatteryStatus(battery(-1)).level)
    }
}
