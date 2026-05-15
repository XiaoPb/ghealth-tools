package com.ghealth.tools.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorRecordDao {
    @Insert
    suspend fun insert(record: SensorRecordEntity)

    @Query("SELECT * FROM sensor_records WHERE deviceAddress = :address ORDER BY timestamp DESC")
    fun getByDevice(address: String): Flow<List<SensorRecordEntity>>

    @Query("SELECT * FROM sensor_records WHERE workMode = :mode ORDER BY timestamp DESC")
    fun getByMode(mode: String): Flow<List<SensorRecordEntity>>

    @Query("DELETE FROM sensor_records WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}
