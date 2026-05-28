package com.ghealth.tools.feature.login

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.core.network.ApiErrorParser
import com.ghealth.tools.core.network.DownloadManager
import com.ghealth.tools.core.network.api.ProjectApi
import com.ghealth.tools.core.network.api.UploadApi
import com.ghealth.tools.core.network.model.CsvFileResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import javax.inject.Inject

data class CsvFileManageUiState(
    val projectId: Int = 0,
    val projectName: String = "",
    val csvFiles: List<CsvFileResponse> = emptyList(),
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
    val downloadingFileId: Int? = null,
    val deletingFileId: Int? = null,
    val showUploadDialog: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class CsvFileManageViewModel @Inject constructor(
    private val projectApi: ProjectApi,
    private val uploadApi: UploadApi,
    private val downloadManager: DownloadManager,
    private val apiErrorParser: ApiErrorParser,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(CsvFileManageUiState())
    val uiState: StateFlow<CsvFileManageUiState> = _uiState.asStateFlow()

    fun loadCsvFiles(projectId: Int, projectName: String) {
        _uiState.update { it.copy(projectId = projectId, projectName = projectName) }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val response = projectApi.getCsvFiles(projectId)
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isLoading = false, csvFiles = response.body()?.data ?: emptyList()) }
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "加载CSV文件失败: ${response.code()}")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Load CSV files failed")
                _uiState.update { it.copy(isLoading = false, errorMessage = "网络错误: ${e.message}") }
            }
        }
    }

    fun uploadCsv() {
        _uiState.update { it.copy(showUploadDialog = true) }
    }

    fun dismissUploadDialog() {
        _uiState.update { it.copy(showUploadDialog = false) }
    }

    fun performUpload(uri: Uri, fileName: String, overwrite: Boolean) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, errorMessage = null) }
            try {
                val filePart = uriToPart(uri, fileName)
                val response = uploadApi.uploadCsvFile(
                    projectId = state.projectId,
                    file = filePart,
                    filename = fileName.toRequestBody("text/plain".toMediaTypeOrNull()),
                    overwrite = overwrite.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                )
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isUploading = false, showUploadDialog = false) }
                    loadCsvFiles(state.projectId, state.projectName)
                } else {
                    val msg = apiErrorParser.parseErrors(response)
                    _uiState.update { it.copy(isUploading = false, errorMessage = msg) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Upload CSV failed")
                _uiState.update {
                    it.copy(isUploading = false, errorMessage = "上传失败: ${e.message}")
                }
            }
        }
    }

    fun downloadFile(file: CsvFileResponse) {
        viewModelScope.launch {
            _uiState.update { it.copy(downloadingFileId = file.id) }
            val result = downloadManager.downloadCsvFile(file.id, file.filename)
            result.fold(
                onSuccess = { savedFile ->
                    _uiState.update {
                        it.copy(downloadingFileId = null, successMessage = "下载成功: ${savedFile.absolutePath}")
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(downloadingFileId = null, errorMessage = "下载失败: ${error.message}")
                    }
                }
            )
        }
    }

    fun deleteFile(file: CsvFileResponse) {
        viewModelScope.launch {
            _uiState.update { it.copy(deletingFileId = file.id) }
            try {
                val response = projectApi.deleteCsvFile(file.id)
                if (response.isSuccessful) {
                    _uiState.update {
                        it.copy(
                            deletingFileId = null,
                            csvFiles = it.csvFiles.filter { f -> f.id != file.id },
                            successMessage = "文件「${file.filename}」已删除"
                        )
                    }
                } else {
                    val msg = apiErrorParser.parseErrors(response)
                    _uiState.update { it.copy(deletingFileId = null, errorMessage = msg) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Delete CSV file failed")
                _uiState.update {
                    it.copy(deletingFileId = null, errorMessage = "删除失败: ${e.message}")
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    private suspend fun uriToPart(uri: Uri, fileName: String): MultipartBody.Part {
        return withContext(Dispatchers.IO) {
            val inputStream = appContext.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Cannot open file: $fileName")
            val bytes = inputStream.use { it.readBytes() }
            val requestBody = bytes.toRequestBody("text/csv".toMediaTypeOrNull())
            MultipartBody.Part.createFormData("csv_file", fileName, requestBody)
        }
    }
}