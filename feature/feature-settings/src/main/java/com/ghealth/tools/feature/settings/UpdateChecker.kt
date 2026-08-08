package com.ghealth.tools.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ghealth.tools.core.network.api.GitHubApi
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val downloadUrl: String,
    val proxyDownloadUrl: String,
    val changelog: String,
    val publishedAt: String,
)

data class UpdateCheckResult(
    val hasUpdate: Boolean,
    val updateInfo: UpdateInfo?,
    val isForceUpdate: Boolean,
    val errorMessage: String?,
)

@Singleton
class UpdateChecker @Inject constructor(
    private val gitHubApi: GitHubApi,
    @Named("app_version") private val currentVersionName: String,
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val GITHUB_OWNER = "XiaoPb"
        private const val GITHUB_REPO = "ghealth-tools"
        private const val VERSION_CODE_UNKNOWN = -1
    }

    private fun getCurrentVersionCode(): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val longVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            longVersionCode.toInt()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get current version code")
            VERSION_CODE_UNKNOWN
        }
    }

    private fun parseVersionCode(tagName: String): Int {
        val regex = Regex("""[vV]?(\d+)\.(\d+)\.(\d+)""")
        val match = regex.find(tagName) ?: return 0
        return match.groupValues[1].toInt() * 10000 +
            match.groupValues[2].toInt() * 100 +
            match.groupValues[3].toInt()
    }

    private fun isForceUpdate(changelog: String): Boolean {
        return changelog.contains("[force]", ignoreCase = true)
    }

    suspend fun checkForUpdate(): UpdateCheckResult {
        return try {
            val response = gitHubApi.getLatestRelease(GITHUB_OWNER, GITHUB_REPO)
            if (!response.isSuccessful) {
                Timber.w("GitHub API error: HTTP %s", response.code())
                return UpdateCheckResult(
                    hasUpdate = false,
                    updateInfo = null,
                    isForceUpdate = false,
                    errorMessage = "GitHub API 返回错误 (${response.code()})",
                )
            }

            val release = response.body()
            if (release == null) {
                Timber.w("GitHub API returned null body")
                return UpdateCheckResult(
                    hasUpdate = false,
                    updateInfo = null,
                    isForceUpdate = false,
                    errorMessage = null,
                )
            }

            if (release.prerelease) {
                Timber.d("Skipping prerelease: %s", release.tagName)
                return UpdateCheckResult(
                    hasUpdate = false,
                    updateInfo = null,
                    isForceUpdate = false,
                    errorMessage = null,
                )
            }

            val latestVersionCode = parseVersionCode(release.tagName)
            if (latestVersionCode == 0) {
                Timber.w("Cannot parse version from tag: %s", release.tagName)
                return UpdateCheckResult(
                    hasUpdate = false,
                    updateInfo = null,
                    isForceUpdate = false,
                    errorMessage = null,
                )
            }

            val currentCode = getCurrentVersionCode()
            if (currentCode == VERSION_CODE_UNKNOWN) {
                return UpdateCheckResult(
                    hasUpdate = false,
                    updateInfo = null,
                    isForceUpdate = false,
                    errorMessage = null,
                )
            }

            val hasUpdate = latestVersionCode > currentCode
            val force = isForceUpdate(release.body ?: "")

            val apkAssetUrl = UpdateDownloadLinks.apkAssetUrl(release.assets)
            val updateInfo = UpdateInfo(
                versionName = release.tagName.trimStart('v', 'V'),
                versionCode = latestVersionCode,
                downloadUrl = release.htmlUrl,
                proxyDownloadUrl = UpdateDownloadLinks.proxyUrl(apkAssetUrl ?: release.htmlUrl),
                changelog = release.body ?: "",
                publishedAt = release.publishedAt ?: "",
            )

            Timber.d(
                "Version check: current=%s(%s) latest=%s(%s) hasUpdate=%s force=%s",
                currentCode, currentVersionName,
                latestVersionCode, release.tagName,
                hasUpdate, force,
            )

            UpdateCheckResult(
                hasUpdate = hasUpdate,
                updateInfo = updateInfo,
                isForceUpdate = force,
                errorMessage = null,
            )
        } catch (e: Exception) {
            Timber.e(e, "Version check failed")
            UpdateCheckResult(
                hasUpdate = false,
                updateInfo = null,
                isForceUpdate = false,
                errorMessage = e.message ?: "网络错误",
            )
        }
    }

    fun openDownloadUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to open download URL")
        }
    }
}