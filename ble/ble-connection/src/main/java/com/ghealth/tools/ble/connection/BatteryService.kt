@file:OptIn(ExperimentalUuidApi::class)

package com.ghealth.tools.ble.connection

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 标准 Battery Service (0x180F) 相关 UUID。
 * 参考 Bluetooth GATT Specification Supplement。
 */
object BatteryServiceUuids {
    /** Battery Service: 0000180f-0000-1000-8000-00805f9b34fb */
    val BATTERY_SERVICE_UUID: Uuid = Uuid.parse("0000180f-0000-1000-8000-00805f9b34fb")

    /** Battery Level (0x2A19): 单字节 uint8，0–100 百分比，必选。 */
    val BATTERY_LEVEL_UUID: Uuid = Uuid.parse("00002a19-0000-1000-8000-00805f9b34fb")

    /** Battery Level Status (0x2A1E): 含 flags 与若干可选字段，用于充放电状态，可选。 */
    val BATTERY_LEVEL_STATUS_UUID: Uuid = Uuid.parse("00002a1e-0000-1000-8000-00805f9b34fb")
}

/**
 * 电池状态快照。
 *
 * @param level 电量百分比 0–100；null 表示尚未读到。
 * @param chargeState 充放电状态；[ChargeState.Unknown] 表示设备未提供或解析失败。
 */
data class BatteryStatus(
    val level: Int? = null,
    val chargeState: ChargeState = ChargeState.Unknown,
) {
    enum class ChargeState {
        Unknown,
        Charging,
        Discharging,
        NotCharging,
        Full,
    }
}
