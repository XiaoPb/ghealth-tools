package com.ghealth.tools.feature.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.ble.connection.BatteryStatus
import com.ghealth.tools.ble.connection.BleConnectionManager
import com.ghealth.tools.ble.connection.ConnectedDevice
import com.ghealth.tools.ble.connection.ConnectionError
import com.ghealth.tools.ble.connection.DeviceRole
import com.ghealth.tools.ble.connection.FirmwareVersionHolder
import com.ghealth.tools.ble.protocol.gh3036.KEY_DOWNLOAD_CONFIG
import com.ghealth.tools.ble.protocol.gh3036.KEY_GH3X_REGS_LIST_WRITE_CMD
import com.ghealth.tools.ble.protocol.gh3036.RegisterCommandPayloadBuilder
import com.ghealth.tools.ble.scanner.BleScanException
import com.ghealth.tools.ble.scanner.BleScanner
import com.ghealth.tools.core.model.BleDevice
import com.ghealth.tools.core.model.ConnectionState
import com.ghealth.tools.core.model.DataLogEntry
import com.ghealth.tools.core.model.FunctionMode
import com.ghealth.tools.core.model.TestConfig
import com.ghealth.tools.core.model.WorkMode
import com.ghealth.tools.feature.factory.model.RegEntry
import com.ghealth.tools.feature.factory.parser.RegisterConfigParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Named

data class ConfigFileInfo(
    val fileName: String,
    val displayPath: String,
    val fullPath: File,
    val chipName: String
)

enum class DownloadStep { START_CONFIG, WRITE_REGS, END_CONFIG }

enum class DownloadStatus { IDLE, LOADING_CONFIGS, CONFIG_READY, DOWNLOADING, COMPLETED, ERROR }

data class RegisterConfigDownloadState(
    val status: DownloadStatus = DownloadStatus.IDLE,
    val availableConfigs: List<ConfigFileInfo> = emptyList(),
    val selectedConfig: ConfigFileInfo? = null,
    val activeStep: DownloadStep? = null,
    val completedSteps: Set<DownloadStep> = emptySet(),
    val error: String? = null
)

