package com.ghealth.tools.core.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DefaultConfigCopierTest {

    @TempDir
    lateinit var assetsRoot: File

    @TempDir
    lateinit var baseDir: File

    /** 用本地临时目录模拟 assets，测试 DefaultConfigCopier 的纯 JVM 逻辑。 */
    private class FileAssetSource(private val root: File) : DefaultConfigAssetSource {
        override fun list(path: String): Array<String>? {
            val dir = File(root, path)
            return if (dir.isDirectory) dir.list() else null
        }

        override fun open(path: String): java.io.InputStream =
            File(root, path).inputStream()
    }

    private fun copier() = DefaultConfigCopier(FileAssetSource(assetsRoot))

    private fun writeAsset(relativePath: String, content: String = "content") {
        val file = File(assetsRoot, relativePath)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    @Test
    fun `copies application config files preserving relative path`() {
        writeAsset("application/config/gh3036/base_noise.config")
        writeAsset("application/config/gh3036/hrp.ini")

        val summary = copier().copyTo(baseDir)

        assertEquals(2, summary.copiedFiles)
        assertEquals(0, summary.skippedFiles)
        assertTrue(File(baseDir, "application/config/gh3036/base_noise.config").exists())
        assertTrue(File(baseDir, "application/config/gh3036/hrp.ini").exists())
    }

    @Test
    fun `copies factory config files preserving relative path`() {
        writeAsset("factory/config/gh3036/L-EVK-T2-GH3038Q/factory_config.json")
        writeAsset("factory/config/gh3036/L-EVK-T2-GH3038Q/base_noise.config")

        val summary = copier().copyTo(baseDir)

        assertEquals(2, summary.copiedFiles)
        assertTrue(File(baseDir, "factory/config/gh3036/L-EVK-T2-GH3038Q/factory_config.json").exists())
        assertTrue(File(baseDir, "factory/config/gh3036/L-EVK-T2-GH3038Q/base_noise.config").exists())
    }

    @Test
    fun `skips files that already exist in destination`() {
        writeAsset("application/config/gh3036/base_noise.config", "new")
        val target = File(baseDir, "application/config/gh3036/base_noise.config")
        target.parentFile.mkdirs()
        target.writeText("user-version")

        val summary = copier().copyTo(baseDir)

        assertEquals(0, summary.copiedFiles)
        assertEquals(1, summary.skippedFiles)
        assertEquals("user-version", target.readText())
    }

    @Test
    fun `ignores files whose extension is not config ini or json`() {
        writeAsset("application/config/gh3036/README.txt")
        writeAsset("application/config/gh3300/.gitkeep")

        val summary = copier().copyTo(baseDir)

        assertEquals(0, summary.copiedFiles)
        assertFalse(File(baseDir, "application/config/gh3036/README.txt").exists())
        assertFalse(File(baseDir, "application/config/gh3300/.gitkeep").exists())
    }

    @Test
    fun `returns zero when asset root is missing`() {
        val summary = copier().copyTo(baseDir)
        assertEquals(0, summary.copiedFiles)
        assertEquals(0, summary.skippedFiles)
    }
}