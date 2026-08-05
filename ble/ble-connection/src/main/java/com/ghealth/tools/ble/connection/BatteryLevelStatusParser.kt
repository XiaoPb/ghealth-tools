package com.ghealth.tools.ble.connection

/**
 * 解析 Battery Service 特征值。
 *
 * - [parseLevel]：Battery Level (0x2A19)，1 字节 uint8，0–100。
 * - [parseChargeState]：Battery Level Status (0x2A1E)，按 GATT Spec Supplement
 *   依 flags 位顺序跳过可选字段后读取 Battery Status 枚举；若设备未提供
 *   Status 字段但外接电源已连接，则保守推断为充电中。
 *
 * 注意：0x2A1E 解析基于 GSS v7（pre-BAS v1.1）结构，即 Flags 各 bit 表示
 * 独立可选字段存在性。BAS v1.1 引入了不兼容的 Power State 字段结构，
 * 若需支持新设备需另行实现。
 */
internal object BatteryLevelStatusParser {

    /** Battery Level Status flags 位掩码。 */
    private const val FLAG_WIRED_POWER = 0x02
    private const val FLAG_WIRELESS_POWER = 0x04
    private const val FLAG_CHARGE_LEVEL_PRESENT = 0x08
    private const val FLAG_CHARGE_TYPE_PRESENT = 0x10
    private const val FLAG_STATUS_PRESENT = 0x20
    private const val FLAG_LEVEL_PRESENT = 0x40

    /** Battery Status 枚举值（低 3 位）。 */
    private const val STATUS_UNKNOWN = 0
    private const val STATUS_NOT_CHARGING = 1
    private const val STATUS_CHARGING = 2
    private const val STATUS_DISCHARGING = 3
    private const val STATUS_FULL = 4

    /** 解析 0x2A19 电量百分比；越界或空数据返回 null。 */
    fun parseLevel(data: ByteArray): Int? {
        if (data.isEmpty()) return null
        val value = data[0].toInt() and 0xFF
        return if (value in 0..100) value else null
    }

    /** 解析 0x2A1E 充放电状态；无法识别返回 [BatteryStatus.ChargeState.Unknown]。 */
    fun parseChargeState(data: ByteArray): BatteryStatus.ChargeState {
        if (data.isEmpty()) return BatteryStatus.ChargeState.Unknown
        val flags = data[0].toInt() and 0xFF

        // 无 Status 字段时，依据外接电源状态保守推断。
        if ((flags and FLAG_STATUS_PRESENT) == 0) {
            val externalPower = (flags and FLAG_WIRED_POWER) != 0 ||
                (flags and FLAG_WIRELESS_POWER) != 0
            return if (externalPower) BatteryStatus.ChargeState.Charging
            else BatteryStatus.ChargeState.Unknown
        }

        // 按字段出现顺序计算偏移：Battery Level(1) → Charge Level(2) → Charge Type(1) → Status(1)
        var offset = 1
        if ((flags and FLAG_LEVEL_PRESENT) != 0) offset += 1
        if ((flags and FLAG_CHARGE_LEVEL_PRESENT) != 0) offset += 2
        if ((flags and FLAG_CHARGE_TYPE_PRESENT) != 0) offset += 1
        if (offset >= data.size) return BatteryStatus.ChargeState.Unknown

        return when (data[offset].toInt() and 0x07) {
            STATUS_UNKNOWN -> BatteryStatus.ChargeState.Unknown
            STATUS_NOT_CHARGING -> BatteryStatus.ChargeState.NotCharging
            STATUS_CHARGING -> BatteryStatus.ChargeState.Charging
            STATUS_DISCHARGING -> BatteryStatus.ChargeState.Discharging
            STATUS_FULL -> BatteryStatus.ChargeState.Full
            else -> BatteryStatus.ChargeState.Unknown
        }
    }
}
