package com.ghealth.tools.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

data class FeedbackUiState(
    val selectedCategory: FeedbackCategory = FeedbackCategory.BUG
)

@HiltViewModel
class FeedbackViewModel @Inject constructor(
    @Named("app_version") private val appVersion: String,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedbackUiState())
    val uiState: StateFlow<FeedbackUiState> = _uiState.asStateFlow()

    fun selectCategory(category: FeedbackCategory) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
    }

    fun openGithubIssues() {
        openUrl(FeedbackLinks.githubIssueUrl(_uiState.value.selectedCategory, appVersion))
    }

    fun openFeishuForm() {
        openUrl(FeedbackLinks.FEISHU_FORM_URL)
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to open feedback URL: $url")
        }
    }
}