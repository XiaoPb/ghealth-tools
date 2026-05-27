package com.ghealth.tools.core.network

import com.ghealth.tools.core.datastore.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class ConfigPathProvider @Inject constructor(
    @Named("storageBaseDir") private val baseDir: File,
    private val userPreferences: UserPreferences
) {
    val isOnlineMode: Flow<Boolean> = combine(
        userPreferences.userInfo.map { it.id > 0 && it.username.isNotEmpty() },
        userPreferences.selectedProjectId.map { it != null && it > 0 }
    ) { loggedIn, hasProject -> loggedIn && hasProject }

    suspend fun getFactoryConfigDir(chip: String, projectName: String): File {
        val online = isOnlineMode.first()
        if (online) {
            val userInfo = userPreferences.userInfo.first()
            return File(baseDir, "factory/config/${userInfo.username}/$projectName")
        }
        return File(baseDir, "factory/config/$chip/$projectName")
    }

    suspend fun getApplicationConfigDir(chip: String, projectName: String): File {
        val online = isOnlineMode.first()
        if (online) {
            val userInfo = userPreferences.userInfo.first()
            return File(baseDir, "application/config/${userInfo.username}/$projectName")
        }
        return File(baseDir, "application/config/$chip/$projectName")
    }

    suspend fun getFactoryScanDir(): File {
        val online = isOnlineMode.first()
        if (online) {
            val userInfo = userPreferences.userInfo.first()
            return File(baseDir, "factory/config/${userInfo.username}")
        }
        return File(baseDir, "factory/config")
    }

    suspend fun getApplicationScanDir(): File {
        val online = isOnlineMode.first()
        if (online) {
            val userInfo = userPreferences.userInfo.first()
            return File(baseDir, "application/config/${userInfo.username}")
        }
        return File(baseDir, "application/config")
    }
}