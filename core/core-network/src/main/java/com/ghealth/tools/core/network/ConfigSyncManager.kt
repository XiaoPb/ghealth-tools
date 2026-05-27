package com.ghealth.tools.core.network

import com.ghealth.tools.core.network.api.ProjectApi
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigSyncManager @Inject constructor(
    private val configDownloader: ConfigDownloader,
    private val projectApi: ProjectApi,
    private val configPathProvider: ConfigPathProvider
) {
    suspend fun syncProductionTestConfig(
        projectId: Int,
        projectName: String
    ): Result<Unit> {
        return try {
            val targetDir = configPathProvider.getFactoryConfigDir("", projectName)
            configDownloader.downloadProductionTestConfig(projectId, targetDir)
            Timber.i("Synced production test config for project: $projectName")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync production test config for project: $projectName")
            Result.failure(e)
        }
    }

    suspend fun syncRegularConfigs(
        projectId: Int,
        projectName: String
    ): Result<Unit> {
        return try {
            val targetDir = configPathProvider.getApplicationConfigDir("", projectName)
            configDownloader.downloadRegularConfigs(projectId, targetDir)
            Timber.i("Synced regular configs for project: $projectName")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync regular configs for project: $projectName")
            Result.failure(e)
        }
    }

    suspend fun fullSync(
        projectId: Int,
        projectName: String
    ): Result<Unit> {
        val prodResult = syncProductionTestConfig(projectId, projectName)
        val regularResult = syncRegularConfigs(projectId, projectName)

        return if (prodResult.isSuccess && regularResult.isSuccess) {
            Timber.i("Full sync completed for project: $projectName")
            Result.success(Unit)
        } else {
            val errors = listOfNotNull(
                prodResult.exceptionOrNull()?.message,
                regularResult.exceptionOrNull()?.message
            )
            Timber.w("Full sync partially failed for project: $projectName, errors: $errors")
            Result.success(Unit)
        }
    }
}