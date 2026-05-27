package com.ghealth.tools.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.core.network.ApiErrorParser
import com.ghealth.tools.core.network.api.ProjectApi
import com.ghealth.tools.core.network.model.CreateProjectRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class ProjectCreateUiState(
    val name: String = "",
    val chipModel: String = "gh3036",
    val hardwareVersion: String = "",
    val description: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false
)

@HiltViewModel
class ProjectCreateViewModel @Inject constructor(
    private val projectApi: ProjectApi,
    private val apiErrorParser: ApiErrorParser
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectCreateUiState())
    val uiState: StateFlow<ProjectCreateUiState> = _uiState.asStateFlow()

    fun updateName(name: String) {
        _uiState.update { it.copy(name = name, errorMessage = null) }
    }

    fun updateChipModel(chipModel: String) {
        _uiState.update { it.copy(chipModel = chipModel) }
    }

    fun updateHardwareVersion(version: String) {
        _uiState.update { it.copy(hardwareVersion = version, errorMessage = null) }
    }

    fun updateDescription(description: String) {
        _uiState.update { it.copy(description = description) }
    }

    fun createProject(onSuccess: () -> Unit) {
        val state = _uiState.value

        if (state.name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入项目名称") }
            return
        }
        if (state.hardwareVersion.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入硬件版本") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                val request = CreateProjectRequest(
                    name = state.name.trim(),
                    chipModel = state.chipModel,
                    hardwareVersion = state.hardwareVersion.trim(),
                    description = state.description.trim()
                )
                val response = projectApi.createProject(request)

                if (response.isSuccessful && response.body()?.data != null) {
                    _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                    onSuccess()
                } else {
                    val errorMsg = apiErrorParser.parseErrors(response)
                    _uiState.update { it.copy(isLoading = false, errorMessage = errorMsg) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Create project failed")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "网络错误: ${e.message ?: "未知错误"}"
                    )
                }
            }
        }
    }
}