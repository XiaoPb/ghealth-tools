package com.ghealth.tools.feature.connection

import java.io.File

/**
 * 离线模式扫描 application/config/{chip}：
 * 1. 芯片目录下直接放置的 .config / .ini（内置默认配置，displayPath 为文件名）；
 * 2. 芯片目录下的项目子目录中的 .config / .ini（兼容已下载到本地的项目配置，displayPath 为 项目名/文件名）。
 */
internal fun scanOfflineAppConfigDir(configDir: File, chip: String): List<ConfigFileInfo> {
    if (!configDir.exists()) return emptyList()
    val configs = mutableListOf<ConfigFileInfo>()
    configDir.listFiles()?.forEach { entry ->
        if (entry.isFile && (entry.name.endsWith(".config") || entry.name.endsWith(".ini"))) {
            configs += ConfigFileInfo(
                fileName = entry.name,
                displayPath = entry.name,
                fullPath = entry,
                chipName = chip
            )
        } else if (entry.isDirectory) {
            entry.listFiles()
                ?.filter { f -> f.isFile && (f.name.endsWith(".config") || f.name.endsWith(".ini")) }
                ?.forEach { file ->
                    configs += ConfigFileInfo(
                        fileName = file.name,
                        displayPath = "${entry.name}/${file.name}",
                        fullPath = file,
                        chipName = chip
                    )
                }
        }
    }
    return configs
}