data class ConnectionUiState(
    val isScanning: Boolean = false,
    val scanResults: List<BleDevice> = emptyList(),
    val connectedDevices: Map<String, ConnectedDevice> = emptyMap(),
    val currentWorkMode: WorkMode? = WorkMode.AUTO_PASS,
    val selectedFunctions: Set<FunctionMode> = emptySet(),
    val scanForRole: DeviceRole? = null,
    val showWorkModeDialog: Boolean = false,
    val showFunctionDialog: Boolean = false,
    val showCommandSheet: Boolean = false,
    val showAppConfigDialog: Boolean = false,
    val minRssi: Int = -80,
    val scanError: String? = null,
    val connectionError: String? = null,
    val connectionErrorDevice: String? = null,
    val isBluetoothEnabled: Boolean = true,
    val hasPermissions: Boolean = true,
    val commandExecutionStates: Map<String, CommandExecutionState> = emptyMap(),
    val showTestConfigDialog: Boolean = false,
    val masterDeviceName: String? = null,
    val dataMonitorState: DataMonitorState = DataMonitorState(),
    val selectedChip: String = "gh3036",
    val registerConfigDownloadState: RegisterConfigDownloadState = RegisterConfigDownloadState(),
    val masterFirmwareVersion: String? = null,
    val batteryStatusByAddress: Map<String, BatteryStatus> = emptyMap(),
)

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val bleScanner: BleScanner,
    private val connectionManager: BleConnectionManager,
    private val firmwareVersionHolder: FirmwareVersionHolder,
    private val recordingManager: com.ghealth.tools.core.storage.RecordingManager,
    private val blePreferences: com.ghealth.tools.core.datastore.BlePreferences,
    private val userPreferences: com.ghealth.tools.core.datastore.UserPreferences,
    private val registerConfigParser: RegisterConfigParser,
    @Named("storageBaseDir") private val baseDir: File,
    private val configPathProvider: com.ghealth.tools.core.network.ConfigPathProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            connectionManager.devices.collect { devices ->
                val previousDevices = _uiState.value.connectedDevices

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

                if (devices.isEmpty() && _uiState.value.dataMonitorState.isMonitoring) {
                    viewModelScope.launch {
                        recordingManager.endSession()
                        stopMonitoring()
                    }
                }

                _uiState.update { it.copy(connectedDevices = devices) }
            }
        }

        // 订阅共享固件版本状态（由 FirmwareVersionHolder 统一获取：优先 0x09，回退 0x01，都失败为 null）
        viewModelScope.launch {
            firmwareVersionHolder.state.collect { versionState ->
                _uiState.update { it.copy(masterFirmwareVersion = versionState.version) }
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
                try {
                    handleParsedData(address, parseResult)
                } catch (e: Exception) {
                    Timber.e(e, "Error handling parsed BLE data from $address")
                }
            }
        }

        viewModelScope.launch {
            connectionManager.recordingStoppedEvents.collect {
                try {
                    if (_uiState.value.dataMonitorState.isMonitoring) {
                        stopMonitoring()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error handling recording stopped event")
                }
            }
        }

        viewModelScope.launch {
            blePreferences.selectedChip.collect { chip ->
                _uiState.update { it.copy(selectedChip = chip) }
            }
        }

        viewModelScope.launch {
            connectionManager.batteryStatus.collect { batteryByAddress ->
                _uiState.update { it.copy(batteryStatusByAddress = batteryByAddress) }
            }
        }

        viewModelScope.launch {
            val autoReconnect = blePreferences.autoReconnect.first()
            val lastAddress = blePreferences.lastDeviceAddress.first()
            val lastName = blePreferences.lastDeviceName.first()
            if (autoReconnect && !lastAddress.isNullOrBlank()) {
                connectionManager.autoConnect(lastAddress, lastName)
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

        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            bleScanner.scan(minRssi = _uiState.value.minRssi)
                .catch { e ->
                    Timber.e(e, "Scan error")
                    val errorMsg = when (e) {
                        is BleScanException -> e.message ?: "扫描失败"
                        else -> "扫描出错: ${e.message}"
                    }
                    _uiState.update { it.copy(isScanning = false, scanError = errorMsg) }
                }
                .collect { device ->
                    _uiState.update { state ->
                        val currentIndex = state.scanResults.indexOfFirst { it.address == device.address }
                        if (currentIndex == -1) {
                            state.copy(scanResults = state.scanResults + device)
                        } else {
                            val updated = state.scanResults.toMutableList()
                            updated[currentIndex] = device
                            state.copy(scanResults = updated)
                        }
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

    fun sortScanResultsByRssi() {
        _uiState.update { state ->
            state.copy(scanResults = state.scanResults.sortedByDescending { it.rssi })
        }
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
        connectionManager.connect(device.address, device.name, role)
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

    fun showAppConfigDialog() {
        val chip = _uiState.value.selectedChip
        _uiState.update {
            it.copy(
                showAppConfigDialog = true,
                registerConfigDownloadState = it.registerConfigDownloadState.copy(
                    status = DownloadStatus.IDLE,
                    error = null
                )
            )
        }
        loadRegisterConfigFiles(chip)
    }

    fun dismissAppConfigDialog() {
        _uiState.update { it.copy(showAppConfigDialog = false) }
    }

    fun selectAndDownloadConfig(info: ConfigFileInfo) {
        selectRegisterConfigFile(info)
        executeRegisterConfigDownload()
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
                    commandExecutionStates = it.commandExecutionStates + (key to CommandExecutionState(
                        isExecuting = false,
                        error = "未连接主设备",
                        commandKey = key
                    ))
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                commandExecutionStates = it.commandExecutionStates + (key to CommandExecutionState(
                    isExecuting = true,
                    commandKey = key
                ))
            )
        }

        viewModelScope.launch {
            try {
                val result = connectionManager.sendCommand(masterAddress, key, param)
                result.fold(
                    onSuccess = { response ->
                        _uiState.update {
                            it.copy(
                                commandExecutionStates = it.commandExecutionStates + (key to CommandExecutionState(
                                    isExecuting = false,
                                    result = response,
                                    commandKey = key
                                ))
                            )
                        }
                    },
                    onFailure = { error ->
                        Timber.e(error, "Command execution failed: $key")
                        _uiState.update {
                            it.copy(
                                commandExecutionStates = it.commandExecutionStates + (key to CommandExecutionState(
                                    isExecuting = false,
                                    error = error.message ?: "命令执行失败",
                                    commandKey = key
                                ))
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Command execution failed: $key")
                _uiState.update {
                    it.copy(
                        commandExecutionStates = it.commandExecutionStates + (key to CommandExecutionState(
                            isExecuting = false,
                            error = e.message ?: "命令执行失败",
                            commandKey = key
                        ))
                    )
                }
            }
        }
    }

    fun clearCommandResults() {
        _uiState.update { it.copy(commandExecutionStates = emptyMap()) }
    }

    fun confirmTestConfig(config: TestConfig) {
        connectionManager.setTestConfig(config)
        connectionManager.resetFrameDecoders()
        val devices = connectionManager.devices.value
        val masterDevice = devices.values.find {
            it.role == DeviceRole.MASTER && it.state == ConnectionState.CONNECTED
        }
        val slaveDevices = devices.values.filter {
            it.role == DeviceRole.SLAVE && it.state == ConnectionState.CONNECTED
        }
        if (masterDevice != null) {
            viewModelScope.launch {
                val projectId = userPreferences.selectedProjectId.first() ?: 0
                val projectName = userPreferences.selectedProjectName.first() ?: ""
                val userInfo = userPreferences.userInfo.first()
                recordingManager.startSession(
                    config = config,
                    masterDeviceName = masterDevice.name ?: "Unknown",
                    masterDeviceAddress = masterDevice.address,
                    slaveDevices = slaveDevices.associate { it.address to (it.name ?: "Unknown") },
                    compareDeviceNames = devices.values
                        .filter { it.role == DeviceRole.COMPARE && it.state == ConnectionState.CONNECTED }
                        .map { it.name ?: it.address },
                    compareDeviceAddresses = devices.values
                        .filter { it.role == DeviceRole.COMPARE && it.state == ConnectionState.CONNECTED }
                        .map { it.address },
                    projectName = projectName,
                    projectId = projectId,
                    username = userInfo.username
                )
            }
        }
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

    // ── 寄存器配置下载 ──────────────────────────────────────────────

    fun loadRegisterConfigFiles(chip: String) {
        _uiState.update {
            it.copy(
                registerConfigDownloadState = it.registerConfigDownloadState.copy(
                    status = DownloadStatus.LOADING_CONFIGS,
                    error = null
                )
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val configs = mutableListOf<ConfigFileInfo>()
            try {
                val isOnline = configPathProvider.isOnlineMode.first()
                val configDir = if (isOnline) {
                    val projectName = userPreferences.selectedProjectName.first()
                    if (projectName.isNullOrBlank()) {
                        throw IllegalStateException("未选择项目")
                    }
                    // 当前项目独有目录，与同步层 getApplicationConfigDir("", projectName) 完全一致，不会读到其他项目
                    configPathProvider.getApplicationConfigDir("", projectName)
                } else {
                    File(baseDir, "application/config/$chip")
                }
                if (configDir.exists()) {
                    configDir.listFiles()
                        ?.filter { f -> f.isFile && (f.name.endsWith(".config") || f.name.endsWith(".ini")) }
                        ?.forEach { file ->
                            configs.add(
                                ConfigFileInfo(
                                    fileName = file.name,
                                    displayPath = file.name,
                                    fullPath = file,
                                    chipName = chip
                                )
                            )
                        }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load register config files (chip=$chip)")
            }
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        registerConfigDownloadState = it.registerConfigDownloadState.copy(
                            status = if (configs.isEmpty()) DownloadStatus.ERROR else DownloadStatus.CONFIG_READY,
                            availableConfigs = configs.sortedBy { c -> c.displayPath },
                            error = if (configs.isEmpty()) "当前项目未找到寄存器配置文件" else null
                        )
                    )
                }
            }
        }
    }

    fun selectRegisterConfigFile(info: ConfigFileInfo) {
        _uiState.update {
            it.copy(
                registerConfigDownloadState = it.registerConfigDownloadState.copy(
                    selectedConfig = info,
                    error = null
                )
            )
        }
    }

    fun executeRegisterConfigDownload() {
        val state = _uiState.value.registerConfigDownloadState
        val configInfo = state.selectedConfig ?: return
        val masterAddress = _uiState.value.connectedDevices.entries
            .find { it.value.role == DeviceRole.MASTER }?.key

        if (masterAddress == null) {
            _uiState.update {
                it.copy(
                    registerConfigDownloadState = it.registerConfigDownloadState.copy(
                        error = "未连接主设备"
                    )
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                registerConfigDownloadState = it.registerConfigDownloadState.copy(
                    status = DownloadStatus.DOWNLOADING,
                    activeStep = null,
                    completedSteps = emptySet(),
                    error = null
                )
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = configInfo.fullPath.readText()
                val registerConfig = registerConfigParser.parseByChip(
                    content, configInfo.chipName, configInfo.fileName
                )
                if (registerConfig.registers.isEmpty()) {
                    throw IllegalStateException("配置文件中没有有效的寄存器数据")
                }

                val interleaved = RegEntry.toInterleavedArray(registerConfig.registers)
                val param = RegisterCommandPayloadBuilder.buildU16ArrayPayload(interleaved)
                Timber.d(
                    "Register config download: file=%s, registerPairs=%d, u16Length=%d, payloadBytes=%d, preview=%s",
                    configInfo.fileName,
                    registerConfig.registers.size,
                    interleaved.size,
                    param.size,
                    registerConfig.registers.take(3).joinToString { entry ->
                        "0x%04X=0x%04X".format(entry.addr, entry.value)
                    }
                )

                // Step 1: download_config stage 0
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            registerConfigDownloadState = it.registerConfigDownloadState.copy(
                                activeStep = DownloadStep.START_CONFIG
                            )
                        )
                    }
                }
                val step1 = connectionManager.sendCommand(
                    masterAddress, KEY_DOWNLOAD_CONFIG, byteArrayOf(0)
                )
                if (step1.isFailure) {
                    throw IllegalStateException("开始配置下载失败: ${step1.exceptionOrNull()?.message}")
                }
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            registerConfigDownloadState = it.registerConfigDownloadState.copy(
                                completedSteps = setOf(DownloadStep.START_CONFIG),
                                activeStep = DownloadStep.WRITE_REGS
                            )
                        )
                    }
                }

                // Step 2: write register list
                val step2 = connectionManager.sendCommand(
                    masterAddress, KEY_GH3X_REGS_LIST_WRITE_CMD, param
                )
                if (step2.isFailure) {
                    throw IllegalStateException("寄存器列表写入失败: ${step2.exceptionOrNull()?.message}")
                }
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            registerConfigDownloadState = it.registerConfigDownloadState.copy(
                                completedSteps = setOf(DownloadStep.START_CONFIG, DownloadStep.WRITE_REGS),
                                activeStep = DownloadStep.END_CONFIG
                            )
                        )
                    }
                }

                // Step 3: download_config stage 1
                val step3 = connectionManager.sendCommand(
                    masterAddress, KEY_DOWNLOAD_CONFIG, byteArrayOf(1)
                )
                if (step3.isFailure) {
                    throw IllegalStateException("结束配置下载失败: ${step3.exceptionOrNull()?.message}")
                }

                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            registerConfigDownloadState = it.registerConfigDownloadState.copy(
                                status = DownloadStatus.COMPLETED,
                                activeStep = null,
                                completedSteps = setOf(
                                    DownloadStep.START_CONFIG,
                                    DownloadStep.WRITE_REGS,
                                    DownloadStep.END_CONFIG
                                ),
                                error = null
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Register config download failed")
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            registerConfigDownloadState = it.registerConfigDownloadState.copy(
                                status = DownloadStatus.ERROR,
                                error = e.message ?: "配置下载失败"
                            )
                        )
                    }
                }
            }
        }
    }

    fun resetRegisterConfigDownload() {
        _uiState.update {
            it.copy(registerConfigDownloadState = RegisterConfigDownloadState())
        }
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }
}
