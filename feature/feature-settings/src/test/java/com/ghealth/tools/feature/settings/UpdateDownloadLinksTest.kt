package com.ghealth.tools.feature.settings

import com.ghealth.tools.core.network.model.GitHubAsset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class UpdateDownloadLinksTest {

    @Test
    fun `proxyUrl 在 GitHub 地址前拼接代理前缀`() {
        val url = "https://github.com/XiaoPb/ghealth-tools/releases/download/v0.6.27/ghealth-tools-0.6.27.apk"
        assertEquals(
            "https://gh-proxy.com/$url",
            UpdateDownloadLinks.proxyUrl(url)
        )
    }

    @Test
    fun `proxyUrl 对已是代理地址的输入保持原样`() {
        val url = "https://gh-proxy.com/https://github.com/XiaoPb/ghealth-tools/releases/download/v0.6.27/ghealth-tools-0.6.27.apk"
        assertEquals(url, UpdateDownloadLinks.proxyUrl(url))
    }

    @Test
    fun `apkAssetUrl 返回第一个 apk 资源的下载地址`() {
        val assets = listOf(
            GitHubAsset(name = "checksums.txt", browserDownloadUrl = "https://github.com/XiaoPb/ghealth-tools/releases/download/v0.6.27/checksums.txt"),
            GitHubAsset(name = "ghealth-tools-0.6.27.apk", browserDownloadUrl = "https://github.com/XiaoPb/ghealth-tools/releases/download/v0.6.27/ghealth-tools-0.6.27.apk"),
        )
        assertEquals(
            "https://github.com/XiaoPb/ghealth-tools/releases/download/v0.6.27/ghealth-tools-0.6.27.apk",
            UpdateDownloadLinks.apkAssetUrl(assets)
        )
    }

    @Test
    fun `apkAssetUrl 忽略后缀大小写`() {
        val assets = listOf(
            GitHubAsset(name = "ghealth-tools-0.6.27.APK", browserDownloadUrl = "https://example.com/app.APK")
        )
        assertEquals("https://example.com/app.APK", UpdateDownloadLinks.apkAssetUrl(assets))
    }

    @Test
    fun `apkAssetUrl 没有 apk 资源时返回 null`() {
        val assets = listOf(
            GitHubAsset(name = "checksums.txt", browserDownloadUrl = "https://example.com/checksums.txt")
        )
        assertNull(UpdateDownloadLinks.apkAssetUrl(assets))
    }

    @Test
    fun `effectiveDownloadUrl 勾选代理且代理地址非空时返回代理地址`() {
        assertEquals(
            "https://gh-proxy.com/https://github.com/XiaoPb/ghealth-tools/releases/download/v0.6.27/ghealth-tools-0.6.27.apk",
            UpdateDownloadLinks.effectiveDownloadUrl(
                useProxy = true,
                directUrl = "https://github.com/XiaoPb/ghealth-tools/releases/tag/v0.6.27",
                proxyUrl = "https://gh-proxy.com/https://github.com/XiaoPb/ghealth-tools/releases/download/v0.6.27/ghealth-tools-0.6.27.apk",
            )
        )
    }

    @Test
    fun `effectiveDownloadUrl 未勾选代理时返回 GitHub 地址`() {
        assertEquals(
            "https://github.com/XiaoPb/ghealth-tools/releases/tag/v0.6.27",
            UpdateDownloadLinks.effectiveDownloadUrl(
                useProxy = false,
                directUrl = "https://github.com/XiaoPb/ghealth-tools/releases/tag/v0.6.27",
                proxyUrl = "https://gh-proxy.com/https://github.com/XiaoPb/ghealth-tools/releases/download/v0.6.27/ghealth-tools-0.6.27.apk",
            )
        )
    }

    @Test
    fun `effectiveDownloadUrl 勾选代理但代理地址为空时回退 GitHub 地址`() {
        assertEquals(
            "https://github.com/XiaoPb/ghealth-tools/releases/tag/v0.6.27",
            UpdateDownloadLinks.effectiveDownloadUrl(
                useProxy = true,
                directUrl = "https://github.com/XiaoPb/ghealth-tools/releases/tag/v0.6.27",
                proxyUrl = "",
            )
        )
    }
}
