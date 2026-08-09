package com.ghealth.tools.feature.login

import com.ghealth.tools.ble.connection.BleConnectionManager
import com.ghealth.tools.core.datastore.BlePreferences
import com.ghealth.tools.core.datastore.UserPreferences
import com.ghealth.tools.core.datastore.UserSessionManager
import com.ghealth.tools.core.network.ApiErrorParser
import com.ghealth.tools.core.network.AuthRepository
import com.ghealth.tools.core.network.TokenManager
import com.ghealth.tools.core.network.api.AuthApi
import com.ghealth.tools.core.network.model.ApiResponse
import com.ghealth.tools.core.network.model.LogoutRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import retrofit2.Response

class LoginViewModelTest {

    @Test
    fun `logout disconnects all ble connections`() = runTest {
        val authApi = mockk<AuthApi>()
        coEvery { authApi.logout(any<LogoutRequest>()) } returns
            Response.success(ApiResponse(code = 0, message = "ok", data = Unit))

        val bleConnectionManager = mockk<BleConnectionManager>(relaxed = true)

        val viewModel = LoginViewModel(
            authApi = authApi,
            authRepository = mockk(relaxed = true),
            tokenManager = mockk<TokenManager>(relaxed = true),
            sessionManager = mockk<UserSessionManager>(relaxed = true),
            apiErrorParser = mockk<ApiErrorParser>(relaxed = true),
            userPreferences = mockk<UserPreferences>(relaxed = true),
            blePreferences = mockk<BlePreferences>(relaxed = true),
            bleConnectionManager = bleConnectionManager
        )

        viewModel.logout()

        verify { bleConnectionManager.disconnectAll() }
        coVerify { authApi.logout(any<LogoutRequest>()) }
    }
}
