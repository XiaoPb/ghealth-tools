package com.ghealth.tools.core.storage

import android.content.Context
import android.widget.Toast
import com.ghealth.tools.core.datastore.UserPreferences
import com.ghealth.tools.core.network.NetworkMonitor
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
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CsvUploadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val uploadApi: UploadApi,
    private val tokenManager: TokenManager,
    private val userPreferences: UserPreferences,
    private val networkMonitor: NetworkMonitor
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingUploads = mutableListOf<File>()

    fun uploadCsvFile(file: File) {
        scope.launch {
            try {
                val isLoggedIn = tokenManager.isLoggedIn.first()
                val projectId = userPreferences.selectedProjectId.first()
                val isNetworkAvailable = networkMonitor.isNetworkAvailable()

                if (!isNetworkAvailable) {
                    Timber.d("Network not available, adding to pending uploads")
                    pendingUploads.add(file)
                    showToast("无网络连接，稍后自动上传")
                    return@launch
                }

                if (!isLoggedIn) {
                    Timber.d("User not logged in, skipping CSV upload")
                    return@launch
                }

                if (projectId == null || projectId <= 0) {
                    Timber.d("No project selected, skipping CSV upload")
                    return@launch
                }

                if (!file.exists()) {
                    Timber.w("CSV file does not exist: ${file.absolutePath}")
                    return@launch
                }

                uploadFile(file, projectId)
            } catch (e: Exception) {
                Timber.e(e, "CSV upload failed")
                pendingUploads.add(file)
                showToast("CSV上传失败: ${e.message}")
            }
        }
    }

    private suspend fun uploadFile(file: File, projectId: Int) = withContext(Dispatchers.IO) {
        try {
            val mediaType = "text/csv".toMediaTypeOrNull()
            val requestBody = file.asRequestBody(mediaType)
            val part = MultipartBody.Part.createFormData("csv_file", file.name, requestBody)

            val response = uploadApi.uploadCsvFile(projectId, part)

            if (response.isSuccessful && response.body()?.code == 200) {
                Timber.i("CSV uploaded successfully: ${file.name}")
                withContext(Dispatchers.Main) {
                    showToast("CSV已上传: ${file.name}")
                }
                pendingUploads.remove(file)
            } else {
                val errorMsg = response.body()?.message ?: "上传失败: ${response.code()}"
                Timber.w("CSV upload failed: $errorMsg")
                withContext(Dispatchers.Main) {
                    showToast("CSV上传失败: $errorMsg")
                }
                pendingUploads.add(file)
            }
        } catch (e: Exception) {
            Timber.e(e, "CSV upload error")
            throw e
        }
    }

    fun retryPendingUploads() {
        scope.launch {
            val isLoggedIn = tokenManager.isLoggedIn.first()
            val projectId = userPreferences.selectedProjectId.first()
            val isNetworkAvailable = networkMonitor.isNetworkAvailable()

            if (!isNetworkAvailable || !isLoggedIn || projectId == null || projectId <= 0) {
                return@launch
            }

            val filesToRetry = pendingUploads.toList()
            pendingUploads.clear()

            for (file in filesToRetry) {
                try {
                    uploadFile(file, projectId)
                } catch (e: Exception) {
                    Timber.e(e, "Retry upload failed for: ${file.name}")
                }
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
