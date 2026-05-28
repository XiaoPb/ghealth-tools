package com.ghealth.tools.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.core.datastore.BlePreferences
import com.ghealth.tools.core.datastore.SavedAccount
import com.ghealth.tools.core.datastore.UserPreferences
import com.ghealth.tools.core.datastore.UserSessionManager
import com.ghealth.tools.core.network.ApiErrorParser
import com.ghealth.tools.core.network.TokenManager
import com.ghealth.tools.core.network.api.AuthApi
import com.ghealth.tools.core.network.model.LoginRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val rememberMe: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoginSuccess: Boolean = false,
    val savedAccounts: List<SavedAccount> = emptyList(),
    val showAccountDropdown: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authApi: AuthApi,
    private val tokenManager: TokenManager,
    private val sessionManager: UserSessionManager,
    private val apiErrorParser: ApiErrorParser,
    private val userPreferences: UserPreferences,
    private val blePreferences: BlePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        loadSavedCredentials()
    }

    private fun loadSavedCredentials() {
        val accounts = userPreferences.getSavedAccounts()
        if (accounts.isNotEmpty()) {
            val latest = accounts.first()
            _uiState.update {
                it.copy(
                    username = latest.username,
                    password = latest.password,
                    rememberMe = true,
                    savedAccounts = accounts
                )
            }
        }
    }

    fun selectAccount(account: SavedAccount) {
        _uiState.update {
            it.copy(
                username = account.username,
                password = account.password,
                showAccountDropdown = false
            )
        }
    }

    fun toggleAccountDropdown() {
        _uiState.update { it.copy(showAccountDropdown = !it.showAccountDropdown) }
    }

    fun dismissAccountDropdown() {
        _uiState.update { it.copy(showAccountDropdown = false) }
    }

    fun updateUsername(username: String) {
        _uiState.update { it.copy(username = username, errorMessage = null) }
    }

    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun updateRememberMe(rememberMe: Boolean) {
        _uiState.update { it.copy(rememberMe = rememberMe) }
    }

    fun login(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.username.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入账号和密码") }
            return
        }

        val usernamePattern = Regex("^[a-zA-Z0-9]+$")
        if (!usernamePattern.matches(state.username.trim())) {
            _uiState.update { it.copy(errorMessage = "账号只能包含字母和数字") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                val request = LoginRequest(
                    username = state.username.trim(),
                    password = state.password
                )
                val response = authApi.login(request)

                if (response.isSuccessful && response.body()?.data != null) {
                    val loginData = response.body()!!.data!!
                    tokenManager.saveTokens(loginData.access, loginData.refresh)
                    loginData.user.let { user ->
                        userPreferences.saveUserInfo(
                            id = user.id,
                            username = user.username,
                            email = user.email,
                            isStaff = user.isStaff
                        )
                    }
                    userPreferences.saveCredentials(
                        username = state.username.trim(),
                        password = state.password,
                        remember = state.rememberMe
                    )
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoginSuccess = true,
                            savedAccounts = userPreferences.getSavedAccounts()
                        )
                    }
                    onSuccess()
                } else {
                    val errorMsg = apiErrorParser.parseErrors(response)
                    _uiState.update { it.copy(isLoading = false, errorMessage = errorMsg) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Login failed")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "网络错误: ${e.localizedMessage ?: "连接失败"}"
                    )
                }
            }
        }
    }

    suspend fun logout() {
        try {
            authApi.logout()
        } catch (e: Exception) {
            Timber.e(e, "Logout API call failed")
        }
        tokenManager.clearTokens()
        userPreferences.clearUserInfo()
        blePreferences.clearSelectedProjectChip()
    }
}