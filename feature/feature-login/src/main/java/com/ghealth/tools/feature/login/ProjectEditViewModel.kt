package com.ghealth.tools.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.core.network.ApiErrorParser
import com.ghealth.tools.core.network.api.ProjectApi
import com.ghealth.tools.core.network.model.UpdateProjectRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class ProjectEditUiState(
    val projectId: Int = 0,
    val name: String = "",
    val chipModel: String = "gh3036",
    val hardwareVersion: String = "",
    val description: String = "",
    val testFrequency: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false
)

@HiltViewModel
class ProjectEditViewModel @Inject constructor(
    private val projectApi: ProjectApi,
    private val apiErrorParser: ApiErrorParser
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectEditUiState())
    val uiState: StateFlow<ProjectEditUiState> = _uiState.asStateFlow()

    fun loadProject(projectId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, projectId = projectId) }
            try {
                val response = projectApi.getProject(projectId)
                if (response.isSuccessful && response.body()?.data != null) {
                    val project = response.body()!!.data!!
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            name = project.name,
                            chipModel = project.chipModel,
                            hardwareVersion = project.hardwareVersion,
                            description = project.description ?: "",
                            testFrequency = project.testFrequency ?: ""
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "加载项目失败: ${response.code()}")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Load project failed")
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "网络错误: ${e.message}")
                }
            }
        }
    }

    fun updateName(name: String) {
        _uiState.update { it.copy(name = name, errorMessage = null) }
    }

    fun updateChipModel(chipModel: String) {
        _uiState.update { it.copy(chipModel = chipModel) }
    }

    fun updateHardwareVersion(version: String) {
        _uiState.update { it.copy(hardwareVersion = version) }
    }

    fun updateDescription(description: String) {
        _uiState.update { it.copy(description = description) }
    }

    fun updateTestFrequency(frequency: String) {
        _uiState.update { it.copy(testFrequency = frequency) }
    }

    fun saveProject(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "项目名称不能为空") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                val request = UpdateProjectRequest(
                    name = state.name.trim(),
                    chipModel = state.chipModel,
                    hardwareVersion = state.hardwareVersion.trim(),
                    description = state.description.trim(),
                    testFrequency = state.testFrequency.trim()
                )
                val response = projectApi.updateProject(state.projectId, request)
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isSaving = false, isSuccess = true) }
                    onSuccess()
                } else {
                    val errorMsg = apiErrorParser.parseErrors(response)
                    _uiState.update { it.copy(isSaving = false, errorMessage = errorMsg) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Update project failed")
                _uiState.update {
                    it.copy(isSaving = false, errorMessage = "保存失败: ${e.message}")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}