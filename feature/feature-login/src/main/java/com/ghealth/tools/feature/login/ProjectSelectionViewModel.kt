package com.ghealth.tools.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.core.datastore.BlePreferences
import com.ghealth.tools.core.datastore.SessionMode
import com.ghealth.tools.core.datastore.UserPreferences
import com.ghealth.tools.core.network.ConfigSyncManager
import com.ghealth.tools.core.network.api.ProjectApi
import com.ghealth.tools.core.network.model.ProjectResponse
import com.ghealth.tools.core.storage.CsvUploadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class ProjectSelectionUiState(
    val projects: List<ProjectResponse> = emptyList(),
    val selectedProject: ProjectResponse? = null,
    val isLoading: Boolean = false,
    val isConfirming: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ProjectSelectionViewModel @Inject constructor(
    private val projectApi: ProjectApi,
    private val userPreferences: UserPreferences,
    private val blePreferences: BlePreferences,
    private val configSyncManager: ConfigSyncManager,
    private val csvUploadManager: CsvUploadManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectSelectionUiState())
    private var loadJob: Job? = null
    val uiState: StateFlow<ProjectSelectionUiState> = _uiState.asStateFlow()

    init {
        loadProjects()
    }

    fun loadProjects(forceReload: Boolean = false) {
        val state = _uiState.value
        if (!forceReload && (state.projects.isNotEmpty() || state.isLoading)) return
        if (loadJob?.isActive == true) {
            Timber.d("loadProjects: already loading, ignoring duplicate call")
            return
        }
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    selectedProject = if (forceReload) null else it.selectedProject,
                    errorMessage = null,
                )
            }
            try {
                val response = projectApi.getProjects()
                if (response.isSuccessful) {
                    val projects = response.body()?.data ?: emptyList()
                    _uiState.update { it.copy(isLoading = false, projects = projects) }
                } else {
                    val errorMsg = "加载项目失败: ${response.code()}"
                    _uiState.update { it.copy(isLoading = false, errorMessage = errorMsg) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Load projects failed")
                _uiState.update { it.copy(isLoading = false, errorMessage = "网络错误: ${e.message ?: "未知错误"}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
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
                blePreferences.setSelectedProjectChip(project.chipModel)
                blePreferences.setSessionMode(SessionMode.ONLINE)
                configSyncManager.fullSync(project.id, project.name)
                _uiState.update { it.copy(isConfirming = false) }
                csvUploadManager.scanAndUploadPending(project.id)
                onSuccess()
            } catch (e: Exception) {
                Timber.e(e, "Sync failed")
                _uiState.update {
                    it.copy(
                        isConfirming = false,
                        errorMessage = "配置同步失败: ${e.message}，可稍后在设置中刷新"
                    )
                }
                csvUploadManager.scanAndUploadPending(project.id)
                onSuccess()
            }
        }
    }
}
