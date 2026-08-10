package com.ghealth.tools.feature.demo

import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.ble.connection.BleConnectionManager
import com.ghealth.tools.ble.connection.ConnectedDevice
import com.ghealth.tools.ble.connection.DeviceRole
import com.ghealth.tools.ble.connection.FirmwareVersionHolder
import com.ghealth.tools.ble.connection.Gh3220FrameAdapter
import com.ghealth.tools.ble.protocol.gh3036.AgcPhysicalCodec
import com.ghealth.tools.ble.protocol.gh3036.GhFuncFrame
import com.ghealth.tools.ble.protocol.gh3036.GhFuncId
import com.ghealth.tools.core.datastore.BlePreferences
import com.ghealth.tools.core.model.ConnectionState
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
    if (displayWidth <= 0) return null
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
    val displayWidths: Map<FunctionMode, Int> = emptyMap(),
    /** 当前选中功能模式「有数据」的可选列,随帧动态更新;无选中或无数据时为空。 */
    val availableColumns: List<String> = emptyList()
)

/** 当前选中功能模式的显示宽度;未选中或未初始化时返回 125 作为兜底。 */
val DemoUiState.currentDisplayWidth: Int
    get() = selectedFunction
        ?.let { displayWidths[it] ?: DisplayWidthConfig.defaultFor(it) }
        ?: 125

/**
 * 清空演示页累积的「接收数据」字段,保留用户偏好(选中功能、列选择、显示宽度、
 * 手动对比设备、测试信息、录制状态、对话框状态、对比设备心率)。
 *
 * 用于重新连接或重新开始录制时,避免上一次的数据影响本次分析。
 */
internal fun DemoUiState.clearReceivedData(): DemoUiState = copy(
    functionDataMap = emptyMap(),
    waveform1Data = emptyList(),
    waveform2Data = emptyList(),
    waveform1Stats = null,
    waveform2Stats = null,
    frameIds = emptyList(),
    masterAlgoResult = AlgorithmResult.None,
    slaveAlgoResult = null,
    availableColumns = emptyList()
)

/**
 * 判断是否发生主设备「重新连接」:主设备从未连接变为已连接(MASTER + CONNECTED 上升沿)。
 *
 * - 首次连接时 `wasMasterConnected=false`,触发清空(此时数据为空,清空无副作用)。
 * - 重连/换设备连接时清空上一次累积数据,避免影响本次分析。
 * - CONNECTING / 仅从设备 / 已持续连接 均不触发。
 *
 * @param wasMasterConnected 上一次设备快照中主设备是否已连接。
 * @param devices 当前设备快照。
 */
internal fun shouldClearOnMasterReconnect(
    wasMasterConnected: Boolean,
    devices: Map<String, ConnectedDevice>
): Boolean {
    val isMasterConnected = devices.values.any {
        it.role == DeviceRole.MASTER && it.state == ConnectionState.CONNECTED
    }
    return isMasterConnected && !wasMasterConnected
}

