package com.ghealth.tools.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.core.network.ApiErrorParser
import com.ghealth.tools.core.network.DownloadManager
import com.ghealth.tools.core.network.api.ProjectApi
import com.ghealth.tools.core.network.model.ProductionTestConfigResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class ProdTestConfigManageUiState(
    val projectId: Int = 0,
    val projectName: String = "",
    val chipModel: String = "",
    val config: ProductionTestConfigResponse? = null,
    val isLoading: Boolean = false,
    val isDownloading: Boolean = false,
    val isDeleting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class ProdTestConfigManageViewModel @Inject constructor(
    private val projectApi: ProjectApi,
    private val downloadManager: DownloadManager,
    private val apiErrorParser: ApiErrorParser
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProdTestConfigManageUiState())
    val uiState: StateFlow<ProdTestConfigManageUiState> = _uiState.asStateFlow()

    fun loadConfig(projectId: Int, projectName: String, chipModel: String = "") {
        _uiState.update { it.copy(projectId = projectId, projectName = projectName, chipModel = chipModel) }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val response = projectApi.getProductionTestConfig(projectId)
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isLoading = false, config = response.body()?.data) }
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "加载产测配置失败: ${response.code()}")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Load prod test config failed")
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "网络错误: ${e.message}")
                }
            }
        }
    }

    fun downloadConfig() {
        val state = _uiState.value
        val config = state.config ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true) }
            val result = downloadManager.downloadProdTestConfig(config.id, state.projectName, state.chipModel)
            result.fold(
                onSuccess = { dir ->
                    _uiState.update {
                        it.copy(isDownloading = false, successMessage = "下载成功: ${dir.absolutePath}")
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isDownloading = false, errorMessage = "下载失败: ${error.message}")
                    }
                }
            )
        }
    }

    fun deleteConfig() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            try {
                val response = projectApi.deleteProductionTestConfig(state.projectId)
                if (response.isSuccessful) {
                    _uiState.update {
                        it.copy(isDeleting = false, config = null, successMessage = "产测配置已删除")
                    }
                } else {
                    val msg = apiErrorParser.parseErrors(response)
                    _uiState.update { it.copy(isDeleting = false, errorMessage = msg) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Delete prod test config failed")
                _uiState.update {
                    it.copy(isDeleting = false, errorMessage = "删除失败: ${e.message}")
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}