package com.ghealth.tools.feature.factory

import com.ghealth.tools.feature.factory.engine.LogLevel
import com.ghealth.tools.feature.factory.model.FactoryConfig
import com.ghealth.tools.feature.factory.model.RegisterConfig
import com.ghealth.tools.feature.factory.model.TestResult
import com.ghealth.tools.feature.factory.model.TestType

data class ProjectConfig(
    val projectName: String,
    val chip: String,
    val factoryConfig: FactoryConfig,
    val registerConfigs: Map<String, RegisterConfig>
)

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val message: String
)

data class FactoryUiState(
    // Config selection
    val chipType: String = "gh3036",
    val availableProjects: List<ProjectConfig> = emptyList(),
    val selectedProject: ProjectConfig? = null,
    val isLoadingConfigs: Boolean = false,
    val configError: String? = null,

    // Connection
    val isDeviceConnected: Boolean = false,
    val deviceAddress: String = "",
    val deviceName: String = "",

    // Test execution
    val isTestRunning: Boolean = false,
    val currentStep: Int = 0,
    val totalSteps: Int = 0,
    val currentStepDescription: String = "",
    val progressPercent: Float = 0f,

    // Log
    val logMessages: List<LogEntry> = emptyList(),

    // Results
    val results: Map<TestType, List<TestResult>> = emptyMap(),
    val overallPassed: Boolean = false,
    val testCompleted: Boolean = false,

    // Dismissable dialogs
    val showEnvironmentSwitchDialog: Boolean = false,
    val showBluetoothUnstableDialog: Boolean = false,
    val exportedFilePath: String? = null,
    val errorMessage: String? = null
)
