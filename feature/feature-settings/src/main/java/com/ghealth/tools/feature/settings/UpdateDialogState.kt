package com.ghealth.tools.feature.settings

data class UpdateDialogState(
    val showDialog: Boolean = false,
    val versionName: String = "",
    val versionCode: Int = 0,
    val changelog: String = "",
    val downloadUrl: String = "",
    val proxyDownloadUrl: String = "",
    val useProxyDownload: Boolean = true,
    val isForceUpdate: Boolean = false,
)
