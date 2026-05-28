package com.ghealth.tools.core.network

import android.content.Context
import android.os.Environment
import com.ghealth.tools.core.network.api.DownloadApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadApi: DownloadApi
) {
    private fun getDownloadDir(): File {
        return context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
    }

    suspend fun downloadProdTestConfig(
        configId: Int,
        projectName: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val response = downloadApi.downloadProdTestConfig(configId)
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("下载失败: ${response.code()}"))
            }
            val body = response.body()
                ?: return@withContext Result.failure(Exception("响应体为空"))
            val dir = getDownloadDir()
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "${projectName}_prod_test_config.zip")
            writeToFile(body, file)
            Timber.d("Downloaded prod test config to ${file.absolutePath}")
            Result.success(file)
        } catch (e: Exception) {
            Timber.e(e, "Download prod test config failed")
            Result.failure(e)
        }
    }

    suspend fun downloadRegularConfig(
        configId: Int,
        filename: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val response = downloadApi.downloadRegularConfig(configId)
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("下载失败: ${response.code()}"))
            }
            val body = response.body()
                ?: return@withContext Result.failure(Exception("响应体为空"))
            val dir = getDownloadDir()
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, filename)
            writeToFile(body, file)
            Timber.d("Downloaded regular config to ${file.absolutePath}")
            Result.success(file)
        } catch (e: Exception) {
            Timber.e(e, "Download regular config failed")
            Result.failure(e)
        }
    }

    suspend fun downloadCsvFile(
        fileId: Int,
        filename: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val response = downloadApi.downloadCsvFile(fileId)
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("下载失败: ${response.code()}"))
            }
            val body = response.body()
                ?: return@withContext Result.failure(Exception("响应体为空"))
            val dir = getDownloadDir()
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, filename)
            writeToFile(body, file)
            Timber.d("Downloaded CSV to ${file.absolutePath}")
            Result.success(file)
        } catch (e: Exception) {
            Timber.e(e, "Download CSV file failed")
            Result.failure(e)
        }
    }

    private fun writeToFile(body: okhttp3.ResponseBody, file: File) {
        body.byteStream().use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
    }
}