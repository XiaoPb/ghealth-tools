package com.ghealth.tools.feature.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.ble.connection.BleConnectionManager
import com.ghealth.tools.ble.connection.ConnectedDevice
import com.ghealth.tools.ble.connection.ConnectionError
import com.ghealth.tools.ble.connection.DeviceRole
import com.ghealth.tools.ble.protocol.gh3036.KEY_G
import com.ghealth.tools.ble.scanner.BleScanException
import com.ghealth.tools.ble.scanner.BleScanner
import com.ghealth.tools.core.model.BleDevice
import com.ghealth.tools.core.model.ConnectionState
import com.ghealth.tools.core.model.DataLogEntry
import com.ghealth.tools.core.model.FunctionMode
import com.ghealth.tools.core.model.TestConfig
import com.ghealth.tools.core.model.WorkMode
import com.juul.kable.Advertisement
import com.juul.kable.Peripheral
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
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
    val showCommandSheet: Boolean = false,
    val minRssi: Int = -80,
    val scanError: String? = null,
    val connectionError: String? = null,
    val connectionErrorDevice: String? = null,
    val isBluetoothEnabled: Boolean = true,
    val hasPermissions: Boolean = true,
    val commandExecutionState: CommandExecutionState = CommandExecutionState(),
    val showTestConfigDialog: Boolean = false,
    val masterDeviceName: String? = null,
    val dataMonitorState: DataMonitorState = DataMonitorState()
)

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val bleScanner: BleScanner,
    private val connectionManager: BleConnectionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private val advertisementCache = mutableMapOf<String, Advertisement>()

    init {
        viewModelScope.launch {
            connectionManager.devices.collect { devices ->
                val previousDevices = _uiState.value.connectedDevices
                _uiState.update { it.copy(connectedDevices = devices) }
                
                val newMaster = devices.entries.find { 
                    it.value.role == DeviceRole.MASTER && 
                    it.value.state == ConnectionState.CONNECTED &&
                    previousDevices[it.key]?.state != ConnectionState.CONNECTED
                }
                
                if (newMaster != null && !_uiState.value.dataMonitorState.isMonitoring) {
                    _uiState.update { 
                        it.copy(
                            showTestConfigDialog = true,
                            masterDeviceName = newMaster.value.name
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            connectionManager.connectionErrors.collect { (address, error) ->
                Timber.w("Connection error from $address: ${error.getMessage()}")
                _uiState.update {
                    it.copy(
                        connectionError = error.getMessage(),
                        connectionErrorDevice = address
                    )
                }
            }
        }

        viewModelScope.launch {
            connectionManager.dataFlow.collect { (address, parseResult) ->
                handleParsedData(address, parseResult)
            }
        }

        checkBluetoothState()
    }

    private fun checkBluetoothState() {
        val isBtEnabled = bleScanner.isBluetoothEnabled
        val hasPerm = bleScanner.hasScanPermission && bleScanner.hasConnectPermission
        _uiState.update { it.copy(
            isBluetoothEnabled = isBtEnabled,
            hasPermissions = hasPerm
        )}
    }

    fun startScan(role: DeviceRole) {
        checkBluetoothState()

        if (!_uiState.value.isBluetoothEnabled) {
            _uiState.update { it.copy(scanError = "蓝牙未启用") }
            return
        }

        if (!_uiState.value.hasPermissions) {
            _uiState.update { it.copy(scanError = "缺少蓝牙权限") }
            return
        }

        _uiState.update {
            it.copy(
                scanForRole = role,
                scanResults = emptyList(),
                isScanning = true,
                scanError = null
            )
        }

        advertisementCache.clear()
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            bleScanner.scanAdvertisements(minRssi = _uiState.value.minRssi)
                .catch { e ->
                    Timber.e(e, "Scan error")
                    val errorMsg = when (e) {
                        is BleScanException -> e.message ?: "扫描失败"
                        else -> "扫描出错: ${e.message}"
                    }
                    _uiState.update { it.copy(isScanning = false, scanError = errorMsg) }
                }
                .collect { advertisement ->
                    val address = advertisement.identifier.toString()
                    advertisementCache[address] = advertisement
                    
                    val device = BleDevice(
                        name = advertisement.name,
                        address = address,
                        rssi = advertisement.rssi
                    )
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

    fun setMinRssi(rssi: Int) {
        _uiState.update { it.copy(minRssi = rssi) }
    }

    fun clearScanError() {
        _uiState.update { it.copy(scanError = null) }
    }

    fun clearConnectionError() {
        _uiState.update { it.copy(connectionError = null, connectionErrorDevice = null) }
    }

    fun connectDevice(device: BleDevice) {
        val role = _uiState.value.scanForRole ?: return
        stopScan()
        
        val advertisement = advertisementCache[device.address]
        if (advertisement != null) {
            viewModelScope.launch {
                try {
                    val peripheral = Peripheral(advertisement) {
                        logging {
                            level = com.juul.kable.logs.Logging.Level.Events
                        }
                        onServicesDiscovered {
                            val negotiatedMtu = requestMtu(247)
                            Timber.i("MTU negotiated: $negotiatedMtu")
                        }
                    }
                    connectionManager.connect(peripheral, role)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to create peripheral for ${device.address}")
                    _uiState.update { it.copy(connectionError = "创建设备连接失败: ${e.message}") }
                }
            }
        } else {
            _uiState.update { it.copy(connectionError = "设备信息已过期，请重新扫描") }
        }
        _uiState.update { it.copy(scanForRole = null) }
    }

    fun disconnectDevice(address: String) {
        viewModelScope.launch {
            connectionManager.disconnect(address)
        }
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
        viewModelScope.launch {
            connectionManager.sendCommand(masterAddress, key, param)
        }
    }

    fun executeCommand(key: String, param: ByteArray) {
        val masterAddress = _uiState.value.connectedDevices.entries
            .find { it.value.role == DeviceRole.MASTER }?.key
        
        if (masterAddress == null) {
            _uiState.update { 
                it.copy(
                    commandExecutionState = CommandExecutionState(
                        isExecuting = false,
                        result = null,
                        error = "未连接主设备"
                    )
                )
            }
            return
        }

        _uiState.update { 
            it.copy(
                commandExecutionState = CommandExecutionState(
                    isExecuting = true,
                    result = null,
                    error = null
                )
            )
        }

        viewModelScope.launch {
            try {
                connectionManager.sendCommand(masterAddress, key, param)
                _uiState.update { 
                    it.copy(
                        commandExecutionState = CommandExecutionState(
                            isExecuting = false,
                            result = ByteArray(0),
                            error = null
                        )
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Command execution failed")
                _uiState.update { 
                    it.copy(
                        commandExecutionState = CommandExecutionState(
                            isExecuting = false,
                            result = null,
                            error = e.message ?: "命令执行失败"
                        )
                    )
                }
            }
        }
    }

    fun clearCommandResult() {
        _uiState.update { 
            it.copy(
                commandExecutionState = CommandExecutionState()
            )
        }
    }

    fun confirmTestConfig(config: TestConfig) {
        _uiState.update { 
            it.copy(
                showTestConfigDialog = false,
                dataMonitorState = DataMonitorState(
                    isMonitoring = true,
                    testConfig = config,
                    logEntries = emptyList(),
                    errorCount = 0,
                    lastError = null
                )
            )
        }
        Timber.i("Test started: tester=${config.testerName}, scenario=${config.scenario}, round=${config.testRound}")
    }

    fun dismissTestConfigDialog() {
        _uiState.update { 
            it.copy(
                showTestConfigDialog = false,
                masterDeviceName = null
            )
        }
    }

    fun stopMonitoring() {
        _uiState.update { 
            it.copy(
                dataMonitorState = it.dataMonitorState.copy(
                    isMonitoring = false
                )
            )
        }
    }

    fun clearDataLogs() {
        _uiState.update { 
            it.copy(
                dataMonitorState = it.dataMonitorState.copy(
                    logEntries = emptyList(),
                    errorCount = 0,
                    lastError = null
                )
            )
        }
    }

    private fun handleParsedData(address: String, parseResult: com.ghealth.tools.ble.protocol.rpccore.ParseResult) {
        val monitorState = _uiState.value.dataMonitorState
        if (!monitorState.isMonitoring) return

        val masterAddress = _uiState.value.connectedDevices.entries
            .find { it.value.role == DeviceRole.MASTER }?.key
        
        if (address != masterAddress) return

        val entry = DataLogEntry(
            timestamp = System.currentTimeMillis(),
            key = parseResult.key,
            param = parseResult.param,
            isError = false,
            errorMessage = null
        )

        val newEntries = monitorState.logEntries + entry
        val maxEntries = 1000
        val trimmedEntries = if (newEntries.size > maxEntries) {
            newEntries.takeLast(maxEntries)
        } else {
            newEntries
        }

        _uiState.update { 
            it.copy(
                dataMonitorState = it.dataMonitorState.copy(
                    logEntries = trimmedEntries
                )
            )
        }

        Timber.v("Data received: key=${parseResult.key}, size=${parseResult.param.size}")
    }

    fun reportDataError(key: String, errorMessage: String) {
        val monitorState = _uiState.value.dataMonitorState
        if (!monitorState.isMonitoring) return

        val entry = DataLogEntry(
            timestamp = System.currentTimeMillis(),
            key = key,
            param = ByteArray(0),
            isError = true,
            errorMessage = errorMessage
        )

        _uiState.update { 
            it.copy(
                dataMonitorState = it.dataMonitorState.copy(
                    logEntries = monitorState.logEntries + entry,
                    errorCount = monitorState.errorCount + 1,
                    lastError = errorMessage
                )
            )
        }

        Timber.w("Data error reported: key=$key, error=$errorMessage")
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }
}
