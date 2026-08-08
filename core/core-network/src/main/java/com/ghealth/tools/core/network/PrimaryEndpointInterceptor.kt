package com.ghealth.tools.core.network

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.IOException

/**
 * 业务请求根据登录时的一次性端点验证结果选择端点：
 * - primary 可用（api.health.xiaopb.cn:8861）→ 所有请求直接使用 primary，不再逐次探测；
 * - primary 不可用 → 所有请求直接使用转发端点（api.xiaopb.cn）；
 * - 尚未验证（null）→ 保留原有探测：先试 primary，IOException 回退 fallback。
 * 登录接口（/api/login/）除外：登录已由 AuthRepository 负责 primary → default 回退并写入验证结果。
 */
class PrimaryEndpointInterceptor(
    private val primaryBaseUrl: HttpUrl,
    private val fallbackBaseUrl: HttpUrl,
    private val endpointPreference: EndpointPreference
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!matchesFallback(request.url) || isLoginRequest(request.url)) {
            return chain.proceed(request)
        }

        return when (endpointPreference.usePrimary()) {
            true -> chain.proceed(rewriteToPrimaryRequest(request))
            false -> chain.proceed(request)
            null -> proceedWithProbeAndFallback(chain, request)
        }
    }

    private fun proceedWithProbeAndFallback(chain: Interceptor.Chain, request: Request): Response {
        return try {
            chain.proceed(rewriteToPrimaryRequest(request))
        } catch (e: IOException) {
            Timber.w(e, "Primary endpoint failed, falling back to default: ${request.url}")
            chain.proceed(request)
        }
    }

    private fun rewriteToPrimaryRequest(request: Request): Request {
        return request.newBuilder()
            .url(rewriteToPrimary(request.url))
            .build()
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
