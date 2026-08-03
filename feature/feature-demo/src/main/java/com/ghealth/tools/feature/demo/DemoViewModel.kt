package com.ghealth.tools.feature.demo

import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.ble.connection.BleConnectionManager
import com.ghealth.tools.ble.connection.DeviceRole
import com.ghealth.tools.ble.protocol.gh3036.GhFuncFrame
import com.ghealth.tools.ble.protocol.gh3036.GhFuncId
import com.ghealth.tools.core.datastore.BlePreferences
import com.ghealth.tools.core.model.DeviceType
import com.ghealth.tools.core.model.FunctionMode
import com.ghealth.tools.core.model.TestConfig
import com.ghealth.tools.core.model.TestScenario
import com.ghealth.tools.core.storage.RecordingManager
import com.ghealth.tools.core.storage.DeviceRole as StorageDeviceRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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

/** 仅统计可见区域(最后 displayWidth 个点)的波形统计值;数据为空返回 null。 */
internal fun computeVisibleStats(data: List<Float>, displayWidth: Int): WaveformStats? {
    if (data.isEmpty()) return null
    val windowed = if (data.size > displayWidth) data.takeLast(displayWidth) else data
    val max = windowed.max()
    val min = windowed.min()
    val avg = windowed.sum() / windowed.size
    val diff = max - min
    return WaveformStats(max = max, min = min, avg = avg, diff = diff)
}

data class ManualCompareDevice(
    val name: String,
    val spo2: Float? = null
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
    val masterAlgoResult: AlgorithmResult = AlgorithmResult.None,
    val slaveAlgoResult: AlgorithmResult? = null,
    val testerName: String = "",
    val scenario: String = "",
    val testRound: Int = 0,
    val lastTestScenario: TestScenario = TestScenario.RESTING,
    val manualCompareDevices: List<ManualCompareDevice> = emptyList(),
    val showAddCompareDialog: Boolean = false,
    val editingCompareDeviceIndex: Int? = null,
    val showRestartConfigDialog: Boolean = false,
    /** 每个功能模式当前选中的显示宽度(数据点数),首次使用时由 DisplayWidthConfig 填入默认值。 */
    val displayWidths: Map<FunctionMode, Int> = emptyMap()
)

/** 当前选中功能模式的显示宽度;未选中或未初始化时返回 125 作为兜底。 */
val DemoUiState.currentDisplayWidth: Int
    get() = selectedFunction
        ?.let { displayWidths[it] ?: DisplayWidthConfig.defaultFor(it) }
        ?: 125

