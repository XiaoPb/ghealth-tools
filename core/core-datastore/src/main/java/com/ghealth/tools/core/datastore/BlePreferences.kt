package com.ghealth.tools.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ble_prefs")

@Singleton
class BlePreferences @Inject constructor(
    private val context: Context,
) {
    private object Keys {
        val SERVICE_UUID = stringPreferencesKey("service_uuid")
        val WRITE_CHAR_UUID = stringPreferencesKey("write_char_uuid")
        val NOTIFY_CHAR_UUID = stringPreferencesKey("notify_char_uuid")
        val LAST_DEVICE_ADDRESS = stringPreferencesKey("last_device_address")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val SELECTED_CHIP = stringPreferencesKey("selected_chip")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val serviceUuid: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.SERVICE_UUID] ?: DEFAULT_SERVICE_UUID
    }

    val writeCharUuid: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.WRITE_CHAR_UUID] ?: DEFAULT_WRITE_CHAR_UUID
    }

    val notifyCharUuid: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.NOTIFY_CHAR_UUID] ?: DEFAULT_NOTIFY_CHAR_UUID
    }

    val lastDeviceAddress: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_DEVICE_ADDRESS]
    }

    val autoReconnect: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUTO_RECONNECT] ?: true
    }

    val selectedChip: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.SELECTED_CHIP] ?: "gh3036"
    }

    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE] ?: "sky_blue"
    }

    suspend fun setServiceUuid(uuid: String) {
        context.dataStore.edit { it[Keys.SERVICE_UUID] = uuid }
    }

    suspend fun setWriteCharUuid(uuid: String) {
        context.dataStore.edit { it[Keys.WRITE_CHAR_UUID] = uuid }
    }

    suspend fun setNotifyCharUuid(uuid: String) {
        context.dataStore.edit { it[Keys.NOTIFY_CHAR_UUID] = uuid }
    }

    suspend fun setLastDeviceAddress(address: String) {
        context.dataStore.edit { it[Keys.LAST_DEVICE_ADDRESS] = address }
    }

    suspend fun setAutoReconnect(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_RECONNECT] = enabled }
    }

    suspend fun setSelectedChip(chipName: String) {
        context.dataStore.edit { it[Keys.SELECTED_CHIP] = chipName }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    companion object {
        const val DEFAULT_SERVICE_UUID = "0000190e-0000-1000-8000-00805f9b34fb"
        const val DEFAULT_WRITE_CHAR_UUID = "00000004-0000-1000-8000-00805f9b34fb"
        const val DEFAULT_NOTIFY_CHAR_UUID = "00000003-0000-1000-8000-00805f9b34fb"
    }
}
