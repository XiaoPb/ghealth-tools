package com.ghealth.tools.feature.settings

object UpdateCheckDecision {

    /**
     * 决定是否弹出更新弹窗。
     *
     * @param respectIgnored 是否尊重用户忽略的版本（自动检查为 true，设置页手动检查为 false）
     * @param ignoredVersionCode 用户已忽略的版本号（0 表示无，见 UpdatePreferences.NO_IGNORED_VERSION）
     * @param latestVersionCode 最新版本号
     */
    fun shouldShowDialog(
        respectIgnored: Boolean,
        ignoredVersionCode: Int,
        latestVersionCode: Int,
    ): Boolean {
        return !respectIgnored || latestVersionCode != ignoredVersionCode
    }
}
