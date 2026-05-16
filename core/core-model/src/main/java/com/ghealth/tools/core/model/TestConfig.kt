package com.ghealth.tools.core.model

data class TestConfig(
    val testerName: String = "",
    val scenario: TestScenario = TestScenario.RESTING,
    val testRound: Int = 1,
    val notes: String = ""
) {
    fun isValid(): Boolean {
        return testerName.isNotBlank() && testRound > 0
    }
}

data class DataLogEntry(
    val timestamp: Long,
    val key: String,
    val param: ByteArray,
    val isError: Boolean = false,
    val errorMessage: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DataLogEntry) return false
        return timestamp == other.timestamp && key == other.key && param.contentEquals(other.param)
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + key.hashCode()
        result = 31 * result + param.contentHashCode()
        return result
    }
}
