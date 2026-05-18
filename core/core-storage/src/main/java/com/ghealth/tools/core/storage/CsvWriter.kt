package com.ghealth.tools.core.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

class CsvWriter(
    private val file: File,
    private val rule: CsvRule,
    private val infoJson: String = ""
) {
    private var writer: BufferedWriter? = null
    private var rowCount = 0

    suspend fun open() = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        writer = BufferedWriter(FileWriter(file))
        if (infoJson.isNotEmpty()) {
            writer?.write(infoJson)
            writer?.newLine()
        }
        writer?.write(rule.columns.joinToString(rule.delimiter))
        writer?.newLine()
        rowCount = 0
        Timber.d("CsvWriter opened: ${file.absolutePath}")
    }

    suspend fun writeRow(values: Map<String, Any?>) = withContext(Dispatchers.IO) {
        val w = writer ?: return@withContext
        val line = rule.columns.joinToString(rule.delimiter) { col ->
            values[col]?.toString() ?: ""
        }
        w.write(line)
        w.newLine()
        rowCount++
        if (rowCount % 100 == 0) {
            w.flush()
        }
    }

    suspend fun writeRawRow(values: List<Any?>) = withContext(Dispatchers.IO) {
        val w = writer ?: return@withContext
        val line = values.joinToString(rule.delimiter) { it?.toString() ?: "" }
        w.write(line)
        w.newLine()
        rowCount++
        if (rowCount % 100 == 0) {
            w.flush()
        }
    }

    suspend fun flush() = withContext(Dispatchers.IO) {
        writer?.flush()
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        writer?.flush()
        writer?.close()
        writer = null
        Timber.d("CsvWriter closed: ${file.absolutePath}, rows=$rowCount")
    }

    val isOpen: Boolean get() = writer != null
}
