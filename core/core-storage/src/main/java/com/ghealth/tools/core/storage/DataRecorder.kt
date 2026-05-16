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

    fun startRecording(
        baseDir: File,
        config: RecordingConfig
    ) {
        val deviceAddress = config.deviceAddress
        if (deviceRecorders.containsKey(deviceAddress)) {
            Timber.w("Device $deviceAddress already recording")
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

        deviceRecorders[deviceAddress] = DeviceRecorder(
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

    fun writeFrame(deviceAddress: String, values: Map<String, Any?>) {
        if (globalState != RecordingState.RECORDING) return
        val recorder = deviceRecorders[deviceAddress] ?: return
        scope.launch {
            recorder.serverWriter.writeRow(values)
            recorder.recordsWriter.writeRow(values)
        }
    }

    fun writeHeartRateResult(deviceAddress: String, index: Int, heartRate: Int) {
        if (globalState != RecordingState.RECORDING) return
        val recorder = deviceRecorders[deviceAddress] ?: return
        if (recorder.deviceRole != DeviceRole.MASTER) return
        
        scope.launch {
            val values = mapOf("REF_RESULT$index" to heartRate)
            recorder.serverWriter.writeRow(values)
        }
    }

    fun stopRecording(deviceAddress: String) {
        val recorder = deviceRecorders.remove(deviceAddress) ?: return
        scope.launch {
            recorder.serverWriter.close()
            recorder.recordsWriter.close()
        }
        Timber.d("Recording stopped for device $deviceAddress")

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

    fun isRecording(deviceAddress: String): Boolean {
        return deviceRecorders.containsKey(deviceAddress)
    }

    fun getRecordingDevices(): List<String> {
        return deviceRecorders.keys.toList()
    }
}
