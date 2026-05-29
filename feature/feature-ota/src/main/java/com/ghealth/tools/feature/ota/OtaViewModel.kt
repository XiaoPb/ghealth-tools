package com.ghealth.tools.feature.ota

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.feature.ota.engine.DebugOperations
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
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(OtaUiState())
    val uiState: StateFlow<OtaUiState> = _uiState.asStateFlow()

    private val context get() = getApplication<Application>()
    private var debugOps: DebugOperations? = null

    init {
        viewModelScope.launch {
            otaEngine.progress.collect { progress ->
                _uiState.update {
                    it.copy(
                        otaState = progress.state,
                        progressPercent = progress.progressPercent,
                        logLines = progress.logLines,
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
        _uiState.update { it.copy(availableDevices = devices) }
    }

    fun selectDevice(device: ConnectedDeviceInfo) {
        _uiState.update { it.copy(selectedDevice = device) }
    }

    fun readFirmwareInfo() {
        val deviceInfo = _uiState.value.selectedDevice ?: run {
            _uiState.update { it.copy(errorMessage = "请先选择目标设备") }
            return
        }
        _uiState.update { it.copy(isReadingFirmwareInfo = true) }
        viewModelScope.launch {
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                val device: BluetoothDevice = adapter.getRemoteDevice(deviceInfo.address)

                _uiState.update {
                    it.copy(
                        isReadingFirmwareInfo = false,
                        errorMessage = "固件信息读取需通过DFU连接获取，请选择固件文件后通过文件解析查看信息",
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to read firmware info")
                _uiState.update { it.copy(isReadingFirmwareInfo = false, errorMessage = e.message) }
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

    fun startFirmwareUpgrade() {
        val fileInfo = _uiState.value.firmwareFile
        if (!fileInfo.isValid) {
            _uiState.update { it.copy(errorMessage = fileInfo.parseError ?: "无效的DFU固件文件，无法升级") }
            return
        }
        prepareAndExecuteUpgrade(fileInfo.uri, fileInfo.fileName) { device, stream ->
            otaEngine.startFirmwareUpgrade(context, device, stream, _uiState.value.otaConfig)
        }
    }

    fun startResourceUpgrade() {
        val state = _uiState.value
        val uriStr = state.resourceFile.uri
        if (uriStr.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "请先选择资源文件") }
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
        prepareAndExecuteUpgrade(uriStr, state.resourceFile.fileName) { device, stream ->
            otaEngine.startResourceUpgrade(context, device, stream, _uiState.value.otaConfig)
        }
    }

    private fun prepareAndExecuteUpgrade(
        uriStr: String,
        fileName: String,
        operation: suspend (BluetoothDevice, java.io.InputStream) -> Result<Unit>,
    ) {
        val deviceInfo = _uiState.value.selectedDevice ?: run {
            _uiState.update { it.copy(errorMessage = "请先选择目标设备") }
            return
        }
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
            )
        }

        viewModelScope.launch {
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                val device: BluetoothDevice = adapter.getRemoteDevice(deviceInfo.address)
                val tempFile = File(context.cacheDir, fileName)
                context.contentResolver.openInputStream(Uri.parse(uriStr))?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }

                val result = tempFile.inputStream().use { stream ->
                    operation(device, stream)
                }
                tempFile.delete()

                result.fold(
                    onSuccess = {
                        _uiState.update {
                            it.copy(
                                isUpgrading = false,
                                otaState = OtaState.COMPLETED,
                                progressPercent = 1f,
                                successMessage = "升级成功！",
                                showResultDialog = true,
                            )
                        }
                    },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(
                                isUpgrading = false,
                                otaState = OtaState.ERROR,
                                errorMessage = e.message ?: "升级失败",
                                showResultDialog = true,
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Upgrade failed")
                _uiState.update {
                    it.copy(
                        isUpgrading = false,
                        otaState = OtaState.ERROR,
                        errorMessage = e.message ?: "升级异常",
                        showResultDialog = true,
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
                debugResult = null,
            )
        }
    }

    fun dismissError() { _uiState.update { it.copy(errorMessage = null) } }
    fun dismissResultDialog() { _uiState.update { it.copy(showResultDialog = false) } }

    fun showFastModeDialog() { _uiState.update { it.copy(showFastModeDialog = true) } }
    fun dismissFastModeDialog() { _uiState.update { it.copy(showFastModeDialog = false) } }

    fun showCopyAddressDialog() { _uiState.update { it.copy(showCopyAddressDialog = true) } }
    fun dismissCopyAddressDialog() { _uiState.update { it.copy(showCopyAddressDialog = false) } }

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
            debugOps?.writeControlPoint(hex)?.fold(
                onSuccess = { result -> _uiState.update { it.copy(debugResult = result.message) } },
                onFailure = { error -> _uiState.update { it.copy(errorMessage = error.message ?: "写控制点失败") } },
            )
        }
    }

    fun executeDebugRead(operation: suspend (DebugOperations) -> Result<com.ghealth.tools.feature.ota.engine.DebugResult>) {
        viewModelScope.launch {
            val ops = debugOps ?: run {
                _uiState.update { it.copy(errorMessage = "调试功能暂不可用") }
                return@launch
            }
            operation(ops).fold(
                onSuccess = { result -> _uiState.update { it.copy(debugResult = result.message) } },
                onFailure = { error -> _uiState.update { it.copy(errorMessage = error.message ?: "调试操作失败") } },
            )
        }
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