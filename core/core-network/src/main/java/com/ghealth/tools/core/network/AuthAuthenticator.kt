package com.ghealth.tools.core.network

import com.ghealth.tools.core.network.api.AuthApi
import com.ghealth.tools.core.network.model.TokenRefreshRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class AuthAuthenticator @Inject constructor(
    private val tokenManager: TokenManager,
    @Named("baseUrl") private val baseUrl: String,
    private val primaryEndpointInterceptor: PrimaryEndpointInterceptor
) : Authenticator {

    @Volatile
    private var retryCount = 0

    private val refreshApi: AuthApi by lazy {
        createRefreshClient()
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        if (retryCount >= 1) {
            retryCount = 0
            tokenManager.clearTokensSync()
            return null
        }

        synchronized(this) {
            val request = response.request

            val currentToken = tokenManager.getAccessTokenSync()
            val requestToken = request.header("Authorization")?.removePrefix("Bearer ")

            if (!currentToken.isNullOrEmpty() && currentToken != requestToken) {
                retryCount = 0
                return request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }

            val refreshToken = tokenManager.getRefreshTokenSync()
            if (refreshToken.isNullOrEmpty()) {
                retryCount = 0
                tokenManager.clearTokensSync()
                return null
            }

            return try {
                val newTokenResponse = runBlocking {
                    refreshApi.refreshToken(TokenRefreshRequest(refreshToken))
                }

                if (newTokenResponse.isSuccessful) {
                    val tokenData = newTokenResponse.body()?.data
                    if (tokenData != null) {
                        val newAccessToken = tokenData.access
                        val newRefreshToken = tokenData.refresh?.takeIf { it.isNotBlank() }
                        if (newRefreshToken != null) {
                            runBlocking { tokenManager.saveTokens(newAccessToken, newRefreshToken) }
                        } else {
                            runBlocking { tokenManager.updateAccessToken(newAccessToken) }
                        }
                        retryCount++

                        request.newBuilder()
                            .header("Authorization", "Bearer $newAccessToken")
                            .build()
                    } else {
                        retryCount = 0
                        tokenManager.clearTokensSync()
                        null
                    }
                } else {
                    retryCount = 0
                    tokenManager.clearTokensSync()
                    null
                }
            } catch (e: Exception) {
                Timber.e(e, "Token refresh failed")
                retryCount = 0
                tokenManager.clearTokensSync()
                null
            }
        }
    }

    private fun createRefreshClient(): AuthApi {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(primaryEndpointInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(AuthApi::class.java)
    }
}