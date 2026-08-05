@file:OptIn(ExperimentalUuidApi::class)

package com.ghealth.tools.ble.connection

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 在已发现特征中定位 Battery Service 相关特征所属服务 UUID。
 *
 * 与 [CharacteristicMatcher] 一致：按特征 UUID 在任意服务中查找，
 * 取首次出现的服务 UUID。Battery Level (0x2A19) 为必选，
 * Battery Level Status (0x2A1E) 为可选；缺失则对应字段为 null。
 */
internal object BatteryServiceMatcher {

    data class Result(
        val batteryLevelServiceUuid: Uuid?,
        val batteryLevelStatusServiceUuid: Uuid?,
    )

    fun match(refs: List<DiscoveredCharacteristicRef>): Result {
        val levelServiceUuid = refs
            .firstOrNull { it.characteristicUuid == BatteryServiceUuids.BATTERY_LEVEL_UUID }
            ?.serviceUuid
        val statusServiceUuid = refs
            .firstOrNull { it.characteristicUuid == BatteryServiceUuids.BATTERY_LEVEL_STATUS_UUID }
            ?.serviceUuid
        return Result(
            batteryLevelServiceUuid = levelServiceUuid,
            batteryLevelStatusServiceUuid = statusServiceUuid,
        )
    }
}
