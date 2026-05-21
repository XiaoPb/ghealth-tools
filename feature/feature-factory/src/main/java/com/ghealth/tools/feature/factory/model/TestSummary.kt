package com.ghealth.tools.feature.factory.model

data class TestSummary(
    val projectName: String,
    val chipType: String,
    val timestamp: Long = System.currentTimeMillis(),
    val deviceInfo: String = "",
    val chipInitStatus: String = "",
    val uuid: String = "",
    val results: Map<TestType, List<TestResult>> = emptyMap()
) {
    val totalTests: Int get() = results.values.sumOf { it.size }
    val passedTests: Int get() = results.values.sumOf { it.count { r -> r.passed } }
    val failedTests: Int get() = totalTests - passedTests
    val overallPassed: Boolean get() = failedTests == 0 && totalTests > 0

    val errorCodes: List<Int>
        get() = results.values.flatten()
            .filter { !it.passed }
            .map { it.errorCodeComputed }
            .ifEmpty {
                if (!overallPassed) listOf(-1) else emptyList()
            }

    val errorCodeString: String
        get() = errorCodes.joinToString("|") { "0x%04X".format(it) }
}
