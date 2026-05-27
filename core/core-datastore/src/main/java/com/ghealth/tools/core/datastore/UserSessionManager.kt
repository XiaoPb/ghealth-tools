package com.ghealth.tools.core.datastore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class UserSession(
    val isLoggedIn: Boolean,
    val userInfo: UserInfo,
    val selectedProjectId: Int?,
    val selectedProjectName: String?
)

@Singleton
class UserSessionManager @Inject constructor(
    private val userPreferences: UserPreferences
) {
    val userSession: Flow<UserSession> = combine(
        userPreferences.userInfo,
        userPreferences.selectedProjectId,
        userPreferences.selectedProjectName
    ) { userInfo, projectId, projectName ->
        UserSession(
            isLoggedIn = userInfo.id > 0 && userInfo.username.isNotEmpty(),
            userInfo = userInfo,
            selectedProjectId = projectId,
            selectedProjectName = projectName
        )
    }

    val isLoggedIn: Flow<Boolean> = userPreferences.userInfo.map { it.id > 0 && it.username.isNotEmpty() }

    val hasSelectedProject: Flow<Boolean> = userPreferences.selectedProjectId.map { it != null && it > 0 }
}
