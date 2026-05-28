package com.ghealth.tools.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.core.network.api.ProjectApi
import com.ghealth.tools.core.network.model.CsvFileResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class CsvFileListUiState(
    val projectId: Int = 0,
    val projectName: String = "",
    val csvFiles: List<CsvFileResponse> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class CsvFileListViewModel @Inject constructor(
    private val projectApi: ProjectApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(CsvFileListUiState())
    val uiState: StateFlow<CsvFileListUiState> = _uiState.asStateFlow()

    fun loadCsvFiles(projectId: Int, projectName: String) {
        _uiState.update { it.copy(projectId = projectId, projectName = projectName) }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val response = projectApi.getCsvFiles(projectId)
                if (response.isSuccessful) {
                    val files = response.body()?.data ?: emptyList()
                    _uiState.update { it.copy(isLoading = false, csvFiles = files) }
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "加载CSV文件失败: ${response.code()}")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Load CSV files failed")
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "网络错误: ${e.message}")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}