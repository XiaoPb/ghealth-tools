package com.ghealth.tools.feature.ota

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.ble.connection.BleConnectionManager
import com.ghealth.tools.feature.ota.engine.DebugResult
import com.ghealth.tools.feature.ota.engine.FirmwareInfo
import com.ghealth.tools.feature.ota.engine.OtaEngine
import com.ghealth.tools.feature.ota.engine.OtaState
import com.ghealth.tools.feature.ota.model.DebugMenuAction
import com.ghealth.tools.feature.ota.model.OtaConfig
import com.ghealth.tools.feature.ota.model.StorageType
import com.ghealth.tools.feature.ota.model.UpgradeRegion
import com.goodix.ble.gr.lib.dfu.v2.pojo.DfuFile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltViewModel
class OtaViewModel @Inject constructor(
    application: Application,
    private val otaEngine: OtaEngine,
    private val connectionManager: BleConnectionManager,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(OtaUiState())
    val uiState: StateFlow<OtaUiState> = _uiState.asStateFlow()

    private val context get() = getApplication<Application>()

    init {
        viewModelScope.launch {
            otaEngine.progress.collect { progress ->
                _uiState.update {
                    it.copy(
                        otaState = progress.state,
                        progressPercent = progress.progressPercent,
                        logLines = progress.logLines,
                        isUpgrading = when (progress.state) {
                            OtaState.COMPLETED, OtaState.CANCELLED, OtaState.ERROR, OtaState.IDLE -> false
                            else -> it.isUpgrading
                        },
                        successMessage = if (progress.state == OtaState.COMPLETED) "升级成功！" else it.successMessage,
                        showResultDialog = progress.state == OtaState.COMPLETED || progress.state == OtaState.ERROR,
                        errorMessage = progress.errorMessage ?: it.errorMessage,
                    )
                }
            }
        }
        viewModelScope.launch {
            otaEngine.logEvents.collect { log ->
                _uiState.update { it.copy(logLines = it.logLines + log) }
            }
        }
    }

    fun loadAvailableDevices(devices: List<ConnectedDeviceInfo>) {
        val selectedDevice = devices.firstOrNull()
        _uiState.update {
            it.copy(
                availableDevices = devices,
                selectedDevice = selectedDevice,
            )
        }
        selectedDevice?.let { bindDfuProfile(it) }
    }

    private fun bindDfuProfile(device: ConnectedDeviceInfo) {
        viewModelScope.launch {
            try {
                otaEngine.bindDfuProfile(context, device.address)
                _uiState.update { it.copy(errorMessage = null) }
            } catch (e: Throwable) {
                Timber.e(e, "Failed to bind DFU profile")
                _uiState.update { it.copy(errorMessage = "DFU服务绑定失败: ${e.message}") }
            }
        }
    }

    fun readFirmwareInfo() {
        _uiState.update { it.copy(isReadingFirmwareInfo = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val firmwareInfo = otaEngine.readFirmwareInfo()
                _uiState.update {
                    it.copy(
                        isReadingFirmwareInfo = false,
                        firmwareInfo = firmwareInfo,
                        errorMessage = null,
                    )
                }
            } catch (e: Throwable) {
                Timber.e(e, "Failed to read firmware info")
                _uiState.update {
                    it.copy(
                        isReadingFirmwareInfo = false,
                        errorMessage = e.message ?: "读取固件信息失败",
                    )
                }
            }
        }
    }

    fun selectFirmwareFile(uri: Uri) {
        val fileName = readFileName(uri)
        val fileSize = readFileSize(uri)

        viewModelScope.launch(Dispatchers.IO) {
            val (isValid, parseError, imgInfo) = try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@launch
                val dfuFile = DfuFile()
                if (dfuFile.load(bytes) && dfuFile.isValidDfuFile && dfuFile.imgInfo != null) {
                    Triple(true, null, FirmwareInfo.fromImgInfo(dfuFile.imgInfo))
                } else {
                    val err = dfuFile.lastError ?: "无效的DFU固件文件"
                    Triple(false, err, null)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse firmware file")
                Triple(false, "文件解析失败: ${e.message}", null)
            }
            _uiState.update {
                it.copy(
                    firmwareFile = FirmwareFileInfo(
                        uri = uri.toString(),
                        fileName = fileName,
                        fileSize = fileSize,
                        isValid = isValid,
                        parseError = parseError,
                        imgInfo = imgInfo,
                    )
                )
            }
        }
    }

    fun selectResourceFile(uri: Uri) {
        val fileName = readFileName(uri)
        val fileSize = readFileSize(uri)
        _uiState.update {
            it.copy(resourceFile = FirmwareFileInfo(uri = uri.toString(), fileName = fileName, fileSize = fileSize))
        }
    }

    fun selectUpgradeRegion(region: UpgradeRegion) {
        _uiState.update {
            it.copy(
                upgradeRegion = region,
                otaConfig = it.otaConfig.copy(upgradeRegion = region),
            )
        }
    }

    fun updateCopyAddress(address: Long) {
        _uiState.update { it.copy(otaConfig = it.otaConfig.copy(copyAddress = address)) }
    }

    fun updateResourceStartAddress(address: Long) {
        _uiState.update { it.copy(resourceStartAddress = address) }
    }

    fun updateResourceStorageType(type: StorageType) {
        _uiState.update { it.copy(resourceStorageType = type) }
    }

    fun updateFastMode(fastMode: Boolean) {
        _uiState.update { it.copy(otaConfig = it.otaConfig.copy(fastMode = fastMode)) }
    }

    fun toggleCopyAddressEnabled() {
        _uiState.update { it.copy(otaConfig = it.otaConfig.copy(copyAddressEnabled = !it.otaConfig.copyAddressEnabled)) }
    }

    fun startFirmwareUpgrade() {
        val fileInfo = _uiState.value.firmwareFile
        if (!fileInfo.isValid) {
            _uiState.update { it.copy(errorMessage = fileInfo.parseError ?: "无效的DFU固件文件，无法升级") }
            return
        }
        if (!otaEngine.isDfuReady) {
            _uiState.update { it.copy(errorMessage = "DFU服务未就绪，请先选择设备") }
            return
        }
        prepareAndExecuteUpgrade(fileInfo.uri, fileInfo.fileName) { stream ->
            otaEngine.startFirmwareUpgrade(context, stream, _uiState.value.otaConfig)
        }
    }

    fun startResourceUpgrade() {
        val state = _uiState.value
        val uriStr = state.resourceFile.uri
        if (uriStr.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "请先选择资源文件") }
            return
        }
        if (!otaEngine.isDfuReady) {
            _uiState.update { it.copy(errorMessage = "DFU服务未就绪，请先选择设备") }
            return
        }
        _uiState.update {
            it.copy(
                otaConfig = it.otaConfig.copy(
                    resourceStartAddress = state.resourceStartAddress,
                    resourceStorageType = state.resourceStorageType,
                )
            )
        }
        prepareAndExecuteUpgrade(uriStr, state.resourceFile.fileName) { stream ->
            otaEngine.startResourceUpgrade(context, stream, _uiState.value.otaConfig)
        }
    }

    private fun prepareAndExecuteUpgrade(
        uriStr: String,
        fileName: String,
        operation: suspend (java.io.InputStream) -> Unit,
    ) {
        if (uriStr.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "请先选择固件文件") }
            return
        }

        _uiState.update {
            it.copy(
                isUpgrading = true,
                otaState = OtaState.PREPARING,
                logLines = emptyList(),
                errorMessage = null,
                successMessage = null,
                showResultDialog = false,
            )
        }

        viewModelScope.launch {
            try {
                val tempFile = File(context.cacheDir, fileName)
                context.contentResolver.openInputStream(Uri.parse(uriStr))?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }

                tempFile.inputStream().use { stream ->
                    operation(stream)
                }
                tempFile.delete()
            } catch (e: Throwable) {
                Timber.e(e, "Upgrade failed")
                _uiState.update {
                    it.copy(
                        isUpgrading = false,
                        otaState = OtaState.ERROR,
                        errorMessage = e.message ?: "升级异常",
                    )
                }
            }
        }
    }

    fun cancelUpgrade() {
        otaEngine.cancel()
        _uiState.update { it.copy(isUpgrading = false, otaState = OtaState.CANCELLED) }
    }

    fun resetState() {
        otaEngine.reset()
        _uiState.update {
            it.copy(
                otaState = OtaState.IDLE,
                progressPercent = 0f,
                logLines = emptyList(),
                errorMessage = null,
                successMessage = null,
                showResultDialog = false,
                debugResults = emptyMap(),
            )
        }
    }

    fun dismissError() { _uiState.update { it.copy(errorMessage = null) } }
    fun dismissResultDialog() { _uiState.update { it.copy(showResultDialog = false) } }

    fun showControlPointDialog() { _uiState.update { it.copy(showControlPointDialog = true) } }
    fun dismissControlPointDialog() { _uiState.update { it.copy(showControlPointDialog = false) } }
    fun updateControlPointHex(hex: String) { _uiState.update { it.copy(controlPointHex = hex) } }

    fun toggleDebugAction(action: DebugMenuAction) {
        _uiState.update { state ->
            val current = state.activeDebugActions
            if (current.contains(action)) {
                state.copy(activeDebugActions = current - action)
            } else {
                state.copy(activeDebugActions = current + action)
            }
        }
    }

    fun writeControlPoint() {
        viewModelScope.launch {
            _uiState.update { it.copy(showControlPointDialog = false) }
            val hex = _uiState.value.controlPointHex
            val result = otaEngine.writeControlPoint(hex)
            if (result.success) {
                _uiState.update { it.copy(debugResults = emptyMap()) }
            } else {
                _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    fun executeDebugCommand(
        action: DebugMenuAction,
        operation: suspend (OtaEngine) -> DebugResult,
    ) {
        viewModelScope.launch {
            if (!otaEngine.isDfuReady) {
                _uiState.update { it.copy(errorMessage = "DFU服务未就绪，请先选择设备") }
                return@launch
            }
            val result = operation(otaEngine)
            if (result.success) {
                _uiState.update {
                    it.copy(debugResults = it.debugResults + (action to result.message))
                }
            } else {
                _uiState.update {
                    it.copy(
                        errorMessage = result.message,
                        debugResults = it.debugResults + (action to "错误: ${result.message}"),
                    )
                }
            }
        }
    }

    fun executeDebugWrite(
        action: DebugMenuAction,
        operation: suspend (OtaEngine) -> DebugResult,
    ) {
        viewModelScope.launch {
            if (!otaEngine.isDfuReady) {
                _uiState.update { it.copy(errorMessage = "DFU服务未就绪，请先选择设备") }
                return@launch
            }
            val result = operation(otaEngine)
            if (result.success) {
                _uiState.update {
                    it.copy(debugResults = it.debugResults + (action to result.message))
                }
            } else {
                _uiState.update {
                    it.copy(
                        errorMessage = result.message,
                        debugResults = it.debugResults + (action to "错误: ${result.message}"),
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        otaEngine.unbindDfuProfile()
    }

    private fun readFileName(uri: Uri): String {
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) it.getString(idx) else "unknown.bin"
                } else "unknown.bin"
            } ?: "unknown.bin"
        } catch (e: Exception) {
            Timber.e(e, "Failed to get file name")
            "unknown.bin"
        }
    }

    private fun readFileSize(uri: Uri): Long {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}