package com.ghealth.tools.core.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

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

    /** 对指定路径 open() 抛 IOException 的 fake source，验证单文件失败不中断整棵复制。 */
    private class FailingOpenAssetSource(
        private val root: File,
        private val failPaths: Set<String>
    ) : DefaultConfigAssetSource {
        override fun list(path: String): Array<String>? {
            val dir = File(root, path)
            return if (dir.isDirectory) dir.list() else null
        }

        override fun open(path: String): java.io.InputStream {
            if (path in failPaths) throw IOException("Simulated failure: $path")
            return File(root, path).inputStream()
        }
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
        assertEquals(0, summary.failedFiles)
        assertTrue(File(baseDir, "application/config/gh3036/base_noise.config").exists())
        assertTrue(File(baseDir, "application/config/gh3036/hrp.ini").exists())
    }

    @Test
    fun `copies factory config files preserving relative path`() {
        writeAsset("factory/config/gh3036/L-EVK-T2-GH3038Q/factory_config.json")
        writeAsset("factory/config/gh3036/L-EVK-T2-GH3038Q/base_noise.config")

        val summary = copier().copyTo(baseDir)

        assertEquals(2, summary.copiedFiles)
        assertEquals(0, summary.skippedFiles)
        assertEquals(0, summary.failedFiles)
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
        assertEquals(0, summary.failedFiles)
        assertEquals("user-version", target.readText())
    }

    @Test
    fun `ignores files whose extension is not config ini or json`() {
        writeAsset("application/config/gh3036/README.txt")
        writeAsset("application/config/gh3300/.gitkeep")

        val summary = copier().copyTo(baseDir)

        assertEquals(0, summary.copiedFiles)
        assertEquals(0, summary.skippedFiles)
        assertEquals(0, summary.failedFiles)
        assertFalse(File(baseDir, "application/config/gh3036/README.txt").exists())
        assertFalse(File(baseDir, "application/config/gh3300/.gitkeep").exists())
    }

    @Test
    fun `returns zero when asset root is missing`() {
        val summary = copier().copyTo(baseDir)

        assertEquals(0, summary.copiedFiles)
        assertEquals(0, summary.skippedFiles)
        assertEquals(0, summary.failedFiles)
    }

    @Test
    fun `directory names containing dots are treated as directories`() {
        writeAsset("application/config/gh3036/v1.2/probe.config")

        val summary = copier().copyTo(baseDir)

        assertEquals(1, summary.copiedFiles)
        assertEquals(0, summary.skippedFiles)
        assertEquals(0, summary.failedFiles)
        assertTrue(File(baseDir, "application/config/gh3036/v1.2/probe.config").exists())
    }

    @Test
    fun `failed file copy does not abort remaining files`() {
        writeAsset("application/config/gh3036/base_noise.config")
        writeAsset("application/config/gh3036/broken.config")
        writeAsset("application/config/gh3036/hrp.ini")

        val summary = DefaultConfigCopier(
            FailingOpenAssetSource(assetsRoot, setOf("application/config/gh3036/broken.config"))
        ).copyTo(baseDir)

        assertEquals(2, summary.copiedFiles)
        assertEquals(0, summary.skippedFiles)
        assertEquals(1, summary.failedFiles)
        assertTrue(File(baseDir, "application/config/gh3036/base_noise.config").exists())
        assertTrue(File(baseDir, "application/config/gh3036/hrp.ini").exists())
        assertFalse(File(baseDir, "application/config/gh3036/broken.config").exists())
    }

    @Test
    fun `matches config extensions case-insensitively`() {
        writeAsset("application/config/gh3036/PROBE.CONFIG")

        val summary = copier().copyTo(baseDir)

        assertEquals(1, summary.copiedFiles)
        assertEquals(0, summary.skippedFiles)
        assertEquals(0, summary.failedFiles)
        assertTrue(File(baseDir, "application/config/gh3036/PROBE.CONFIG").exists())
    }

    @Test
    fun `failed file in nested directory does not abort siblings or other roots`() {
        writeAsset("application/config/gh3036/v1.2/probe.config")
        writeAsset("application/config/gh3036/v1.2/broken.config")
        writeAsset("factory/config/gh3036/factory_config.json")

        val summary = DefaultConfigCopier(
            FailingOpenAssetSource(assetsRoot, setOf("application/config/gh3036/v1.2/broken.config"))
        ).copyTo(baseDir)

        assertEquals(2, summary.copiedFiles)
        assertEquals(0, summary.skippedFiles)
        assertEquals(1, summary.failedFiles)
        assertTrue(File(baseDir, "application/config/gh3036/v1.2/probe.config").exists())
        assertTrue(File(baseDir, "factory/config/gh3036/factory_config.json").exists())
    }
}
