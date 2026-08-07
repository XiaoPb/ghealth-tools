package com.ghealth.tools.feature.connection

/**
 * 配置列表分组：name 为分组名（在线模式为文件名，离线模式为项目目录名），
 * showHeader 表示该分组是否需要渲染标题。
 */
internal data class ConfigGroup(
    val name: String,
    val configs: List<ConfigFileInfo>,
    val showHeader: Boolean
)

/**
 * 按 displayPath 的目录前缀分组，并判断每个分组是否需要标题。
 * 在线模式 displayPath 只有文件名（无 "/"），每个文件自成一组且组名与文件名相同，
 * 此时渲染标题会让每个配置文件显示两次，因此 showHeader = false。
 * 离线模式 displayPath 为 "项目名/文件名"，组名是真实目录前缀，需要标题。
 */
internal fun groupConfigsForDisplay(configs: List<ConfigFileInfo>): List<ConfigGroup> {
    val grouped = configs.groupBy { it.displayPath.substringBefore("/") }
    return grouped.map { (name, items) ->
        ConfigGroup(
            name = name,
            configs = items,
            showHeader = grouped.size > 1 && items.any { it.displayPath.startsWith("$name/") }
        )
    }
}