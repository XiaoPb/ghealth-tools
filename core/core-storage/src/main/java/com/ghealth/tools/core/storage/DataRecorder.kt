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

@Singleton
class DataRecorder @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val deviceRecorders = mutableMapOf<String, DeviceRecorder>()
    private var globalState = RecordingState.IDLE

    val recordingState: RecordingState get() = globalState

    fun startRecording(
        baseDir: File,
        deviceAddress: String,
        deviceRole: DeviceRole,
        deviceName: String,
        chip: String,
        mode: String,
        scenario: String = "default",
        tester: String = "unknown"
    ) {
        if (deviceRecorders.containsKey(deviceAddress)) {
            Timber.w("Device $deviceAddress already recording")
            return
        }

        val rule = CsvRuleParser.forChip(chip)
        val path = StoragePath(
            mode = mode,
            deviceRole = deviceRole,
            scenario = scenario,
            tester = tester,
            chip = chip,
            deviceName = deviceName,
            deviceAddress = deviceAddress
        )

        val serverFile = File(baseDir, path.serverPath())
        val recordsFile = File(baseDir, path.recordsPath())
        val infoJson = path.infoJson()

        val serverWriter = CsvWriter(serverFile, rule, infoJson)
        val recordsWriter = CsvWriter(recordsFile, rule, infoJson)

        deviceRecorders[deviceAddress] = DeviceRecorder(
            deviceAddress = deviceAddress,
            deviceRole = deviceRole,
            serverWriter = serverWriter,
            recordsWriter = recordsWriter
        )

        scope.launch {
            serverWriter.open()
            recordsWriter.open()
        }

        globalState = RecordingState.RECORDING
        Timber.d("Recording started for device $deviceAddress as ${deviceRole.name}")
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
