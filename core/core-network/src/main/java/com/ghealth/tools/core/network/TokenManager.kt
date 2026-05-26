package com.ghealth.tools.core.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.tokenDataStore: DataStore<Preferences> by preferencesDataStore(name = "token_prefs")

@Singleton
class TokenManager @Inject constructor(
    private val context: Context
) {
    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
    }

    val accessToken: Flow<String?> = context.tokenDataStore.data.map { prefs ->
        prefs[Keys.ACCESS_TOKEN]
    }

    val refreshToken: Flow<String?> = context.tokenDataStore.data.map { prefs ->
        prefs[Keys.REFRESH_TOKEN]
    }

    val isLoggedIn: Flow<Boolean> = context.tokenDataStore.data.map { prefs ->
        prefs[Keys.ACCESS_TOKEN] != null && prefs[Keys.ACCESS_TOKEN]!!.isNotEmpty()
    }

    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        context.tokenDataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = accessToken
            prefs[Keys.REFRESH_TOKEN] = refreshToken
        }
    }

    suspend fun updateAccessToken(accessToken: String) {
        context.tokenDataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = accessToken
        }
    }

    suspend fun getAccessToken(): String? {
        return context.tokenDataStore.data.map { prefs ->
            prefs[Keys.ACCESS_TOKEN]
        }.first()
    }

    suspend fun getRefreshToken(): String? {
        return context.tokenDataStore.data.map { prefs ->
            prefs[Keys.REFRESH_TOKEN]
        }.first()
    }

    suspend fun clearTokens() {
        context.tokenDataStore.edit { prefs ->
            prefs.remove(Keys.ACCESS_TOKEN)
            prefs.remove(Keys.REFRESH_TOKEN)
        }
    }
}
