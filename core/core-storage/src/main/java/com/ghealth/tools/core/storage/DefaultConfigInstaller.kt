package com.ghealth.tools.core.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * 应用启动时把 APK assets 内置的默认配置解压到存储目录：
 * - assets/application/config → {storageBaseDir}/application/config
 * - assets/factory/config     → {storageBaseDir}/factory/config
 */
@Singleton
class DefaultConfigInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("storageBaseDir") private val baseDir: File
) {
    suspend fun install(): DefaultConfigCopySummary = withContext(Dispatchers.IO) {
        val summary = DefaultConfigCopier(
            AndroidDefaultConfigAssetSource(context.assets)
        ).copyTo(baseDir)
        Timber.i(
            "Default configs installed: copied=%d, skipped=%d, failed=%d, baseDir=%s",
            summary.copiedFiles,
            summary.skippedFiles,
            summary.failedFiles,
            baseDir.absolutePath
        )
        summary
    }
}