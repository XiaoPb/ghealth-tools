package com.ghealth.tools.core.storage

import com.ghealth.tools.core.model.FunctionMode
import com.ghealth.tools.core.model.TestConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

data class WriteTask(
    val deviceAddress: String,
    val columnMap: Map<String, Any?>,
    val role: DeviceRole,
    val timestamp: Long
)

/**
 * 判断当前帧是否触发 server CSV 轮转（开启新文件）。
 * - 帧声明 NEW_TEST 列（GH3220/GH3300：结果段 flag2 bit1(0x02)=首帧，即一次新测试开始）时，
 *   以 NEW_TEST==true 为准，不再使用 FRAME_ID（8 位帧计数每 256 帧自然回绕，会误轮转）；
 * - 未声明（GH3036 等）时保持 FRAME_ID==0 轮转：首次不计，之后帧计数复位即轮转。
 */
internal fun shouldRotateServerFile(
    columnMap: Map<String, Any?>,
    frameZeroCounts: MutableMap<String, Int>,
    writerKey: String,
): Boolean {
    val newTest = columnMap["NEW_TEST"]
    if (newTest != null) {
        return newTest == true
    }
    val frameId = (columnMap["FRAME_ID"] as? Number)?.toInt() ?: -1
    if (frameId != 0) return false
    val count = frameZeroCounts.getOrDefault(writerKey, 0) + 1
    frameZeroCounts[writerKey] = count
    return count > 1
}

/**
 * 把比较设备（金标）实时值注入 server CSV 行。
 * - 心率: REF_RESULT0..4（对应比较设备 index）
 * - 血氧: REF_RESULT5..9（手动输入/实时推送，index 从 5 开始）
 * 列名保持 REF_RESULT 原样，金标设备名记录在首行 JSON（ref_result_devices）。
 */
internal fun injectCompareValues(
    serverValues: MutableMap<String, Any?>,
    compareHrs: Map<Int, Int>,
    compareSpo2s: Map<Int, Float>
) {
    for ((index, hr) in compareHrs) {
        serverValues["REF_RESULT$index"] = hr
    }
    for ((index, spo2) in compareSpo2s) {
        serverValues["REF_RESULT${5 + index}"] = spo2
    }
}

