package com.ghealth.tools.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.ble.connection.BleConnectionManager
import com.ghealth.tools.ble.connection.FirmwareVersionHolder
import com.ghealth.tools.ble.connection.DeviceRole
import com.ghealth.tools.core.datastore.BlePreferences
import com.ghealth.tools.core.datastore.UserPreferences
import com.ghealth.tools.core.model.ConnectionState
import com.ghealth.tools.core.network.ConfigPathProvider
import com.ghealth.tools.core.network.ConfigSyncManager
import com.ghealth.tools.core.network.api.ProjectApi
import com.ghealth.tools.core.storage.LogManager
import com.ghealth.tools.core.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Named

data class SettingsUiState(
    val serviceUuid: String = "",
    val writeUuid: String = "",
    val notifyUuid: String = "",
    val autoReconnect: Boolean = true,
    val appVersion: String = "",
    val exportedLogPath: String? = null,
    val themeMode: ThemeMode = ThemeMode.BLUE_500,
    val availableThemes: List<ThemeMode> = ThemeMode.entries,
    val selectedChip: String = "gh3036",
    val isOnlineMode: Boolean = false,
    val selectedProjectName: String? = null,
    val selectedProjectId: Int? = null,
    val isDeletingProject: Boolean = false,
    val isSyncingConfig: Boolean = false,
    val operationMessage: String? = null,
    val showUpdateDialog: Boolean = false,
    val updateVersionName: String = "",
    val updateChangelog: String = "",
    val updateDownloadUrl: String = "",
    val updateProxyDownloadUrl: String = "",
    val useProxyDownload: Boolean = true,
    val isForceUpdate: Boolean = false,
    val isCheckingUpdate: Boolean = false,
    val isDeviceConnected: Boolean = false,
    val bleVersion: String = "",
    val isReadingBleVersion: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val connectionManager: BleConnectionManager,
    private val firmwareVersionHolder: FirmwareVersionHolder,
    private val blePreferences: BlePreferences,
    private val logManager: LogManager,
    @Named("app_version") private val versionName: String,
    private val userPreferences: UserPreferences,
    private val configSyncManager: ConfigSyncManager,
    private val projectApi: ProjectApi,
    private val configPathProvider: ConfigPathProvider,
    private val updateChecker: UpdateChecker
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(appVersion = versionName))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            blePreferences.serviceUuid.collect { uuid ->
                _uiState.update { it.copy(serviceUuid = uuid) }
            }
        }
        viewModelScope.launch {
            blePreferences.writeCharUuid.collect { uuid ->
                _uiState.update { it.copy(writeUuid = uuid) }
            }
        }
        viewModelScope.launch {
            blePreferences.notifyCharUuid.collect { uuid ->
                _uiState.update { it.copy(notifyUuid = uuid) }
            }
        }
        viewModelScope.launch {
            blePreferences.autoReconnect.collect { auto ->
                _uiState.update { it.copy(autoReconnect = auto) }
            }
        }
        viewModelScope.launch {
            blePreferences.themeMode.collect { key ->
                _uiState.update { it.copy(themeMode = ThemeMode.fromKey(key)) }
            }
        }
        viewModelScope.launch {
            blePreferences.effectiveChip.collect { chip ->
                _uiState.update { it.copy(selectedChip = chip) }
            }
        }
        viewModelScope.launch {
            configPathProvider.isOnlineMode.collect { online ->
                _uiState.update { it.copy(isOnlineMode = online) }
            }
        }
        viewModelScope.launch {
            userPreferences.selectedProjectName.collect { name ->
                _uiState.update { it.copy(selectedProjectName = name) }
            }
        }
        viewModelScope.launch {
            userPreferences.selectedProjectId.collect { id ->
                _uiState.update { it.copy(selectedProjectId = id) }
            }
        }
        // 主设备连接状态（用于设置页"未连接"提示；版本读取由 FirmwareVersionHolder 统一负责）
        viewModelScope.launch {
            connectionManager.devices.collect { devices ->
                val master = devices.values.find {
                    it.role == DeviceRole.MASTER && it.state == ConnectionState.CONNECTED
                }
                _uiState.update { it.copy(isDeviceConnected = master != null) }
            }
        }
        // 订阅共享固件版本状态（与连接页同一数据源，避免重复下发 BLE 版本读取命令）
        viewModelScope.launch {
            firmwareVersionHolder.state.collect { versionState ->
                _uiState.update {
                    it.copy(
                        bleVersion = versionState.version ?: "",
                        isReadingBleVersion = versionState.isReading
                    )
                }
            }
        }
    }

    fun updateServiceUuid(uuid: String) {
        viewModelScope.launch { blePreferences.setServiceUuid(uuid) }
    }

    fun updateWriteUuid(uuid: String) {
        viewModelScope.launch { blePreferences.setWriteCharUuid(uuid) }
    }

    fun updateNotifyUuid(uuid: String) {
        viewModelScope.launch { blePreferences.setNotifyCharUuid(uuid) }
    }

    fun toggleAutoReconnect() {
        viewModelScope.launch { blePreferences.setAutoReconnect(!_uiState.value.autoReconnect) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { blePreferences.setThemeMode(mode.key) }
    }

    fun exportLogs() {
        viewModelScope.launch {
            val file = logManager.exportLogs()
            _uiState.update { it.copy(exportedLogPath = file?.absolutePath) }
        }
    }

    fun clearExportMessage() {
        _uiState.update { it.copy(exportedLogPath = null) }
    }

    fun deleteProject() {
        val projectId = _uiState.value.selectedProjectId
        if (projectId == null || projectId <= 0) {
            _uiState.update { it.copy(operationMessage = "无法删除：未选择有效项目") }
            return
        }
        val projectName = _uiState.value.selectedProjectName ?: ""
        viewModelScope.launch {
            _uiState.update { it.copy(isDeletingProject = true) }
            try {
                val response = projectApi.deleteProject(projectId)
                if (response.isSuccessful) {
                    userPreferences.setSelectedProject(0, "")
                    _uiState.update {
                        it.copy(
                            isDeletingProject = false,
                            selectedProjectId = null,
                            selectedProjectName = null,
                            operationMessage = "项目「${projectName}」已删除"
                        )
                    }
                } else {
                    val msg = when (response.code()) {
                        403 -> "无权限删除此项目"
                        404 -> "项目不存在或已被删除"
                        else -> "删除失败(${response.code()})"
                    }
                    _uiState.update {
                        it.copy(
                            isDeletingProject = false,
                            operationMessage = msg
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Delete project failed")
                _uiState.update {
                    it.copy(
                        isDeletingProject = false,
                        operationMessage = "删除失败: ${e.message ?: "网络错误"}"
                    )
                }
            }
        }
    }

    fun refreshConfig() {
        val projectId = _uiState.value.selectedProjectId ?: return
        val projectName = _uiState.value.selectedProjectName ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingConfig = true) }
            try {
                configSyncManager.fullSync(projectId, projectName)
                _uiState.update {
                    it.copy(
                        isSyncingConfig = false,
                        operationMessage = "配置刷新成功"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Config sync failed")
                _uiState.update {
                    it.copy(
                        isSyncingConfig = false,
                        operationMessage = "配置刷新失败: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearOperationMessage() {
        _uiState.update { it.copy(operationMessage = null) }
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingUpdate = true) }
            try {
                val result = updateChecker.checkForUpdate()
                if (result.hasUpdate && result.updateInfo != null) {
                    _uiState.update {
                        it.copy(
                            isCheckingUpdate = false,
                            showUpdateDialog = true,
                            updateVersionName = result.updateInfo.versionName,
                            updateChangelog = result.updateInfo.changelog,
                            updateDownloadUrl = result.updateInfo.downloadUrl,
                            updateProxyDownloadUrl = result.updateInfo.proxyDownloadUrl,
                            useProxyDownload = true,
                            isForceUpdate = result.isForceUpdate,
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isCheckingUpdate = false,
                            operationMessage = result.errorMessage ?: "已是最新版本"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCheckingUpdate = false,
                        operationMessage = "检查更新失败: ${e.message}"
                    )
                }
            }
        }
    }

    fun setUseProxyDownload(useProxy: Boolean) {
        _uiState.update { it.copy(useProxyDownload = useProxy) }
    }

    fun dismissUpdateDialog() {
        _uiState.update {
            it.copy(
                showUpdateDialog = false,
                updateVersionName = "",
                updateChangelog = "",
                updateDownloadUrl = "",
                updateProxyDownloadUrl = "",
                useProxyDownload = true,
                isForceUpdate = false,
            )
        }
    }

    fun openDownloadPage() {
        val state = _uiState.value
        val url = UpdateDownloadLinks.effectiveDownloadUrl(
            useProxy = state.useProxyDownload,
            directUrl = state.updateDownloadUrl,
            proxyUrl = state.updateProxyDownloadUrl,
        )
        if (url.isNotEmpty()) {
            updateChecker.openDownloadUrl(url)
        }
    }
}
