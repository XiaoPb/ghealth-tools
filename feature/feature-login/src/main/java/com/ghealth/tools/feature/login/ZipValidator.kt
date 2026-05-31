package com.ghealth.tools.feature.login

import android.content.Context
import android.net.Uri
import java.util.zip.ZipInputStream

data class ZipValidationResult(
    val isValid: Boolean,
    val matchedFiles: Map<String, String>,
    val missingFields: List<String>,
    val unrecognizedFiles: List<String>,
    val errorMessage: String? = null
)

object ZipValidator {

    private val FILE_PATTERNS = mapOf(
        "json_config" to Regex("^factory_config\\.json$", RegexOption.IGNORE_CASE),
        "base_noise_config" to Regex("^Base_Noise_.+\\.config$", RegexOption.IGNORE_CASE),
        "lpctr_config" to Regex("^LPCTR_.+\\.config$", RegexOption.IGNORE_CASE),
        "lplctr_config" to Regex("^LPLCTR_.+\\.config$", RegexOption.IGNORE_CASE),
        "ppg_noise_config" to Regex("^PPG_Noise_.+\\.config$", RegexOption.IGNORE_CASE),
    )

    private const val MAX_ZIP_SIZE = 50L * 1024 * 1024

    fun validate(context: Context, uri: Uri): ZipValidationResult {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return ZipValidationResult(
                    false, emptyMap(), FILE_PATTERNS.keys.toList(), emptyList(), "无法打开文件"
                )

            inputStream.use { stream ->
                val matched = mutableMapOf<String, String>()
                val unrecognized = mutableListOf<String>()

                ZipInputStream(stream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val fileName = entry.name.substringAfterLast('/')
                            var matchedField: String? = null
                            for ((fieldName, pattern) in FILE_PATTERNS) {
                                if (pattern.matches(fileName) && fieldName !in matched) {
                                    matched[fieldName] = fileName
                                    matchedField = fieldName
                                    break
                                }
                            }
                            if (matchedField == null) {
                                unrecognized.add(fileName)
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }

                val missing = FILE_PATTERNS.keys.filter { it !in matched }

                ZipValidationResult(
                    isValid = missing.isEmpty(),
                    matchedFiles = matched,
                    missingFields = missing,
                    unrecognizedFiles = unrecognized,
                )
            }
        } catch (e: java.util.zip.ZipException) {
            ZipValidationResult(
                false, emptyMap(), FILE_PATTERNS.keys.toList(), emptyList(), "无效的ZIP文件"
            )
        } catch (e: Exception) {
            ZipValidationResult(
                false, emptyMap(), FILE_PATTERNS.keys.toList(), emptyList(), "文件读取失败: ${e.message}"
            )
        }
    }
}