@HiltViewModel
class DemoViewModel @Inject constructor(
    private val connectionManager: BleConnectionManager,
    private val recordingManager: RecordingManager,
    private val blePreferences: BlePreferences,
    private val firmwareVersionHolder: FirmwareVersionHolder,
    @Named("app_version") private val appVersion: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(DemoUiState())
    val uiState: StateFlow<DemoUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            blePreferences.effectiveChip.map { chipName ->
                DeviceType.entries.find { it.chipName == chipName } ?: DeviceType.GH3036
            }.collect { deviceType ->
                if (_uiState.value.chipType != deviceType) {
                    Log.d("DemoViewModel", "Chip synced from effectiveChip: ${_uiState.value.chipType.chipName} -> ${deviceType.chipName}")
                }
                _uiState.update { it.copy(chipType = deviceType) }
            }
        }
    }

    private val buffers = FunctionDataBuffers(BUFFER_CAPACITY)
    private val frameDeduper = FrameDeduper()
    private var lastDevicesSnapshot: Map<String, ConnectedDevice> = emptyMap()
    private var autoRecordingStopped = false
    private val lastColumnValues = mutableMapOf<FunctionMode, MutableMap<String, Any?>>()
    private val algoNonZeroSeen = mutableMapOf<String, Boolean>()
    private val lastAlgoResultsByRole = mutableMapOf<FunctionMode, MutableMap<DeviceRole, AlgorithmResult>>()
    // ADT IDLE 回退：按 role 记录上一次非 IDLE 的 wearEvent，用于 IDLE 帧显示补偿
    private val lastNonIdleWearByRole = mutableMapOf<DeviceRole, Int>()
    // ADT detStatus UNKNOWN 回退：按 role 记录上一次非 UNKNOWN 的 detStatus
    private val lastNonUnknownDetByRole = mutableMapOf<DeviceRole, Int>()
    // 主设备连接状态:用于检测重新连接(从未连接 → 已连接)上升沿,触发演示数据清空
    private var wasMasterConnected = false

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
                val removedAddresses = lastDevicesSnapshot.keys - devices.keys
                removedAddresses.forEach { frameDeduper.removeAddress(it) }
                lastDevicesSnapshot = devices
                if (devices.isEmpty()) {
                    autoRecordingStopped = false
                }
                // 主设备重新连接(从未连接 → 已连接)时清空演示页累积数据,
                // 避免上一次的数据影响本次分析。首次连接时数据为空,清空无副作用。
                if (shouldClearOnMasterReconnect(wasMasterConnected, devices)) {
                    Log.d("DemoViewModel", "Master reconnected, clearing demo data")
                    resetAllData()
                }
                wasMasterConnected = devices.values.any {
                    it.role == DeviceRole.MASTER && it.state == ConnectionState.CONNECTED
                }
                val hasSlave = devices.values.any {
                    it.role == DeviceRole.SLAVE && it.state == ConnectionState.CONNECTED
                }
                if (!hasSlave) {
                    lastAlgoResultsByRole.values.forEach { it.remove(DeviceRole.SLAVE) }
                    lastNonIdleWearByRole.remove(DeviceRole.SLAVE)
                    lastNonUnknownDetByRole.remove(DeviceRole.SLAVE)
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
        _uiState.update { it.clearReceivedData() }
        buffers.clear()
        frameDeduper.clear()
        lastColumnValues.clear()
        algoNonZeroSeen.clear()
        lastAlgoResultsByRole.clear()
        lastNonIdleWearByRole.clear()
        lastNonUnknownDetByRole.clear()
    }

    fun selectFunction(function: FunctionMode) {
        val chipType = _uiState.value.chipType
        val defaultCols = defaultColumnsForChip(chipType)
        val w1Data = buffers.getColumn(function, defaultCols.first)
        val w2Data = buffers.getColumn(function, defaultCols.second)
        val roleResults = lastAlgoResultsByRole[function] ?: emptyMap()
        // 用目标功能的宽度,而非 currentDisplayWidth(此时 selectedFunction 尚未更新,currentDisplayWidth 会取到旧功能的宽度)
        val width = _uiState.value.displayWidths[function] ?: DisplayWidthConfig.defaultFor(function)
        _uiState.update {
            it.copy(
                selectedFunction = function,
                waveform1Column = defaultCols.first,
                waveform2Column = defaultCols.second,
                waveform1Data = w1Data,
                waveform2Data = w2Data,
                waveform1Stats = computeVisibleStats(w1Data, width),
                waveform2Stats = computeVisibleStats(w2Data, width),
                frameIds = buffers.frameIds(function),
                masterAlgoResult = roleResults[DeviceRole.MASTER] ?: AlgorithmResult.None,
                slaveAlgoResult = roleResults[DeviceRole.SLAVE],
                displayWidths = it.displayWidths + (function to width),
                availableColumns = buffers.availableColumns(function, chipType)
            )
        }
    }

    fun selectWaveform1Column(column: String) {
        val funcMode = _uiState.value.selectedFunction ?: return
        val data = buffers.getColumn(funcMode, column)
        val width = _uiState.value.currentDisplayWidth
        _uiState.update {
            it.copy(
                waveform1Column = column,
                waveform1Data = data,
                waveform1Stats = computeVisibleStats(data, width)
            )
        }
    }

    fun selectWaveform2Column(column: String) {
        val funcMode = _uiState.value.selectedFunction ?: return
        val data = buffers.getColumn(funcMode, column)
        val width = _uiState.value.currentDisplayWidth
        _uiState.update {
            it.copy(
                waveform2Column = column,
                waveform2Data = data,
                waveform2Stats = computeVisibleStats(data, width)
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
            it.copy(
                displayWidths = it.displayWidths + (func to width),
                waveform1Stats = computeVisibleStats(it.waveform1Data, width),
                waveform2Stats = computeVisibleStats(it.waveform2Data, width)
            )
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
        if (frameDeduper.isDuplicate(deviceAddress, funcMode, frame.frameCnt, frame.timestamp)) {
            return
        }

        detectChipType()

        val devicesSnapshot = connectionManager.devices.value
        val role = devicesSnapshot[deviceAddress]?.role ?: DeviceRole.MASTER
        val roleResults = lastAlgoResultsByRole.getOrPut(funcMode) { mutableMapOf() }
        val newResult = parseAlgorithmResult(funcMode, frame.algoData)
        // ADT: 对 wearEvent IDLE 与 detStatus UNKNOWN 应用历史值回退，避免界面闪烁
        val effectiveResult = applyAdtStateFallback(role, newResult)
        if (effectiveResult.hasData) {
            roleResults[role] = effectiveResult
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

        buffers.addFrame(funcMode, frame)

        val selectedFunc = _uiState.value.selectedFunction
        if (selectedFunc == funcMode) {
            val w1Col = _uiState.value.waveform1Column
            val w2Col = _uiState.value.waveform2Column
            val w1Data = buffers.getColumn(funcMode, w1Col)
            val w2Data = buffers.getColumn(funcMode, w2Col)
            val width = _uiState.value.currentDisplayWidth
            val cols = buffers.availableColumns(funcMode, _uiState.value.chipType)
            _uiState.update {
                // 列集合不变时复用旧引用,避免 ColumnSelectDialog 不必要重组;单次 update 减少发射
                it.copy(
                    availableColumns = if (cols != it.availableColumns) cols else it.availableColumns,
                    waveform1Data = w1Data,
                    waveform2Data = w2Data,
                    waveform1Stats = computeVisibleStats(w1Data, width),
                    waveform2Stats = computeVisibleStats(w2Data, width),
                    frameIds = buffers.frameIds(funcMode),
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
            val sessionChip = recordingManager.activeSessionChip
            if (sessionChip != null && sessionChip != chipType.chipName) {
                Log.w("DemoViewModel", "CSV chip mismatch: frame mapped as ${chipType.chipName} but session rule is $sessionChip (columns may be dropped/misaligned)")
            }
            recordingManager.writeFrame(deviceAddress, funcMode.name, frame.toColumnMap(funcMode, chipType), role)
        }
    }

    /**
     * ADT 状态回退：对 wearEvent IDLE 与 detStatus UNKNOWN 分别用该 role 的历史值补偿显示，
     * 避免界面在有效状态与默认状态间频繁闪烁。非 IDLE/UNKNOWN 帧更新历史。
     */
    private fun applyAdtStateFallback(role: DeviceRole, result: AlgorithmResult): AlgorithmResult {
        if (result !is AlgorithmResult.ADT) return result
        // wearEvent IDLE 回退
        val (newLastWear, effectiveWear) = AdtWearStateReducer.reduce(lastNonIdleWearByRole[role], result.wearEvent)
        if (newLastWear != null) lastNonIdleWearByRole[role] = newLastWear
        // detStatus UNKNOWN 回退
        val (newLastDet, effectiveDet) = AdtWearStateReducer.reduceDetState(lastNonUnknownDetByRole[role], result.detStatus)
        if (newLastDet != null) lastNonUnknownDetByRole[role] = newLastDet
        val wearChanged = effectiveWear != result.wearEvent
        val detChanged = effectiveDet != result.detStatus
        return if (wearChanged || detChanged) result.copy(wearEvent = effectiveWear, detStatus = effectiveDet) else result
    }

    private fun detectChipType() {
        val devices = connectionManager.devices.value
        val masterDevice = devices.values.find {
            it.role == DeviceRole.MASTER &&
            it.state == com.ghealth.tools.core.model.ConnectionState.CONNECTED
        }
        val detectedType = masterDevice?.deviceType ?: DeviceType.GH3036
        if (_uiState.value.chipType != detectedType) {
            val selected = _uiState.value.selectedFunction
            val cols = if (selected != null) buffers.availableColumns(selected, detectedType) else emptyList()
            Log.d("DemoViewModel", "Chip re-detected from master device: ${_uiState.value.chipType.chipName} -> ${detectedType.chipName} (deviceType=${masterDevice?.deviceType})")
            _uiState.update { it.copy(chipType = detectedType, availableColumns = cols) }
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
                // AGC_INFO_CH / LED_INFO_CH: 仅 GH3036，列值按物理量位域重新打包
                //   AGC_INFO_CH = gain|bg_cancel_level|dc_cancel_level|dc_cancel_code|led_current_sum
                //   LED_INFO_CH = led_current_drv0|led_current_drv1（均 0.1mA）
                //   详见 AgcPhysicalCodec 与 .claude/csv_rules/gh3036.yaml
                val (packedAgcInfo, packedLedInfo) = AgcPhysicalCodec.encodeColumns(agcInfo, agcInfoHigh)
                fillRangeCached(map, cache, "AGC_INFO_CH", 0, 31, packedAgcInfo)
                fillRangeCached(map, cache, "LED_INFO_CH", 0, 31, packedLedInfo)
                putCached(map, cache, "GYRO_X", null)
                putCached(map, cache, "GYRO_Y", null)
                putCached(map, cache, "GYRO_Z", null)
            }
            DeviceType.GH3220, DeviceType.GH3300 -> {
                // GH3220 分段：结果段 flag2 bit1(0x02)=首帧（frameCnt==0），即一次新测试开始；
                // RecordingManager 据此轮转 server CSV（NEW_TEST=true），不再按 FRAME_ID==0
                // （8 位帧计数每 256 帧自然回绕会误轮转）。
                map["NEW_TEST"] = Gh3220FrameAdapter.isNewTestFrame(this)
                fillRangeCached(map, cache, "CH", 0, 31, rawdata)
                fillRangeCached(map, cache, "FLAG", 0, 7, flags)
                fillRangeCached(map, cache, "REF_RESULT", 0, 15, null)
                fillRangeCached(map, cache, "ALGO_RESULT", 0, 15, algoData)
                fillRangeCached(map, cache, "AGC_INFO_CH", 0, 31, agcInfo)
                fillRangeCached(map, cache, "AMB_CH", 0, 15, phyValue)
                putCached(map, cache, "GYRO_X", gyro.getOrNull(0))
                putCached(map, cache, "GYRO_Y", gyro.getOrNull(1))
                putCached(map, cache, "GYRO_Z", gyro.getOrNull(2))
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
            // 先等待版本读取完成，再弹测试信息输入框
            viewModelScope.launch {
                firmwareVersionHolder.awaitVersionRead()
                _uiState.update { it.copy(showRestartConfigDialog = true) }
            }
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
            val sessionChip = masterDevice.deviceType?.chipName ?: _uiState.value.chipType.chipName
            Log.i("DemoViewModel", "Recording session started from demo page: chip=$sessionChip (masterDevice.deviceType=${masterDevice.deviceType}, uiState.chipType=${_uiState.value.chipType.chipName})")
            recordingManager.startSession(
                config = config,
                chip = sessionChip,
                masterDeviceName = masterDevice.name ?: "Unknown",
                masterDeviceAddress = masterDevice.address,
                slaveDevices = slaveDevices.associate { it.address to (it.name ?: "Unknown") },
                compareDeviceNames = devices.values
                    .filter { it.role == DeviceRole.COMPARE && it.state == com.ghealth.tools.core.model.ConnectionState.CONNECTED }
                    .map { it.name ?: it.address },
                compareDeviceAddresses = devices.values
                    .filter { it.role == DeviceRole.COMPARE && it.state == com.ghealth.tools.core.model.ConnectionState.CONNECTED }
                    .map { it.address },
                sdkVersion = firmwareVersionHolder.state.value.sdkVersion,
                hrVersion = firmwareVersionHolder.state.value.hrVersion,
                spo2Version = firmwareVersionHolder.state.value.spo2Version,
                nadtVersion = firmwareVersionHolder.state.value.nadtVersion,
                hrvVersion = firmwareVersionHolder.state.value.hrvVersion
            )
        }
        _uiState.update { it.copy(showRestartConfigDialog = false) }
    }

    fun cancelRestartRecording() {
        _uiState.update { it.copy(showRestartConfigDialog = false) }
    }

    companion object {
        private const val BUFFER_CAPACITY = 500

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
