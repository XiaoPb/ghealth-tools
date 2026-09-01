package com.ghealth.tools.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.core.datastore.BlePreferences
import com.ghealth.tools.core.datastore.SessionMode
import com.ghealth.tools.core.model.DeviceType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChipSelectionViewModel @Inject constructor(
    private val blePreferences: BlePreferences
) : ViewModel() {

    private val _selectedChip = MutableStateFlow(DeviceType.GH3036)
    val selectedChip: StateFlow<DeviceType> = _selectedChip.asStateFlow()

    init {
        viewModelScope.launch {
            val chipName = blePreferences.selectedChip.first()
            _selectedChip.value = DeviceType.entries.find { it.chipName == chipName }
                ?: DeviceType.GH3036
        }
    }

    fun selectChip(deviceType: DeviceType) {
        _selectedChip.value = deviceType
    }

    fun confirm(onSuccess: () -> Unit) {
        viewModelScope.launch {
            blePreferences.setSelectedChip(_selectedChip.value.chipName)
            blePreferences.setSessionMode(SessionMode.OFFLINE)
            blePreferences.clearSelectedProjectChip()
            onSuccess()
        }
    }
}
