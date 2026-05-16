package com.ghealth.tools.core.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogManager(private val baseDir: File) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val writers = mutableMapOf<String, BufferedWriter>()

    fun init() {
        cleanOldLogs(7)
    }

    fun logBle(deviceAddress: String, direction: String, data: ByteArray) {
        val date = dateFormat.format(Date())
        val time = timeFormat.format(Date())
        val hex = data.joinToString(" ") { "%02X".format(it) }
        val line = "$time [$direction] $deviceAddress: $hex"
        writeLine("logs/$date/ble_raw_${deviceAddress.replace(":", "")}_$date.log", line)
    }

    fun logProtocol(message: String) {
        val date = dateFormat.format(Date())
        val time = timeFormat.format(Date())
        writeLine("logs/$date/protocol_$date.log", "$time $message")
    }

    fun logApp(level: String, tag: String, message: String) {
        val date = dateFormat.format(Date())
        val time = timeFormat.format(Date())
        writeLine("logs/$date/app_$date.log", "$time [$level] $tag: $message")
    }

    private fun writeLine(relativePath: String, line: String) {
        try {
            val writer = writers.getOrPut(relativePath) {
                val file = File(baseDir, relativePath)
                file.parentFile?.mkdirs()
                BufferedWriter(FileWriter(file, true))
            }
            writer.write(line)
            writer.newLine()
            writer.flush()
        } catch (e: Exception) {
            Timber.w(e, "Failed to write log: $relativePath")
        }
    }

    fun closeAll() {
        writers.values.forEach { it.close() }
        writers.clear()
    }

    private fun cleanOldLogs(retentionDays: Int) {
        val logsDir = File(baseDir, "logs")
        if (!logsDir.exists()) return

        val cutoff = System.currentTimeMillis() - retentionDays * 24 * 60 * 60 * 1000L
        logsDir.listFiles()?.forEach { dayDir ->
            if (dayDir.isDirectory && dayDir.lastModified() < cutoff) {
                dayDir.deleteRecursively()
                Timber.d("Cleaned old logs: ${dayDir.name}")
            }
        }
    }

    suspend fun exportLogs(): File? = withContext(Dispatchers.IO) {
        val logsDir = File(baseDir, "logs")
        if (!logsDir.exists() || logsDir.listFiles().isNullOrEmpty()) return@withContext null

        val zipFile = File(baseDir, "export/logs_${dateFormat.format(Date())}.zip")
        zipFile.parentFile?.mkdirs()

        try {
            java.util.zip.ZipOutputStream(zipFile.outputStream().buffered()).use { zos ->
                logsDir.walkTopDown().filter { it.isFile }.forEach { file ->
                    val entryName = file.relativeTo(logsDir).path.replace('\\', '/')
                    zos.putNextEntry(java.util.zip.ZipEntry(entryName))
                    file.inputStream().buffered().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            Timber.d("Logs exported to ${zipFile.absolutePath}")
            zipFile
        } catch (e: Exception) {
            Timber.w(e, "Failed to export logs")
            null
        }
    }
}
