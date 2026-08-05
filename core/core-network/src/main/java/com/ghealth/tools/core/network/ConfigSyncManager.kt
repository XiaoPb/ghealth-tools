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
            val result = configDownloader.downloadProductionTestConfig(projectId, targetDir)
            if (result.isFailure) {
                val e = result.exceptionOrNull()!!
                Timber.e(e, "Failed to sync production test config for project: $projectName")
                return Result.failure(e)
            }
            when (val file = result.getOrNull()) {
                null -> Timber.i("No prod-test config for project: $projectName (not uploaded yet)")
                else -> Timber.i("Synced production test config for project: $projectName -> ${file.absolutePath}")
            }
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
    ) {
        val prodResult = syncProductionTestConfig(projectId, projectName)
        val regularResult = syncRegularConfigs(projectId, projectName)

        if (prodResult.isSuccess && regularResult.isSuccess) {
            Timber.i("Full sync completed for project: $projectName")
            return
        }

        val errors = listOfNotNull(
            prodResult.exceptionOrNull()?.message,
            regularResult.exceptionOrNull()?.message
        )
        val message = "Full sync partially failed for project: $projectName, errors: $errors"
        Timber.w(message)
        throw IllegalStateException(message)
    }
}