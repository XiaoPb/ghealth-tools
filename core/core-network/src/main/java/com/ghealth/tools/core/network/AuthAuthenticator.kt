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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthAuthenticator @Inject constructor(
    private val tokenManager: TokenManager
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        val request = response.request
        if (response.code != 401) return null

        synchronized(this) {
            val currentToken = runBlocking { tokenManager.getAccessToken() }
            val requestToken = request.header("Authorization")?.removePrefix("Bearer ")
            
            if (currentToken != null && currentToken != requestToken) {
                return request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }

            val refreshToken = runBlocking { tokenManager.getRefreshToken() }
            if (refreshToken.isNullOrEmpty()) {
                runBlocking { tokenManager.clearTokens() }
                return null
            }

            return try {
                val newTokenResponse = runBlocking {
                    createRefreshClient().refreshToken(TokenRefreshRequest(refreshToken))
                }

                if (newTokenResponse.isSuccessful && newTokenResponse.body()?.data != null) {
                    val newAccessToken = newTokenResponse.body()!!.data!!.access
                    runBlocking { tokenManager.updateAccessToken(newAccessToken) }
                    
                    request.newBuilder()
                        .header("Authorization", "Bearer $newAccessToken")
                        .build()
                } else {
                    runBlocking { tokenManager.clearTokens() }
                    null
                }
            } catch (e: Exception) {
                Timber.e(e, "Token refresh failed")
                runBlocking { tokenManager.clearTokens() }
                null
            }
        }
    }

    private fun createRefreshClient(): AuthApi {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl("http://localhost/api/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(AuthApi::class.java)
    }
}
