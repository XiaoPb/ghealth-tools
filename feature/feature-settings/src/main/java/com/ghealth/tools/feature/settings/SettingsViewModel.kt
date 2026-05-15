package com.ghealth.tools.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.core.datastore.BlePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val serviceUuid: String = "",
    val writeUuid: String = "",
    val notifyUuid: String = "",
    val autoReconnect: Boolean = true,
    val appVersion: String = "1.0.0"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val blePreferences: BlePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
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
    }

    fun updateServiceUuid(uuid: String) {
        viewModelScope.launch {
            blePreferences.setServiceUuid(uuid)
        }
    }

    fun updateWriteUuid(uuid: String) {
        viewModelScope.launch {
            blePreferences.setWriteCharUuid(uuid)
        }
    }

    fun updateNotifyUuid(uuid: String) {
        viewModelScope.launch {
            blePreferences.setNotifyCharUuid(uuid)
        }
    }

    fun toggleAutoReconnect() {
        viewModelScope.launch {
            blePreferences.setAutoReconnect(!_uiState.value.autoReconnect)
        }
    }
}
