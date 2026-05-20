package com.ghealth.tools.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.core.datastore.BlePreferences
import com.ghealth.tools.core.storage.LogManager
import com.ghealth.tools.core.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val selectedChip: String = "gh3036"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val blePreferences: BlePreferences,
    private val logManager: LogManager,
    @Named("app_version") private val versionName: String
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
}
