package com.ghealth.tools.core.network

import com.ghealth.tools.core.network.model.RegularConfigResponse
import java.io.File

data class RegularConfigSyncPlan(
    val filesToDelete: List<File>,
    val filesToDownload: List<RegularConfigResponse>,
    val filesSkipped: List<RegularConfigResponse>
) {
    val skippedCount: Int get() = filesSkipped.size
}

object RegularConfigSyncPlanner {

    fun plan(
        targetDir: File,
        remoteConfigs: List<RegularConfigResponse>
    ): RegularConfigSyncPlan {
        val validConfigs = remoteConfigs.filter {
            it.filename.isNotBlank() &&
                it.filename != "." &&
                it.filename != ".." &&
                File(it.filename).name == it.filename
        }
        val remoteNames = validConfigs.map { it.filename }.toSet()

        val existingFiles = targetDir.listFiles()?.filter { it.isFile } ?: emptyList()
        val filesToDelete = existingFiles.filter { it.name !in remoteNames }

        val filesToDownload = validConfigs.filter { config ->
            val local = File(targetDir, config.filename)
            !local.isFile || local.length() != config.fileSize
        }
        val filesSkipped = validConfigs.filter { config ->
            val local = File(targetDir, config.filename)
            local.isFile && local.length() == config.fileSize
        }

        return RegularConfigSyncPlan(
            filesToDelete = filesToDelete,
            filesToDownload = filesToDownload,
            filesSkipped = filesSkipped
        )
    }
}
