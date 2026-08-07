package com.ghealth.tools.core.network

import com.ghealth.tools.core.network.model.RegularConfigResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class RegularConfigSyncPlannerTest {

    @TempDir
    lateinit var targetDir: File

    private fun remoteConfig(id: Int, filename: String, fileSize: Long = 1024) =
        RegularConfigResponse(
            id = id,
            project = 1,
            projectName = "项目A",
            configFile = null,
            configFileUrl = null,
            filename = filename,
            version = "1.0.0",
            description = null,
            fileSize = fileSize,
            fileSizeDisplay = null,
            uploadedBy = 1,
            uploadedByName = "admin",
            uploadedAt = "2026-01-01T00:00:00Z"
        )

    private fun localFile(name: String, size: Long = 1024): File {
        val file = File(targetDir, name)
        file.writeBytes(ByteArray(size.toInt()))
        return file
    }

    @Test
    fun `skips download when local file exists with matching name and size`() {
        localFile("gh3036.config", 1024)

        val plan = RegularConfigSyncPlanner.plan(
            targetDir,
            listOf(remoteConfig(1, "gh3036.config", 1024))
        )

        assertEquals(0, plan.filesToDownload.size)
        assertEquals(0, plan.filesToDelete.size)
        assertEquals(1, plan.skippedCount)
    }

    @Test
    fun `downloads file when local file is missing`() {
        val plan = RegularConfigSyncPlanner.plan(
            targetDir,
            listOf(remoteConfig(1, "gh3036.config"))
        )

        assertEquals(listOf(1), plan.filesToDownload.map { it.id })
        assertEquals(0, plan.filesToDelete.size)
        assertEquals(0, plan.skippedCount)
    }

    @Test
    fun `downloads file when local file has different size`() {
        localFile("gh3036.config", 512)

        val plan = RegularConfigSyncPlanner.plan(
            targetDir,
            listOf(remoteConfig(1, "gh3036.config", 1024))
        )

        assertEquals(listOf(1), plan.filesToDownload.map { it.id })
        assertEquals(0, plan.filesToDelete.size)
        assertEquals(0, plan.skippedCount)
    }

    @Test
    fun `deletes local files not present in remote list`() {
        localFile("gh3036.config")
        localFile("stale.config")

        val plan = RegularConfigSyncPlanner.plan(
            targetDir,
            listOf(remoteConfig(1, "gh3036.config"))
        )

        assertEquals(listOf("stale.config"), plan.filesToDelete.map { it.name })
        assertEquals(0, plan.filesToDownload.size)
        assertEquals(1, plan.skippedCount)
    }

    @Test
    fun `deletes all local files when remote list is empty`() {
        localFile("a.config")
        localFile("b.config")

        val plan = RegularConfigSyncPlanner.plan(targetDir, emptyList())

        assertEquals(2, plan.filesToDelete.size)
        assertEquals(0, plan.filesToDownload.size)
        assertEquals(0, plan.skippedCount)
    }

    @Test
    fun `ignores blank filenames in remote list`() {
        val plan = RegularConfigSyncPlanner.plan(
            targetDir,
            listOf(remoteConfig(1, "  "))
        )

        assertEquals(0, plan.filesToDownload.size)
        assertEquals(0, plan.filesToDelete.size)
        assertEquals(0, plan.skippedCount)
    }

    @Test
    fun `mixed case plans partial download and cleanup`() {
        localFile("keep.config", 2048)
        localFile("stale.config")

        val plan = RegularConfigSyncPlanner.plan(
            targetDir,
            listOf(
                remoteConfig(1, "keep.config", 2048),
                remoteConfig(2, "new.config", 4096)
            )
        )

        assertEquals(listOf(2), plan.filesToDownload.map { it.id })
        assertEquals(listOf("stale.config"), plan.filesToDelete.map { it.name })
        assertEquals(1, plan.skippedCount)
    }

    @Test
    fun `rejects non-bare filenames from remote list`() {
        val plan = RegularConfigSyncPlanner.plan(
            targetDir,
            listOf(
                remoteConfig(1, "../evil.config"),
                remoteConfig(2, "a/b.config")
            )
        )

        assertEquals(0, plan.filesToDownload.size)
        assertEquals(0, plan.filesToDelete.size)
        assertEquals(0, plan.skippedCount)
    }

    @Test
    fun `rejects dot and dotdot filenames from remote list`() {
        val plan = RegularConfigSyncPlanner.plan(
            targetDir,
            listOf(remoteConfig(1, "."), remoteConfig(2, ".."))
        )

        assertEquals(0, plan.filesToDownload.size)
        assertEquals(0, plan.filesToDelete.size)
        assertEquals(0, plan.skippedCount)
    }
}
