package com.ghealth.tools.core.storage

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
 *
 * 解压是 best-effort：单文件失败由 DefaultConfigCopier 计数跳过，
 * 未预期的运行时异常在此兜底并返回空统计，保证不中断应用启动。
 */
@Singleton
class DefaultConfigInstaller @Inject constructor(
    private val assetSource: DefaultConfigAssetSource,
    @Named("storageBaseDir") private val baseDir: File
) {
    suspend fun install(): DefaultConfigCopySummary = withContext(Dispatchers.IO) {
        try {
            val summary = DefaultConfigCopier(assetSource).copyTo(baseDir)
            Timber.i(
                "Default configs installed: copied=%d, skipped=%d, failed=%d, baseDir=%s",
                summary.copiedFiles,
                summary.skippedFiles,
                summary.failedFiles,
                baseDir.absolutePath
            )
            summary
        } catch (e: Exception) {
            Timber.e(e, "Failed to install default configs to %s", baseDir.absolutePath)
            DefaultConfigCopySummary(0, 0, 0)
        }
    }
}
