package com.ghealth.tools.feature.settings

import android.util.Log
import com.ghealth.tools.core.datastore.UpdatePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateCheckCoordinator @Inject constructor(
    private val updateChecker: UpdateChecker,
    private val updatePreferences: UpdatePreferences,
) {

    private val _state = MutableStateFlow(UpdateDialogState())
    val state: StateFlow<UpdateDialogState> = _state.asStateFlow()

    /**
     * 检查更新；有更新且满足显示条件时更新弹窗状态。
     *
     * @param respectIgnored true 表示自动检查（尊重用户忽略的版本），
     *                       false 表示设置页手动检查（总是显示对应版本）。
     * @return 底层检查结果（供设置页展示"已是最新版本"/错误消息）。
     */
    suspend fun checkForUpdate(respectIgnored: Boolean): UpdateCheckResult {
        val result = updateChecker.checkForUpdate()
        val info = result.updateInfo
        if (result.hasUpdate && info != null) {
            val ignoredVersionCode = updatePreferences.ignoredUpdateVersionCode.first()
            Log.i("UpdateCheck", "弹窗决策: respectIgnored=$respectIgnored 已忽略版本=$ignoredVersionCode 最新版本=${info.versionCode} 是否弹窗=${UpdateCheckDecision.shouldShowDialog(respectIgnored, ignoredVersionCode, info.versionCode)}")
            if (UpdateCheckDecision.shouldShowDialog(respectIgnored, ignoredVersionCode, info.versionCode)) {
                _state.update {
                    it.copy(
                        showDialog = true,
                        versionName = info.versionName,
                        versionCode = info.versionCode,
                        changelog = info.changelog,
                        downloadUrl = info.downloadUrl,
                        proxyDownloadUrl = info.proxyDownloadUrl,
                        useProxyDownload = true,
                        isForceUpdate = result.isForceUpdate,
                    )
                }
            }
        }
        return result
    }

    /** 忽略当前弹窗对应的版本：持久化版本号并关闭弹窗。 */
    suspend fun ignoreUpdate() {
        val versionCode = _state.value.versionCode
        if (versionCode <= 0) return
        _state.update { it.copy(showDialog = false) }
        updatePreferences.setIgnoredUpdateVersionCode(versionCode)
    }

    fun dismissUpdateDialog() {
        _state.update { it.copy(showDialog = false) }
    }

    fun setUseProxyDownload(useProxy: Boolean) {
        _state.update { it.copy(useProxyDownload = useProxy) }
    }

    fun openDownloadPage() {
        val state = _state.value
        val url = UpdateDownloadLinks.effectiveDownloadUrl(
            useProxy = state.useProxyDownload,
            directUrl = state.downloadUrl,
            proxyUrl = state.proxyDownloadUrl,
        )
        if (url.isNotEmpty()) {
            updateChecker.openDownloadUrl(url)
        }
    }
}
