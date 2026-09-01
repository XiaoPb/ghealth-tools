package com.ghealth.tools.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.core.datastore.BlePreferences
import com.ghealth.tools.core.network.ApiErrorParser
import com.ghealth.tools.core.network.api.ProjectApi
import com.ghealth.tools.core.datastore.UserPreferences
import com.ghealth.tools.core.network.model.ArchiveActionRequest
import com.ghealth.tools.core.network.model.ProjectResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class ProjectManageUiState(
    val projects: List<ProjectResponse> = emptyList(),
    val archivedProjects: List<ProjectResponse> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingArchived: Boolean = false,
    val isDeleting: Boolean = false,
    val deletingProjectId: Int? = null,
    val isArchiving: Boolean = false,
    val archivingProjectId: Int? = null,
    val isExporting: Boolean = false,
    val exportingProjectId: Int? = null,
    val isVerifyingPassword: Boolean = false,
    val errorMessage: String? = null,
    val archivedErrorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class ProjectManageViewModel @Inject constructor(
    private val projectApi: ProjectApi,
    private val apiErrorParser: ApiErrorParser,
    private val userPreferences: UserPreferences,
    private val blePreferences: BlePreferences,
) : ViewModel() {

    suspend fun verifyPassword(password: String): Boolean {
        if (_uiState.value.isVerifyingPassword || _uiState.value.isDeleting || _uiState.value.isArchiving) {
            return false
        }
        if (password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入当前密码") }
            return false
        }
        _uiState.update { it.copy(isVerifyingPassword = true, errorMessage = null) }
        return try {
            val verified = userPreferences.verifySessionPassword(password)
            if (!verified) _uiState.update { it.copy(errorMessage = "密码错误") }
            verified
        } finally {
            _uiState.update { it.copy(isVerifyingPassword = false) }
        }
    }

    private val _uiState = MutableStateFlow(ProjectManageUiState())
    val uiState: StateFlow<ProjectManageUiState> = _uiState.asStateFlow()
    private var archivedLoadJob: Job? = null

    init {
        loadProjects()
        loadArchivedProjects()
    }

    fun loadArchivedProjects() {
        archivedLoadJob?.cancel()
        archivedLoadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingArchived = true, archivedErrorMessage = null) }
            try {
                val response = projectApi.getProjects(archived = 1)
                if (response.isSuccessful) {
                    val projects = response.body()?.data ?: emptyList()
                    _uiState.update {
                        it.copy(
                            isLoadingArchived = false,
                            archivedProjects = projects,
                            archivedErrorMessage = null,
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoadingArchived = false,
                            archivedErrorMessage = "加载已归档项目失败: ${response.code()}"
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Load archived projects failed")
                _uiState.update {
                    it.copy(
                        isLoadingArchived = false,
                        archivedErrorMessage = "网络错误: ${e.message}"
                    )
                }
            }
        }
    }

    fun loadProjects() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val response = projectApi.getProjects()
                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse == null) {
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = "服务器返回数据为空")
                        }
                    } else {
                        val projects = apiResponse.data ?: emptyList()
                        _uiState.update { it.copy(isLoading = false, projects = projects) }
                    }
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

    fun deleteProject(project: ProjectResponse, onSuccess: (wasCurrentProject: Boolean) -> Unit = {}) {
        if (_uiState.value.isDeleting || _uiState.value.isArchiving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, deletingProjectId = project.id) }
            try {
                val response = projectApi.deleteProject(project.id)
                if (response.isSuccessful) {
                    val wasCurrentProject = clearProjectSessionIfCurrent(project.id)
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            deletingProjectId = null,
                            projects = it.projects.filter { p -> p.id != project.id },
                            archivedProjects = (it.archivedProjects + project).distinctBy { p -> p.id },
                            successMessage = "项目「${project.name}」已归档"
                        )
                    }
                    loadArchivedProjects()
                    onSuccess(wasCurrentProject)
                } else {
                    val msg = when (response.code()) {
                        403 -> "无权限归档此项目"
                        404 -> "项目不存在或已归档"
                        else -> "归档失败(${response.code()})"
                    }
                    _uiState.update {
                        it.copy(isDeleting = false, deletingProjectId = null, errorMessage = msg)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Archive project via DELETE failed")
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        deletingProjectId = null,
                        errorMessage = "归档失败: ${e.message}"
                    )
                }
            }
        }
    }

    fun archiveProject(project: ProjectResponse, onSuccess: (wasCurrentProject: Boolean) -> Unit = {}) {
        if (_uiState.value.isDeleting || _uiState.value.isArchiving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isArchiving = true, archivingProjectId = project.id) }
            try {
                val response = projectApi.projectAction(project.id, ArchiveActionRequest("archive"))
                if (response.isSuccessful) {
                    val wasCurrentProject = clearProjectSessionIfCurrent(project.id)
                    _uiState.update {
                        it.copy(
                            isArchiving = false,
                            archivingProjectId = null,
                            projects = it.projects.filter { p -> p.id != project.id },
                            archivedProjects = (it.archivedProjects + project).distinctBy { p -> p.id },
                            successMessage = "项目「${project.name}」已归档"
                        )
                    }
                    loadArchivedProjects()
                    onSuccess(wasCurrentProject)
                } else {
                    val msg = when (response.code()) {
                        403 -> "无权限操作此项目"
                        404 -> "项目不存在"
                        else -> "归档失败(${response.code()})"
                    }
                    _uiState.update {
                        it.copy(isArchiving = false, archivingProjectId = null, errorMessage = msg)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Archive project failed")
                _uiState.update {
                    it.copy(isArchiving = false, archivingProjectId = null, errorMessage = "归档失败: ${e.message}")
                }
            }
        }
    }

    private suspend fun clearProjectSessionIfCurrent(projectId: Int): Boolean {
        val selectedProjectId = userPreferences.selectedProjectId.first()
        if (!isCurrentProject(projectId, selectedProjectId)) return false
        userPreferences.clearSelectedProject()
        blePreferences.clearSelectedProjectChip()
        return true
    }

    fun restoreProject(project: ProjectResponse) {
        if (_uiState.value.isDeleting || _uiState.value.isArchiving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isArchiving = true, archivingProjectId = project.id) }
            try {
                val response = projectApi.projectAction(project.id, ArchiveActionRequest("restore"))
                if (response.isSuccessful) {
                    _uiState.update {
                        it.copy(
                            isArchiving = false,
                            archivingProjectId = null,
                            archivedProjects = it.archivedProjects.filter { p -> p.id != project.id },
                            successMessage = "项目「${project.name}」已恢复"
                        )
                    }
                    loadProjects()
                    loadArchivedProjects()
                } else {
                    val msg = when (response.code()) {
                        403 -> "无权限操作此项目"
                        404 -> "项目不存在"
                        else -> "恢复失败(${response.code()})"
                    }
                    _uiState.update {
                        it.copy(isArchiving = false, archivingProjectId = null, errorMessage = msg)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Restore project failed")
                _uiState.update {
                    it.copy(isArchiving = false, archivingProjectId = null, errorMessage = "恢复失败: ${e.message}")
                }
            }
        }
    }

    fun exportProject(project: ProjectResponse) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportingProjectId = project.id) }
            try {
                val response = projectApi.exportProject(project.id)
                if (response.isSuccessful) {
                    _uiState.update {
                        it.copy(isExporting = false, exportingProjectId = null, successMessage = "导出成功")
                    }
                } else {
                    _uiState.update {
                        it.copy(isExporting = false, exportingProjectId = null, errorMessage = "导出失败(${response.code()})")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Export project failed")
                _uiState.update {
                    it.copy(isExporting = false, exportingProjectId = null, errorMessage = "导出失败: ${e.message}")
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}

internal fun isCurrentProject(targetProjectId: Int, selectedProjectId: Int?): Boolean =
    selectedProjectId != null && selectedProjectId > 0 && targetProjectId == selectedProjectId