@Singleton
class RecordingManager @Inject constructor(
    @Named("storageBaseDir") private val baseDir: File,
    @Named("app_version") private val appVersion: String,
    @Named("phone_device") private val phoneDevice: String,
    private val csvUploadManager: CsvUploadManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val modeStates = ConcurrentHashMap<String, ModeState>()
    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private var sessionDate: Date = Date()
    private var currentConfig: SessionConfig? = null

    /** 当前会话使用的 chip（规则来源）。为 null 表示无活动会话。 */
    val activeSessionChip: String? get() = currentConfig?.chip
    private var currentProjectName: String = ""
    private var currentProjectId: Int = 0
    private var currentUsername: String = ""

    private data class SessionConfig(
        val scenario: String,
        val tester: String,
        val chip: String,
        val masterName: String,
        val masterAddress: String,
        val slaveNames: Map<String, String>,
        val compareNames: List<String>,
        val compareAddresses: List<String>,
        val sdkVersion: String?,
        val hrVersion: String?,
        val spo2Version: String?,
        val nadtVersion: String?,
        val hrvVersion: String?
    )

    private class ModeState(
        val channel: Channel<WriteTask>,
        val consumerJob: kotlinx.coroutines.Job,
        val serverWriters: ConcurrentHashMap<String, CsvWriter>,
        val recordsBuffer: RecordsBufferState,
        val lock: Mutex,
        val maxCompareDevices: Int
    ) {
        @Volatile var recordsWriter: CsvWriter? = null
    }

    private data class RecordsBufferState(
        var lastWrittenSecond: Long = 0L,
        var masterAlgo: String = "",
        var slaveAlgo: String = "",
        val compareHrs: MutableMap<Int, Int> = mutableMapOf(),
        val compareSpo2s: MutableMap<Int, Float> = mutableMapOf()
    )

    companion object {
        private const val CHANNEL_CAPACITY = 256
        private const val MAX_COMPARE_DEVICES = 5
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)
    }

    fun startSession(
        config: TestConfig,
        chip: String = "gh3036",
        masterDeviceName: String,
        masterDeviceAddress: String,
        slaveDevices: Map<String, String> = emptyMap(),
        compareDeviceNames: List<String> = emptyList(),
        compareDeviceAddresses: List<String> = emptyList(),
        projectName: String = "",
        projectId: Int = 0,
        username: String = "",
        sdkVersion: String? = null,
        hrVersion: String? = null,
        spo2Version: String? = null,
        nadtVersion: String? = null,
        hrvVersion: String? = null
    ) {
        runBlocking { endSession() }

        sessionDate = Date()
        currentConfig = SessionConfig(
            scenario = config.scenario.name,
            tester = config.testerName.takeIf { it.isNotBlank() } ?: "unknown",
            chip = chip,
            masterName = masterDeviceName,
            masterAddress = masterDeviceAddress,
            slaveNames = slaveDevices,
            compareNames = compareDeviceNames,
            compareAddresses = compareDeviceAddresses,
            sdkVersion = sdkVersion,
            hrVersion = hrVersion,
            spo2Version = spo2Version,
            nadtVersion = nadtVersion,
            hrvVersion = hrvVersion
        )
        currentProjectName = projectName
        currentProjectId = projectId
        currentUsername = username
        _isSessionActive.value = true

        val numCompare = compareDeviceAddresses.size
        Timber.i("Recording session started: chip=$chip, scenario=${config.scenario.name}, tester=${config.testerName}, master=$masterDeviceName, compare=$numCompare")

        for (mode in FunctionMode.entries) {
            val channel = Channel<WriteTask>(CHANNEL_CAPACITY)
            val serverWriters = ConcurrentHashMap<String, CsvWriter>()
            val recordsBuffer = RecordsBufferState()
            val lock = Mutex()

            val consumerJob = scope.launch {
                consumeModeChannel(mode.name, channel, serverWriters, recordsBuffer, lock, numCompare)
            }

            val modeState = ModeState(
                channel = channel,
                consumerJob = consumerJob,
                serverWriters = serverWriters,
                recordsBuffer = recordsBuffer,
                lock = lock,
                maxCompareDevices = numCompare
            )
            modeStates[mode.name] = modeState
        }

        Timber.i("Recording session started: ${FunctionMode.entries.size} mode channels, tester=${currentConfig?.tester}")
    }

    fun writeFrame(deviceAddress: String, mode: String, columnMap: Map<String, Any?>, role: DeviceRole) {
        val state = modeStates[mode] ?: return
        val timestamp = (columnMap["TimeStamp"] as? Long) ?: System.currentTimeMillis()
        val task = WriteTask(
            deviceAddress = deviceAddress,
            columnMap = columnMap,
            role = role,
            timestamp = timestamp
        )
        val result = state.channel.trySend(task)
        if (result.isFailure) {
            Timber.w("WriteTask dropped: mode=$mode device=$deviceAddress channel full or closed")
        }
    }

    fun updateCompareHr(index: Int, heartRate: Int) {
        if (index !in 0 until MAX_COMPARE_DEVICES) return
        for ((_, state) in modeStates) {
            scope.launch {
                state.lock.withLock {
                    state.recordsBuffer.compareHrs[index] = heartRate
                }
            }
        }
    }

    fun updateCompareSpo2(index: Int, spo2: Float?) {
        for ((_, state) in modeStates) {
            scope.launch {
                state.lock.withLock {
                    if (spo2 != null) {
                        state.recordsBuffer.compareSpo2s[index] = spo2
                    } else {
                        state.recordsBuffer.compareSpo2s.remove(index)
                    }
                }
            }
        }
    }

    fun updateAllCompareSpo2(values: Map<Int, Float>) {
        for ((_, state) in modeStates) {
            scope.launch {
                state.lock.withLock {
                    state.recordsBuffer.compareSpo2s.clear()
                    state.recordsBuffer.compareSpo2s.putAll(values)
                }
            }
        }
    }

    suspend fun endSession() {
        if (!_isSessionActive.value) return
        Timber.i("Ending recording session...")

        // 1. Close all channels to stop new writes
        for ((mode, state) in modeStates) {
            state.channel.close()
            Timber.d("Channel closed for mode=$mode")
        }

        // 2. Wait for all consumers to drain and exit
        for ((mode, state) in modeStates) {
            state.consumerJob.join()
            Timber.d("Consumer drained for mode=$mode")
        }

        // 3. Flush and close all writers, collect server files
        val filesToUpload = mutableListOf<File>()
        for ((mode, state) in modeStates) {
            for ((key, writer) in state.serverWriters) {
                writer.flush()
                writer.close()
                filesToUpload.add(writer.outputFile)
                Timber.d("Server writer closed: mode=$mode key=$key")
            }
            state.recordsWriter?.let {
                it.flush()
                it.close()
                Timber.d("Records writer closed for mode=$mode")
            }
        }

        // 4. Trigger upload for all completed server CSV files
        for (file in filesToUpload) {
            csvUploadManager.uploadCsvFile(file)
        }

        // 5. Clean up
        modeStates.clear()
        currentConfig = null
        _isSessionActive.value = false
        Timber.i("Recording session ended, ${filesToUpload.size} files queued for upload")
    }

    private suspend fun consumeModeChannel(
        mode: String,
        channel: Channel<WriteTask>,
        serverWriters: ConcurrentHashMap<String, CsvWriter>,
        recordsBuffer: RecordsBufferState,
        lock: Mutex,
        numCompareDevices: Int
    ) {
        var recordsWriter: CsvWriter? = null
        val frameZeroCounts = mutableMapOf<String, Int>()
        for (task in channel) {
            try {
                recordsWriter = writeTaskToCsv(mode, task, serverWriters, recordsWriter, recordsBuffer, lock, numCompareDevices, frameZeroCounts)
            } catch (e: Exception) {
                Timber.e(e, "Error writing task for mode=$mode device=${task.deviceAddress}")
            }
        }
        // Store for endSession cleanup
        modeStates[mode]?.recordsWriter = recordsWriter
    }

    private suspend fun writeTaskToCsv(
        mode: String,
        task: WriteTask,
        serverWriters: ConcurrentHashMap<String, CsvWriter>,
        recordsWriter: CsvWriter?,
        recordsBuffer: RecordsBufferState,
        lock: Mutex,
        numCompareDevices: Int,
        frameZeroCounts: MutableMap<String, Int>
    ): CsvWriter? {
        var currentRecordsWriter = recordsWriter
        val writerKey = "${task.deviceAddress}_$mode"

        // Server CSV segmentation: close current file, create new one with fresh timestamp
        if (shouldRotateServerFile(task.columnMap, frameZeroCounts, writerKey)) {
            serverWriters[writerKey]?.let { old ->
                old.close()
                Timber.d("Closed rotated server file for key=$writerKey")
            }
            serverWriters.remove(writerKey)
        }

        // Lazy-create server CSV writer on first frame for this device+mode
        val serverWriter = serverWriters.getOrPut(writerKey) {
            createServerWriter(mode, task) ?: return currentRecordsWriter
        }

        // 1. Write server CSV row (inject compare HR/SPO2 values into REF_RESULT columns)
        val serverValues = task.columnMap.toMutableMap()
        lock.withLock {
            injectCompareValues(serverValues, recordsBuffer.compareHrs, recordsBuffer.compareSpo2s)
        }
        serverWriter.writeRow(serverValues)

        // 2. Lazy-create records CSV on first write for this mode
        if (currentRecordsWriter == null) {
            currentRecordsWriter = createRecordsWriter(mode, numCompareDevices)
        }

        // 3. Update records buffer
        val algoValue = (task.columnMap["ALGO_RESULT0"] as? Number)?.toInt()?.toString() ?: ""
        when (task.role) {
            DeviceRole.MASTER -> recordsBuffer.masterAlgo = algoValue
            DeviceRole.SLAVE -> recordsBuffer.slaveAlgo = algoValue
            else -> {}
        }

        val currentSecond = task.timestamp / 1000
        if (currentSecond != recordsBuffer.lastWrittenSecond) {
            flushRecordsRow(mode, task.timestamp, currentRecordsWriter, recordsBuffer, lock)
            recordsBuffer.lastWrittenSecond = currentSecond
        }

        return currentRecordsWriter
    }

    private suspend fun createServerWriter(mode: String, task: WriteTask): CsvWriter? {
        val cfg = currentConfig ?: return null
        val now = Date()
        val dateStr = DATE_FORMAT.format(now)

        val deviceName: String
        val role: DeviceRole
        if (task.deviceAddress == cfg.masterAddress) {
            deviceName = cfg.masterName
            role = DeviceRole.MASTER
        } else if (cfg.slaveNames.containsKey(task.deviceAddress)) {
            deviceName = cfg.slaveNames[task.deviceAddress] ?: "Unknown"
            role = DeviceRole.SLAVE
        } else {
            deviceName = "Unknown"
            role = DeviceRole.COMPARE
        }

        val rule = CsvRuleParser.forChip(cfg.chip)
        Timber.d("Server writer rule: chip=${cfg.chip}, compare=${cfg.compareNames.size}, columns=${rule.columns.size}")

        val path = StoragePath(
            mode = mode,
            deviceRole = role,
            scenario = cfg.scenario,
            tester = cfg.tester,
            chip = cfg.chip,
            deviceName = deviceName,
            deviceAddress = task.deviceAddress,
            phoneDevice = phoneDevice,
            appVersion = appVersion,
            sdkVersion = cfg.sdkVersion,
            hrVersion = cfg.hrVersion,
            spo2Version = cfg.spo2Version,
            nadtVersion = cfg.nadtVersion,
            hrvVersion = cfg.hrvVersion,
            projectName = currentProjectName,
            projectId = currentProjectId,
            username = currentUsername,
            compareDeviceNames = cfg.compareNames,
            date = now
        )

        val serverFile = File(baseDir, path.serverPath())
        val writer = CsvWriter(serverFile, rule, path.infoJson())
        writer.open()
        Timber.d("Server writer created: ${path.serverPath()}")
        return writer
    }

    private suspend fun createRecordsWriter(mode: String, numCompareDevices: Int): CsvWriter? {
        val dateStr = DATE_FORMAT.format(sessionDate)
        val recordsRule = CsvRuleParser.forRecordsCsv(numCompareDevices)
        val recordsPath = "records/$mode/extra_records_${mode}_$dateStr.csv"
        val recordsFile = File(baseDir, recordsPath)
        recordsFile.parentFile?.mkdirs()
        val writer = CsvWriter(recordsFile, recordsRule, "")
        writer.open()
        Timber.d("Records writer created for mode=$mode")
        return writer
    }

    private suspend fun flushRecordsRow(
        mode: String,
        timestampMs: Long,
        recordsWriter: CsvWriter?,
        recordsBuffer: RecordsBufferState,
        lock: Mutex
    ) {
        val writer = recordsWriter ?: return
        val values = mutableMapOf<String, Any?>()
        values["TimeStamp"] = timestampMs
        values["MasterAlgo"] = recordsBuffer.masterAlgo.ifEmpty { "" }
        values["SlaveAlgo"] = recordsBuffer.slaveAlgo.ifEmpty { "" }
        lock.withLock {
            for (i in 0 until MAX_COMPARE_DEVICES) {
                values["Compare${i}_HR"] = recordsBuffer.compareHrs[i]?.toString() ?: ""
            }
        }
        writer.writeRow(values)
        writer.flush()
    }
}
