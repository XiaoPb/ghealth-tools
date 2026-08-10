package com.ghealth.tools.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.ble.connection.BleConnectionManager
import com.ghealth.tools.ble.connection.DeviceRole
import com.ghealth.tools.ble.gh3220.Gh3220Cmd
import com.ghealth.tools.ble.gh3220.commands.BasicCommands
import com.ghealth.tools.ble.protocol.gh3036.KEY_GH3X_GET_VERSION
import com.ghealth.tools.ble.protocol.gh3036.parseGh3036VersionString
import com.ghealth.tools.core.model.ConnectionState
import com.ghealth.tools.core.model.DeviceType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

data class VersionEntry(
    val label: String,
    val value: String = "",
    val isLoading: Boolean = false,
    val isError: Boolean = false
)

data class DeviceInfoUiState(
    val isConnected: Boolean = false,
    val deviceName: String = "",
    val deviceAddress: String = "",
    val basicVersions: List<VersionEntry> = emptyList(),
    val algoVersions: List<VersionEntry> = emptyList(),
    val isReading: Boolean = false,
    val errorMessage: String? = null
)

internal data class VersionQuery(
    val label: String,
    val verType: Byte
)

internal val BASIC_VERSION_QUERIES = listOf(
    VersionQuery("固件版本", 0x01),
    VersionQuery("虚拟寄存器版本", 0x03),
    VersionQuery("Bootloader版本", 0x04),
    VersionQuery("协议版本", 0x05),
    VersionQuery("驱动功能支持", 0x06),
    VersionQuery("驱动版本", 0x07),
    VersionQuery("芯片版本", 0x08),
    VersionQuery("BLE版本", 0x09),
    VersionQuery("算法Demo版本", 0x0A)
)

internal val ALGO_VERSION_QUERIES = listOf(
    VersionQuery("HR", 0x20),
    VersionQuery("HRV", 0x21),
    VersionQuery("SpO2", 0x22),
    VersionQuery("ADT", 0x23),
    VersionQuery("NADT", 0x24),
)

/** GH3220 0x19 版本类型。
 *  算法版本类型 = `0x12 + GH3X2X_FUNC_OFFSET_*`（C 端 `gh_uprotocol.h`
 *  `UPROTOCOL_GET_VER_TYPE_ALGO_VER=0x12`，`GH3X2X_GetVersion` 按
 *  `verType - 0x12` 作功能偏移索引）。注意协议文档 §3.21 从 PWA 起多写 2
 *  （PWA=0x19），真机 2026-08-10 验证 0x19 返回的是 ECG 版本，文档有误。 */
internal val GH3220_VERSION_QUERIES = listOf(
    VersionQuery("固件版本", 0x01),
    VersionQuery("虚拟寄存器版本", 0x0B),
    VersionQuery("Bootloader版本", 0x0C),
    VersionQuery("BLE版本", 0x0D),
    VersionQuery("协议版本", 0x0E),
    VersionQuery("支持功能", 0x0F),
    VersionQuery("驱动库版本", 0x10),
    VersionQuery("芯片版本", 0x11),
    VersionQuery("ADT", 0x12),
    VersionQuery("HR", 0x13),
    VersionQuery("HRV", 0x14),
    VersionQuery("HSM", 0x15),
    VersionQuery("FPBP", 0x16),
    VersionQuery("PWA", 0x17),
    VersionQuery("SpO2", 0x18),
    VersionQuery("ECG", 0x19),
    VersionQuery("PWTT", 0x1A),
    VersionQuery("SOFTADT", 0x1B),
    VersionQuery("BT", 0x1C),
)

/** 解析 0x19 响应 [verType][len][text(UTF-8)]；空文本/解析失败返回 null。 */
internal fun parseGh3220VersionText(raw: ByteArray): String? =
    BasicCommands.parseVersion(raw).getOrNull()?.text?.takeIf { it.isNotBlank() }

