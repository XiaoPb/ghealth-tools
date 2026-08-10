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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    val sdkVersion: String? = null,
    val hrVersion: String? = null,
    val spo2Version: String? = null,
    val nadtVersion: String? = null,
    val hrvVersion: String? = null,
    val isReading: Boolean = false,
)

internal const val VER_TYPE_BLE: Byte = 0x09
internal const val VER_TYPE_FW: Byte = 0x01
private const val VERSION_FETCH_DELAY_MS = 2_000L
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

/** CSV 元数据版本字段 → 设备版本查询类型。 */
internal data class CsvVersionQuery(val label: String, val verType: Byte)

/**
 * CSV 元数据（SDK/HR/SPO2/NADT/HRV）按芯片的版本查询计划。
 *
 * GH3036/GH3300（RPC GH3X_GetVersion，.claude/gh_protocol/c/user/gh_protocol_cmd.h）：
 *   SDK=0x0A 算法Demo版本，HR=0x20，SPO2=0x22，NADT=0x24，HRV=0x21。
 * GH3220（0x19 响应 [verType][len][text]，算法版本 = 0x12 + gh_drv.h GH3X2X_FUNC_OFFSET_*）：
 *   SDK=0x0A 算法Demo版本，HR=0x13，SPO2=0x18，NADT=0x1B(SOFT_ADT_GREEN 活体检测)，HRV=0x14。
 */
internal fun csvVersionPlan(isGh3220: Boolean): List<CsvVersionQuery> =
    if (isGh3220) {
        listOf(
            CsvVersionQuery("SDK", 0x0A),
            CsvVersionQuery("HR", 0x13),
            CsvVersionQuery("SPO2", 0x18),
            CsvVersionQuery("NADT", 0x1B),
            CsvVersionQuery("HRV", 0x14),
        )
    } else {
        listOf(
            CsvVersionQuery("SDK", 0x0A),
            CsvVersionQuery("HR", 0x20),
            CsvVersionQuery("SPO2", 0x22),
            CsvVersionQuery("NADT", 0x24),
            CsvVersionQuery("HRV", 0x21),
        )
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
        _state.update {
            it.copy(
                isReading = true, version = null,
                sdkVersion = null, hrVersion = null, spo2Version = null, nadtVersion = null, hrvVersion = null
            )
        }
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
            val csvVersions = readCsvVersions(sendCmd, isGh3220)
            if (isStillCurrentMaster(address)) {
                _state.update {
                    it.copy(
                        version = version,
                        sdkVersion = csvVersions["SDK"],
                        hrVersion = csvVersions["HR"],
                        spo2Version = csvVersions["SPO2"],
                        nadtVersion = csvVersions["NADT"],
                        hrvVersion = csvVersions["HRV"],
                        isReading = false
                    )
                }
                Timber.d("Firmware version for $address: $version, csvVersions=$csvVersions")
            }
        }
    }

    /** 并行读取 CSV 元数据所需的 5 个版本字段；读取失败/无响应的字段为 null。 */
    private suspend fun readCsvVersions(
        sendCmd: suspend (Byte) -> ByteArray?,
        isGh3220: Boolean
    ): Map<String, String?> = coroutineScope {
        csvVersionPlan(isGh3220).map { query ->
            async {
                val raw = sendCmd(query.verType) ?: return@async query.label to null
                val text = if (isGh3220) {
                    BasicCommands.parseVersion(raw).getOrNull()?.text?.takeIf { it.isNotBlank() }
                } else {
                    parseGh3036VersionString(raw).takeIf { it != "no_ver" }
                }
                query.label to text
            }
        }.awaitAll().toMap()
    }

    /**
     * 等待主设备版本读取完成后再返回当前状态。
     * 用于"连接后先读取版本再弹窗"流程：主设备已连接但读取尚未调度时立即触发读取；
     * 读取已在途时挂起等待；读取已完成或主设备未连接时直接返回。
     */
    suspend fun awaitVersionRead(): FirmwareVersionState {
        val master = connectionManager.devices.value.values.find {
            it.role == DeviceRole.MASTER && it.state == ConnectionState.CONNECTED
        }
        if (master != null && currentMasterAddress != master.address) {
            // 先登记地址再调度，避免与 init 收集器并发调度时互相取消读取
            currentMasterAddress = master.address
            scheduleFetch(master.address)
        }
        fetchJob?.join()
        return _state.value
    }

    private fun isStillCurrentMaster(address: String): Boolean {
        val device = connectionManager.devices.value[address] ?: return false
        return device.role == DeviceRole.MASTER && device.state == ConnectionState.CONNECTED
    }
}
