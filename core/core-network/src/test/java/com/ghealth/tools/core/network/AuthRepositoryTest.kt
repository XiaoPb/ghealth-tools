package com.ghealth.tools.core.network

import com.ghealth.tools.core.network.api.AuthApi
import com.ghealth.tools.core.network.model.ApiResponse
import com.ghealth.tools.core.network.model.LoginRequest
import com.ghealth.tools.core.network.model.LoginResponse
import com.ghealth.tools.core.network.model.RegisterRequest
import com.ghealth.tools.core.network.model.TokenRefreshRequest
import com.ghealth.tools.core.network.model.UserResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import retrofit2.Response
import java.io.IOException

class AuthRepositoryTest {

    private val loginRequest = LoginRequest(username = "user", password = "pass")

    @Test
    fun `primary responds within 3s and is returned without fallback`() = runTest {
        var fallbackCalls = 0
        val repo = AuthRepository(
            primaryAuthApi = fakeAuthApi { Response.success(successBody("primary")) },
            authApi = fakeAuthApi {
                fallbackCalls++
                Response.success(successBody("fallback"))
            },
            endpointPreference = FakeEndpointPreference()
        )

        val result = repo.login(loginRequest)

        assertEquals("primary", result.body()?.data?.access)
        assertEquals(0, fallbackCalls)
    }

    @Test
    fun `primary times out after 3s so login falls back to default endpoint`() = runTest {
        var fallbackCalls = 0
        val repo = AuthRepository(
            primaryAuthApi = fakeAuthApi {
                delay(10_000)
                Response.success(successBody("primary"))
            },
            authApi = fakeAuthApi {
                fallbackCalls++
                Response.success(successBody("fallback"))
            },
            endpointPreference = FakeEndpointPreference()
        )

        val result = repo.login(loginRequest)

        assertEquals("fallback", result.body()?.data?.access)
        assertEquals(1, fallbackCalls)
    }

    @Test
    fun `primary throws exception so login falls back to default endpoint`() = runTest {
        var fallbackCalls = 0
        val repo = AuthRepository(
            primaryAuthApi = fakeAuthApi { throw IOException("unreachable") },
            authApi = fakeAuthApi {
                fallbackCalls++
                Response.success(successBody("fallback"))
            },
            endpointPreference = FakeEndpointPreference()
        )

        val result = repo.login(loginRequest)

        assertEquals("fallback", result.body()?.data?.access)
        assertEquals(1, fallbackCalls)
    }

    @Test
    fun `primary HTTP error response is returned without fallback`() = runTest {
        var fallbackCalls = 0
        val repo = AuthRepository(
            primaryAuthApi = fakeAuthApi {
                Response.error(400, "".toResponseBody(null))
            },
            authApi = fakeAuthApi {
                fallbackCalls++
                Response.success(successBody("fallback"))
            },
            endpointPreference = FakeEndpointPreference()
        )

        val result = repo.login(loginRequest)

        assertEquals(400, result.code())
        assertEquals(0, fallbackCalls)
    }

    @Test
    fun `login records primary preference when primary succeeds`() = runTest {
        val preference = FakeEndpointPreference()
        val repo = AuthRepository(
            primaryAuthApi = fakeAuthApi { Response.success(successBody("primary")) },
            authApi = fakeAuthApi { Response.success(successBody("fallback")) },
            endpointPreference = preference
        )

        repo.login(loginRequest)

        assertEquals(true, preference.value)
    }

    @Test
    fun `login records fallback preference when primary is unreachable`() = runTest {
        val preference = FakeEndpointPreference()
        val repo = AuthRepository(
            primaryAuthApi = fakeAuthApi { throw IOException("unreachable") },
            authApi = fakeAuthApi { Response.success(successBody("fallback")) },
            endpointPreference = preference
        )

        repo.login(loginRequest)

        assertEquals(false, preference.value)
    }

    private fun fakeAuthApi(
        onLogin: suspend (LoginRequest) -> Response<ApiResponse<LoginResponse>>
    ): AuthApi {
        return object : AuthApi {
            override suspend fun login(request: LoginRequest) = onLogin(request)
            override suspend fun register(request: RegisterRequest) = error("not used")
            override suspend fun logout() = error("not used")
            override suspend fun refreshToken(request: TokenRefreshRequest) = error("not used")
            override suspend fun getCurrentUser() = error("not used")
        }
    }

    private fun successBody(access: String): ApiResponse<LoginResponse> {
        return ApiResponse(
            code = 200,
            message = "success",
            data = LoginResponse(
                access = access,
                refresh = "refresh-token",
                user = UserResponse(id = 1, username = "user", email = "user@example.com"),
                redirectUrl = ""
            )
        )
    }

    private class FakeEndpointPreference(
        @Volatile var value: Boolean? = null
    ) : EndpointPreference {
        override fun usePrimary(): Boolean? = value
        override suspend fun setUsePrimary(usePrimary: Boolean) {
            value = usePrimary
        }
    }
}
