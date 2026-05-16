package com.ghealth.tools.feature.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class DeviceInfoUiState(
    val isConnected: Boolean = false,
    val deviceName: String = "",
    val deviceAddress: String = "",
    val chipType: String = "",
    val algorithmVersion: String = "",
    val sdkVersion: String = "",
    val firmwareVersion: String = "",
    val hardwareVersion: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class DeviceInfoViewModel @Inject constructor(
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceInfoUiState())
    val uiState: StateFlow<DeviceInfoUiState> = _uiState.asStateFlow()

    fun refreshDeviceInfo() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        _uiState.update {
            it.copy(
                isLoading = false,
                isConnected = false,
                deviceName = "未连接",
                deviceAddress = "-",
                chipType = "-",
                algorithmVersion = "-",
                sdkVersion = "-",
                firmwareVersion = "-",
                hardwareVersion = "-"
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
