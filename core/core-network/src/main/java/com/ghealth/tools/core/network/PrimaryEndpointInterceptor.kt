package com.ghealth.tools.core.network

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import java.io.IOException

/**
 * 业务请求优先走主端点（api.health.xiaopb.cn:8861），失败时回退到转发端点（api.xiaopb.cn）。
 * 仅网络层失败（IOException）触发回退；HTTP 4xx/5xx 不回退，避免掩盖服务端错误。
 * 登录接口（/api/login/）除外：登录已由 AuthRepository 显式 primary → default 回退。
 */
class PrimaryEndpointInterceptor(
    private val primaryBaseUrl: HttpUrl,
    private val fallbackBaseUrl: HttpUrl
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!matchesFallback(request.url) || isLoginRequest(request.url)) {
            return chain.proceed(request)
        }

        val primaryRequest = request.newBuilder()
            .url(rewriteToPrimary(request.url))
            .build()

        return try {
            chain.proceed(primaryRequest)
        } catch (e: IOException) {
            Timber.w(e, "Primary endpoint failed, falling back to default: ${request.url}")
            chain.proceed(request)
        }
    }

    private fun matchesFallback(url: HttpUrl): Boolean {
        return url.scheme == fallbackBaseUrl.scheme &&
            url.host == fallbackBaseUrl.host &&
            url.port == fallbackBaseUrl.port
    }

    private fun isLoginRequest(url: HttpUrl): Boolean {
        return url.encodedPath.endsWith("/api/login/")
    }

    private fun rewriteToPrimary(url: HttpUrl): HttpUrl {
        return url.newBuilder()
            .scheme(primaryBaseUrl.scheme)
            .host(primaryBaseUrl.host)
            .port(primaryBaseUrl.port)
            .build()
    }
}