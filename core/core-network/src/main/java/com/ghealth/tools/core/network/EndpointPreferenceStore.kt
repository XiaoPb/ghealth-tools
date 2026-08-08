package com.ghealth.tools.core.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.endpointDataStore: DataStore<Preferences> by preferencesDataStore(name = "endpoint_prefs")

@Singleton
class EndpointPreferenceStore @Inject constructor(
    @ApplicationContext private val context: Context
) : EndpointPreference {

    @Volatile
    private var cachedUsePrimary: Boolean? = null

    private object Keys {
        val USE_PRIMARY = booleanPreferencesKey("use_primary")
    }

    override fun usePrimary(): Boolean? {
        cachedUsePrimary?.let { return it }
        return synchronized(this) {
            cachedUsePrimary ?: runCatching {
                runBlocking { context.endpointDataStore.data.first()[Keys.USE_PRIMARY] }
            }.getOrNull().also { cachedUsePrimary = it }
        }
    }

    override suspend fun setUsePrimary(usePrimary: Boolean) {
        cachedUsePrimary = usePrimary
        context.endpointDataStore.edit { prefs ->
            prefs[Keys.USE_PRIMARY] = usePrimary
        }
    }
}