@HiltViewModel
class DemoViewModel @Inject constructor(
    private val connectionManager: BleConnectionManager,
    private val recordingManager: RecordingManager,
    private val blePreferences: BlePreferences,
    @Named("app_version") private val appVersion: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(DemoUiState())
    val uiState: StateFlow<DemoUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            blePreferences.effectiveChip.map { chipName ->
                DeviceType.entries.find { it.chipName == chipName } ?: DeviceType.GH3036
            }.collect { deviceType ->
                _uiState.update { it.copy(chipType = deviceType) }
            }
        }
    }

    private val perFunctionBuffers = mutableMapOf<FunctionMode, MultiChannelRingBuffer>()
    private val perFunctionPhyBuffers = mutableMapOf<FunctionMode, MultiChannelRingBuffer>()
    private val perFunctionScalarBuffers = mutableMapOf<FunctionMode, MultiChannelRingBuffer>()
    private var autoRecordingStopped = false
    private val lastColumnValues = mutableMapOf<FunctionMode, MutableMap<String, Any?>>()
    private val algoNonZeroSeen = mutableMapOf<String, Boolean>()
    private val lastAlgoResultsByRole = mutableMapOf<FunctionMode, MutableMap<DeviceRole, AlgorithmResult>>()

    init {
        viewModelScope.launch {
            connectionManager.ghFrameFlow.collect { (address, frame) ->
                try {
                    onFrameReceived(address, frame)
                } catch (e: Exception) {
                    Log.e("DemoViewModel", "Error processing GH frame from $address", e)
                }
            }
        }
        viewModelScope.launch {
            connectionManager.heartRateResults.collect { hrMap ->
                try {
                    onHeartRateResultsChanged(hrMap)
                } catch (e: Exception) {
                    Log.e("DemoViewModel", "Error processing heart rate results", e)
                }
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
                        testRound = config?.testRound ?: 0,
                        lastTestScenario = config?.scenario ?: TestScenario.RESTING
                    )
                }
            }
        }
        viewModelScope.launch {
            connectionManager.devices.collect { devices ->
                if (devices.isEmpty()) {
                    autoRecordingStopped = false
                }
                val hasSlave = devices.values.any {
                    it.role == DeviceRole.SLAVE && it.state == com.ghealth.tools.core.model.ConnectionState.CONNECTED
                }
                if (!hasSlave) {
                    lastAlgoResultsByRole.values.forEach { it.remove(DeviceRole.SLAVE) }
                }
                _uiState.update { state ->
                    val selectedFunc = state.selectedFunction
                    val roleResults = if (selectedFunc != null) lastAlgoResultsByRole[selectedFunc] else null
                    val slaveAlgo = if (hasSlave) {
                        roleResults?.get(DeviceRole.SLAVE) ?: AlgorithmResult.None
                    } else {
                        null
                    }
                    state.copy(slaveAlgoResult = slaveAlgo)
                }
            }
        }
    }

    private fun resetAllData() {
        _uiState.update { it.copy(functionDataMap = emptyMap(), masterAlgoResult = AlgorithmResult.None, slaveAlgoResult = null) }
        perFunctionBuffers.clear()
        perFunctionPhyBuffers.clear()
        perFunctionScalarBuffers.clear()
        lastColumnValues.clear()
        algoNonZeroSeen.clear()
        lastAlgoResultsByRole.clear()
    }

    fun selectFunction(function: FunctionMode) {
        val chipType = _uiState.value.chipType
        val defaultCols = defaultColumnsForChip(chipType)
        val w1Data = getColumnData(function, defaultCols.first)
        val w2Data = getColumnData(function, defaultCols.second)
        val roleResults = lastAlgoResultsByRole[function] ?: emptyMap()
        val width = _uiState.value.displayWidths[function] ?: DisplayWidthConfig.defaultFor(function)
        _uiState.update {
            it.copy(
                selectedFunction = function,
                waveform1Column = defaultCols.first,
                waveform2Column = defaultCols.second,
                waveform1Data = w1Data,
                waveform2Data = w2Data,
                waveform1Stats = computeStats(w1Data),
                waveform2Stats = computeStats(w2Data),
                frameIds = getFrameIds(function),
                masterAlgoResult = roleResults[DeviceRole.MASTER] ?: AlgorithmResult.None,
                slaveAlgoResult = roleResults[DeviceRole.SLAVE],
                displayWidths = it.displayWidths + (function to width)
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

    /** 切换当前选中功能模式的显示宽度(数据点数)。 */
    fun selectDisplayWidth(width: Int) {
        require(DisplayWidthConfig.OPTIONS.contains(width)) {
            "不支持的显示宽度: $width, 可选: ${DisplayWidthConfig.OPTIONS}"
        }
        val func = _uiState.value.selectedFunction ?: return
        _uiState.update {
            it.copy(displayWidths = it.displayWidths + (func to width))
        }
    }

    fun goBack() {
        _uiState.update { it.copy(selectedFunction = null) }
    }

    fun showAddCompareDialog() {
        _uiState.update { it.copy(showAddCompareDialog = true) }
    }

    fun hideAddCompareDialog() {
        _uiState.update { it.copy(showAddCompareDialog = false) }
    }

    fun addManualCompareDevice(name: String) {
        _uiState.update { state ->
            state.copy(
                manualCompareDevices = state.manualCompareDevices + ManualCompareDevice(name = name),
                showAddCompareDialog = false
            )
        }
    }

    fun startEditCompareDevice(index: Int) {
        _uiState.update { it.copy(editingCompareDeviceIndex = index) }
    }

    fun stopEditCompareDevice() {
        _uiState.update { it.copy(editingCompareDeviceIndex = null) }
    }

    fun updateManualCompareSpo2(index: Int, spo2: Float?) {
        _uiState.update { state ->
            val updated = state.manualCompareDevices.toMutableList()
            if (index in updated.indices) {
                updated[index] = updated[index].copy(spo2 = spo2)
            }
            state.copy(manualCompareDevices = updated)
        }
        recordingManager.updateCompareSpo2(index, spo2)
    }

    fun removeManualCompareDevice(index: Int) {
        _uiState.update { state ->
            state.copy(manualCompareDevices = state.manualCompareDevices.toMutableList().also {
                if (index in it.indices) it.removeAt(index)
            })
        }
        // Rebuild SPO2 buffer with re-indexed values
        val spo2s = _uiState.value.manualCompareDevices
            .mapIndexedNotNull { i, d -> d.spo2?.let { i to it } }
            .toMap()
        recordingManager.updateAllCompareSpo2(spo2s)
    }

    private fun onFrameReceived(deviceAddress: String, frame: GhFuncFrame) {
        val funcMode = frame.funcId.toFunctionMode() ?: return

        detectChipType()

        val devicesSnapshot = connectionManager.devices.value
        val role = devicesSnapshot[deviceAddress]?.role ?: DeviceRole.MASTER
        val roleResults = lastAlgoResultsByRole.getOrPut(funcMode) { mutableMapOf() }
        val newResult = parseAlgorithmResult(funcMode, frame.algoData)
        if (newResult.hasData) {
            roleResults[role] = newResult
        }
        val masterResult = roleResults[DeviceRole.MASTER] ?: AlgorithmResult.None
        val slaveResult = roleResults[DeviceRole.SLAVE]

        _uiState.update { state ->
            val current = state.functionDataMap[funcMode] ?: FunctionData(funcMode)
            val updated = current.copy(
                frameCount = current.frameCount + 1,
                algorithmResult = masterResult
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
                    frameIds = getFrameIds(funcMode),
                    masterAlgoResult = masterResult,
                    slaveAlgoResult = slaveResult ?: it.slaveAlgoResult
                )
            }
        }

        if (recordingManager.isSessionActive.value) {
            val role = when (devicesSnapshot[deviceAddress]?.role) {
                DeviceRole.MASTER -> StorageDeviceRole.MASTER
                DeviceRole.SLAVE -> StorageDeviceRole.SLAVE
                DeviceRole.COMPARE -> StorageDeviceRole.COMPARE
                null -> StorageDeviceRole.MASTER
            }
            val chipType = _uiState.value.chipType
            recordingManager.writeFrame(deviceAddress, funcMode.name, frame.toColumnMap(funcMode, chipType), role)
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

    private fun GhFuncFrame.toColumnMap(funcMode: FunctionMode, chipType: DeviceType): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val cache = lastColumnValues.getOrPut(funcMode) { mutableMapOf() }
        map["TimeStamp"] = timestamp
        map["FRAME_ID"] = frameCnt

        putCached(map, cache, "ACCX", gsData.getOrNull(0)?.takeIf { gsData.size > 0 })
        putCached(map, cache, "ACCY", gsData.getOrNull(1)?.takeIf { gsData.size > 1 })
        putCached(map, cache, "ACCZ", gsData.getOrNull(2)?.takeIf { gsData.size > 2 })

        when (chipType) {
            DeviceType.GH3036 -> {
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
            }
            DeviceType.GH3220, DeviceType.GH3300 -> {
                fillRangeCached(map, cache, "CH", 0, 31, rawdata)
                fillRangeCached(map, cache, "FLAG", 0, 7, flags)
                fillRangeCached(map, cache, "REF_RESULT", 0, 15, null)
                fillRangeCached(map, cache, "ALGO_RESULT", 0, 15, algoData)
                fillRangeCached(map, cache, "AGC_INFO_CH", 0, 31, agcInfo)
                fillRangeCached(map, cache, "AMB_CH", 0, 15, phyValue)
                putCached(map, cache, "GYRO_X", null)
                putCached(map, cache, "GYRO_Y", null)
                putCached(map, cache, "GYRO_Z", null)
                // CH16-31 is a literal column name (not expanded)
                putCached(map, cache, "CH16-31", null)
                fillRangeCached(map, cache, "CAP_CH", 0, 3, null)
                fillRangeCached(map, cache, "TEMP_CH", 0, 3, null)
            }
        }
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
        // Offset compare device keys: 0→2, 1→3, etc. to leave room for Master(0) and Slave(1)
        val displayMap = hrMap.mapKeys { it.key + 2 }
        _uiState.update { it.copy(compareHrResults = displayMap) }
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
            // Show config dialog for tester info before starting
            _uiState.update { it.copy(showRestartConfigDialog = true) }
        }
    }

    fun confirmRestartRecording(config: TestConfig) {
        connectionManager.setTestConfig(config)
        autoRecordingStopped = false
        connectionManager.resetFrameDecoders()
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
                chip = _uiState.value.chipType.chipName,
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
        _uiState.update { it.copy(showRestartConfigDialog = false) }
    }

    fun cancelRestartRecording() {
        _uiState.update { it.copy(showRestartConfigDialog = false) }
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
            "Ipd" -> {
                val buffer = perFunctionPhyBuffers[funcMode] ?: return emptyList()
                buffer.getChannel(index)
            }
            "CH" -> {
                // GH3036 = no CH columns; GH3220/GH3300 = CH from rawdata
                val buffer = perFunctionBuffers[funcMode] ?: return emptyList()
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
        GhFuncId.HSM -> FunctionMode.HSM
        GhFuncId.FPBP -> FunctionMode.FPBP
        GhFuncId.PWA -> FunctionMode.PWA
        GhFuncId.PWTT -> FunctionMode.PWTT
        GhFuncId.BT -> FunctionMode.BT
        GhFuncId.RESP -> FunctionMode.RESP
        GhFuncId.AF -> FunctionMode.AF
        GhFuncId.LEAD -> FunctionMode.LEAD
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
