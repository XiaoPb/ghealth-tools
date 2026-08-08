package com.ghealth.tools.core.storage

import timber.log.Timber
import java.io.File
import java.io.IOException

/** 本次解压的统计结果。 */
data class DefaultConfigCopySummary(
    val copiedFiles: Int,
    val skippedFiles: Int,
    val failedFiles: Int = 0
)

/**
 * 将 assets 中 `application/config` 与 `factory/config` 两个根目录下的默认配置
 * 复制到 [baseDir] 的对应目录（`application/config`、`factory/config`），保持相对路径不变。
 *
 * 目录优先判定：条目可被 [DefaultConfigAssetSource.list] 列出时视为子目录递归，否则视为文件。
 * 仅复制 .config / .ini / .json 后缀文件（大小写不敏感）。
 * 已存在的目标文件跳过，避免覆盖用户后续下载或修改的配置。
 * 单个文件复制失败只记录失败数，不中断整棵复制树。
 */
class DefaultConfigCopier(
    private val assetSource: DefaultConfigAssetSource
) {
    private val supportedExtensions = setOf("config", "ini", "json")

    fun copyTo(baseDir: File): DefaultConfigCopySummary {
        var copied = 0
        var skipped = 0
        var failed = 0
        for (root in ASSET_ROOTS) {
            val result = copyTree(root, File(baseDir, root))
            copied += result.copiedFiles
            skipped += result.skippedFiles
            failed += result.failedFiles
        }
        return DefaultConfigCopySummary(copied, skipped, failed)
    }

    private fun copyTree(assetPath: String, targetDir: File): DefaultConfigCopySummary {
        val entries = assetSource.list(assetPath) ?: return DefaultConfigCopySummary(0, 0)
        var copied = 0
        var skipped = 0
        var failed = 0
        targetDir.mkdirs()
        for (entry in entries) {
            val childAssetPath = "$assetPath/$entry"
            if (assetSource.list(childAssetPath) != null) {
                // 子目录：递归
                val childResult = copyTree(childAssetPath, File(targetDir, entry))
                copied += childResult.copiedFiles
                skipped += childResult.skippedFiles
                failed += childResult.failedFiles
                continue
            }
            // 文件：仅复制支持的扩展名（大小写不敏感），目标已存在则跳过
            if (entry.substringAfterLast('.').lowercase() !in supportedExtensions) continue
            val targetFile = File(targetDir, entry)
            if (targetFile.exists()) {
                skipped++
                continue
            }
            try {
                assetSource.open(childAssetPath).use { input ->
                    targetFile.outputStream().use { output -> input.copyTo(output) }
                }
                copied++
            } catch (e: IOException) {
                Timber.w(e, "Failed to copy default config %s", childAssetPath)
                failed++
            }
        }
        return DefaultConfigCopySummary(copied, skipped, failed)
    }

    private companion object {
        val ASSET_ROOTS = listOf("application/config", "factory/config")
    }
}
