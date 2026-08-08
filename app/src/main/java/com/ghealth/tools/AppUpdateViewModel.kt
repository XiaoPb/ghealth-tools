package com.ghealth.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.feature.settings.UpdateCheckCoordinator
import com.ghealth.tools.feature.settings.UpdateDialogState
import com.ghealth.tools.feature.settings.UpdateScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val updateCheckCoordinator: UpdateCheckCoordinator,
) : ViewModel() {

    val updateDialogState: StateFlow<UpdateDialogState> = updateCheckCoordinator.state

    init {
        viewModelScope.launch {
            hourlyCheckLoop()
        }
    }

    /** 每次启动应用进入主界面后触发一次自动检查（尊重用户忽略的版本）。 */
    fun onMainUiShown() {
        viewModelScope.launch {
            updateCheckCoordinator.checkForUpdate(respectIgnored = true)
        }
    }

    /** 点击「忽略更新」：持久化忽略当前版本并关闭弹窗。 */
    fun ignoreUpdate() {
        viewModelScope.launch {
            updateCheckCoordinator.ignoreUpdate()
        }
    }

    fun dismissUpdateDialog() = updateCheckCoordinator.dismissUpdateDialog()

    fun setUseProxyDownload(useProxy: Boolean) = updateCheckCoordinator.setUseProxyDownload(useProxy)

    fun openDownloadPage() = updateCheckCoordinator.openDownloadPage()

    /** 每小时 0 分触发一次自动检查（尊重用户忽略的版本）。 */
    private suspend fun hourlyCheckLoop() {
        while (true) {
            delay(UpdateScheduler.delayUntilNextHourMillis(System.currentTimeMillis()))
            updateCheckCoordinator.checkForUpdate(respectIgnored = true)
        }
    }
}