package com.ghealth.tools.feature.demo

import androidx.lifecycle.ViewModel
import com.ghealth.tools.ble.protocol.GhFuncFrame
import com.ghealth.tools.ble.protocol.GhFuncId
import com.ghealth.tools.core.model.FunctionMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class FunctionData(
    val function: FunctionMode,
    val algorithmResult: String = "--",
    val frameCount: Int = 0
)

data class DemoUiState(
    val functionDataMap: Map<FunctionMode, FunctionData> = emptyMap(),
    val selectedFunction: FunctionMode? = null,
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
        val funcMode = frame.funcId.toFunctionMode() ?: return
        _uiState.update { state ->
            val current = state.functionDataMap[funcMode] ?: FunctionData(funcMode)
            val updated = current.copy(
                frameCount = current.frameCount + 1,
                algorithmResult = extractAlgorithmResult(frame)
            )
            state.copy(functionDataMap = state.functionDataMap + (funcMode to updated))
        }

        if (_uiState.value.selectedFunction == funcMode && frame.rawdata.isNotEmpty()) {
            waveformBuffer.add(frame.rawdata[0].toFloat())
            _uiState.update { it.copy(waveformData = waveformBuffer.toList()) }
        }
    }

    private fun extractAlgorithmResult(frame: GhFuncFrame): String {
        return if (frame.rawdata.isNotEmpty()) frame.rawdata[0].toString() else "--"
    }

    fun toggleRecording() {
        _uiState.update { it.copy(isRecording = !it.isRecording) }
    }

    fun goBack() {
        _uiState.update { it.copy(selectedFunction = null, waveformData = emptyList()) }
        waveformBuffer.clear()
    }

    private fun GhFuncId.toFunctionMode(): FunctionMode? = when (this) {
        GhFuncId.ADT -> FunctionMode.ADT
        GhFuncId.HR -> FunctionMode.HR
        GhFuncId.HRV -> FunctionMode.HRV
        GhFuncId.SPO2 -> FunctionMode.SPO2
        GhFuncId.NADT_GREEN -> FunctionMode.NADT_GREEN
        GhFuncId.NADT_IR -> FunctionMode.NADT_IR
        GhFuncId.TEST1 -> FunctionMode.TEST1
        GhFuncId.TEST2 -> FunctionMode.TEST2
        GhFuncId.EVK -> FunctionMode.EVK
        GhFuncId.ECG -> FunctionMode.ECG
        GhFuncId.GSR -> FunctionMode.GSR
        GhFuncId.BIA -> FunctionMode.BIA
        else -> null
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
