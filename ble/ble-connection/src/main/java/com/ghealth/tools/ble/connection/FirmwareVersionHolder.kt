package com.ghealth.tools.ble.connection

import com.ghealth.tools.ble.gh3220.Gh3220Cmd
import com.ghealth.tools.ble.gh3220.commands.BasicCommands
import com.ghealth.tools.ble.protocol.gh3036.KEY_GH3X_GET_VERSION
import com.ghealth.tools.ble.protocol.gh3036.parseGh3036VersionString
import com.ghealth.tools.core.model.ConnectionState
import com.ghealth.tools.core.model.DeviceType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class FirmwareVersionState(
    val version: String? = null,
    val isReading: Boolean = false,
)

internal const val VER_TYPE_BLE: Byte = 0x09
internal const val VER_TYPE_FW: Byte = 0x01
private const val VERSION_FETCH_DELAY_MS = 5_000L
private const val VERSION_READ_TIMEOUT_MS = 3_000L

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

/**
 * GH3220 版本读取：0x19 响应 [verType][len][text(UTF-8)]；verType 0x00/0x01 均为 EVK 版本，
 * 此处固定取 0x01；读取失败或文本为空返回 null。
 */
internal suspend fun resolveGh3220Version(
    fetchRaw: suspend (verType: Byte) -> ByteArray?
): String? {
    val raw = fetchRaw(VER_TYPE_FW) ?: return null
    return BasicCommands.parseVersion(raw).getOrNull()?.text?.takeIf { it.isNotBlank() }
}

/**
 * 主设备固件版本共享状态持有者（@Singleton）。
 *
 * - 单一数据源：连接页与设置页共享同一份版本状态，避免重复下发 BLE 版本读取命令。
 * - 获取策略：优先 BLE 版本(0x09)，失败回退固件版本(0x01)，两者都失败置为 null（UI 不显示）。
 * - 生命周期：内部订阅 [BleConnectionManager.devices]，主设备 CONNECTED 后延迟 5 秒读取，
 *   主设备断开时取消在途读取并清空状态。
 */
@Singleton
class FirmwareVersionHolder @Inject constructor(
    private val connectionManager: BleConnectionManager,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(FirmwareVersionState())
    val state: StateFlow<FirmwareVersionState> = _state.asStateFlow()

    private var fetchJob: Job? = null
    private var currentMasterAddress: String? = null

    init {
        scope.launch {
            connectionManager.devices.collect { devices ->
                val master = devices.values.find {
                    it.role == DeviceRole.MASTER && it.state == ConnectionState.CONNECTED
                }
                if (master != null && master.address != currentMasterAddress) {
                    currentMasterAddress = master.address
                    scheduleFetch(master.address)
                } else if (master == null && currentMasterAddress != null) {
                    fetchJob?.cancel()
                    fetchJob = null
                    currentMasterAddress = null
                    _state.value = FirmwareVersionState()
                }
            }
        }
    }

    private fun scheduleFetch(address: String) {
        fetchJob?.cancel()
        _state.update { it.copy(isReading = true, version = null) }
        fetchJob = scope.launch {
            delay(VERSION_FETCH_DELAY_MS)
            if (!isStillCurrentMaster(address)) return@launch
            val isGh3220 = connectionManager.devices.value[address]?.deviceType == DeviceType.GH3220
            val sendCmd: suspend (Byte) -> ByteArray? = { verType ->
                try {
                    withTimeoutOrNull(VERSION_READ_TIMEOUT_MS) {
                        if (isGh3220) {
                            connectionManager.sendGh3220Command(address, Gh3220Cmd.GET_VER, byteArrayOf(verType))
                        } else {
                            connectionManager.sendCommand(address, KEY_GH3X_GET_VERSION, byteArrayOf(verType))
                        }
                    }?.getOrNull()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Version read exception for $address (verType=0x%02X)".format(verType))
                    null
                }
            }
            val version = if (isGh3220) resolveGh3220Version(sendCmd) else resolveFirmwareVersion(sendCmd)
            if (isStillCurrentMaster(address)) {
                _state.update { it.copy(version = version, isReading = false) }
                Timber.d("Firmware version for $address: $version")
            }
        }
    }

    private fun isStillCurrentMaster(address: String): Boolean {
        val device = connectionManager.devices.value[address] ?: return false
        return device.role == DeviceRole.MASTER && device.state == ConnectionState.CONNECTED
    }
}
