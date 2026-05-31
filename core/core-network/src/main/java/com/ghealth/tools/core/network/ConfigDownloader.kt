package com.ghealth.tools.core.network

import android.content.Context
import com.ghealth.tools.core.network.api.DownloadApi
import com.ghealth.tools.core.network.api.ProjectApi
import com.ghealth.tools.core.network.model.ProductionTestConfigResponse
import com.ghealth.tools.core.network.model.RegularConfigResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class ConfigDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectApi: ProjectApi,
    private val downloadApi: DownloadApi,
    private val tokenManager: TokenManager,
    @Named("baseUrl") private val baseUrl: String
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
                return@withContext Result.failure(
                    Exception("产测配置文件尚未上传，请先在网页端上传配置文件")
                )
            }

            val downloadedFile = downloadAndUnzipConfig(config, targetDir)
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
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            val token = tokenManager.getAccessTokenSync()
            val client = OkHttpClient.Builder().build()

            for (config in configs) {
                val configUrl = config.configFileUrl ?: config.configFile ?: continue
                val filename = config.filename
                if (filename.isBlank()) continue

                try {
                    val file = File(targetDir, filename)
                    downloadFile(client, configUrl, file, token)
                    Timber.d("Downloaded regular config: $filename")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to download regular config: $filename")
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Download regular configs failed")
            Result.failure(e)
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

        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

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

    private fun downloadFile(
        client: OkHttpClient,
        url: String,
        targetFile: File,
        token: String?
    ) {
        val baseUrlStripped = baseUrl.trimEnd('/')
        val fullUrl = if (url.startsWith("http")) url else "$baseUrlStripped$url"

        val requestBuilder = Request.Builder().url(fullUrl)
        if (!token.isNullOrEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        val request = requestBuilder.build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Download HTTP ${response.code}")
            }

            val contentLength = response.body?.contentLength() ?: 0L

            if (contentLength > maxConfigFileSize) {
                throw Exception("Config file too large: $contentLength bytes")
            }

            response.body?.byteStream()?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}