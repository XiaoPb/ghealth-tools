package com.ghealth.tools.core.storage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class RecordingState {
    IDLE, RECORDING, PAUSED
}

class DeviceRecorder(
    val deviceAddress: String,
    val deviceRole: DeviceRole,
    var serverWriter: CsvWriter,
    private val baseDir: File,
    private val config: RecordingConfig,
    private val csvRule: CsvRule,
    private val infoJsonStr: String
) {
    @Volatile
    private var frameZeroCount = 0

    suspend fun checkAndRotate(frameId: Int) {
        if (frameId != 0) return
        frameZeroCount++
        Timber.d("FRAME_ID=0 detected, frameZeroCount=$frameZeroCount")
        if (frameZeroCount > 1) {
            rotate()
        }
    }

    private suspend fun rotate() {
        serverWriter.close()
        val path = StoragePath(
            mode = config.mode,
            deviceRole = config.deviceRole,
            scenario = config.scenario,
            tester = config.tester,
            chip = config.chip,
            deviceName = config.deviceName,
            deviceAddress = config.deviceAddress,
            phoneDevice = config.phoneDevice,
            appVersion = config.appVersion,
            sdkVersion = config.sdkVersion,
            hrVersion = config.hrVersion,
            spo2Version = config.spo2Version,
            nadtVersion = config.nadtVersion,
            hrvVersion = config.hrvVersion,
            date = java.util.Date()
        )
        val newFile = File(baseDir, path.serverPath())
        serverWriter = CsvWriter(newFile, csvRule, infoJsonStr)
        serverWriter.open()
        Timber.d("Rotated to new file: ${newFile.name}")
    }
}

