package com.ghealth.tools.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SensorRecordEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class GHealthDatabase : RoomDatabase() {
    abstract fun sensorRecordDao(): SensorRecordDao
}
