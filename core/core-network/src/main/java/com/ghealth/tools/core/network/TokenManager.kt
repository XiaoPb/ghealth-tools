package com.ghealth.tools.core.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.tokenDataStore: DataStore<Preferences> by preferencesDataStore(name = "token_prefs")

@Singleton
class TokenManager @Inject constructor(
    private val context: Context
) {
    @Volatile
    private var cachedAccessToken: String? = null

    @Volatile
    private var cachedRefreshToken: String? = null

    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
    }

    val accessToken: Flow<String?> = context.tokenDataStore.data.map { prefs ->
        prefs[Keys.ACCESS_TOKEN].also { cachedAccessToken = it }
    }

    val refreshToken: Flow<String?> = context.tokenDataStore.data.map { prefs ->
        prefs[Keys.REFRESH_TOKEN].also { cachedRefreshToken = it }
    }

    val isLoggedIn: Flow<Boolean> = context.tokenDataStore.data.map { prefs ->
        !prefs[Keys.ACCESS_TOKEN].isNullOrEmpty()
    }

    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        cachedAccessToken = accessToken
        cachedRefreshToken = refreshToken
        context.tokenDataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = accessToken
            prefs[Keys.REFRESH_TOKEN] = refreshToken
        }
    }

    suspend fun updateAccessToken(accessToken: String) {
        cachedAccessToken = accessToken
        context.tokenDataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = accessToken
        }
    }

    fun getAccessTokenSync(): String? {
        return cachedAccessToken ?: runCatching {
            kotlinx.coroutines.runBlocking { getAccessTokenSuspend() }
        }.getOrNull()
    }

    fun getRefreshTokenSync(): String? {
        return cachedRefreshToken ?: runCatching {
            kotlinx.coroutines.runBlocking { getRefreshTokenSuspend() }
        }.getOrNull()
    }

    fun isLoggedInSync(): Boolean {
        return !cachedAccessToken.isNullOrEmpty()
    }

    suspend fun getAccessTokenSuspend(): String? {
        return context.tokenDataStore.data.map { prefs ->
            prefs[Keys.ACCESS_TOKEN]
        }.firstOrNull()
    }

    suspend fun getRefreshTokenSuspend(): String? {
        return context.tokenDataStore.data.map { prefs ->
            prefs[Keys.REFRESH_TOKEN]
        }.firstOrNull()
    }

    suspend fun clearTokens() {
        cachedAccessToken = null
        cachedRefreshToken = null
        context.tokenDataStore.edit { prefs ->
            prefs.remove(Keys.ACCESS_TOKEN)
            prefs.remove(Keys.REFRESH_TOKEN)
        }
    }

    fun clearTokensSync() {
        cachedAccessToken = null
        cachedRefreshToken = null
        kotlinx.coroutines.runBlocking {
            context.tokenDataStore.edit { prefs ->
                prefs.remove(Keys.ACCESS_TOKEN)
                prefs.remove(Keys.REFRESH_TOKEN)
            }
        }
    }
}