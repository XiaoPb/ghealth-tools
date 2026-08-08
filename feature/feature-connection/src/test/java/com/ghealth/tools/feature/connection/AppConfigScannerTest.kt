package com.ghealth.tools.feature.connection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AppConfigScannerTest {

    @TempDir
    lateinit var chipDir: File

    @Test
    fun `lists config files directly under chip dir`() {
        File(chipDir, "base_noise.config").writeText("x")
        File(chipDir, "hrp.ini").writeText("x")

        val result = scanOfflineAppConfigDir(chipDir, "gh3036")

        assertEquals(listOf("base_noise.config", "hrp.ini"), result.map { it.fileName })
        assertTrue(result.all { it.displayPath == it.fileName })
        assertTrue(result.all { it.chipName == "gh3036" })
    }

    @Test
    fun `lists config files inside project subdirectories with prefixed display path`() {
        val projectDir = File(chipDir, "ProjectA").apply { mkdirs() }
        File(projectDir, "lpctr.config").writeText("x")

        val result = scanOfflineAppConfigDir(chipDir, "gh3036")

        assertEquals(1, result.size)
        assertEquals("lpctr.config", result[0].fileName)
        assertEquals("ProjectA/lpctr.config", result[0].displayPath)
        assertEquals("gh3036", result[0].chipName)
    }

    @Test
    fun `ignores non config files and does not recurse deeper than one level`() {
        File(chipDir, "notes.txt").writeText("x")
        File(chipDir, "EmptyDir").mkdirs()
        val nested = File(chipDir, "ProjectB").apply { mkdirs() }
        File(nested, "deep").mkdirs()
        File(File(nested, "deep"), "inner.config").writeText("x")

        val result = scanOfflineAppConfigDir(chipDir, "gh3036")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `returns empty list when dir does not exist`() {
        val result = scanOfflineAppConfigDir(File(chipDir, "missing"), "gh3036")
        assertTrue(result.isEmpty())
    }
}
