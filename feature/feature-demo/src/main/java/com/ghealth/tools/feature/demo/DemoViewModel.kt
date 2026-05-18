package com.ghealth.tools.feature.demo

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.ble.connection.BleConnectionManager
import com.ghealth.tools.ble.connection.DeviceRole
import com.ghealth.tools.ble.protocol.gh3036.GhFuncFrame
import com.ghealth.tools.ble.protocol.gh3036.GhFuncId
import com.ghealth.tools.core.model.FunctionMode
import com.ghealth.tools.core.storage.DataRecorder
import com.ghealth.tools.core.storage.DeviceRole as StorageDeviceRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
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
    private val dataRecorder: DataRecorder
) : ViewModel() {

    private val _uiState = MutableStateFlow(DemoUiState())
    val uiState: StateFlow<DemoUiState> = _uiState.asStateFlow()

    private val perFunctionBuffers = mutableMapOf<FunctionMode, MultiChannelRingBuffer>()
    private var currentRecordingDevice: String? = null
    private var autoRecordingStopped = false
    private val lastColumnValues = mutableMapOf<FunctionMode, MutableMap<String, Any?>>()
    private val recordingModes = mutableSetOf<FunctionMode>()

    private val baseDir: File by lazy {
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "GHealthTools"
        )
    }

    init {
        viewModelScope.launch {
            connectionManager.ghFrameFlow.collect { (address, frame) ->
                onFrameReceived(address, frame)
            }
        }
    }

    fun selectFunction(function: FunctionMode) {
        val buffer = perFunctionBuffers[function]
        val chCount = buffer?.getMaxChannelCount() ?: 0
        _uiState.update {
            it.copy(
                selectedFunction = function,
                selectedChannel = 0,
                channelCount = chCount,
                waveformData = buffer?.getChannel(0) ?: emptyList()
            )
        }
    }

    fun selectChannel(channel: Int) {
        val buffer = _uiState.value.selectedFunction?.let { perFunctionBuffers[it] }
        _uiState.update {
            it.copy(
                selectedChannel = channel,
                waveformData = buffer?.getChannel(channel) ?: emptyList()
            )
        }
    }

    private fun onFrameReceived(deviceAddress: String, frame: GhFuncFrame) {
        val funcMode = frame.funcId.toFunctionMode() ?: return
        _uiState.update { state ->
            val current = state.functionDataMap[funcMode] ?: FunctionData(funcMode)
            val updated = current.copy(
                frameCount = current.frameCount + 1,
                algorithmResult = extractAlgorithmResult(frame)
            )
            state.copy(functionDataMap = state.functionDataMap + (funcMode to updated))
        }

        if (frame.rawdata.isNotEmpty()) {
            val buffer = perFunctionBuffers.getOrPut(funcMode) {
                MultiChannelRingBuffer(maxChannels = 32, capacity = BUFFER_CAPACITY)
            }
            buffer.addFrame(frame.rawdata)

            if (_uiState.value.selectedFunction == funcMode) {
                val ch = _uiState.value.selectedChannel
                _uiState.update {
                    it.copy(
                        waveformData = buffer.getChannel(ch),
                        channelCount = buffer.getMaxChannelCount()
                    )
                }
            }
        }

        if (!autoRecordingStopped) {
            ensureRecording(deviceAddress, funcMode)
        }
        if (dataRecorder.isRecording(currentRecordingDevice ?: deviceAddress, funcMode.name)) {
            dataRecorder.writeFrame(currentRecordingDevice ?: deviceAddress, funcMode.name, frame.toColumnMap(funcMode))
        }
    }

    private fun ensureRecording(deviceAddress: String, funcMode: FunctionMode) {
        if (recordingModes.contains(funcMode)) return
        val cfg = connectionManager.testConfig.value ?: return
        if (recordingModes.isEmpty()) {
            connectionManager.resetFrameDecoders()
        }
        val devices = connectionManager.devices.value
        val masterDevice = devices.values.find {
            it.role == DeviceRole.MASTER &&
            it.state == com.ghealth.tools.core.model.ConnectionState.CONNECTED
        }
        if (masterDevice != null) {
            currentRecordingDevice = masterDevice.address
            dataRecorder.startRecording(
                baseDir = baseDir,
                deviceAddress = masterDevice.address,
                deviceRole = StorageDeviceRole.MASTER,
                deviceName = masterDevice.name ?: "Unknown",
                chip = "gh3036",
                mode = funcMode.name,
                scenario = cfg.scenario.name,
                tester = cfg.testerName.takeIf { it.isNotBlank() } ?: "unknown"
            )
            recordingModes.add(funcMode)
            _uiState.update { it.copy(isRecording = true) }
        }
    }

    private fun GhFuncFrame.toColumnMap(funcMode: FunctionMode): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val cache = lastColumnValues.getOrPut(funcMode) { mutableMapOf() }
        map["TimeStamp"] = timestamp
        map["FRAME_ID"] = frameCnt

        putCached(map, cache, "ACCX", gsData.getOrNull(0)?.takeIf { gsData.size > 0 })
        putCached(map, cache, "ACCY", gsData.getOrNull(1)?.takeIf { gsData.size > 1 })
        putCached(map, cache, "ACCZ", gsData.getOrNull(2)?.takeIf { gsData.size > 2 })
        fillRangeCached(map, cache, "Ipd", 0, 31, phyValue)
        fillRangeCached(map, cache, "FLAG", 0, 7, flags)
        fillRangeCached(map, cache, "REF_RESULT", 0, 15, null)
        fillRangeCached(map, cache, "ALGO_RESULT", 0, 15, algoData)
        fillRangeCached(map, cache, "Rawdata", 0, 31, rawdata)
        fillRangeCached(map, cache, "AGC_INFO_CH", 0, 31, agcInfo)
        fillRangeCached(map, cache, "LED_INFO_CH", 0, 31, agcInfoHigh)
        putCached(map, cache, "GYRO_X", null)
        putCached(map, cache, "GYRO_Y", null)
        putCached(map, cache, "GYRO_Z", null)
        return map
    }

    private fun putCached(map: MutableMap<String, Any?>, cache: MutableMap<String, Any?>, key: String, value: Any?) {
        val effective = value ?: cache[key] ?: 0
        map[key] = effective
        cache[key] = effective
    }

    private fun fillRangeCached(
        map: MutableMap<String, Any?>, cache: MutableMap<String, Any?>,
        prefix: String, start: Int, end: Int, array: IntArray?
    ) {
        for (i in start..end) {
            val key = "$prefix$i"
            val value = if (array != null && i < array.size) array[i] else null
            val effective = value ?: cache[key] ?: 0
            map[key] = effective
            cache[key] = effective
        }
    }

    private fun extractAlgorithmResult(frame: GhFuncFrame): String {
        if (frame.algoData.isNotEmpty()) return frame.algoData[0].toString()
        if (frame.rawdata.isNotEmpty()) return frame.rawdata[0].toString()
        return "--"
    }

    fun toggleRecording() {
        val currentlyRecording = _uiState.value.isRecording
        if (currentlyRecording) {
            recordingModes.forEach { dataRecorder.stopRecording(currentRecordingDevice ?: "", it.name) }
            recordingModes.clear()
            currentRecordingDevice = null
            autoRecordingStopped = true
        } else {
            autoRecordingStopped = false
            connectionManager.resetFrameDecoders()
        }
        _uiState.update { it.copy(isRecording = !currentlyRecording) }
    }

    fun goBack() {
        _uiState.update { it.copy(selectedFunction = null, waveformData = emptyList(), channelCount = 0, selectedChannel = 0) }
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

    companion object {
        private const val BUFFER_CAPACITY = 500
    }
}

class MultiChannelRingBuffer(private val maxChannels: Int, private val capacity: Int) {
    private val channels = Array(maxChannels) { FloatArray(capacity) }
    private var head = 0
    private var size = 0
    private var maxChannelIdx = -1

    fun addFrame(rawdata: IntArray) {
        val activeChannels = rawdata.size.coerceAtMost(maxChannels)
        for (ch in 0 until activeChannels) {
            channels[ch][head] = rawdata[ch].toFloat()
        }
        if (activeChannels - 1 > maxChannelIdx) {
            maxChannelIdx = activeChannels - 1
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

    fun getMaxChannelCount(): Int = maxChannelIdx + 1

    fun clearAll() {
        head = 0
        size = 0
        maxChannelIdx = -1
    }
}
