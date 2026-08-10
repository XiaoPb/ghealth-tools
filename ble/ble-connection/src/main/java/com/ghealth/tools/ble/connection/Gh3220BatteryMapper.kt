package com.ghealth.tools.ble.connection

import com.ghealth.tools.ble.gh3220.event.Gh3220CurrentBattery

/**
 * GH3220 0x0D 电池上报 -> [BatteryStatus]。
 *
 * 协议单位与 BAT 服务一致为百分比（0-100），越界值钳制到合法区间；
 * 0x0D 不含充放电状态，chargeState 保持 [BatteryStatus.ChargeState.Unknown]。
 */
internal object Gh3220BatteryMapper {

    fun toBatteryStatus(battery: Gh3220CurrentBattery): BatteryStatus =
        BatteryStatus(level = battery.batteryPercent.coerceIn(0, 100))
}
