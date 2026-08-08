package com.ghealth.tools.core.storage

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

class DefaultConfigInstallerTest {

    @TempDir
    lateinit var assetsRoot: File

    @TempDir
    lateinit var baseDir: File

    private class FileAssetSource(private val root: File) : DefaultConfigAssetSource {
        override fun list(path: String): Array<String>? {
            val dir = File(root, path)
            return if (dir.isDirectory) dir.list() else null
        }

        override fun open(path: String): java.io.InputStream =
            File(root, path).inputStream()
    }

    private class FailingOpenAssetSource(private val root: File) : DefaultConfigAssetSource {
        override fun list(path: String): Array<String>? {
            val dir = File(root, path)
            return if (dir.isDirectory) dir.list() else null
        }

        override fun open(path: String): java.io.InputStream {
            throw IOException("boom")
        }
    }

    @Test
    fun `installs bundled configs into base dir`() = runTest {
        File(assetsRoot, "application/config/gh3036/base_noise.config").apply {
            parentFile.mkdirs()
            writeText("content")
        }
        val installer = DefaultConfigInstaller(FileAssetSource(assetsRoot), baseDir)

        val summary = installer.install()

        assertEquals(1, summary.copiedFiles)
        assertTrue(File(baseDir, "application/config/gh3036/base_noise.config").exists())
    }

    @Test
    fun `does not throw when asset open fails`() = runTest {
        File(assetsRoot, "factory/config/gh3036/ProjA/factory_config.json").apply {
            parentFile.mkdirs()
            writeText("content")
        }
        val installer = DefaultConfigInstaller(FailingOpenAssetSource(assetsRoot), baseDir)

        val summary = installer.install()

        assertEquals(0, summary.copiedFiles)
        assertTrue(summary.failedFiles > 0)
    }
}
