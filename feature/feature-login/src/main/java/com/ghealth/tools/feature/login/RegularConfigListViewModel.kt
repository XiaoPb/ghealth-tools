package com.ghealth.tools.feature.login

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.core.network.ApiErrorParser
import com.ghealth.tools.core.network.DownloadManager
import com.ghealth.tools.core.network.api.ProjectApi
import com.ghealth.tools.core.network.api.UploadApi
import com.ghealth.tools.core.network.model.RegularConfigResponse
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

data class RegularConfigListUiState(
    val projectId: Int = 0,
    val projectName: String = "",
    val chipModel: String = "gh3036",
    val configs: List<RegularConfigResponse> = emptyList(),
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
    val downloadingConfigId: Int? = null,
    val deletingConfigId: Int? = null,
    val showUploadDialog: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class RegularConfigListViewModel @Inject constructor(
    private val projectApi: ProjectApi,
    private val uploadApi: UploadApi,
    private val downloadManager: DownloadManager,
    private val apiErrorParser: ApiErrorParser,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegularConfigListUiState())
    val uiState: StateFlow<RegularConfigListUiState> = _uiState.asStateFlow()

    fun loadConfigs(projectId: Int, projectName: String, chipModel: String) {
        _uiState.update { it.copy(projectId = projectId, projectName = projectName, chipModel = chipModel) }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val response = projectApi.getRegularConfigs(projectId)
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isLoading = false, configs = response.body()?.data ?: emptyList()) }
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "加载配置失败: ${response.code()}")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Load regular configs failed")
                _uiState.update { it.copy(isLoading = false, errorMessage = "网络错误: ${e.message}") }
            }
        }
    }

    fun uploadConfig() {
        _uiState.update { it.copy(showUploadDialog = true) }
    }

    fun dismissUploadDialog() {
        _uiState.update { it.copy(showUploadDialog = false) }
    }

    fun performUpload(uri: Uri, fileName: String, version: String, description: String, overwrite: Boolean) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, errorMessage = null) }
            try {
                val filePart = uriToPart(uri, fileName)
                val response = uploadApi.uploadRegularConfig(
                    projectId = state.projectId,
                    config_file = filePart,
                    version = version.toRequestBody("text/plain".toMediaTypeOrNull()),
                    description = description.takeIf { it.isNotBlank() }
                        ?.toRequestBody("text/plain".toMediaTypeOrNull()),
                    overwrite = overwrite.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                )
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isUploading = false, showUploadDialog = false) }
                    loadConfigs(state.projectId, state.projectName, state.chipModel)
                } else {
                    val msg = apiErrorParser.parseErrors(response)
                    _uiState.update { it.copy(isUploading = false, errorMessage = msg) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Upload regular config failed")
                _uiState.update {
                    it.copy(isUploading = false, errorMessage = "上传失败: ${e.message}")
                }
            }
        }
    }

    fun downloadConfig(config: RegularConfigResponse) {
        viewModelScope.launch {
            _uiState.update { it.copy(downloadingConfigId = config.id) }
            val result = downloadManager.downloadRegularConfig(config.id, config.filename)
            result.fold(
                onSuccess = { file ->
                    _uiState.update {
                        it.copy(downloadingConfigId = null, successMessage = "下载成功: ${file.absolutePath}")
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(downloadingConfigId = null, errorMessage = "下载失败: ${error.message}")
                    }
                }
            )
        }
    }

    fun deleteConfig(config: RegularConfigResponse) {
        viewModelScope.launch {
            _uiState.update { it.copy(deletingConfigId = config.id) }
            try {
                val response = projectApi.deleteRegularConfig(config.id)
                if (response.isSuccessful) {
                    _uiState.update {
                        it.copy(
                            deletingConfigId = null,
                            configs = it.configs.filter { c -> c.id != config.id },
                            successMessage = "配置「${config.filename}」已删除"
                        )
                    }
                } else {
                    val msg = apiErrorParser.parseErrors(response)
                    _uiState.update { it.copy(deletingConfigId = null, errorMessage = msg) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Delete regular config failed")
                _uiState.update {
                    it.copy(deletingConfigId = null, errorMessage = "删除失败: ${e.message}")
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
            val requestBody = bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull())
            MultipartBody.Part.createFormData("config_file", fileName, requestBody)
        }
    }
}