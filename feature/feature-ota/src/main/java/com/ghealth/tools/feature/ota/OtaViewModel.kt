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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private var upgradeJob: Job? = null

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
                readFirmwareInfo()
            } catch (e: Throwable) {
                Timber.e(e, "Failed to bind DFU profile")
                _uiState.update { it.copy(errorMessage = "DFU服务绑定失败: ${e.message}") }
            }
        }
    }

    fun readFirmwareInfo() {
        _uiState.update { it.copy(isReadingFirmwareInfo = true, firmwareInfo = null, errorMessage = null) }
        viewModelScope.launch {
            try {
                val firmwareInfo = otaEngine.readFirmwareInfo()
                _uiState.update {
                    it.copy(
                        isReadingFirmwareInfo = false,
                        firmwareInfo = firmwareInfo,
                        flashAddress = "0x${firmwareInfo.loadAddr.toString(16).uppercase()}",
                        errorMessage = null,
                    )
                }
            } catch (e: Throwable) {
                Timber.e(e, "Failed to read firmware info")
                _uiState.update {
                    it.copy(
                        isReadingFirmwareInfo = false,
                        firmwareInfo = null,
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
            val defaultCopyAddress = if (imgInfo != null) {
                val rawAddr = (imgInfo.loadAddr + imgInfo.binSize).toLong()
                (rawAddr + 0xFFF) and 0xFFFFF000L
            } else 0L
            _uiState.update {
                it.copy(
                    firmwareFile = FirmwareFileInfo(
                        uri = uri.toString(),
                        fileName = fileName,
                        fileSize = fileSize,
                        isValid = isValid,
                        parseError = parseError,
                        imgInfo = imgInfo,
                    ),
                    otaConfig = it.otaConfig.copy(copyAddress = defaultCopyAddress),
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
        _uiState.update { it.copy(otaConfig = it.otaConfig.copy(upgradeRegion = region)) }
    }

    fun updateCopyAddress(address: Long) {
        _uiState.update { it.copy(otaConfig = it.otaConfig.copy(copyAddress = address)) }
    }

    fun updateResourceStartAddress(address: Long) {
        _uiState.update { it.copy(otaConfig = it.otaConfig.copy(resourceStartAddress = address)) }
    }

    fun updateResourceStorageType(type: StorageType) {
        _uiState.update { it.copy(otaConfig = it.otaConfig.copy(resourceStorageType = type)) }
    }

    fun updateFastMode(fastMode: Boolean) {
        if (fastMode) {
            val info = _uiState.value.firmwareInfo
            if (info != null && !info.isAppBootloaderSolution) {
                _uiState.update { it.copy(errorMessage = "当前设备不支持快速模式 (需要AppBootloader方案)") }
                return
            }
        }
        _uiState.update { it.copy(otaConfig = it.otaConfig.copy(fastMode = fastMode)) }
    }

    fun toggleCopyAddressEnabled() {
        _uiState.update { state ->
            val newEnabled = !state.otaConfig.copyAddressEnabled
            val newConfig = if (!newEnabled) {
                val imgInfo = state.firmwareFile.imgInfo
                val defaultAddr = if (imgInfo != null) {
                    val rawAddr = (imgInfo.loadAddr + imgInfo.binSize).toLong()
                    (rawAddr + 0xFFF) and 0xFFFFF000L
                } else state.otaConfig.copyAddress
                state.otaConfig.copy(copyAddressEnabled = false, copyAddress = defaultAddr)
            } else {
                state.otaConfig.copy(copyAddressEnabled = true)
            }
            state.copy(otaConfig = newConfig)
        }
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

        upgradeJob = viewModelScope.launch {
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
        upgradeJob?.cancel()
        upgradeJob = null
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

    fun updateRamAddress(v: String) { _uiState.update { it.copy(ramAddress = v) } }
    fun updateRamLength(v: String) { _uiState.update { it.copy(ramLength = v) } }
    fun updateRamLengthUnit(v: String) { _uiState.update { it.copy(ramLengthUnit = v) } }
    fun updateRamData(v: String) { _uiState.update { it.copy(ramData = v) } }

    fun readRam() {
        val addr = _uiState.value.ramAddress.trim().removePrefix("0x").removePrefix("0X")
            .toLongOrNull(16)?.toInt() ?: return
        var len = _uiState.value.ramLength.trim().toIntOrNull() ?: return
        if (_uiState.value.ramLengthUnit == "KB") len *= 1024
        executeDebugRead(DebugMenuAction.RAM_READ_WRITE) { e ->
            val result = e.readRam(addr, len)
            if (result.success && result.data != null) {
                _uiState.update { it.copy(ramReadData = result.data) }
            }
            result
        }
    }

    fun writeRam() {
        val addr = _uiState.value.ramAddress.trim().removePrefix("0x").removePrefix("0X")
            .toLongOrNull(16)?.toInt() ?: return
        val data = parseHexString(_uiState.value.ramData) ?: return
        executeDebugWrite(DebugMenuAction.RAM_READ_WRITE) { it.writeRam(addr, data) }
    }

    fun downloadRam() {
        val addr = _uiState.value.ramAddress.trim().removePrefix("0x").removePrefix("0X")
            .toLongOrNull(16)?.toInt() ?: return
        var len = _uiState.value.ramLength.trim().toIntOrNull() ?: return
        if (_uiState.value.ramLengthUnit == "KB") len *= 1024
        viewModelScope.launch {
            if (!otaEngine.isDfuReady) {
                _uiState.update { it.copy(errorMessage = "DFU服务未就绪，请先选择设备") }
                return@launch
            }
            val result = otaEngine.readRam(addr, len)
            if (result.success && result.data != null) {
                _uiState.update { it.copy(ramReadData = result.data, debugResults = it.debugResults + (DebugMenuAction.RAM_READ_WRITE to result.message)) }
                val defaultName = "0x${addr.toString(16).uppercase()}-${java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.getDefault()).format(java.util.Date())}.bin"
                val savePath = getApplication<android.app.Application>().getExternalFilesDir(null)?.absolutePath + "/ota_dump/"
                _uiState.update { it.copy(showDownloadDialog = true, downloadDefaultName = defaultName, downloadData = result.data, downloadSavePath = savePath) }
            } else {
                _uiState.update { it.copy(errorMessage = result.message, debugResults = it.debugResults + (DebugMenuAction.RAM_READ_WRITE to "错误: ${result.message}")) }
            }
        }
    }

    fun updateFlashAddress(v: String) { _uiState.update { it.copy(flashAddress = v) } }
    fun updateFlashLength(v: String) { _uiState.update { it.copy(flashLength = v) } }
    fun updateFlashLengthUnit(v: String) { _uiState.update { it.copy(flashLengthUnit = v) } }
    fun updateFlashData(v: String) { _uiState.update { it.copy(flashData = v) } }

    fun readFlash() {
        val addr = _uiState.value.flashAddress.trim().removePrefix("0x").removePrefix("0X")
            .toLongOrNull(16)?.toInt() ?: return
        var len = _uiState.value.flashLength.trim().toIntOrNull() ?: return
        if (_uiState.value.flashLengthUnit == "KB") len *= 1024
        executeDebugRead(DebugMenuAction.FLASH_READ_WRITE) { e ->
            val result = e.readFlash(addr, len)
            if (result.success && result.data != null) {
                _uiState.update { it.copy(flashReadData = result.data) }
            }
            result
        }
    }

    fun writeFlash() {
        val addr = _uiState.value.flashAddress.trim().removePrefix("0x").removePrefix("0X")
            .toLongOrNull(16)?.toInt() ?: return
        val data = parseHexString(_uiState.value.flashData) ?: return
        executeDebugWrite(DebugMenuAction.FLASH_READ_WRITE) { it.writeFlash(addr, data) }
    }

    fun downloadFlash() {
        val addr = _uiState.value.flashAddress.trim().removePrefix("0x").removePrefix("0X")
            .toLongOrNull(16)?.toInt() ?: return
        var len = _uiState.value.flashLength.trim().toIntOrNull() ?: return
        if (_uiState.value.flashLengthUnit == "KB") len *= 1024
        viewModelScope.launch {
            if (!otaEngine.isDfuReady) {
                _uiState.update { it.copy(errorMessage = "DFU服务未就绪，请先选择设备") }
                return@launch
            }
            val result = otaEngine.readFlash(addr, len)
            if (result.success && result.data != null) {
                _uiState.update { it.copy(flashReadData = result.data, debugResults = it.debugResults + (DebugMenuAction.FLASH_READ_WRITE to result.message)) }
                val defaultName = "0x${addr.toString(16).uppercase()}-${java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.getDefault()).format(java.util.Date())}.bin"
                val savePath = getApplication<android.app.Application>().getExternalFilesDir(null)?.absolutePath + "/ota_dump/"
                _uiState.update { it.copy(showDownloadDialog = true, downloadDefaultName = defaultName, downloadData = result.data, downloadSavePath = savePath) }
            } else {
                _uiState.update { it.copy(errorMessage = result.message, debugResults = it.debugResults + (DebugMenuAction.FLASH_READ_WRITE to "错误: ${result.message}")) }
            }
        }
    }

    fun updateRegisterAddress(v: String) { _uiState.update { it.copy(registerAddress = v) } }
    fun updateRegisterData(v: String) { _uiState.update { it.copy(registerData = v) } }

    fun readRegister() {
        val addr = _uiState.value.registerAddress.trim().removePrefix("0x").removePrefix("0X")
            .toLongOrNull(16)?.toInt() ?: return
        executeDebugRead(DebugMenuAction.REGISTER_READ_WRITE) { it.readRegister(addr) }
    }

    fun writeRegister() {
        val addr = _uiState.value.registerAddress.trim().removePrefix("0x").removePrefix("0X")
            .toLongOrNull(16)?.toInt() ?: return
        val data = parseHexString(_uiState.value.registerData) ?: return
        executeDebugWrite(DebugMenuAction.REGISTER_READ_WRITE) { it.writeRegister(addr, data) }
    }

    fun updateNvdsTag(v: String) { _uiState.update { it.copy(nvdsTag = v) } }
    fun updateNvdsData(v: String) { _uiState.update { it.copy(nvdsData = v) } }

    fun readNvds() {
        val tag = _uiState.value.nvdsTag.trim().removePrefix("0x").removePrefix("0X")
            .toLongOrNull(16)?.toInt() ?: 0
        executeDebugRead(DebugMenuAction.NVDS_READ_WRITE) { it.readNvds(tag) }
    }

    fun writeNvds() {
        val tag = _uiState.value.nvdsTag.trim().removePrefix("0x").removePrefix("0X")
            .toLongOrNull(16)?.toInt() ?: 0
        val data = parseHexString(_uiState.value.nvdsData) ?: return
        executeDebugWrite(DebugMenuAction.NVDS_READ_WRITE) { it.writeNvds(tag, data) }
    }

    fun deleteNvds() {
        val tag = _uiState.value.nvdsTag.trim().removePrefix("0x").removePrefix("0X")
            .toLongOrNull(16)?.toInt() ?: 0
        executeDebugWrite(DebugMenuAction.NVDS_READ_WRITE) { it.writeNvds(tag, ByteArray(0)) }
    }

    fun readEfuse() {
        executeDebugRead(DebugMenuAction.READ_EFUSE) { it.readEfuse() }
    }

    fun clearEfuseResult() {
        _uiState.update { it.copy(efuseResult = "") }
    }

    fun readBootInfo() {
        viewModelScope.launch {
            if (!otaEngine.isDfuReady) {
                _uiState.update { it.copy(errorMessage = "DFU服务未就绪，请先选择设备") }
                return@launch
            }
            _uiState.update { it.copy(bootInfoData = null, errorMessage = null) }
            try {
                val result = otaEngine.readBootInfo()
                if (result.success) {
                    _uiState.update { it.copy(bootInfoData = parseBootInfoFromMessage(result.message)) }
                } else {
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
            } catch (e: Throwable) {
                _uiState.update { it.copy(errorMessage = "读取BootInfo失败: ${e.message}") }
            }
        }
    }

    fun rebootDevice() {
        viewModelScope.launch {
            if (!otaEngine.isDfuReady) {
                _uiState.update { it.copy(errorMessage = "DFU服务未就绪，请先选择设备") }
                return@launch
            }
            val result = otaEngine.reboot()
            if (!result.success) {
                _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    fun dismissDownloadDialog() {
        _uiState.update { it.copy(showDownloadDialog = false, downloadData = null) }
    }

    fun updateDownloadFileName(name: String) {
        _uiState.update { it.copy(downloadDefaultName = name) }
    }

    fun confirmDownload() {
        val data = _uiState.value.downloadData ?: return
        val fileName = _uiState.value.downloadDefaultName.ifBlank { "dump.bin" }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dir = java.io.File(getApplication<android.app.Application>().getExternalFilesDir(null), "ota_dump")
                if (!dir.exists()) dir.mkdirs()
                val file = java.io.File(dir, fileName)
                file.writeBytes(data)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(showDownloadDialog = false, downloadData = null) }
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(errorMessage = "保存失败: ${e.message}") }
                }
            }
        }
    }

    private fun parseHexString(hex: String): ByteArray? {
        val clean = hex.trim().replace(" ", "").replace("\n", "").replace("\r", "")
        if (clean.isEmpty() || clean.length % 2 != 0) return null
        return ByteArray(clean.length / 2) { i ->
            ((Character.digit(clean[i * 2], 16) shl 4) + Character.digit(clean[i * 2 + 1], 16)).toByte()
        }
    }

    private fun parseBootInfoFromMessage(message: String): BootInfoData? {
        try {
            val lines = message.lines()
            var bi = BootInfoData()
            for (line in lines) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("binSize=") -> bi = bi.copy(binSize = trimmed.substringAfter("=").split(",")[0].toIntOrNull() ?: 0)
                    trimmed.startsWith("checksum=") -> bi = bi.copy(checksum = trimmed.substringAfter("0x").split(",")[0].toIntOrNull(16) ?: 0)
                    trimmed.startsWith("loadAddr=") -> bi = bi.copy(loadAddr = trimmed.substringAfter("0x").split(",")[0].toIntOrNull(16) ?: 0)
                    trimmed.startsWith("runAddr=") -> bi = bi.copy(runAddr = trimmed.substringAfter("0x").split(",")[0].toIntOrNull(16) ?: 0)
                    trimmed.startsWith("xqspiXipCmd=") -> bi = bi.copy(xqspiXipCmd = trimmed.substringAfter("0x").split(",")[0].toIntOrNull(16) ?: 0)
                    trimmed.startsWith("xqspiSpeed=") -> bi = bi.copy(xqspiSpeed = trimmed.substringAfter("=").toIntOrNull() ?: 0)
                    trimmed.startsWith("codeCopyMode=") -> bi = bi.copy(codeCopyMode = trimmed.substringAfter("=").toIntOrNull() ?: 0)
                    trimmed.startsWith("systemClk=") -> bi = bi.copy(systemClk = trimmed.substringAfter("=").toIntOrNull() ?: 0)
                    trimmed.startsWith("checkImage=") -> bi = bi.copy(checkImage = trimmed.substringAfter("=").toIntOrNull() ?: 0)
                    trimmed.startsWith("bootDelay=") -> bi = bi.copy(bootDelay = trimmed.substringAfter("=").toIntOrNull() ?: 0)
                    trimmed.startsWith("isDapBoot=") -> bi = bi.copy(isDapBoot = trimmed.substringAfter("=").toIntOrNull() ?: 0)
                    trimmed.startsWith("isEncrypted=") -> bi = bi.copy(isEncrypted = trimmed.substringAfter("=").toBoolean())
                }
            }
            return bi
        } catch (e: Throwable) {
            return null
        }
    }

    private fun executeDebugRead(
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

    private fun executeDebugWrite(
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