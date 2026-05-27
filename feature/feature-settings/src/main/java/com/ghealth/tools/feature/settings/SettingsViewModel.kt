package com.ghealth.tools.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.core.datastore.BlePreferences
import com.ghealth.tools.core.datastore.UserPreferences
import com.ghealth.tools.core.network.ConfigPathProvider
import com.ghealth.tools.core.network.ConfigSyncManager
import com.ghealth.tools.core.network.api.ProjectApi
import com.ghealth.tools.core.storage.LogManager
import com.ghealth.tools.core.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
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
    val themeMode: ThemeMode = ThemeMode.SKY_BLUE,
    val availableThemes: List<ThemeMode> = ThemeMode.entries,
    val selectedChip: String = "gh3036",
    val isOnlineMode: Boolean = false,
    val selectedProjectName: String? = null,
    val selectedProjectId: Int? = null,
    val isDeletingProject: Boolean = false,
    val isSyncingConfig: Boolean = false,
    val operationMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val blePreferences: BlePreferences,
    private val logManager: LogManager,
    @Named("app_version") private val versionName: String,
    private val userPreferences: UserPreferences,
    private val configSyncManager: ConfigSyncManager,
    private val projectApi: ProjectApi,
    private val configPathProvider: ConfigPathProvider
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
            blePreferences.selectedChip.collect { chip ->
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
        val projectId = _uiState.value.selectedProjectId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeletingProject = true) }
            try {
                val response = projectApi.deleteProject(projectId)
                if (response.isSuccessful) {
                    userPreferences.setSelectedProject(0, "")
                    _uiState.update {
                        it.copy(
                            isDeletingProject = false,
                            operationMessage = "项目已删除"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isDeletingProject = false,
                            operationMessage = "删除失败: ${response.code()}"
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Delete project failed")
                _uiState.update {
                    it.copy(
                        isDeletingProject = false,
                        operationMessage = "删除失败: ${e.message}"
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
}