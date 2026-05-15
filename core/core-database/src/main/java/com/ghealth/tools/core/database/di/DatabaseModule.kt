package com.ghealth.tools.core.database.di

import android.content.Context
import androidx.room.Room
import com.ghealth.tools.core.database.GHealthDatabase
import com.ghealth.tools.core.database.SensorRecordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GHealthDatabase =
        Room.databaseBuilder(
            context,
            GHealthDatabase::class.java,
            "ghealth.db",
        ).build()

    @Provides
    fun provideSensorRecordDao(database: GHealthDatabase): SensorRecordDao =
        database.sensorRecordDao()
}
