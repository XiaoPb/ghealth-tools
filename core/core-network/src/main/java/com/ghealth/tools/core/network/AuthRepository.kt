package com.ghealth.tools.core.network

import com.ghealth.tools.core.network.api.AuthApi
import com.ghealth.tools.core.network.model.ApiResponse
import com.ghealth.tools.core.network.model.LoginRequest
import com.ghealth.tools.core.network.model.LoginResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.Response
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * 登录认证仓库：优先使用原 API 地址（api.health.xiaopb.cn:8861）登录，
 * 3 秒内无响应或请求失败时回退到转发域名（api.xiaopb.cn），避免登录长时间转圈。
 */
@Singleton
class AuthRepository @Inject constructor(
    @Named("primaryAuthApi") private val primaryAuthApi: AuthApi,
    private val authApi: AuthApi
) {

    suspend fun login(request: LoginRequest): Response<ApiResponse<LoginResponse>> {
        val primaryResponse = try {
            withTimeoutOrNull(PRIMARY_LOGIN_TIMEOUT_MS) {
                primaryAuthApi.login(request)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Login: primary endpoint failed, falling back to default")
            null
        }
        if (primaryResponse != null) {
            return primaryResponse
        }
        Timber.w("Login: primary endpoint did not respond within ${PRIMARY_LOGIN_TIMEOUT_MS}ms, falling back to default")
        return authApi.login(request)
    }

    private companion object {
        const val PRIMARY_LOGIN_TIMEOUT_MS = 3_000L
    }
}