data class RecordingConfig(
    val deviceAddress: String,
    val deviceRole: DeviceRole,
    val deviceName: String,
    val chip: String,
    val mode: String,
    val scenario: String = "default",
    val tester: String = "unknown",
    val phoneDevice: String = "",
    val appVersion: String = "1.0.0",
    val sdkVersion: String = "1.0.0",
    val hrVersion: String = "1.0.0",
    val spo2Version: String = "1.0.0",
    val nadtVersion: String = "1.0.0",
    val hrvVersion: String = "1.0.0",
    val compareDeviceNames: List<String> = emptyList(),
    val compareDeviceAddresses: List<String> = emptyList()
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Singleton
class DataRecorder @Inject constructor(
    private val csvUploadManager: CsvUploadManager
) {
    private val writeDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + writeDispatcher)
    private val deviceRecorders = mutableMapOf<String, DeviceRecorder>()
    private val recordsBuffers = mutableMapOf<String, RecordsBuffer>()
    private val modeRecordingCounts = mutableMapOf<String, Int>()
    private var globalState = RecordingState.IDLE

    val recordingState: RecordingState get() = globalState

    private fun recorderKey(deviceAddress: String, mode: String): String = "${deviceAddress}_$mode"

    fun startRecording(
        baseDir: File,
        config: RecordingConfig
    ) {
        val deviceAddress = config.deviceAddress
        val key = recorderKey(deviceAddress, config.mode)
        if (deviceRecorders.containsKey(key)) {
            Timber.w("Recorder $key already exists")
            return
        }

        val rule = if (config.compareDeviceNames.isNotEmpty()) {
            CsvRuleParser.forChipWithCompareDevices(config.chip, config.compareDeviceNames)
        } else {
            CsvRuleParser.forChip(config.chip)
        }

        val path = StoragePath(
            mode = config.mode,
            deviceRole = config.deviceRole,
            scenario = config.scenario,
            tester = config.tester,
            chip = config.chip,
            deviceName = config.deviceName,
            deviceAddress = config.deviceAddress,
            phoneDevice = config.phoneDevice,
            appVersion = config.appVersion,
            sdkVersion = config.sdkVersion,
            hrVersion = config.hrVersion,
            spo2Version = config.spo2Version,
            nadtVersion = config.nadtVersion,
            hrvVersion = config.hrvVersion
        )

        val serverFile = File(baseDir, path.serverPath())
        val infoJson = path.infoJson()

        val serverWriter = CsvWriter(serverFile, rule, infoJson)

        deviceRecorders[key] = DeviceRecorder(
            deviceAddress = deviceAddress,
            deviceRole = config.deviceRole,
            serverWriter = serverWriter,
            baseDir = baseDir,
            config = config,
            csvRule = rule,
            infoJsonStr = infoJson
        )

        scope.launch {
            serverWriter.open()
        }

        if (!recordsBuffers.containsKey(config.mode)) {
            val recordsRule = CsvRuleParser.forRecordsCsv(config.compareDeviceAddresses.size)
            val recordsFile = File(baseDir, path.recordsPathForMode())
            val recordsWriter = CsvWriter(recordsFile, recordsRule, "")
            recordsBuffers[config.mode] = RecordsBuffer(writer = recordsWriter)
            scope.launch {
                recordsWriter.open()
            }
        }

        modeRecordingCounts[config.mode] = (modeRecordingCounts[config.mode] ?: 0) + 1

        globalState = RecordingState.RECORDING
        Timber.d("Recording started for device $deviceAddress as ${config.deviceRole.name}")
    }

    fun startRecording(
        baseDir: File,
        deviceAddress: String,
        deviceRole: DeviceRole,
        deviceName: String,
        chip: String,
        mode: String,
        scenario: String = "default",
        tester: String = "unknown",
        phoneDevice: String = "",
        appVersion: String = "1.0.0",
        sdkVersion: String = "1.0.0",
        hrVersion: String = "1.0.0",
        spo2Version: String = "1.0.0",
        nadtVersion: String = "1.0.0",
        hrvVersion: String = "1.0.0",
        compareDeviceNames: List<String> = emptyList(),
        compareDeviceAddresses: List<String> = emptyList()
    ) {
        startRecording(
            baseDir,
            RecordingConfig(
                deviceAddress = deviceAddress,
                deviceRole = deviceRole,
                deviceName = deviceName,
                chip = chip,
                mode = mode,
                scenario = scenario,
                tester = tester,
                phoneDevice = phoneDevice,
                appVersion = appVersion,
                sdkVersion = sdkVersion,
                hrVersion = hrVersion,
                spo2Version = spo2Version,
                nadtVersion = nadtVersion,
                hrvVersion = hrvVersion,
                compareDeviceNames = compareDeviceNames,
                compareDeviceAddresses = compareDeviceAddresses
            )
        )
    }

    fun writeFrame(deviceAddress: String, mode: String, values: Map<String, Any?>) {
        if (globalState != RecordingState.RECORDING) return
        val recorder = deviceRecorders[recorderKey(deviceAddress, mode)] ?: return

        val frameId = (values["FRAME_ID"] as? Number)?.toInt() ?: -1

        val buffer = recordsBuffers[mode]
        if (buffer != null) {
            val mutableValues = values.toMutableMap()
            synchronized(buffer) {
                for ((index, hr) in buffer.compareHrs) {
                    mutableValues["REF_RESULT$index"] = hr
                }
            }
            // Rotate and write in same coroutine — rotation must happen before write
            scope.launch {
                if (frameId == 0) recorder.checkAndRotate(frameId)
                recorder.serverWriter.writeRow(mutableValues)
            }

            val timestamp = (values["TimeStamp"] as? Long) ?: System.currentTimeMillis()
            val algoValue = (values["ALGO_RESULT0"] as? Number)?.toInt()?.toString() ?: ""

            synchronized(buffer) {
                when (recorder.deviceRole) {
                    DeviceRole.MASTER -> buffer.masterAlgo = algoValue
                    DeviceRole.SLAVE -> buffer.slaveAlgo = algoValue
                    else -> {}
                }

                val currentSecond = timestamp / 1000
                if (currentSecond != buffer.lastWrittenSecond) {
                    flushRecordsRow(mode, timestamp)
                    buffer.lastWrittenSecond = currentSecond
                }
            }
        } else {
            scope.launch {
                if (frameId == 0) recorder.checkAndRotate(frameId)
                recorder.serverWriter.writeRow(values)
            }
        }
    }

    fun updateCompareHr(mode: String, index: Int, heartRate: Int) {
        if (globalState != RecordingState.RECORDING) return
        val buffer = recordsBuffers[mode] ?: return
        if (index !in 0..4) return

        synchronized(buffer) {
            buffer.compareHrs[index] = heartRate

            val currentSecond = System.currentTimeMillis() / 1000
            if (currentSecond != buffer.lastWrittenSecond) {
                flushRecordsRow(mode, System.currentTimeMillis())
                buffer.lastWrittenSecond = currentSecond
            }
        }
    }

    fun getCompareHr(mode: String, index: Int): Int? {
        val buffer = recordsBuffers[mode] ?: return null
        return synchronized(buffer) {
            buffer.compareHrs[index]
        }
    }

    private fun flushRecordsRow(mode: String, timestampMs: Long) {
        val buffer = recordsBuffers[mode] ?: return
        val values = mutableMapOf<String, Any?>()
        values["TimeStamp"] = timestampMs
        values["MasterAlgo"] = buffer.masterAlgo.ifEmpty { "" }
        values["SlaveAlgo"] = buffer.slaveAlgo.ifEmpty { "" }
        for (i in 0 until MAX_COMPARE_DEVICES) {
            values["Compare${i}_HR"] = buffer.compareHrs[i]?.toString() ?: ""
        }
        scope.launch {
            buffer.writer.writeRow(values)
            buffer.writer.flush()
        }
    }

    fun stopRecording(deviceAddress: String, mode: String) {
        val recorder = deviceRecorders.remove(recorderKey(deviceAddress, mode)) ?: return
        scope.launch {
            recorder.serverWriter.close()
            
            val csvFile = recorder.serverWriter.outputFile
            if (csvFile.exists()) {
                csvUploadManager.uploadCsvFile(csvFile)
            }
        }
        Timber.d("Recording stopped for device $deviceAddress mode $mode")

        val count = (modeRecordingCounts[mode] ?: 1) - 1
        if (count <= 0) {
            modeRecordingCounts.remove(mode)
            recordsBuffers.remove(mode)?.let { (writer) ->
                scope.launch { writer.close() }
            }
        } else {
            modeRecordingCounts[mode] = count
        }

        if (deviceRecorders.isEmpty()) {
            globalState = RecordingState.IDLE
        }
    }

    fun stopAllRecording() {
        scope.launch {
            deviceRecorders.values.forEach { recorder ->
                recorder.serverWriter.close()
            }
            deviceRecorders.clear()

            recordsBuffers.values.forEach { it.writer.close() }
            recordsBuffers.clear()
            modeRecordingCounts.clear()
        }
        globalState = RecordingState.IDLE
        Timber.d("All recordings stopped")
    }

    fun pause() {
        if (globalState == RecordingState.RECORDING) {
            globalState = RecordingState.PAUSED
            Timber.d("Recording paused")
        }
    }

    fun resume() {
        if (globalState == RecordingState.PAUSED) {
            globalState = RecordingState.RECORDING
            Timber.d("Recording resumed")
        }
    }

    fun isRecording(deviceAddress: String, mode: String): Boolean {
        return deviceRecorders.containsKey(recorderKey(deviceAddress, mode))
    }

    fun isAnyRecording(): Boolean {
        return deviceRecorders.isNotEmpty()
    }

    fun getRecordingModes(deviceAddress: String): List<String> {
        val prefix = "${deviceAddress}_"
        return deviceRecorders.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }
    }

    companion object {
        private const val MAX_COMPARE_DEVICES = 5
    }
}

private data class RecordsBuffer(
    val writer: CsvWriter,
    var lastWrittenSecond: Long = 0L,
    var masterAlgo: String = "",
    var slaveAlgo: String = "",
    var compareHrs: MutableMap<Int, Int> = mutableMapOf()
)
