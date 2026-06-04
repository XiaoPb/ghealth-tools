package com.ghealth.tools.core.network

import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import java.io.IOException

class RetryInterceptor(
    private val maxRetries: Int = 3
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var lastException: IOException? = null

        repeat(maxRetries) { attempt ->
            try {
                val response = chain.proceed(request)
                if (response.isSuccessful || response.code != 408) {
                    return response
                }
                response.close()
                Timber.w("Retry ${attempt + 1}/$maxRetries: HTTP ${response.code} for ${request.url}")
            } catch (e: IOException) {
                lastException = e
                Timber.w("Retry ${attempt + 1}/$maxRetries: ${e.message} for ${request.url}")
            }
        }

        throw lastException ?: IOException("Request failed after $maxRetries retries")
    }
}
