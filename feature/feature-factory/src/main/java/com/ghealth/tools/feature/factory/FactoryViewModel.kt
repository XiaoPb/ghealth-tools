package com.ghealth.tools.feature.factory

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.ble.connection.BleConnectionManager
import com.ghealth.tools.ble.connection.ConnectedDevice
import com.ghealth.tools.core.model.ConnectionState
import com.ghealth.tools.ble.connection.DeviceRole
import com.ghealth.tools.core.datastore.BlePreferences
import com.ghealth.tools.feature.factory.engine.FactoryTestEngine
import com.ghealth.tools.feature.factory.engine.LogLevel
import com.ghealth.tools.feature.factory.engine.TestEngineEvent
import com.ghealth.tools.feature.factory.exporter.CsvResultExporter
import com.ghealth.tools.feature.factory.model.FactoryConfig
import com.ghealth.tools.feature.factory.model.RegisterConfig
import com.ghealth.tools.feature.factory.model.TestResult
import com.ghealth.tools.feature.factory.model.TestSummary
import com.ghealth.tools.feature.factory.model.TestType
import com.ghealth.tools.feature.factory.parser.ConfigJsonParser
import com.ghealth.tools.feature.factory.parser.RegisterConfigParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class FactoryViewModel @Inject constructor(
    private val application: Application,
    private val connectionManager: BleConnectionManager,
    private val blePreferences: BlePreferences,
    private val testEngine: FactoryTestEngine,
    private val configJsonParser: ConfigJsonParser,
    private val registerConfigParser: RegisterConfigParser,
    private val csvExporter: CsvResultExporter,
    @Named("storageBaseDir") private val baseDir: File
) : ViewModel() {

    private val _uiState = MutableStateFlow(FactoryUiState())
    val uiState: StateFlow<FactoryUiState> = _uiState.asStateFlow()

    init {
        loadConfigs()
        monitorChipChanges()
        monitorConnectionState()
    }

    // ── Config loading ────────────────────────────────────────────────

    private fun loadConfigs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingConfigs = true, configError = null) }

            val projects = withContext(Dispatchers.IO) {
                copyFactoryConfigsToStorage()
                loadAllProjects()
            }

            _uiState.update {
                it.copy(
                    availableProjects = projects,
                    isLoadingConfigs = false,
                    configError = if (projects.isEmpty()) "未找到产测配置文件" else null
                )
            }
        }
    }

    private fun loadAllProjects(): List<ProjectConfig> {
        val assetManager = application.assets
        val projects = mutableListOf<ProjectConfig>()

        try {
            // Iterate chip directories: gh3036, gh3220, gh3300
            val chips = listOf("gh3036", "gh3220", "gh3300")
            for (chip in chips) {
                try {
                    val chipDir = "factory/$chip"
                    val projectDirs = assetManager.list(chipDir) ?: continue
                    for (projectDir in projectDirs) {
                        if (projectDir.contains(".")) continue // skip files
                        val projectPath = "$chipDir/$projectDir"
                        val projectConfig = loadProjectConfig(assetManager, projectPath, chip, projectDir)
                        if (projectConfig != null) {
                            projects.add(projectConfig)
                        }
                    }
                } catch (_: Exception) {
                    // Chip directory not found, skip
                }
            }
        } catch (e: Exception) {
            // Log but don't crash
        }

        return projects
    }

    private fun loadProjectConfig(
        assetManager: android.content.res.AssetManager,
        projectPath: String,
        chip: String,
        projectDir: String
    ): ProjectConfig? {
        try {
            // Find the JSON config file
            val files = assetManager.list(projectPath) ?: return null
            val jsonFile = files.firstOrNull { it.endsWith(".json") } ?: return null

            val jsonContent = assetManager.open("$projectPath/$jsonFile")
                .bufferedReader().use { it.readText() }

            val config = configJsonParser.parseOrNull(jsonContent) ?: return null

            // Load register configs for each enabled test
            val registerConfigs = mutableMapOf<String, RegisterConfig>()
            for ((testKey, testDef) in config.tests) {
                if (!testDef.enabled) continue

                // Match config file by test name prefix or chip-specific pattern
                val registerFile = files.firstOrNull { f ->
                    f.startsWith(testKey, ignoreCase = true) &&
                            (f.endsWith(".config") || f.endsWith(".ini"))
                }
                if (registerFile != null) {
                    val content = assetManager.open("$projectPath/$registerFile")
                        .bufferedReader().use { it.readText() }
                    registerConfigs[testKey] = registerConfigParser.parseByChip(content, chip, registerFile)
                }
            }

            return ProjectConfig(
                projectName = config.project,
                chip = config.chip,
                factoryConfig = config,
                registerConfigs = registerConfigs
            )
        } catch (_: Exception) {
            return null
        }
    }

    // ── Copy factory configs to external storage ───────────────────────

    private fun copyFactoryConfigsToStorage() {
        try {
            val factoryConfigDir = File(baseDir, "factory/config")
            val assetManager = application.assets
            val chips = listOf("gh3036", "gh3220", "gh3300")

            for (chip in chips) {
                val assetChipDir = "factory/$chip"
                val projectDirs = assetManager.list(assetChipDir) ?: continue
                for (projectDir in projectDirs) {
                    if (projectDir.contains(".")) continue
                    val assetProjectPath = "$assetChipDir/$projectDir"
                    val files = assetManager.list(assetProjectPath) ?: continue

                    val targetDir = File(factoryConfigDir, "$chip/$projectDir")
                    if (!targetDir.exists()) {
                        targetDir.mkdirs()
                        for (fileName in files) {
                            // Only copy .config, .ini, and .json files
                            if (!fileName.endsWith(".config") && !fileName.endsWith(".ini") && !fileName.endsWith(".json")) continue
                            val targetFile = File(targetDir, fileName)
                            if (targetFile.exists()) continue
                            assetManager.open("$assetProjectPath/$fileName").use { input ->
                                targetFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Non-critical — loading from assets still works
        }
    }

    // ── Chip / Connection monitoring ──────────────────────────────────

    private fun monitorChipChanges() {
        viewModelScope.launch {
            blePreferences.selectedChip.collect { chipName ->
                _uiState.update { it.copy(chipType = chipName) }
            }
        }
    }

    private fun monitorConnectionState() {
        viewModelScope.launch {
            connectionManager.devices.collect { devices ->
                val masterDevice = devices.values.firstOrNull {
                    it.role == DeviceRole.MASTER && it.state == ConnectionState.CONNECTED
                }
                _uiState.update {
                    it.copy(
                        isDeviceConnected = masterDevice != null,
                        deviceAddress = masterDevice?.address ?: "",
                        deviceName = masterDevice?.name ?: ""
                    )
                }
            }
        }
    }

    // ── User actions ──────────────────────────────────────────────────

    fun selectProject(project: ProjectConfig) {
        _uiState.update { it.copy(selectedProject = project) }
    }

    fun startTest() {
        val state = _uiState.value
        val project = state.selectedProject
        if (project == null) {
            _uiState.update { it.copy(errorMessage = "请先选择项目") }
            return
        }
        if (!state.isDeviceConnected) {
            _uiState.update { it.copy(errorMessage = "请先连接主设备") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isTestRunning = true,
                    testCompleted = false,
                    results = emptyMap(),
                    logMessages = emptyList(),
                    errorMessage = null,
                    exportedFilePath = null,
                    overallPassed = false
                )
            }

            testEngine.runTestSequence(
                deviceAddress = state.deviceAddress,
                chip = project.chip,
                factoryConfig = project.factoryConfig,
                registerConfigs = project.registerConfigs,
                onEvent = { event -> handleEngineEvent(event, project) }
            )
        }
    }

    private suspend fun handleEngineEvent(event: TestEngineEvent, project: ProjectConfig) {
        when (event) {
            is TestEngineEvent.StepStarted -> {
                _uiState.update { it.copy(currentStepDescription = event.description) }
                addLog(LogLevel.INFO, event.description)
            }
            is TestEngineEvent.Progress -> {
                _uiState.update {
                    it.copy(
                        currentStep = event.currentStep,
                        totalSteps = event.totalSteps,
                        progressPercent = event.currentStep.toFloat() / event.totalSteps.coerceAtLeast(1)
                    )
                }
            }
            is TestEngineEvent.TestCompleted -> {
                _uiState.update { state ->
                    state.copy(results = state.results + (event.type to event.results))
                }
                val passCount = event.results.count { it.passed }
                addLog(LogLevel.INFO,
                    "${event.type.displayName}: $passCount/${event.results.size} 通过")
            }
            is TestEngineEvent.LogMessage -> addLog(event.level, event.message)
            is TestEngineEvent.ShowEnvironmentSwitchDialog -> {
                _uiState.update { it.copy(showEnvironmentSwitchDialog = true) }
            }
            is TestEngineEvent.SequenceCompleted -> {
                _uiState.update {
                    it.copy(
                        isTestRunning = false,
                        overallPassed = event.overallPassed,
                        testCompleted = true
                    )
                }
                addLog(LogLevel.INFO,
                    if (event.overallPassed) "测试完成 - 全部通过" else "测试完成 - 有失败项")

                exportResults(project)
            }
            is TestEngineEvent.SequenceFailed -> {
                _uiState.update {
                    it.copy(isTestRunning = false, errorMessage = event.error)
                }
                addLog(LogLevel.ERROR, event.error)
            }
        }
    }

    fun dismissEnvironmentDialog() {
        _uiState.update { it.copy(showEnvironmentSwitchDialog = false) }
        testEngine.resumeAfterEnvironmentSwitch()
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private suspend fun exportResults(project: ProjectConfig) {
        val state = _uiState.value
        val summary = TestSummary(
            projectName = project.projectName,
            chipType = project.chip,
            deviceInfo = state.deviceName.ifEmpty { state.deviceAddress },
            results = state.results
        )

        val file = csvExporter.export(summary, baseDir)
        if (file != null) {
            _uiState.update { it.copy(exportedFilePath = file.absolutePath) }
            addLog(LogLevel.INFO, "结果已导出: ${file.absolutePath}")
        } else {
            addLog(LogLevel.ERROR, "CSV 导出失败")
        }
    }

    private fun addLog(level: LogLevel, message: String) {
        val entry = LogEntry(System.currentTimeMillis(), level, message)
        _uiState.update { state ->
            state.copy(logMessages = state.logMessages + entry)
        }
    }
}
