package com.ghealth.tools.ble.connection

import java.util.concurrent.ConcurrentHashMap

/**
 * 电池来源优先级选择器。
 *
 * 规则：设备暴露 GATT Battery Service（0x180F / 0x2A19）时以 BAT 服务为准；
 * 仅当设备没有 BAT 服务时，才允许 GH3220 协议 0x0D 上报驱动电量显示。
 * 标记与查询按地址隔离，可跨线程安全调用；断连时调用 [remove] 清理，避免残留影响重连。
 */
internal class BatterySourceSelector {

    private val batteryServiceAddresses = ConcurrentHashMap.newKeySet<String>()

    /** 标记该地址已暴露 GATT BAT 服务（幂等）；此后 [shouldUseProtocolBattery] 返回 false。 */
    fun markBatteryService(address: String) {
        batteryServiceAddresses.add(address)
    }

    /** 断连清理，防止残留标记影响后续重连。 */
    fun remove(address: String) {
        batteryServiceAddresses.remove(address)
    }

    /** 该地址是否允许使用 GH3220 协议电量（即未检测到 BAT 服务）。 */
    fun shouldUseProtocolBattery(address: String): Boolean =
        address !in batteryServiceAddresses
}
