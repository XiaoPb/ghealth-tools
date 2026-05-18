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

data class DeviceRecorder(
    val deviceAddress: String,
    val deviceRole: DeviceRole,
    val serverWriter: CsvWriter,
    val recordsWriter: CsvWriter
)

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
    val compareDeviceNames: List<String> = emptyList()
)

@Singleton
class DataRecorder @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val deviceRecorders = mutableMapOf<String, DeviceRecorder>()
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
        val recordsFile = File(baseDir, path.recordsPath())
        val infoJson = path.infoJson()

        val serverWriter = CsvWriter(serverFile, rule, infoJson)
        val recordsWriter = CsvWriter(recordsFile, rule, infoJson)

        deviceRecorders[key] = DeviceRecorder(
            deviceAddress = deviceAddress,
            deviceRole = config.deviceRole,
            serverWriter = serverWriter,
            recordsWriter = recordsWriter
        )

        scope.launch {
            serverWriter.open()
            recordsWriter.open()
        }

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
        compareDeviceNames: List<String> = emptyList()
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
                compareDeviceNames = compareDeviceNames
            )
        )
    }

    fun writeFrame(deviceAddress: String, mode: String, values: Map<String, Any?>) {
        if (globalState != RecordingState.RECORDING) return
        val recorder = deviceRecorders[recorderKey(deviceAddress, mode)] ?: return
        scope.launch {
            recorder.serverWriter.writeRow(values)
            recorder.recordsWriter.writeRow(values)
        }
    }

    fun writeHeartRateResult(deviceAddress: String, mode: String, index: Int, heartRate: Int) {
        if (globalState != RecordingState.RECORDING) return
        val recorder = deviceRecorders[recorderKey(deviceAddress, mode)] ?: return
        if (recorder.deviceRole != DeviceRole.MASTER) return

        scope.launch {
            val values = mapOf("REF_RESULT$index" to heartRate)
            recorder.serverWriter.writeRow(values)
        }
    }

    fun stopRecording(deviceAddress: String, mode: String) {
        val recorder = deviceRecorders.remove(recorderKey(deviceAddress, mode)) ?: return
        scope.launch {
            recorder.serverWriter.close()
            recorder.recordsWriter.close()
        }
        Timber.d("Recording stopped for device $deviceAddress mode $mode")

        if (deviceRecorders.isEmpty()) {
            globalState = RecordingState.IDLE
        }
    }

    fun stopAllRecording() {
        scope.launch {
            deviceRecorders.values.forEach { recorder ->
                recorder.serverWriter.close()
                recorder.recordsWriter.close()
            }
            deviceRecorders.clear()
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
}
