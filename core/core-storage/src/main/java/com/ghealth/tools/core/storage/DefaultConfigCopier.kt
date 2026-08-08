package com.ghealth.tools.core.storage

import java.io.File

/** 本次解压的统计结果。 */
data class DefaultConfigCopySummary(
    val copiedFiles: Int,
    val skippedFiles: Int
)

/**
 * 将 assets 中 `application/config` 与 `factory/config` 两个根目录下的默认配置
 * 复制到 [baseDir] 的对应目录（`application/config`、`factory/config`），保持相对路径不变。
 *
 * 已存在的目标文件跳过，避免覆盖用户后续下载或修改的配置。
 * 仅复制 .config / .ini / .json 后缀文件；条目名含 "." 视为文件，否则视为子目录递归。
 */
class DefaultConfigCopier(
    private val assetSource: DefaultConfigAssetSource
) {
    private val supportedExtensions = setOf("config", "ini", "json")

    fun copyTo(baseDir: File): DefaultConfigCopySummary {
        var copied = 0
        var skipped = 0
        for (root in ASSET_ROOTS) {
            val result = copyTree(root, File(baseDir, root))
            copied += result.copiedFiles
            skipped += result.skippedFiles
        }
        return DefaultConfigCopySummary(copied, skipped)
    }

    private fun copyTree(assetPath: String, targetDir: File): DefaultConfigCopySummary {
        val entries = assetSource.list(assetPath) ?: return DefaultConfigCopySummary(0, 0)
        var copied = 0
        var skipped = 0
        for (entry in entries) {
            val childAssetPath = "$assetPath/$entry"
            if (entry.contains(".")) {
                // 文件：仅复制支持的扩展名，目标已存在则跳过
                if (entry.substringAfterLast('.', "") !in supportedExtensions) continue
                targetDir.mkdirs()
                val targetFile = File(targetDir, entry)
                if (targetFile.exists()) {
                    skipped++
                    continue
                }
                assetSource.open(childAssetPath).use { input ->
                    targetFile.outputStream().use { output -> input.copyTo(output) }
                }
                copied++
            } else {
                // 子目录：递归
                val childResult = copyTree(childAssetPath, File(targetDir, entry))
                copied += childResult.copiedFiles
                skipped += childResult.skippedFiles
            }
        }
        return DefaultConfigCopySummary(copied, skipped)
    }

    private companion object {
        val ASSET_ROOTS = listOf("application/config", "factory/config")
    }
}