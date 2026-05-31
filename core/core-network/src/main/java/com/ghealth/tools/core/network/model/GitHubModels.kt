package com.ghealth.tools.core.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GitHubRelease(
    @Json(name = "tag_name") val tagName: String,
    @Json(name = "html_url") val htmlUrl: String,
    val body: String?,
    @Json(name = "published_at") val publishedAt: String?,
    @Json(name = "prerelease") val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class GitHubAsset(
    val name: String,
    @Json(name = "browser_download_url") val browserDownloadUrl: String,
    val size: Long = 0,
)