package com.ghealth.tools.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.core.datastore.UserPreferences
import com.ghealth.tools.core.network.api.ProjectApi
import com.ghealth.tools.core.network.model.ProjectResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class ProjectSelectionUiState(
    val username: String = "",
    val projects: List<ProjectResponse> = emptyList(),
    val selectedProject: ProjectResponse? = null,
    val isLoading: Boolean = true,
    val isConfirming: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ProjectSelectionViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val projectApi: ProjectApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectSelectionUiState())
    val uiState: StateFlow<ProjectSelectionUiState> = _uiState.asStateFlow()

    init {
        loadUserInfo()
        loadProjects()
    }

    private fun loadUserInfo() {
        viewModelScope.launch {
            val userInfo = userPreferences.userInfo.first()
            _uiState.update { it.copy(username = userInfo.username) }
        }
    }

    fun loadProjects() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                val response = projectApi.getProjects()
                
                if (response.isSuccessful && response.body() != null) {
                    val projects = response.body()!!.results
                    _uiState.update { 
                        it.copy(
                            projects = projects,
                            isLoading = false
                        )
                    }
                } else {
                    val errorMsg = "加载项目失败: ${response.code()}"
                    _uiState.update { it.copy(isLoading = false, errorMessage = errorMsg) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Load projects failed")
                val errorMsg = "网络错误: ${e.message ?: "未知错误"}"
                _uiState.update { it.copy(isLoading = false, errorMessage = errorMsg) }
            }
        }
    }

    fun selectProject(project: ProjectResponse) {
        _uiState.update { it.copy(selectedProject = project) }
    }

    fun confirmSelection(onSuccess: () -> Unit) {
        val project = _uiState.value.selectedProject ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isConfirming = true) }

            try {
                userPreferences.setSelectedProject(project.id, project.name)
                _uiState.update { it.copy(isConfirming = false) }
                onSuccess()
            } catch (e: Exception) {
                Timber.e(e, "Save project selection failed")
                _uiState.update { it.copy(isConfirming = false, errorMessage = "保存失败: ${e.message}") }
            }
        }
    }
}
