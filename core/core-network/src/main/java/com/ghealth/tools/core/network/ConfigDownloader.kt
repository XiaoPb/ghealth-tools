package com.ghealth.tools.core.network

import android.content.Context
import com.ghealth.tools.core.network.api.ProjectApi
import com.ghealth.tools.core.network.model.ProductionTestConfigResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectApi: ProjectApi,
    private val tokenManager: TokenManager
) {
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

    private suspend fun downloadConfigFiles(
        config: ProductionTestConfigResponse,
        targetDir: File
    ): File? {
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val token = tokenManager.getAccessToken()
        val client = OkHttpClient.Builder().build()

        val filesToDownload = listOfNotNull(
            config.jsonConfig to "factory_config.json",
            config.baseNoiseConfig to config.baseNoiseConfig?.substringAfterLast("/") ?: "base_noise.config",
            config.lpctrConfig to config.lpctrConfig?.substringAfterLast("/") ?: "lpctr.config",
            config.lplctrConfig to config.lplctrConfig?.substringAfterLast("/") ?: "lplctr.config",
            config.ppgNoiseConfig to config.ppgNoiseConfig?.substringAfterLast("/") ?: "ppg_noise.config"
        ).filter { it.first != null }

        var downloadedJsonFile: File? = null

        for ((url, filename) in filesToDownload) {
            if (url == null) continue
            
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
        val fullUrl = if (url.startsWith("http")) url else "http://192.168.1.100$url"
        
        val requestBuilder = Request.Builder().url(fullUrl)
        if (!token.isNullOrEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        val request = requestBuilder.build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Download failed: ${response.code}")
            }
            
            response.body?.byteStream()?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}
