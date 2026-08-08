package com.ghealth.tools.core.storage

import com.ghealth.tools.core.model.LogLevel
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogManagerTest {

    @TempDir
    lateinit var baseDir: File

    private val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun appLogFile(): File = File(baseDir, "logs/$date/app_$date.log")

    @Test
    fun `saves all levels when threshold is verbose`() {
        val manager = LogManager(baseDir)
        manager.appLogLevel = LogLevel.VERBOSE.priority

        manager.logApp(LogLevel.VERBOSE.priority, "tag", "v msg")
        manager.logApp(LogLevel.DEBUG.priority, "tag", "d msg")
        manager.logApp(LogLevel.INFO.priority, "tag", "i msg")
        manager.logApp(LogLevel.WARN.priority, "tag", "w msg")
        manager.logApp(LogLevel.ERROR.priority, "tag", "e msg")

        val content = appLogFile().readText()
        assertTrue(content.contains("[V]"), content)
        assertTrue(content.contains("[D]"), content)
        assertTrue(content.contains("[I]"), content)
        assertTrue(content.contains("[W]"), content)
        assertTrue(content.contains("[E]"), content)
        manager.closeAll()
    }

    @Test
    fun `filters out logs below warn threshold`() {
        val manager = LogManager(baseDir)
        manager.appLogLevel = LogLevel.WARN.priority

        manager.logApp(LogLevel.VERBOSE.priority, "tag", "v msg")
        manager.logApp(LogLevel.DEBUG.priority, "tag", "d msg")
        manager.logApp(LogLevel.INFO.priority, "tag", "i msg")
        manager.logApp(LogLevel.WARN.priority, "tag", "w msg")
        manager.logApp(LogLevel.ERROR.priority, "tag", "e msg")

        val content = appLogFile().readText()
        assertFalse(content.contains("[V]"), content)
        assertFalse(content.contains("[D]"), content)
        assertFalse(content.contains("[I]"), content)
        assertTrue(content.contains("[W]"), content)
        assertTrue(content.contains("[E]"), content)
        manager.closeAll()
    }

    @Test
    fun `does not create app log file when all logs are filtered`() {
        val manager = LogManager(baseDir)
        manager.appLogLevel = LogLevel.ERROR.priority

        manager.logApp(LogLevel.INFO.priority, "tag", "i msg")

        assertFalse(appLogFile().exists())
    }

    @Test
    fun `defaults to debug threshold`() {
        val manager = LogManager(baseDir)

        manager.logApp(LogLevel.VERBOSE.priority, "tag", "v msg")
        manager.logApp(LogLevel.DEBUG.priority, "tag", "d msg")
        manager.logApp(LogLevel.INFO.priority, "tag", "i msg")

        val content = appLogFile().readText()
        assertFalse(content.contains("[V]"), content)
        assertTrue(content.contains("[D]"), content)
        assertTrue(content.contains("[I]"), content)
        manager.closeAll()
    }
}
