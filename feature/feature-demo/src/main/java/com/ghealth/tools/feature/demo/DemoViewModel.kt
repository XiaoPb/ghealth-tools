package com.ghealth.tools.feature.demo

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.ble.connection.BleConnectionManager
import com.ghealth.tools.ble.connection.DeviceRole
import com.ghealth.tools.ble.protocol.gh3036.GhFuncFrame
import com.ghealth.tools.ble.protocol.gh3036.GhFuncId
import com.ghealth.tools.core.model.DeviceType
import com.ghealth.tools.core.model.FunctionMode
import com.ghealth.tools.core.storage.RecordingManager
import com.ghealth.tools.core.storage.DeviceRole as StorageDeviceRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Named

data class FunctionData(
    val function: FunctionMode,
    val algorithmResult: AlgorithmResult = AlgorithmResult.None,
    val frameCount: Int = 0
)

data class WaveformStats(
    val max: Float,
    val min: Float,
    val avg: Float,
    val diff: Float
)

data class DemoUiState(
    val functionDataMap: Map<FunctionMode, FunctionData> = emptyMap(),
    val selectedFunction: FunctionMode? = null,
    val chipType: DeviceType = DeviceType.GH3036,
    val waveform1Data: List<Float> = emptyList(),
    val waveform2Data: List<Float> = emptyList(),
    val waveform1Column: String = "Ipd0",
    val waveform2Column: String = "Ipd1",
    val waveform1Stats: WaveformStats? = null,
    val waveform2Stats: WaveformStats? = null,
    val frameIds: List<Float> = emptyList(),
    val isRecording: Boolean = false,
    val compareHrResults: Map<Int, Int> = emptyMap(),
    val testerName: String = "",
    val scenario: String = "",
    val testRound: Int = 0
)

