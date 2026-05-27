package com.ghealth.tools.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghealth.tools.core.datastore.BlePreferences
import com.ghealth.tools.core.datastore.UserPreferences
import com.ghealth.tools.core.model.DeviceType
import com.ghealth.tools.core.network.ApiErrorParser
import com.ghealth.tools.core.network.TokenManager
import com.ghealth.tools.core.network.api.AuthApi
import com.ghealth.tools.core.network.model.LoginRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val blePreferences: BlePreferences,
    private val userPreferences: UserPreferences,
    private val tokenManager: TokenManager,
    private val authApi: AuthApi,
    private val apiErrorParser: ApiErrorParser
) : ViewModel() {

    private val _selectedChip = MutableStateFlow(DeviceType.GH3036)
    val selectedChip: StateFlow<DeviceType> = _selectedChip.asStateFlow()

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            blePreferences.selectedChip.collect { chipName ->
                _selectedChip.value = DeviceType.entries.find { it.chipName == chipName }
                    ?: DeviceType.GH3036
            }
        }
    }

    fun selectChip(chip: DeviceType) {
        _selectedChip.value = chip
        viewModelScope.launch {
            blePreferences.setSelectedChip(chip.chipName)
        }
    }

    fun updateUsername(username: String) {
        _uiState.update { it.copy(username = username, errorMessage = null) }
    }

    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun login(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val state = _uiState.value
        if (state.username.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入用户名和密码") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                val response = authApi.login(LoginRequest(state.username, state.password))
                
                if (response.isSuccessful && response.body()?.data != null) {
                    val loginData = response.body()!!.data!!
                    
                    tokenManager.saveTokens(loginData.access, loginData.refresh)
                    userPreferences.saveUserInfo(
                        id = loginData.user.id,
                        username = loginData.user.username,
                        email = loginData.user.email
                    )
                    
                    _uiState.update { it.copy(isLoading = false) }
                    onSuccess()
                } else {
                    val errorMsg = apiErrorParser.parseErrors(response)
                    _uiState.update { it.copy(isLoading = false, errorMessage = errorMsg) }
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                Timber.e(e, "Login failed")
                val errorMsg = "网络错误: ${e.message ?: "未知错误"}"
                _uiState.update { it.copy(isLoading = false, errorMessage = errorMsg) }
                onError(errorMsg)
            }
        }
    }
}
