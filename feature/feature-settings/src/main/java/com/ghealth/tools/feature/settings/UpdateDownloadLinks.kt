package com.ghealth.tools.feature.settings

import com.ghealth.tools.core.network.model.GitHubAsset

object UpdateDownloadLinks {

    const val PROXY_PREFIX = "https://gh-proxy.com/"

    fun proxyUrl(githubUrl: String): String {
        return if (githubUrl.startsWith(PROXY_PREFIX)) githubUrl else PROXY_PREFIX + githubUrl
    }

    fun apkAssetUrl(assets: List<GitHubAsset>): String? {
        return assets
            .firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?.browserDownloadUrl
    }

    fun effectiveDownloadUrl(useProxy: Boolean, directUrl: String, proxyUrl: String): String {
        return if (useProxy && proxyUrl.isNotEmpty()) proxyUrl else directUrl
    }
}
