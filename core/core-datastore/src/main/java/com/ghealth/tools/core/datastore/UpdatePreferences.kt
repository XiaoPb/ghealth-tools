package com.ghealth.tools.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.updateDataStore: DataStore<Preferences> by preferencesDataStore(name = "update_prefs")

@Singleton
class UpdatePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val NO_IGNORED_VERSION = 0
    }

    private object Keys {
        val IGNORED_UPDATE_VERSION_CODE = intPreferencesKey("ignored_update_version_code")
    }

    val ignoredUpdateVersionCode: Flow<Int> = context.updateDataStore.data.map { prefs ->
        prefs[Keys.IGNORED_UPDATE_VERSION_CODE] ?: NO_IGNORED_VERSION
    }

    suspend fun setIgnoredUpdateVersionCode(versionCode: Int) {
        context.updateDataStore.edit { prefs ->
            prefs[Keys.IGNORED_UPDATE_VERSION_CODE] = versionCode
        }
    }
}
