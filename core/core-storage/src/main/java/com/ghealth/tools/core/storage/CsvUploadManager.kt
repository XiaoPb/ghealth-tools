package com.ghealth.tools.core.storage

import android.content.Context
import android.widget.Toast
import com.ghealth.tools.core.datastore.UserPreferences
import com.ghealth.tools.core.network.NetworkMonitor
import com.ghealth.tools.core.network.NetworkStatus
import com.ghealth.tools.core.network.TokenManager
import com.ghealth.tools.core.network.api.UploadApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File
import java.util.Collections
import java.util.LinkedHashSet
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class CsvUploadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("storageBaseDir") private val baseDir: File,
    private val uploadApi: UploadApi,
    private val tokenManager: TokenManager,
    private val userPreferences: UserPreferences,
    private val networkMonitor: NetworkMonitor
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingUploads = Collections.synchronizedSet(LinkedHashSet<File>())

    private val maxQueueSize = 20
    private val minFileSize = 500L * 1024
    private val maxFileSize = 50L * 1024 * 1024

    init {
        scope.launch {
            networkMonitor.networkStatus.collect { status ->
                if (status.isAvailable) {
                    retryPendingUploads()
                }
            }
        }
    }

    fun uploadCsvFile(file: File) {
        scope.launch {
            try {
                if (!file.exists()) {
                    Timber.w("CSV file does not exist: ${file.absolutePath}")
                    return@launch
                }

                val fileLength = file.length()
                if (fileLength < minFileSize) {
                    Timber.d("File too small (${fileLength / 1024}KB): ${file.name}, skipping upload")
                    return@launch
                }

                if (fileLength > maxFileSize) {
                    Timber.w("File too large (${fileLength / 1024 / 1024}MB): ${file.name}")
                    showToast("文件过大(${fileLength / 1024 / 1024}MB)，跳过上传: ${file.name}")
                    return@launch
                }

                val isLoggedIn = tokenManager.isLoggedInSync()
                val isNetworkAvailable = networkMonitor.isNetworkAvailable()
                val projectId = userPreferences.selectedProjectId.first()

                if (!isNetworkAvailable || !isLoggedIn || projectId == null || projectId <= 0) {
                    if (!isNetworkAvailable) {
                        Timber.d("Network not available, adding to pending uploads")
                        addToPending(file)
                        showToast("无网络连接，稍后自动上传")
                    }
                    return@launch
                }

                uploadFile(file, projectId)
            } catch (e: Exception) {
                Timber.e(e, "CSV upload failed")
                addToPending(file)
            }
        }
    }

    private suspend fun uploadFile(file: File, projectId: Int) = withContext(Dispatchers.IO) {
        try {
            val mediaType = "text/csv".toMediaTypeOrNull()
            val requestBody = file.asRequestBody(mediaType)
            val part = MultipartBody.Part.createFormData("csv_file", file.name, requestBody)
            val filenameBody = file.name.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = uploadApi.uploadCsvFile(projectId, part, filenameBody)

            if (response.isSuccessful && (response.body()?.code ?: 0) in 200..299) {
                Timber.i("CSV uploaded successfully: ${file.name}")
                pendingUploads.remove(file)

                val renamedFile = renameToUploaded(file)
                val displayName = renamedFile?.name ?: file.name

                withContext(Dispatchers.Main) {
                    showToast("CSV已上传: $displayName")
                }
            } else {
                val errorMsg = response.body()?.message ?: "上传失败: ${response.code()}"
                Timber.w("CSV upload failed: $errorMsg")
                addToPending(file)
                withContext(Dispatchers.Main) {
                    showToast("CSV上传失败: $errorMsg")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "CSV upload error")
            throw e
        }
    }

    private fun addToPending(file: File) {
        synchronized(pendingUploads) {
            if (pendingUploads.size >= maxQueueSize) {
                val first = pendingUploads.firstOrNull()
                if (first != null) {
                    pendingUploads.remove(first)
                    Timber.w("Pending queue full, dropping oldest: ${first.name}")
                }
            }
            pendingUploads.add(file)
        }
    }

    fun retryPendingUploads() {
        scope.launch {
            val isLoggedIn = tokenManager.isLoggedInSync()
            val projectId = userPreferences.selectedProjectId.first()
            val isNetworkAvailable = networkMonitor.isNetworkAvailable()

            if (!isNetworkAvailable || !isLoggedIn || projectId == null || projectId <= 0) {
                return@launch
            }

            val filesToRetry: List<File>
            synchronized(pendingUploads) {
                filesToRetry = pendingUploads.toList()
            }

            for (file in filesToRetry) {
                try {
                    uploadFile(file, projectId)
                } catch (e: Exception) {
                    Timber.e(e, "Retry upload failed for: ${file.name}")
                }
            }
        }
    }

    private fun renameToUploaded(file: File): File? {
        val name = file.name
        if (!name.startsWith("extra_")) return file
        val newName = name.replaceFirst("extra_", "uploaded_")
        val newFile = File(file.parentFile, newName)
        return if (file.renameTo(newFile)) {
            Timber.d("Renamed: ${file.name} -> ${newFile.name}")
            newFile
        } else {
            Timber.w("Failed to rename: ${file.name}")
            null
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun scanAndUploadPending(currentProjectId: Int) {
        scope.launch {
            try {
                val serverDir = File(baseDir, "server")
                if (!serverDir.exists()) return@launch

                val extraFiles = serverDir.walkTopDown()
                    .filter { it.isFile && it.name.startsWith("extra_") && it.extension == "csv" }
                    .toList()

                Timber.i("Scan found ${extraFiles.size} pending CSV files")

                for (file in extraFiles) {
                    val fileProjectId = parseProjectIdFromCsv(file)
                    if (fileProjectId == currentProjectId) {
                        Timber.d("Matched project file: ${file.name}")
                        uploadCsvFile(file)
                    } else {
                        Timber.d("Skipped (project_id=$fileProjectId != $currentProjectId): ${file.name}")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "scanAndUploadPending failed")
            }
        }
    }

    private fun parseProjectIdFromCsv(file: File): Int {
        return try {
            val firstLine = file.bufferedReader().use { it.readLine() } ?: return -1
            if (!firstLine.startsWith("{")) return -1
            val regex = """"project_id"\s*:\s*(\d+)""".toRegex()
            regex.find(firstLine)?.groupValues?.get(1)?.toIntOrNull() ?: -1
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse info from: ${file.name}")
            -1
        }
    }
}