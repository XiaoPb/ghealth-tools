package com.ghealth.tools.core.storage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

enum class RecordingState {
    IDLE, RECORDING, PAUSED
}

class DataRecorder(
    private val baseDir: File,
    private val chip: String
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverWriter: CsvWriter? = null
    private var recordsWriter: CsvWriter? = null
    private var state = RecordingState.IDLE

    val recordingState: RecordingState get() = state

    fun start(
        mode: String,
        scenario: String = "default",
        tester: String = "unknown",
        infoJson: String = ""
    ) {
        if (state == RecordingState.RECORDING) return

        val rule = CsvRuleParser.forChip(chip)
        val path = StoragePath(
            mode = mode,
            chip = chip,
            scenario = scenario,
            tester = tester
        )

        val serverFile = File(baseDir, path.serverPath())
        val recordsFile = File(baseDir, path.recordsPath())

        serverWriter = CsvWriter(serverFile, rule, infoJson)
        recordsWriter = CsvWriter(recordsFile, rule, infoJson)

        scope.launch {
            serverWriter?.open()
            recordsWriter?.open()
        }

        state = RecordingState.RECORDING
        Timber.d("DataRecorder started: mode=$mode, chip=$chip")
    }

    fun writeFrame(values: Map<String, Any?>) {
        if (state != RecordingState.RECORDING) return
        scope.launch {
            serverWriter?.writeRow(values)
            recordsWriter?.writeRow(values)
        }
    }

    fun pause() {
        if (state == RecordingState.RECORDING) {
            state = RecordingState.PAUSED
            Timber.d("DataRecorder paused")
        }
    }

    fun resume() {
        if (state == RecordingState.PAUSED) {
            state = RecordingState.RECORDING
            Timber.d("DataRecorder resumed")
        }
    }

    fun stop() {
        state = RecordingState.IDLE
        scope.launch {
            serverWriter?.close()
            recordsWriter?.close()
            serverWriter = null
            recordsWriter = null
        }
        Timber.d("DataRecorder stopped")
    }
}
