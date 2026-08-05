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

    @Test
    fun `loads register configs for enabled tests`() {
        writeProjectConfig(
            "ProjectE",
            """
            {
              "project": "ProjectE",
              "chip": "gh3036",
              "tests": {
                "base_noise": {"enabled": true},
                "disabled_test": {"enabled": false}
              }
            }
            """.trimIndent()
        )
        // gh3036 寄存器配置格式：[Register_List] 段 + {0xaddr, 0xvalue} 行
        val projectDir = File(scanDir, "ProjectE")
        File(projectDir, "base_noise.config").writeText(
            """
            [Register_List]
            addr, value, default
            {0x01, 0x02}
            {0x03, 0x04}
            """.trimIndent()
        )

        val result = loader.load(scanDir, "ProjectE")
        assertNotNull(result)
        // enabled 测试项的寄存器配置被加载并正确解析
        val baseNoise = result!!.registerConfigs["base_noise"]
        assertNotNull(baseNoise)
        assertEquals(2, baseNoise!!.registers.size)
        assertEquals(0x01, baseNoise.registers[0].addr)
        assertEquals(0x02, baseNoise.registers[0].value)
        // disabled 测试项被过滤，不加载寄存器配置
        assertNull(result.registerConfigs["disabled_test"])
    }
}
