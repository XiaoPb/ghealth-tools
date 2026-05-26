package com.ghealth.tools.core.network.api

import com.ghealth.tools.core.network.model.ApiResponse
import com.ghealth.tools.core.network.model.LoginRequest
import com.ghealth.tools.core.network.model.LoginResponse
import com.ghealth.tools.core.network.model.RegisterRequest
import com.ghealth.tools.core.network.model.TokenRefreshRequest
import com.ghealth.tools.core.network.model.TokenRefreshResponse
import com.ghealth.tools.core.network.model.UserResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {
    @POST("register/")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<ApiResponse<UserResponse>>

    @POST("login/")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<ApiResponse<LoginResponse>>

    @POST("logout/")
    suspend fun logout(): Response<ApiResponse<Unit>>

    @POST("token/refresh/")
    suspend fun refreshToken(
        @Body request: TokenRefreshRequest
    ): Response<ApiResponse<TokenRefreshResponse>>

    @GET("user/me/")
    suspend fun getCurrentUser(): Response<ApiResponse<UserResponse>>
}