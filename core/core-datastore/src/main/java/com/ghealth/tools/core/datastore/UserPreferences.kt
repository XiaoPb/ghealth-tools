package com.ghealth.tools.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

data class UserInfo(
    val id: Int = 0,
    val username: String = "",
    val email: String = "",
    val isStaff: Boolean = false,
    val projectCount: Int = 0
)

@Singleton
class UserPreferences @Inject constructor(
    private val context: Context
) {
    private object Keys {
        val USER_ID = intPreferencesKey("user_id")
        val USERNAME = stringPreferencesKey("username")
        val EMAIL = stringPreferencesKey("email")
        val IS_STAFF = stringPreferencesKey("is_staff")
        val PROJECT_COUNT = intPreferencesKey("project_count")
        val SELECTED_PROJECT_ID = intPreferencesKey("selected_project_id")
        val SELECTED_PROJECT_NAME = stringPreferencesKey("selected_project_name")
    }

    val userInfo: Flow<UserInfo> = context.userDataStore.data.map { prefs ->
        UserInfo(
            id = prefs[Keys.USER_ID] ?: 0,
            username = prefs[Keys.USERNAME] ?: "",
            email = prefs[Keys.EMAIL] ?: "",
            isStaff = prefs[Keys.IS_STAFF] == "true",
            projectCount = prefs[Keys.PROJECT_COUNT] ?: 0
        )
    }

    val selectedProjectId: Flow<Int?> = context.userDataStore.data.map { prefs ->
        val id = prefs[Keys.SELECTED_PROJECT_ID]
        if (id == null || id == 0) null else id
    }

    val selectedProjectName: Flow<String> = context.userDataStore.data.map { prefs ->
        prefs[Keys.SELECTED_PROJECT_NAME] ?: ""
    }

    suspend fun saveUserInfo(
        id: Int,
        username: String,
        email: String,
        isStaff: Boolean = false,
        projectCount: Int = 0
    ) {
        context.userDataStore.edit { prefs ->
            prefs[Keys.USER_ID] = id
            prefs[Keys.USERNAME] = username
            prefs[Keys.EMAIL] = email
            prefs[Keys.IS_STAFF] = if (isStaff) "true" else "false"
            prefs[Keys.PROJECT_COUNT] = projectCount
        }
    }

    suspend fun setSelectedProject(projectId: Int, projectName: String) {
        context.userDataStore.edit { prefs ->
            prefs[Keys.SELECTED_PROJECT_ID] = projectId
            prefs[Keys.SELECTED_PROJECT_NAME] = projectName
        }
    }

    suspend fun clearUserInfo() {
        context.userDataStore.edit { prefs ->
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.USERNAME)
            prefs.remove(Keys.EMAIL)
            prefs.remove(Keys.IS_STAFF)
            prefs.remove(Keys.PROJECT_COUNT)
            prefs.remove(Keys.SELECTED_PROJECT_ID)
            prefs.remove(Keys.SELECTED_PROJECT_NAME)
        }
    }
}
