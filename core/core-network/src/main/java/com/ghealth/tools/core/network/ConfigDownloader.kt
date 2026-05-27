package com.ghealth.tools.core.network

import android.content.Context
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
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class ConfigDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectApi: ProjectApi,
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
            val downloadedFile = downloadConfigFiles(config, targetDir)
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

    private suspend fun downloadConfigFiles(
        config: ProductionTestConfigResponse,
        targetDir: File
    ): File? {
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val token = tokenManager.getAccessTokenSync()
        val client = OkHttpClient.Builder().build()

        val filesToDownload = buildList {
            config.jsonConfig?.let { add(it to "factory_config.json") }
            config.baseNoiseConfig?.let { add(it to "base_noise.config") }
            config.lpctrConfig?.let { add(it to "lpctr.config") }
            config.lplctrConfig?.let { add(it to "lplctr.config") }
            config.ppgNoiseConfig?.let { add(it to "ppg_noise.config") }
        }

        var downloadedJsonFile: File? = null

        for ((url, filename) in filesToDownload) {
            try {
                val file = File(targetDir, filename)
                downloadFile(client, url, file, token)

                if (filename == "factory_config.json") {
                    downloadedJsonFile = file
                }
                Timber.d("Downloaded config file: $filename")
            } catch (e: Exception) {
                Timber.e(e, "Failed to download: $filename")
            }
        }

        return downloadedJsonFile
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