package com.ghealth.tools.feature.demo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.ble.connection.BleConnectionManager
import com.ghealth.tools.ble.protocol.gh3036.Gh3036FrameDecoder
import com.ghealth.tools.ble.protocol.gh3036.GhFuncFrame
import com.ghealth.tools.ble.protocol.gh3036.GhFuncId
import com.ghealth.tools.core.model.FunctionMode
import com.ghealth.tools.core.storage.DataRecorder
import com.ghealth.tools.core.storage.di.DataRecorderFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val isRecording: Boolean = false,
    val channelCount: Int = 0,
    val selectedChannel: Int = 0
)

@HiltViewModel
class DemoViewModel @Inject constructor(
    private val connectionManager: BleConnectionManager,
    private val recorderFactory: DataRecorderFactory
) : ViewModel() {

    private val _uiState = MutableStateFlow(DemoUiState())
    val uiState: StateFlow<DemoUiState> = _uiState.asStateFlow()

    private val waveformBuffers = MultiChannelRingBuffer(maxChannels = 32, capacity = 1024)
    private val gFrameDecoder = Gh3036FrameDecoder()
    private var recorder: DataRecorder? = null

    init {
        viewModelScope.launch {
            connectionManager.dataFlow.collect { (_, parseResult) ->
                if (parseResult.key == "G") {
                    val frames = gFrameDecoder.decode(parseResult.param)
                    frames.forEach { frame -> onFrameReceived(frame) }
                }
            }
        }
    }

    fun selectFunction(function: FunctionMode) {
        _uiState.update { it.copy(selectedFunction = function, selectedChannel = 0, channelCount = 0) }
        waveformBuffers.clearAll()
    }

    fun selectChannel(channel: Int) {
        _uiState.update { it.copy(selectedChannel = channel, waveformData = waveformBuffers.getChannel(channel)) }
    }

    private fun onFrameReceived(frame: GhFuncFrame) {
        val funcMode = frame.funcId.toFunctionMode() ?: return
        _uiState.update { state ->
            val current = state.functionDataMap[funcMode] ?: FunctionData(funcMode)
            val updated = current.copy(
                frameCount = current.frameCount + 1,
                algorithmResult = extractAlgorithmResult(frame)
            )
            state.copy(functionDataMap = state.functionDataMap + (funcMode to updated))
        }

        if (_uiState.value.selectedFunction == funcMode) {
            if (frame.rawdata.isNotEmpty()) {
                waveformBuffers.addFrame(frame.rawdata)
                val ch = _uiState.value.selectedChannel
                _uiState.update {
                    it.copy(
                        waveformData = waveformBuffers.getChannel(ch),
                        channelCount = frame.rawdata.size
                    )
                }
            }

            if (_uiState.value.isRecording) {
                recorder?.writeFrame(frame.toColumnMap())
            }
        }
    }

    private fun GhFuncFrame.toColumnMap(): Map<String, Any?> = buildMap {
        put("func_id", funcId.name)
        put("frame_cnt", frameCnt)
        put("timestamp", timestamp)
        for (i in rawdata.indices) put("rawdata_$i", rawdata[i])
        for (i in algoData.indices) put("algo_$i", algoData[i])
        put("slot_cfg", slotCfg)
    }

    private fun extractAlgorithmResult(frame: GhFuncFrame): String {
        if (frame.algoData.isNotEmpty()) return frame.algoData[0].toString()
        if (frame.rawdata.isNotEmpty()) return frame.rawdata[0].toString()
        return "--"
    }

    fun toggleRecording() {
        val currentlyRecording = _uiState.value.isRecording
        if (currentlyRecording) {
            recorder?.stop()
            recorder = null
        } else {
            val func = _uiState.value.selectedFunction ?: return
            recorder = recorderFactory.create("gh3036").also {
                it.start(mode = func.name)
            }
        }
        _uiState.update { it.copy(isRecording = !currentlyRecording) }
    }

    fun goBack() {
        recorder?.stop()
        recorder = null
        _uiState.update { it.copy(selectedFunction = null, waveformData = emptyList(), channelCount = 0, selectedChannel = 0, isRecording = false) }
        waveformBuffers.clearAll()
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

class MultiChannelRingBuffer(private val maxChannels: Int, private val capacity: Int) {
    private val channels = Array(maxChannels) { FloatArray(capacity) }
    private var head = 0
    private var size = 0

    fun addFrame(rawdata: IntArray) {
        for (ch in rawdata.indices.take(maxChannels)) {
            channels[ch][head] = rawdata[ch].toFloat()
        }
        head = (head + 1) % capacity
        if (size < capacity) size++
    }

    fun getChannel(channel: Int): List<Float> {
        if (size == 0 || channel >= maxChannels) return emptyList()
        val result = FloatArray(size)
        val start = if (size < capacity) 0 else head
        for (i in 0 until size) {
            result[i] = channels[channel][(start + i) % capacity]
        }
        return result.toList()
    }

    fun clearAll() {
        head = 0
        size = 0
    }
}
