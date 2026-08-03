package com.ghealth.tools.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.ble.connection.BleConnectionManager
import com.ghealth.tools.ble.connection.DeviceRole
import com.ghealth.tools.ble.protocol.gh3036.KEY_GH3X_GET_VERSION
import com.ghealth.tools.ble.protocol.gh3036.parseGh3036VersionString
import com.ghealth.tools.core.model.ConnectionState
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

private data class VersionQuery(
    val label: String,
    val verType: Byte
)

private val BASIC_VERSION_QUERIES = listOf(
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

private val ALGO_VERSION_QUERIES = listOf(
    VersionQuery("HR", 0x20),
    VersionQuery("HRV", 0x21),
    VersionQuery("SpO2", 0x22),
    VersionQuery("ADT", 0x23),
    VersionQuery("NADT", 0x24),
)

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
            _uiState.update {
                it.copy(
                    isReading = true,
                    errorMessage = null,
                    basicVersions = BASIC_VERSION_QUERIES.map { VersionEntry(it.label, isLoading = true) },
                    algoVersions = ALGO_VERSION_QUERIES.map { VersionEntry(it.label, isLoading = true) }
                )
            }

            val basicResults = mutableListOf<VersionEntry>()
            for (query in BASIC_VERSION_QUERIES) {
                basicResults.add(fetchSingleVersion(masterAddress, query))
            }

            val algoResults = mutableListOf<VersionEntry>()
            for (query in ALGO_VERSION_QUERIES) {
                algoResults.add(fetchSingleVersion(masterAddress, query))
            }

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
        query: VersionQuery
    ): VersionEntry {
        return try {
            val result = withTimeoutOrNull(2000L) {
                connectionManager.sendCommand(
                    address = masterAddress,
                    key = KEY_GH3X_GET_VERSION,
                    param = byteArrayOf(query.verType)
                )
            }

            when {
                result == null -> {
                    Timber.w("Version query timeout: ${query.label}")
                    VersionEntry(query.label, value = "超时", isError = true)
                }
                result.isFailure -> {
                    Timber.w("Version query failed: ${query.label} - ${result.exceptionOrNull()?.message}")
                    VersionEntry(query.label, value = "失败", isError = true)
                }
                result.isSuccess -> {
                    val data = result.getOrThrow()
                    val versionStr = parseGh3036VersionString(data)
                    Timber.d("Version ${query.label}: $versionStr")
                    VersionEntry(query.label, value = versionStr)
                }
                else -> VersionEntry(query.label, value = "-", isError = true)
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
