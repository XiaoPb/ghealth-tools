package com.ghealth.tools.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.ghealth.tools.core.datastore.UserInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class UserInfo(
    val id: Int = 0,
    val username: String = "",
    val email: String = "",
    val isStaff: Boolean = false,
    val projectCount: Int = 0
)

private val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val encryptedPrefs by lazy {
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "user_secure_prefs",
            masterKey,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private object Keys {
        val USER_ID = intPreferencesKey("user_id")
        val USERNAME = stringPreferencesKey("username")
        val EMAIL = stringPreferencesKey("email")
        val IS_STAFF = booleanPreferencesKey("is_staff")
        val PROJECT_COUNT = intPreferencesKey("project_count")
        val SELECTED_PROJECT_ID = intPreferencesKey("selected_project_id")
        val SELECTED_PROJECT_NAME = stringPreferencesKey("selected_project_name")
        val REMEMBER_CREDENTIALS = booleanPreferencesKey("remember_credentials")
        val SAVED_USERNAME = stringPreferencesKey("saved_username")
        val SAVED_PASSWORD = stringPreferencesKey("saved_password")
    }

    private object SecureKeys {
        const val SAVED_PASSWORD = "secure_saved_password"
    }

    val userInfo: Flow<UserInfo> = context.userDataStore.data.map { prefs ->
        UserInfo(
            id = prefs[Keys.USER_ID] ?: 0,
            username = prefs[Keys.USERNAME] ?: "",
            email = prefs[Keys.EMAIL] ?: "",
            isStaff = prefs[Keys.IS_STAFF] ?: false
        )
    }

    val projectCount: Flow<Int> = context.userDataStore.data.map { prefs ->
        prefs[Keys.PROJECT_COUNT] ?: 0
    }

    val selectedProjectId: Flow<Int?> = context.userDataStore.data.map { prefs ->
        prefs[Keys.SELECTED_PROJECT_ID]
    }

    val selectedProjectName: Flow<String?> = context.userDataStore.data.map { prefs ->
        prefs[Keys.SELECTED_PROJECT_NAME]
    }

    val rememberCredentials: Flow<Boolean> = context.userDataStore.data.map { prefs ->
        prefs[Keys.REMEMBER_CREDENTIALS] ?: false
    }

    val savedUsername: Flow<String> = context.userDataStore.data.map { prefs ->
        prefs[Keys.SAVED_USERNAME] ?: ""
    }

    val savedPassword: Flow<String> = context.userDataStore.data.map {
        ""
    }

    suspend fun saveUserInfo(
        id: Int,
        username: String,
        email: String = "",
        isStaff: Boolean = false
    ) {
        context.userDataStore.edit { prefs ->
            prefs[Keys.USER_ID] = id
            prefs[Keys.USERNAME] = username
            prefs[Keys.EMAIL] = email
            prefs[Keys.IS_STAFF] = isStaff
        }
    }

    suspend fun setProjectCount(count: Int) {
        context.userDataStore.edit { prefs ->
            prefs[Keys.PROJECT_COUNT] = count
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

    suspend fun saveCredentials(username: String, password: String, remember: Boolean) {
        context.userDataStore.edit { prefs ->
            prefs[Keys.REMEMBER_CREDENTIALS] = remember
            if (remember) {
                prefs[Keys.SAVED_USERNAME] = username
                encryptedPrefs.edit().putString(SecureKeys.SAVED_PASSWORD, password).apply()
            } else {
                prefs.remove(Keys.SAVED_USERNAME)
                encryptedPrefs.edit().remove(SecureKeys.SAVED_PASSWORD).apply()
            }
        }
    }

    suspend fun clearCredentials() {
        context.userDataStore.edit { prefs ->
            prefs.remove(Keys.REMEMBER_CREDENTIALS)
            prefs.remove(Keys.SAVED_USERNAME)
        }
        encryptedPrefs.edit().remove(SecureKeys.SAVED_PASSWORD).apply()
    }

    fun getPasswordSync(): String? {
        return encryptedPrefs.getString(SecureKeys.SAVED_PASSWORD, null)
    }
}