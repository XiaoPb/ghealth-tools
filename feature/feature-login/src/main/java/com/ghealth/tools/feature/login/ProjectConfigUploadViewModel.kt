package com.ghealth.tools.feature.login

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.core.network.ApiErrorParser
import com.ghealth.tools.core.network.api.UploadApi
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

data class ConfigUploadUiState(
    val projectId: Int = 0,
    val projectName: String = "",
    val jsonConfigUri: Uri? = null,
    val jsonConfigName: String = "",
    val baseNoiseUri: Uri? = null,
    val baseNoiseName: String = "",
    val lpctrUri: Uri? = null,
    val lpctrName: String = "",
    val lplctrUri: Uri? = null,
    val lplctrName: String = "",
    val ppgNoiseUri: Uri? = null,
    val ppgNoiseName: String = "",
    val hardwareVersion: String = "",
    val testFrequency: String = "100Hz",
    val isUploading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false,
    val uploadMode: UploadMode = UploadMode.INDIVIDUAL_FILES,
    val zipUri: Uri? = null,
    val zipFileName: String = "",
    val zipValidationResult: ZipValidationResult? = null
)

enum class UploadMode { INDIVIDUAL_FILES, ZIP_PACKAGE }

@HiltViewModel
class ProjectConfigUploadViewModel @Inject constructor(
    private val uploadApi: UploadApi,
    private val apiErrorParser: ApiErrorParser,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConfigUploadUiState())
    val uiState: StateFlow<ConfigUploadUiState> = _uiState.asStateFlow()

    fun initProject(projectId: Int, projectName: String) {
        _uiState.update { it.copy(projectId = projectId, projectName = projectName) }
    }

    fun setJsonConfig(uri: Uri, fileName: String) {
        _uiState.update { it.copy(jsonConfigUri = uri, jsonConfigName = fileName) }
    }

    fun setBaseNoiseConfig(uri: Uri, fileName: String) {
        _uiState.update { it.copy(baseNoiseUri = uri, baseNoiseName = fileName) }
    }

    fun setLpctrConfig(uri: Uri, fileName: String) {
        _uiState.update { it.copy(lpctrUri = uri, lpctrName = fileName) }
    }

    fun setLplctrConfig(uri: Uri, fileName: String) {
        _uiState.update { it.copy(lplctrUri = uri, lplctrName = fileName) }
    }

    fun setPpgNoiseConfig(uri: Uri, fileName: String) {
        _uiState.update { it.copy(ppgNoiseUri = uri, ppgNoiseName = fileName) }
    }

    fun updateHardwareVersion(version: String) {
        _uiState.update { it.copy(hardwareVersion = version) }
    }

    fun updateTestFrequency(frequency: String) {
        _uiState.update { it.copy(testFrequency = frequency) }
    }

    fun uploadConfig(onSuccess: () -> Unit) {
        val state = _uiState.value
        when (state.uploadMode) {
            UploadMode.INDIVIDUAL_FILES -> {
                if (state.jsonConfigUri == null) {
                    _uiState.update { it.copy(errorMessage = "请选择 factory_config.json") }
                    return
                }
                uploadIndividualFiles(state, onSuccess)
            }
            UploadMode.ZIP_PACKAGE -> {
                if (state.zipUri == null) {
                    _uiState.update { it.copy(errorMessage = "请选择ZIP文件") }
                    return
                }
                if (state.zipValidationResult?.isValid != true) {
                    _uiState.update { it.copy(errorMessage = "ZIP文件内容不完整，请检查") }
                    return
                }
                uploadZipFile(state, onSuccess)
            }
        }
    }

    fun setUploadMode(mode: UploadMode) {
        _uiState.update { it.copy(uploadMode = mode, zipValidationResult = null) }
    }

    fun setZipFile(uri: Uri, fileName: String) {
        val result = ZipValidator.validate(appContext, uri)
        _uiState.update {
            it.copy(
                zipUri = uri,
                zipFileName = fileName,
                zipValidationResult = result
            )
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun uploadIndividualFiles(state: ConfigUploadUiState, onSuccess: () -> Unit) {

        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, errorMessage = null) }

            try {
                val response = uploadApi.uploadProdTestConfig(
                    projectId = state.projectId,
                    json_config = state.jsonConfigUri?.let { uriToPart(it, "factory_config.json", "application/json") },
                    base_noise_config = state.baseNoiseUri?.let { uriToPart(it, state.baseNoiseName, "application/octet-stream") },
                    lpctr_config = state.lpctrUri?.let { uriToPart(it, state.lpctrName, "application/octet-stream") },
                    lplctr_config = state.lplctrUri?.let { uriToPart(it, state.lplctrName, "application/octet-stream") },
                    ppg_noise_config = state.ppgNoiseUri?.let { uriToPart(it, state.ppgNoiseName, "application/octet-stream") },
                    hardware_version = state.hardwareVersion.takeIf { it.isNotBlank() }
                        ?.toRequestBody("text/plain".toMediaTypeOrNull()),
                    test_frequency = state.testFrequency.toRequestBody("text/plain".toMediaTypeOrNull())
                )

                if (response.isSuccessful) {
                    _uiState.update { it.copy(isUploading = false, isSuccess = true) }
                    onSuccess()
                } else {
                    val errorMsg = apiErrorParser.parseErrors(response)
                    _uiState.update { it.copy(isUploading = false, errorMessage = errorMsg) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Config upload failed")
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        errorMessage = "上传失败: ${e.message ?: "网络错误"}"
                    )
                }
            }
        }
    }

    private fun uploadZipFile(state: ConfigUploadUiState, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, errorMessage = null) }
            try {
                val filePart = uriToPart(
                    state.zipUri!!, state.zipFileName, "application/zip"
                )
                val response = uploadApi.uploadProdTestConfigZip(
                    projectId = state.projectId,
                    zip_file = filePart,
                    hardware_version = state.hardwareVersion.takeIf { it.isNotBlank() }
                        ?.toRequestBody("text/plain".toMediaTypeOrNull()),
                    test_frequency = state.testFrequency.toRequestBody("text/plain".toMediaTypeOrNull())
                )
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isUploading = false, isSuccess = true) }
                    onSuccess()
                } else {
                    val errorMsg = apiErrorParser.parseErrors(response)
                    _uiState.update { it.copy(isUploading = false, errorMessage = errorMsg) }
                }
            } catch (e: Exception) {
                Timber.e(e, "ZIP config upload failed")
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        errorMessage = "上传失败: ${e.message ?: "网络错误"}"
                    )
                }
            }
        }
    }

    private suspend fun uriToPart(uri: Uri, fileName: String, mimeType: String): MultipartBody.Part {
        return withContext(Dispatchers.IO) {
            val inputStream = appContext.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Cannot open file: $fileName")
            val bytes = inputStream.use { it.readBytes() }
            val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            MultipartBody.Part.createFormData(fileName, fileName, requestBody)
        }
    }
}