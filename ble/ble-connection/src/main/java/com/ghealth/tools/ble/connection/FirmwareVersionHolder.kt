package com.ghealth.tools.ble.connection

import com.ghealth.tools.ble.protocol.gh3036.parseGh3036VersionString

data class FirmwareVersionState(
    val version: String? = null,
    val isReading: Boolean = false,
)

internal const val VER_TYPE_BLE: Byte = 0x09
internal const val VER_TYPE_FW: Byte = 0x01

/**
 * 解析固件版本：优先 BLE 版本(0x09)，失败回退固件版本(0x01)，两者都失败返回 null。
 *
 * [fetchRaw] 负责下发指定 [verType] 的 GH3X_GetVersion 命令并返回响应原始字节；
 * 超时/异常/失败时返回 null（由调用方处理）。解析结果为 "no_ver" 视为本次读取失败，触发回退。
 */
internal suspend fun resolveFirmwareVersion(
    fetchRaw: suspend (verType: Byte) -> ByteArray?
): String? {
    val bleRaw = fetchRaw(VER_TYPE_BLE)
    if (bleRaw != null) {
        val parsed = parseGh3036VersionString(bleRaw)
        if (parsed != "no_ver") return parsed
    }
    val fwRaw = fetchRaw(VER_TYPE_FW)
    if (fwRaw != null) {
        val parsed = parseGh3036VersionString(fwRaw)
        if (parsed != "no_ver") return parsed
    }
    return null
}
