package com.ghealth.tools.core.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ProdTestSyncMarkerTest {

    @TempDir
    lateinit var targetDir: File

    private fun marker() = ProdTestSyncMarker.forDir(targetDir)

    private fun writeLocalFiles(vararg names: String) {
        names.forEach { File(targetDir, it).writeText("content") }
    }

    private fun writeMarker(state: ProdTestSyncState) {
        marker().write(state)
    }

    @Test
    fun `returns null when marker file does not exist`() {
        assertNull(marker().read())
    }

    @Test
    fun `round trips state through marker file`() {
        writeLocalFiles("factory_config.json", "Base_Noise_TEST1_100Hz.config")

        writeMarker(
            ProdTestSyncState(
                configId = 7,
                uploadedAt = "2026-01-01T00:00:00Z",
                jsonFileName = "factory_config.json",
                fileNames = listOf("factory_config.json", "Base_Noise_TEST1_100Hz.config")
            )
        )

        val state = marker().read()
        assertEquals(7, state!!.configId)
        assertEquals("2026-01-01T00:00:00Z", state.uploadedAt)
        assertEquals("factory_config.json", state.jsonFileName)
        assertEquals(2, state.fileNames.size)
    }

    @Test
    fun `is up to date when config and files match`() {
        writeLocalFiles("factory_config.json", "Base_Noise_TEST1_100Hz.config")
        writeMarker(
            ProdTestSyncState(
                configId = 7,
                uploadedAt = "2026-01-01T00:00:00Z",
                jsonFileName = "factory_config.json",
                fileNames = listOf("factory_config.json", "Base_Noise_TEST1_100Hz.config")
            )
        )

        assertTrue(marker().upToDateState(7, "2026-01-01T00:00:00Z") != null)
    }

    @Test
    fun `not up to date when uploadedAt differs`() {
        writeLocalFiles("factory_config.json")
        writeMarker(
            ProdTestSyncState(
                configId = 7,
                uploadedAt = "2026-01-01T00:00:00Z",
                jsonFileName = "factory_config.json",
                fileNames = listOf("factory_config.json")
            )
        )

        assertNull(marker().upToDateState(7, "2026-02-02T00:00:00Z"))
    }

    @Test
    fun `not up to date when a recorded file is missing`() {
        writeLocalFiles("factory_config.json")
        writeMarker(
            ProdTestSyncState(
                configId = 7,
                uploadedAt = "2026-01-01T00:00:00Z",
                jsonFileName = "factory_config.json",
                fileNames = listOf("factory_config.json", "Base_Noise_TEST1_100Hz.config")
            )
        )

        assertNull(marker().upToDateState(7, "2026-01-01T00:00:00Z"))
    }

    @Test
    fun `returns null for corrupted marker file`() {
        File(targetDir, ".prod_test_sync.meta").writeText("{not valid json")

        assertNull(marker().read())
    }

    @Test
    fun `delete removes marker file`() {
        writeLocalFiles("factory_config.json")
        writeMarker(
            ProdTestSyncState(
                configId = 7,
                uploadedAt = "2026-01-01T00:00:00Z",
                jsonFileName = "factory_config.json",
                fileNames = listOf("factory_config.json")
            )
        )

        marker().delete()

        assertFalse(File(targetDir, ".prod_test_sync.meta").exists())
    }
}