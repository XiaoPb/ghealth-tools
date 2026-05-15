package com.ghealth.tools.feature.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.ble.connection.BleConnectionManager
import com.ghealth.tools.ble.connection.ConnectedDevice
import com.ghealth.tools.ble.connection.DeviceRole
import com.ghealth.tools.ble.scanner.BleScanner
import com.ghealth.tools.core.model.BleDevice
import com.ghealth.tools.core.model.ConnectionState
import com.ghealth.tools.core.model.FunctionMode
import com.ghealth.tools.core.model.WorkMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConnectionUiState(
    val isScanning: Boolean = false,
    val scanResults: List<BleDevice> = emptyList(),
    val connectedDevices: Map<String, ConnectedDevice> = emptyMap(),
    val currentWorkMode: WorkMode? = null,
    val selectedFunctions: Set<FunctionMode> = emptySet(),
    val scanForRole: DeviceRole? = null,
    val showWorkModeDialog: Boolean = false,
    val showFunctionDialog: Boolean = false,
    val showCommandSheet: Boolean = false
)

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val bleScanner: BleScanner,
    private val connectionManager: BleConnectionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            connectionManager.devices.collect { devices ->
                _uiState.update { it.copy(connectedDevices = devices) }
            }
        }
    }

    fun startScan(role: DeviceRole) {
        _uiState.update { it.copy(scanForRole = role, scanResults = emptyList(), isScanning = true) }
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            bleScanner.scan()
                .catch { _uiState.update { s -> s.copy(isScanning = false) } }
                .collect { device ->
                    _uiState.update { state ->
                        val existing = state.scanResults.associateBy { it.address }.toMutableMap()
                        existing[device.address] = device
                        state.copy(scanResults = existing.values.sortedByDescending { it.rssi })
                    }
                }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update { it.copy(isScanning = false) }
    }

    fun connectDevice(device: BleDevice) {
        val role = _uiState.value.scanForRole ?: return
        stopScan()
        connectionManager.connect(device.address, device.name, role)
        _uiState.update { it.copy(scanForRole = null) }
    }

    fun disconnectDevice(address: String) {
        connectionManager.disconnect(address)
    }

    fun disconnectAll() {
        connectionManager.disconnectAll()
    }

    fun setWorkMode(mode: WorkMode) {
        _uiState.update { it.copy(currentWorkMode = mode, showWorkModeDialog = false) }
        if (mode == WorkMode.PASS_THROUGH) {
            _uiState.update { it.copy(showFunctionDialog = true) }
        }
    }

    fun setSelectedFunctions(functions: Set<FunctionMode>) {
        _uiState.update { it.copy(selectedFunctions = functions, showFunctionDialog = false) }
    }

    fun showWorkModeDialog() {
        _uiState.update { it.copy(showWorkModeDialog = true) }
    }

    fun dismissWorkModeDialog() {
        _uiState.update { it.copy(showWorkModeDialog = false) }
    }

    fun dismissFunctionDialog() {
        _uiState.update { it.copy(showFunctionDialog = false) }
    }

    fun showCommandSheet() {
        _uiState.update { it.copy(showCommandSheet = true) }
    }

    fun dismissCommandSheet() {
        _uiState.update { it.copy(showCommandSheet = false) }
    }

    fun sendCommand(key: String, param: ByteArray = ByteArray(0)) {
        val masterAddress = _uiState.value.connectedDevices.entries
            .find { it.value.role == DeviceRole.MASTER }?.key ?: return
        connectionManager.sendCommand(masterAddress, key, param)
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }
}
