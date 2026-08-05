package com.ghealth.tools.feature.factory

import com.ghealth.tools.feature.factory.parser.ConfigJsonParser
import com.ghealth.tools.feature.factory.parser.RegisterConfigParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class OnlineProjectConfigLoaderTest {

    @TempDir
    lateinit var scanDir: File

    private val loader = OnlineProjectConfigLoader(ConfigJsonParser(), RegisterConfigParser())

    private fun writeProjectConfig(projectName: String, json: String) {
        val projectDir = File(scanDir, projectName).apply { mkdirs() }
        File(projectDir, "factory_config.json").writeText(json)
    }

    @Test
    fun `loads config for the named project`() {
        writeProjectConfig("ProjectA", """{"project":"ProjectA","chip":"gh3036"}""")
        val result = loader.load(scanDir, "ProjectA")
        assertNotNull(result)
        assertEquals("ProjectA", result!!.projectName)
        assertEquals("gh3036", result.chip)
    }

    @Test
    fun `returns null when named project dir does not exist`() {
        writeProjectConfig("ProjectA", """{"project":"ProjectA","chip":"gh3036"}""")
        val result = loader.load(scanDir, "ProjectB")
        assertNull(result)
    }

    @Test
    fun `does not fall back to sibling project when named project has no config`() {
        // ProjectA 有完整配置；ProjectB 存在但为空目录。
        writeProjectConfig("ProjectA", """{"project":"ProjectA","chip":"gh3036"}""")
        File(scanDir, "ProjectB").mkdirs()
        val result = loader.load(scanDir, "ProjectB")
        // 关键保证：绝不能返回 ProjectA 的配置。
        assertNull(result)
    }

    @Test
    fun `returns null when project dir has no json file`() {
        File(scanDir, "ProjectC").mkdirs()
        val result = loader.load(scanDir, "ProjectC")
        assertNull(result)
    }

    @Test
    fun `returns null when project json is invalid`() {
        writeProjectConfig("ProjectD", """{not valid json""")
        val result = loader.load(scanDir, "ProjectD")
        assertNull(result)
    }
}
