package com.ghealth.tools.feature.demo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.ble.protocol.GhFuncFrame
import com.ghealth.tools.ble.protocol.GhFuncId
import com.ghealth.tools.core.model.FunctionMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class DemoUiState(
    val availableFunctions: List<FunctionMode> = FunctionMode.entries,
    val selectedFunction: FunctionMode? = null,
    val latestFrame: GhFuncFrame? = null,
    val waveformData: List<Float> = emptyList(),
    val isRecording: Boolean = false
)

@HiltViewModel
class DemoViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(DemoUiState())
    val uiState: StateFlow<DemoUiState> = _uiState.asStateFlow()

    private val waveformBuffer = RingBuffer(1024)

    fun selectFunction(function: FunctionMode) {
        _uiState.update { it.copy(selectedFunction = function) }
        waveformBuffer.clear()
    }

    fun onFrameReceived(frame: GhFuncFrame) {
        _uiState.update { it.copy(latestFrame = frame) }
        if (frame.rawdata.isNotEmpty()) {
            waveformBuffer.add(frame.rawdata[0].toFloat())
            _uiState.update { it.copy(waveformData = waveformBuffer.toList()) }
        }
    }

    fun toggleRecording() {
        _uiState.update { it.copy(isRecording = !it.isRecording) }
    }

    fun goBack() {
        _uiState.update { it.copy(selectedFunction = null, waveformData = emptyList()) }
        waveformBuffer.clear()
    }
}

class RingBuffer(private val capacity: Int) {
    private val data = FloatArray(capacity)
    private var head = 0
    private var size = 0

    fun add(value: Float) {
        data[head] = value
        head = (head + 1) % capacity
        if (size < capacity) size++
    }

    fun clear() {
        head = 0
        size = 0
    }

    fun toList(): List<Float> {
        if (size == 0) return emptyList()
        val result = FloatArray(size)
        val start = if (size < capacity) 0 else head
        for (i in 0 until size) {
            result[i] = data[(start + i) % capacity]
        }
        return result.toList()
    }
}