/** 按设备类型返回 (basicQueries, algoQueries) 版本查询计划：GH3220 无独立算法版本区。 */
internal fun versionPlan(isGh3220: Boolean): Pair<List<VersionQuery>, List<VersionQuery>> =
    if (isGh3220) {
        GH3220_VERSION_QUERIES to emptyList()
    } else {
        BASIC_VERSION_QUERIES to ALGO_VERSION_QUERIES
    }

@HiltViewModel
class DeviceInfoViewModel @Inject constructor(
    private val connectionManager: BleConnectionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceInfoUiState())
    val uiState: StateFlow<DeviceInfoUiState> = _uiState.asStateFlow()

    private var lastReadAddress: String? = null

    init {
        viewModelScope.launch {
            connectionManager.devices.collect { devices ->
                val master = devices.values.find {
                    it.role == DeviceRole.MASTER && it.state == ConnectionState.CONNECTED
                }
                if (master != null) {
                    _uiState.update {
                        it.copy(
                            isConnected = true,
                            deviceName = master.name ?: "Unknown",
                            deviceAddress = master.address
                        )
                    }
                    if (master.address != lastReadAddress || _uiState.value.basicVersions.isEmpty()) {
                        lastReadAddress = master.address
                        fetchAllVersions(master.address)
                    }
                } else {
                    _uiState.update {
                        it.copy(isConnected = false, deviceName = "未连接", deviceAddress = "-")
                    }
                    lastReadAddress = null
                }
            }
        }
    }

    fun refreshDeviceInfo() {
        val masterAddress = _uiState.value.deviceAddress
        if (masterAddress.isNotEmpty() && _uiState.value.isConnected) {
            fetchAllVersions(masterAddress)
        }
    }

    private fun fetchAllVersions(masterAddress: String) {
        viewModelScope.launch {
            val isGh3220 = connectionManager.devices.value[masterAddress]?.deviceType == DeviceType.GH3220
            val (basicQueries, algoQueries) = versionPlan(isGh3220)
            _uiState.update {
                it.copy(
                    isReading = true,
                    errorMessage = null,
                    basicVersions = basicQueries.map { VersionEntry(it.label, isLoading = true) },
                    algoVersions = algoQueries.map { VersionEntry(it.label, isLoading = true) }
                )
            }

            val basicResults = basicQueries.map { fetchSingleVersion(masterAddress, it, isGh3220) }
            val algoResults = algoQueries.map { fetchSingleVersion(masterAddress, it, isGh3220) }

            _uiState.update {
                it.copy(
                    isReading = false,
                    basicVersions = basicResults,
                    algoVersions = algoResults
                )
            }
        }
    }

    private suspend fun fetchSingleVersion(
        masterAddress: String,
        query: VersionQuery,
        isGh3220: Boolean,
    ): VersionEntry {
        return try {
            val raw = withTimeoutOrNull(2000L) {
                if (isGh3220) {
                    connectionManager.sendGh3220Command(masterAddress, Gh3220Cmd.GET_VER, byteArrayOf(query.verType))
                } else {
                    connectionManager.sendCommand(masterAddress, KEY_GH3X_GET_VERSION, byteArrayOf(query.verType))
                }
            }

            when {
                raw == null -> {
                    Timber.w("Version query timeout: ${query.label}")
                    VersionEntry(query.label, value = "超时", isError = true)
                }
                raw.isFailure -> {
                    Timber.w("Version query failed: ${query.label} - ${raw.exceptionOrNull()?.message}")
                    VersionEntry(query.label, value = "失败", isError = true)
                }
                else -> {
                    val data = raw.getOrThrow()
                    val versionStr = if (isGh3220) {
                        parseGh3220VersionText(data) ?: "-"
                    } else {
                        parseGh3036VersionString(data)
                    }
                    Timber.d("Version ${query.label}: $versionStr")
                    VersionEntry(query.label, value = versionStr)
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Version query exception: ${query.label}")
            VersionEntry(query.label, value = "异常", isError = true)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
