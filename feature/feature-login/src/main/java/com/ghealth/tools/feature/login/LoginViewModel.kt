package com.ghealth.tools.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.core.datastore.BlePreferences
import com.ghealth.tools.core.model.DeviceType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val blePreferences: BlePreferences,
) : ViewModel() {

    private val _selectedChip = MutableStateFlow(DeviceType.GH3036)
    val selectedChip: StateFlow<DeviceType> = _selectedChip.asStateFlow()

    init {
        viewModelScope.launch {
            blePreferences.selectedChip.collect { chipName ->
                _selectedChip.value = DeviceType.entries.find { it.chipName == chipName }
                    ?: DeviceType.GH3036
            }
        }
    }

    fun selectChip(chip: DeviceType) {
        _selectedChip.value = chip
        viewModelScope.launch {
            blePreferences.setSelectedChip(chip.chipName)
        }
    }
}
