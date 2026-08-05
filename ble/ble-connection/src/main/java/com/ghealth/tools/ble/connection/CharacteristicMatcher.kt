@file:OptIn(ExperimentalUuidApi::class)

package com.ghealth.tools.ble.connection

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 已发现特征的精简引用：仅保留所属服务 UUID 与特征 UUID。
 * 用于把 Kable 的 DiscoveredService 列表拍平后交给纯逻辑匹配器，
 * 避免单测依赖 Kable 类型。
 */
internal data class DiscoveredCharacteristicRef(
    val serviceUuid: Uuid,
    val characteristicUuid: Uuid,
)

/**
 * 在已发现的服务/特征中按特征 UUID 定位写入与通知特征。
 *
 * 服务 UUID 不参与匹配——只要特征 UUID 在任意服务中命中即视为找到。
 * 这样设备固件使用与配置不同的服务 UUID 时仍能正常连接。
 *
 * 重复特征 UUID 取首次出现的服务（GATT 中同一特征 UUID 一般唯一）。
 */
internal object CharacteristicMatcher {

    sealed class Result {
        /** 写入与通知特征均已定位。各记录所属服务的真实 UUID，供 characteristicOf 使用。 */
        data class Matched(
            val writeServiceUuid: Uuid,
            val notifyServiceUuid: Uuid,
        ) : Result()

        /** 任意服务中均未发现目标写入特征 UUID。 */
        data object WriteNotFound : Result()

        /** 任意服务中均未发现目标通知特征 UUID。 */
        data object NotifyNotFound : Result()
    }

    /**
     * @param refs 所有已发现 (服务UUID, 特征UUID) 对，跨全部服务拍平。
     * @param writeCharUuid 目标写入特征 UUID。
     * @param notifyCharUuid 目标通知特征 UUID。
     * @return 先检查写入、再检查通知；任一缺失返回对应 NotFound。
     */
    fun match(
        refs: List<DiscoveredCharacteristicRef>,
        writeCharUuid: Uuid,
        notifyCharUuid: Uuid,
    ): Result {
        val writeServiceUuid = refs
            .firstOrNull { it.characteristicUuid == writeCharUuid }
            ?.serviceUuid
            ?: return Result.WriteNotFound

        val notifyServiceUuid = refs
            .firstOrNull { it.characteristicUuid == notifyCharUuid }
            ?.serviceUuid
            ?: return Result.NotifyNotFound

        return Result.Matched(
            writeServiceUuid = writeServiceUuid,
            notifyServiceUuid = notifyServiceUuid,
        )
    }
}
