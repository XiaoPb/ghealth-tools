package com.ghealth.tools.core.network

import android.content.Context
import com.ghealth.tools.core.network.api.DownloadApi
import com.ghealth.tools.core.network.api.ProjectApi
import com.ghealth.tools.core.network.model.ProductionTestConfigResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectApi: ProjectApi,
    private val downloadApi: DownloadApi
) {
    private val maxConfigFileSize = 5 * 1024 * 1024L

    suspend fun downloadProductionTestConfig(
        projectId: Int,
        targetDir: File
    ): Result<File?> = withContext(Dispatchers.IO) {
        try {
            val response = projectApi.getProductionTestConfig(projectId)

            if (!response.isSuccessful || response.body()?.data == null) {
                return@withContext Result.failure(
                    Exception(response.body()?.message ?: "获取配置失败: ${response.code()}")
                )
            }

            val config = response.body()!!.data!!
            if (!config.isComplete) {
                // 项目尚未上传完整产测配置：视为正常的空状态（成功但无文件）。
                // 不同项目配置位于独有路径，无需也不应清除任何目录。
                return@withContext Result.success(null)
            }

            val marker = ProdTestSyncMarker.forDir(targetDir)
            val upToDate = marker.upToDateState(config.id, config.uploadedAt.orEmpty())
            if (upToDate != null) {
                Timber.i("Prod-test config unchanged, skipping ZIP download: ${config.projectName}")
                return@withContext Result.success(File(targetDir, upToDate.jsonFileName))
            }

            val downloadedFile = downloadAndUnzipConfig(config, targetDir)
            if (downloadedFile != null) {
                val fileNames = targetDir.listFiles()
                    ?.filter { it.isFile && it.name != ProdTestSyncMarker.MARKER_FILE_NAME }
                    ?.map { it.name }
                    ?: emptyList()
                marker.write(
                    ProdTestSyncState(
                        configId = config.id,
                        uploadedAt = config.uploadedAt.orEmpty(),
                        jsonFileName = downloadedFile.name,
                        fileNames = fileNames
                    )
                )
            }
            Result.success(downloadedFile)
        } catch (e: Exception) {
            Timber.e(e, "Download config failed")
            Result.failure(e)
        }
    }

    suspend fun downloadRegularConfigs(
        projectId: Int,
        targetDir: File
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = projectApi.getRegularConfigs(projectId)

            if (!response.isSuccessful || response.body()?.data == null) {
                return@withContext Result.failure(
                    Exception("获取应用配置列表失败: ${response.code()}")
                )
            }

            val configs = response.body()!!.data!!
            targetDir.mkdirs()

            val plan = RegularConfigSyncPlanner.plan(targetDir, configs)
            for (staleFile in plan.filesToDelete) {
                staleFile.delete()
                Timber.d("Removed stale regular config: ${staleFile.name}")
            }

            for (config in plan.filesToDownload) {
                try {
                    val file = File(targetDir, config.filename)
                    downloadRegularConfigFile(config.id, file)
                    Timber.d("Downloaded regular config: ${config.filename}")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to download regular config: ${config.filename}")
                }
            }

            Timber.i(
                "Regular config sync done: ${plan.filesToDownload.size} downloaded, " +
                    "${plan.skippedCount} skipped, ${plan.filesToDelete.size} removed"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Download regular configs failed")
            Result.failure(e)
        }
    }

    private suspend fun downloadRegularConfigFile(configId: Int, targetFile: File) {
        val response = downloadApi.downloadRegularConfig(configId)
        if (!response.isSuccessful) {
            throw Exception("Download HTTP ${response.code()}")
        }

        val body = response.body()
            ?: throw Exception("响应体为空")

        val contentLength = body.contentLength()
        if (contentLength > maxConfigFileSize) {
            throw Exception("Config file too large: $contentLength bytes")
        }

        body.byteStream().use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    private suspend fun downloadAndUnzipConfig(
        config: ProductionTestConfigResponse,
        targetDir: File
    ): File? {
        val zipResponse = downloadApi.downloadProdTestConfig(config.id)
        if (!zipResponse.isSuccessful) {
            val message = when (zipResponse.code()) {
                403 -> "没有下载权限，请联系管理员"
                else -> "下载 ZIP 失败: ${zipResponse.code()}"
            }
            throw Exception(message)
        }

        val body = zipResponse.body()
            ?: throw Exception("响应体为空")

        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        targetDir.mkdirs()

        val tempZipFile = File(targetDir.parentFile, "temp_${config.id}_config.zip")
        body.byteStream().use { input ->
            FileOutputStream(tempZipFile).use { output ->
                input.copyTo(output)
            }
        }

        var jsonConfigFile: File? = null
        ZipInputStream(tempZipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val fileName = entry.name.substringAfterLast('/')
                    val targetFile = File(targetDir, fileName)
                    if (entry.size > maxConfigFileSize) {
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }
                    FileOutputStream(targetFile).use { output ->
                        zis.copyTo(output)
                    }
                    if (fileName == "factory_config.json") {
                        jsonConfigFile = targetFile
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        tempZipFile.delete()

        return jsonConfigFile
    }
}
