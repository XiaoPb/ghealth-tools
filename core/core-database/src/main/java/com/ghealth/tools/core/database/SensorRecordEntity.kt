package com.ghealth.tools.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sensor_records")
data class SensorRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceAddress: String,
    val workMode: String,
    val timestamp: Long,
    val heartRate: Int?,
    val spo2: Int?,
    val hrv: Int?,
    val stress: Int?,
)
