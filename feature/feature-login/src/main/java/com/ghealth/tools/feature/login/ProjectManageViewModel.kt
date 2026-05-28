package com.ghealth.tools.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.core.network.ApiErrorParser
import com.ghealth.tools.core.network.api.ProjectApi
import com.ghealth.tools.core.network.model.ProjectResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class ProjectManageUiState(
    val projects: List<ProjectResponse> = emptyList(),
    val isLoading: Boolean = false,
    val isDeleting: Boolean = false,
    val deletingProjectId: Int? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class ProjectManageViewModel @Inject constructor(
    private val projectApi: ProjectApi,
    private val apiErrorParser: ApiErrorParser
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectManageUiState())
    val uiState: StateFlow<ProjectManageUiState> = _uiState.asStateFlow()

    init {
        loadProjects()
    }

    fun loadProjects() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val response = projectApi.getProjects()
                if (response.isSuccessful) {
                    val projects = response.body()?.data ?: emptyList()
                    _uiState.update { it.copy(isLoading = false, projects = projects) }
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "加载项目失败: ${response.code()}")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Load projects failed")
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "网络错误: ${e.message}")
                }
            }
        }
    }

    fun deleteProject(project: ProjectResponse) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, deletingProjectId = project.id) }
            try {
                val response = projectApi.deleteProject(project.id)
                if (response.isSuccessful) {
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            deletingProjectId = null,
                            projects = it.projects.filter { p -> p.id != project.id },
                            successMessage = "项目「${project.name}」已删除"
                        )
                    }
                } else {
                    val msg = when (response.code()) {
                        403 -> "无权限删除此项目"
                        404 -> "项目不存在或已被删除"
                        else -> "删除失败(${response.code()})"
                    }
                    _uiState.update {
                        it.copy(isDeleting = false, deletingProjectId = null, errorMessage = msg)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Delete project failed")
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        deletingProjectId = null,
                        errorMessage = "删除失败: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}