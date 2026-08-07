package com.ghealth.tools.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.core.datastore.UserPreferences
import com.ghealth.tools.core.network.ApiErrorParser
import com.ghealth.tools.core.network.AuthRepository
import com.ghealth.tools.core.network.TokenManager
import com.ghealth.tools.core.network.api.AuthApi
import com.ghealth.tools.core.network.model.LoginRequest
import com.ghealth.tools.core.network.model.RegisterRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class RegisterUiState(
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val passwordConfirm: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val usernameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val passwordConfirmError: String? = null
) {
    val isValid: Boolean
        get() = username.isNotBlank() && 
                email.isNotBlank() && 
                password.isNotBlank() && 
                passwordConfirm.isNotBlank() &&
                usernameError == null &&
                emailError == null &&
                passwordError == null &&
                passwordConfirmError == null
}

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val tokenManager: TokenManager,
    private val authApi: AuthApi,
    private val authRepository: AuthRepository,
    private val apiErrorParser: ApiErrorParser
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun updateUsername(username: String) {
        val usernamePattern = Regex("^[a-zA-Z0-9]+$")
        val error = when {
            username.isBlank() -> "用户名不能为空"
            username.length < 3 -> "用户名至少3个字符"
            username.length > 150 -> "用户名最多150个字符"
            !usernamePattern.matches(username) -> "用户名只能包含字母和数字"
            else -> null
        }
        _uiState.update { it.copy(username = username, usernameError = error, errorMessage = null) }
    }

    fun updateEmail(email: String) {
        val error = when {
            email.isBlank() -> "邮箱不能为空"
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "邮箱格式不正确"
            else -> null
        }
        _uiState.update { it.copy(email = email, emailError = error, errorMessage = null) }
    }

    fun updatePassword(password: String) {
        val hasLetter = password.any { it.isLetter() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecial = password.any { !it.isLetterOrDigit() }
        val error = when {
            password.isBlank() -> "密码不能为空"
            password.length < 8 -> "密码至少8个字符"
            !hasLetter -> "密码需包含字母"
            !hasDigit -> "密码需包含数字"
            !hasSpecial -> "密码需包含特殊字符"
            else -> null
        }
        val confirmError = if (_uiState.value.passwordConfirm.isNotBlank() && 
                               password != _uiState.value.passwordConfirm) {
            "两次密码不一致"
        } else {
            null
        }
        _uiState.update { 
            it.copy(
                password = password, 
                passwordError = error, 
                passwordConfirmError = confirmError,
                errorMessage = null
            ) 
        }
    }

    fun updatePasswordConfirm(passwordConfirm: String) {
        val error = when {
            passwordConfirm.isBlank() -> "请确认密码"
            passwordConfirm != _uiState.value.password -> "两次密码不一致"
            else -> null
        }
        _uiState.update { it.copy(passwordConfirm = passwordConfirm, passwordConfirmError = error, errorMessage = null) }
    }

    fun register(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val state = _uiState.value
        
        if (!state.isValid) {
            _uiState.update { it.copy(errorMessage = "请检查输入信息") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                val response = authApi.register(
                    RegisterRequest(
                        username = state.username,
                        email = state.email,
                        password = state.password,
                        passwordConfirm = state.passwordConfirm
                    )
                )

                if (response.isSuccessful && response.body()?.data != null) {
                    val loginResponse = authRepository.login(
                        LoginRequest(state.username, state.password)
                    )

                    if (loginResponse.isSuccessful && loginResponse.body()?.data != null) {
                        val loginData = loginResponse.body()!!.data!!
                        tokenManager.saveTokens(loginData.access, loginData.refresh)
                        userPreferences.saveUserInfo(
                            id = loginData.user.id,
                            username = loginData.user.username,
                            email = loginData.user.email
                        )
                        _uiState.update { it.copy(isLoading = false) }
                        onSuccess()
                    } else {
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = "注册成功但自动登录失败，请返回手动登录")
                        }
                    }
                } else {
                    val errorMsg = apiErrorParser.parseErrors(response)
                    _uiState.update { it.copy(isLoading = false, errorMessage = errorMsg) }
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                Timber.e(e, "Register failed")
                val errorMsg = "网络错误: ${e.message ?: "未知错误"}"
                _uiState.update { it.copy(isLoading = false, errorMessage = errorMsg) }
                onError(errorMsg)
            }
        }
    }
}
