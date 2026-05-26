package com.ghealth.tools.core.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException

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
        try {
            w.write(line)
            w.newLine()
            rowCount++
            if (rowCount % 100 == 0) {
                w.flush()
            }
        } catch (e: IOException) {
            Timber.e(e, "CsvWriter writeRow failed")
        }
    }

    suspend fun writeRawRow(values: List<Any?>) = withContext(Dispatchers.IO) {
        val w = writer ?: return@withContext
        val line = values.joinToString(rule.delimiter) { it?.toString() ?: "" }
        try {
            w.write(line)
            w.newLine()
            rowCount++
            if (rowCount % 100 == 0) {
                w.flush()
            }
        } catch (e: IOException) {
            Timber.e(e, "CsvWriter writeRawRow failed")
        }
    }

    suspend fun flush() = withContext(Dispatchers.IO) {
        writer?.flush()
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        try {
            writer?.flush()
            writer?.close()
        } catch (e: IOException) {
            Timber.e(e, "CsvWriter close failed")
        }
        writer = null
        Timber.d("CsvWriter closed: ${file.absolutePath}, rows=$rowCount")
    }

    val isOpen: Boolean get() = writer != null
    val outputFile: File get() = file
}
