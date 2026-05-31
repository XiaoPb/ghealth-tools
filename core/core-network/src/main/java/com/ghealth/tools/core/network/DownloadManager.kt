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
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadApi: DownloadApi,
    private val configPathProvider: ConfigPathProvider
) {
    private fun getDownloadDir(): File {
        return context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
    }

    suspend fun downloadProdTestConfig(
        configId: Int,
        projectName: String,
        chip: String = ""
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val response = downloadApi.downloadProdTestConfig(configId)
            if (!response.isSuccessful) {
                val message = when (response.code()) {
                    404 -> "配置文件尚未上传，请先在网页端上传产测配置文件"
                    else -> "下载失败: ${response.code()}"
                }
                return@withContext Result.failure(Exception(message))
            }
            val body = response.body()
                ?: return@withContext Result.failure(Exception("响应体为空"))

            val zipFile = File(getDownloadDir(), "${projectName}_prod_test_config.zip")
            writeToFile(body, zipFile)
            Timber.d("Downloaded prod test config to ${zipFile.absolutePath}")

            val targetDir = configPathProvider.getFactoryConfigDir(chip, projectName)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            unzipFile(zipFile, targetDir)
            zipFile.delete()
            Timber.d("Extracted prod test config to ${targetDir.absolutePath}")

            Result.success(targetDir)
        } catch (e: Exception) {
            Timber.e(e, "Download prod test config failed")
            Result.failure(e)
        }
    }

    suspend fun downloadProdTestConfigFile(
        configId: Int,
        field: String,
        filename: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val response = downloadApi.downloadProdTestConfigFile(configId, field)
            if (!response.isSuccessful) {
                val message = when (response.code()) {
                    404 -> "文件不存在"
                    400 -> "无效的文件字段: $field"
                    else -> "下载失败: ${response.code()}"
                }
                return@withContext Result.failure(Exception(message))
            }
            val body = response.body()
                ?: return@withContext Result.failure(Exception("响应体为空"))
            val dir = getDownloadDir()
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, filename)
            writeToFile(body, file)
            Timber.d("Downloaded prod test config file $field to ${file.absolutePath}")
            Result.success(file)
        } catch (e: Exception) {
            Timber.e(e, "Download prod test config file failed")
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
                val message = when (response.code()) {
                    404 -> "配置文件不存在或已被删除"
                    else -> "下载失败: ${response.code()}"
                }
                return@withContext Result.failure(Exception(message))
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
                val message = when (response.code()) {
                    404 -> "CSV文件不存在或已被删除"
                    else -> "下载失败: ${response.code()}"
                }
                return@withContext Result.failure(Exception(message))
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

    private fun unzipFile(zipFile: File, targetDir: File) {
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val fileName = entry.name.substringAfterLast('/')
                    val targetFile = File(targetDir, fileName)
                    FileOutputStream(targetFile).use { output ->
                        zis.copyTo(output)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}