@HiltViewModel
class DemoViewModel @Inject constructor(
    private val connectionManager: BleConnectionManager,
    private val recordingManager: RecordingManager,
    @Named("app_version") private val appVersion: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(DemoUiState())
    val uiState: StateFlow<DemoUiState> = _uiState.asStateFlow()

    private val perFunctionBuffers = mutableMapOf<FunctionMode, MultiChannelRingBuffer>()
    private val perFunctionPhyBuffers = mutableMapOf<FunctionMode, MultiChannelRingBuffer>()
    private val perFunctionScalarBuffers = mutableMapOf<FunctionMode, MultiChannelRingBuffer>()
    private var autoRecordingStopped = false
    private val lastColumnValues = mutableMapOf<FunctionMode, MutableMap<String, Any?>>()
    private val algoNonZeroSeen = mutableMapOf<String, Boolean>()
    private val lastAlgoResults = mutableMapOf<FunctionMode, AlgorithmResult>()

    init {
        viewModelScope.launch {
            connectionManager.ghFrameFlow.collect { (address, frame) ->
                onFrameReceived(address, frame)
            }
        }
        viewModelScope.launch {
            connectionManager.heartRateResults.collect { hrMap ->
                onHeartRateResultsChanged(hrMap)
            }
        }
        viewModelScope.launch {
            recordingManager.isSessionActive.collect { active ->
                if (active) resetAllData()
                _uiState.update { it.copy(isRecording = active) }
            }
        }
        viewModelScope.launch {
            connectionManager.testConfig.collect { config ->
                _uiState.update {
                    it.copy(
                        testerName = config?.testerName ?: "",
                        scenario = config?.scenario?.displayName ?: "",
                        testRound = config?.testRound ?: 0
                    )
                }
            }
        }
        viewModelScope.launch {
            connectionManager.devices.collect { devices ->
                if (devices.isEmpty()) {
                    autoRecordingStopped = false
                }
            }
        }
    }

    private fun resetAllData() {
        _uiState.update { it.copy(functionDataMap = emptyMap()) }
        perFunctionBuffers.clear()
        perFunctionPhyBuffers.clear()
        perFunctionScalarBuffers.clear()
        lastColumnValues.clear()
        algoNonZeroSeen.clear()
        lastAlgoResults.clear()
    }

    fun selectFunction(function: FunctionMode) {
        val chipType = _uiState.value.chipType
        val defaultCols = defaultColumnsForChip(chipType)
        val w1Data = getColumnData(function, defaultCols.first)
        val w2Data = getColumnData(function, defaultCols.second)
        _uiState.update {
            it.copy(
                selectedFunction = function,
                waveform1Column = defaultCols.first,
                waveform2Column = defaultCols.second,
                waveform1Data = w1Data,
                waveform2Data = w2Data,
                waveform1Stats = computeStats(w1Data),
                waveform2Stats = computeStats(w2Data),
                frameIds = getFrameIds(function)
            )
        }
    }

    fun selectWaveform1Column(column: String) {
        val funcMode = _uiState.value.selectedFunction ?: return
        val data = getColumnData(funcMode, column)
        _uiState.update {
            it.copy(
                waveform1Column = column,
                waveform1Data = data,
                waveform1Stats = computeStats(data)
            )
        }
    }

    fun selectWaveform2Column(column: String) {
        val funcMode = _uiState.value.selectedFunction ?: return
        val data = getColumnData(funcMode, column)
        _uiState.update {
            it.copy(
                waveform2Column = column,
                waveform2Data = data,
                waveform2Stats = computeStats(data)
            )
        }
    }

    fun goBack() {
        _uiState.update { it.copy(selectedFunction = null) }
    }

    private fun onFrameReceived(deviceAddress: String, frame: GhFuncFrame) {
        val funcMode = frame.funcId.toFunctionMode() ?: return

        detectChipType()

        val newResult = parseAlgorithmResult(funcMode, frame.algoData)
        val displayResult = if (newResult.hasData) {
            lastAlgoResults[funcMode] = newResult
            newResult
        } else {
            lastAlgoResults[funcMode] ?: AlgorithmResult.None
        }

        _uiState.update { state ->
            val current = state.functionDataMap[funcMode] ?: FunctionData(funcMode)
            val updated = current.copy(
                frameCount = current.frameCount + 1,
                algorithmResult = displayResult
            )
            state.copy(functionDataMap = state.functionDataMap + (funcMode to updated))
        }

        if (frame.rawdata.isNotEmpty()) {
            val buffer = perFunctionBuffers.getOrPut(funcMode) {
                MultiChannelRingBuffer(maxChannels = 32, capacity = BUFFER_CAPACITY)
            }
            buffer.addFrame(frame.rawdata)
        }

        if (frame.phyValue.isNotEmpty()) {
            val phyBuffer = perFunctionPhyBuffers.getOrPut(funcMode) {
                MultiChannelRingBuffer(maxChannels = 32, capacity = BUFFER_CAPACITY)
            }
            phyBuffer.addFrame(frame.phyValue)
        }

        val scalarBuffer = perFunctionScalarBuffers.getOrPut(funcMode) {
            MultiChannelRingBuffer(maxChannels = 4, capacity = BUFFER_CAPACITY)
        }
        scalarBuffer.addFrame(
            intArrayOf(
                frame.gsData.getOrNull(0) ?: 0,
                frame.gsData.getOrNull(1) ?: 0,
                frame.gsData.getOrNull(2) ?: 0,
                frame.frameCnt
            )
        )

        val selectedFunc = _uiState.value.selectedFunction
        if (selectedFunc == funcMode) {
            val w1Col = _uiState.value.waveform1Column
            val w2Col = _uiState.value.waveform2Column
            val w1Data = getColumnData(funcMode, w1Col)
            val w2Data = getColumnData(funcMode, w2Col)
            _uiState.update {
                it.copy(
                    waveform1Data = w1Data,
                    waveform2Data = w2Data,
                    waveform1Stats = computeStats(w1Data),
                    waveform2Stats = computeStats(w2Data),
                    frameIds = getFrameIds(funcMode)
                )
            }
        }

        if (recordingManager.isSessionActive.value) {
            val role = when (connectionManager.devices.value[deviceAddress]?.role) {
                DeviceRole.MASTER -> StorageDeviceRole.MASTER
                DeviceRole.SLAVE -> StorageDeviceRole.SLAVE
                DeviceRole.COMPARE -> StorageDeviceRole.COMPARE
                null -> StorageDeviceRole.MASTER
            }
            recordingManager.writeFrame(deviceAddress, funcMode.name, frame.toColumnMap(funcMode), role)
        }
    }

    private fun detectChipType() {
        val devices = connectionManager.devices.value
        val masterDevice = devices.values.find {
            it.role == DeviceRole.MASTER &&
            it.state == com.ghealth.tools.core.model.ConnectionState.CONNECTED
        }
        val detectedType = masterDevice?.deviceType ?: DeviceType.GH3036
        if (_uiState.value.chipType != detectedType) {
            _uiState.update { it.copy(chipType = detectedType) }
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
        val isAlgoField = prefix == "ALGO_RESULT"
        for (i in start..end) {
            val key = "$prefix$i"
            val value = if (array != null && i < array.size) array[i] else null
            val effective = if (isAlgoField && value != null) {
                if (value != 0) {
                    algoNonZeroSeen[key] = true
                    value
                } else if (algoNonZeroSeen[key] == true) {
                    cache[key] ?: 0
                } else {
                    0
                }
            } else {
                value ?: cache[key] ?: 0
            }
            map[key] = effective
            cache[key] = effective
        }
    }

    private fun onHeartRateResultsChanged(hrMap: Map<Int, Int>) {
        _uiState.update { it.copy(compareHrResults = hrMap) }
        if (recordingManager.isSessionActive.value) {
            for ((index, hr) in hrMap) {
                recordingManager.updateCompareHr(index, hr)
            }
        }
    }


    fun toggleRecording() {
        val currentlyRecording = recordingManager.isSessionActive.value
        if (currentlyRecording) {
            autoRecordingStopped = true
            connectionManager.notifyRecordingStopped()
            viewModelScope.launch { recordingManager.endSession() }
        } else {
            autoRecordingStopped = false
            connectionManager.resetFrameDecoders()
            val config = connectionManager.testConfig.value
            if (config != null) {
                val devices = connectionManager.devices.value
                val masterDevice = devices.values.find {
                    it.role == DeviceRole.MASTER && it.state == com.ghealth.tools.core.model.ConnectionState.CONNECTED
                }
                val slaveDevices = devices.values.filter {
                    it.role == DeviceRole.SLAVE && it.state == com.ghealth.tools.core.model.ConnectionState.CONNECTED
                }
                if (masterDevice != null) {
                    recordingManager.startSession(
                        config = config,
                        masterDeviceName = masterDevice.name ?: "Unknown",
                        masterDeviceAddress = masterDevice.address,
                        slaveDevices = slaveDevices.associate { it.address to (it.name ?: "Unknown") },
                        compareDeviceNames = devices.values
                            .filter { it.role == DeviceRole.COMPARE && it.state == com.ghealth.tools.core.model.ConnectionState.CONNECTED }
                            .map { it.name ?: it.address },
                        compareDeviceAddresses = devices.values
                            .filter { it.role == DeviceRole.COMPARE && it.state == com.ghealth.tools.core.model.ConnectionState.CONNECTED }
                            .map { it.address }
                    )
                }
            }
        }
    }

    private fun getColumnData(funcMode: FunctionMode, columnName: String): List<Float> {
        // Scalar columns (no numeric suffix)
        val scalarBuffer = perFunctionScalarBuffers[funcMode]
        when (columnName) {
            "ACCX" -> return scalarBuffer?.getChannel(0) ?: emptyList()
            "ACCY" -> return scalarBuffer?.getChannel(1) ?: emptyList()
            "ACCZ" -> return scalarBuffer?.getChannel(2) ?: emptyList()
            "FRAME_ID" -> return scalarBuffer?.getChannel(3) ?: emptyList()
        }

        val (prefix, index) = parseColumnName(columnName) ?: return emptyList()
        return when (prefix) {
            "Ipd", "CH" -> {
                val buffer = perFunctionPhyBuffers[funcMode] ?: return emptyList()
                buffer.getChannel(index)
            }
            "Rawdata" -> {
                val buffer = perFunctionBuffers[funcMode] ?: return emptyList()
                buffer.getChannel(index)
            }
            "ALGO_RESULT" -> emptyList()
            else -> emptyList()
        }
    }

    private fun getFrameIds(funcMode: FunctionMode): List<Float> {
        return perFunctionScalarBuffers[funcMode]?.getChannel(3) ?: emptyList()
    }

    private fun parseColumnName(name: String): Pair<String, Int>? {
        val regex = Regex("""^(Ipd|CH|Rawdata|ALGO_RESULT)(\d+)$""")
        val match = regex.find(name) ?: return null
        val prefix = match.groupValues[1]
        val index = match.groupValues[2].toIntOrNull() ?: return null
        return prefix to index
    }

    private fun computeStats(data: List<Float>): WaveformStats? {
        if (data.isEmpty()) return null
        val max = data.max()
        val min = data.min()
        val avg = data.sum() / data.size
        val diff = max - min
        return WaveformStats(max = max, min = min, avg = avg, diff = diff)
    }

    companion object {
        private const val BUFFER_CAPACITY = 500

        fun availableColumns(chipType: DeviceType): List<String> {
            val columns = mutableListOf<String>()
            columns.add("ACCX")
            columns.add("ACCY")
            columns.add("ACCZ")
            columns.add("FRAME_ID")
            when (chipType) {
                DeviceType.GH3036 -> {
                    for (i in 0..31) columns.add("Ipd$i")
                    for (i in 0..31) columns.add("Rawdata$i")
                }
                DeviceType.GH3220, DeviceType.GH3300 -> {
                    for (i in 0..31) columns.add("CH$i")
                }
            }
            for (i in 0..15) columns.add("ALGO_RESULT$i")
            return columns
        }

        fun defaultColumnsForChip(chipType: DeviceType): Pair<String, String> = when (chipType) {
            DeviceType.GH3036 -> "Ipd0" to "Ipd1"
            DeviceType.GH3220, DeviceType.GH3300 -> "CH0" to "CH1"
        }
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
