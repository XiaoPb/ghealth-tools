package com.ghealth.tools.core.model

data class SensorData(
    val timestamp: Long,
    val workMode: WorkMode,
    val heartRate: Int? = null,
    val spo2: Int? = null,
    val hrv: Int? = null,
    val stress: Int? = null,
    val rawPpg: List<Int>? = null,
    val rawAccel: List<Float>? = null,
)
