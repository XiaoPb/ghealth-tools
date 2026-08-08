package com.ghealth.tools.core.storage.di

import android.content.Context
import android.os.Environment
import com.ghealth.tools.core.storage.DataRecorder
import com.ghealth.tools.core.storage.DefaultConfigInstaller
import com.ghealth.tools.core.storage.LogManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    @Named("storageBaseDir")
    fun provideStorageBaseDir(@ApplicationContext context: Context): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "GHealthTools"
        )
        dir.mkdirs()
        return dir
    }

    @Provides
    @Singleton
    fun provideLogManager(@Named("storageBaseDir") baseDir: File): LogManager {
        return LogManager(baseDir).also { it.init() }
    }

    @Provides
    @Singleton
    fun provideDefaultConfigInstaller(
        @ApplicationContext context: Context,
        @Named("storageBaseDir") baseDir: File
    ): DefaultConfigInstaller = DefaultConfigInstaller(context, baseDir)
}
