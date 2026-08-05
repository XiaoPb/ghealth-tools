package com.ghealth.tools.feature.factory

import com.ghealth.tools.feature.factory.model.RegisterConfig
import com.ghealth.tools.feature.factory.parser.ConfigJsonParser
import com.ghealth.tools.feature.factory.parser.RegisterConfigParser
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 在线模式下，按当前选中项目名加载其独有的产测配置。
 *
 * 只读取 [scanDir]/[projectName] 这一个目录，绝不回退到兄弟项目目录，
 * 从而避免账号下多项目时复用其他项目的配置。
 *
 * @param scanDir 父目录（等同 [com.ghealth.tools.core.network.ConfigPathProvider.getFactoryScanDir]）
 * @param projectName 当前选中项目名（来自 UserPreferences.selectedProjectName）
 * @return 解析成功且包含合法 factory_config.json 时返回 [ProjectConfig]；否则 null
 */
@Singleton
class OnlineProjectConfigLoader @Inject constructor(
    private val configJsonParser: ConfigJsonParser,
    private val registerConfigParser: RegisterConfigParser
) {
    fun load(scanDir: File, projectName: String): ProjectConfig? {
        val projectDir = File(scanDir, projectName)
        if (!projectDir.exists() || !projectDir.isDirectory) return null

        val configFile = projectDir.listFiles()?.firstOrNull { it.extension == "json" } ?: return null
        val jsonContent = configFile.readText()
        val config = configJsonParser.parseOrNull(jsonContent) ?: return null

        val registerConfigs = mutableMapOf<String, RegisterConfig>()
        val allFiles = projectDir.listFiles() ?: emptyArray()
        for ((testKey, testDef) in config.tests) {
            if (!testDef.enabled) continue
            val registerFile = allFiles.firstOrNull { f ->
                f.name.startsWith(testKey, ignoreCase = true) &&
                    (f.extension == "config" || f.extension == "ini")
            }
            if (registerFile != null) {
                val content = registerFile.readText()
                registerConfigs[testKey] = registerConfigParser.parseByChip(
                    content, config.chip, registerFile.name
                )
            }
        }

        return ProjectConfig(
            projectName = config.project,
            chip = config.chip,
            factoryConfig = config,
            registerConfigs = registerConfigs
        )
    }
}
