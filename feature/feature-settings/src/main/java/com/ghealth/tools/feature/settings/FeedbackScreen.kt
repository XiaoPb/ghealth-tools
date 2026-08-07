package com.ghealth.tools.feature.settings

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ghealth.tools.core.ui.adaptive.CONTENT_MAX_WIDTH
import com.ghealth.tools.core.ui.adaptive.isWide

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun FeedbackScreen(
    onNavigateBack: () -> Unit,
    viewModel: FeedbackViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val windowSizeClass = calculateWindowSizeClass(context as Activity)
    val isWide = windowSizeClass.widthSizeClass.isWide
    val maxW = if (isWide) CONTENT_MAX_WIDTH else Dp.Infinity
    val listItemColors = ListItemDefaults.colors(containerColor = Color.Transparent)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = maxW)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "感谢您的反馈！请选择反馈类别，然后选择提交渠道。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            SectionHeader("反馈类别")
            SettingsGroupCard {
                FeedbackCategory.entries.forEachIndexed { index, category ->
                    if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = { Text(category.label) },
                        supportingContent = { Text(category.description) },
                        leadingContent = {
                            Icon(
                                imageVector = category.icon(),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            RadioButton(
                                selected = state.selectedCategory == category,
                                onClick = null
                            )
                        },
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .selectable(
                                selected = state.selectedCategory == category,
                                onClick = { viewModel.selectCategory(category) },
                                role = Role.RadioButton
                            ),
                        colors = listItemColors
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader("提交渠道")
            SettingsGroupCard {
                ListItem(
                    headlineContent = { Text("GitHub Issues") },
                    supportingContent = {
                        Text("在 GitHub 上提交 Issue，将自动附带所选类别与 App 版本信息")
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Feedback,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    colors = listItemColors
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ListItem(
                    headlineContent = { Text("飞书表单") },
                    supportingContent = {
                        Text("在飞书多维表格中填写反馈表单")
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    colors = listItemColors
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.openGithubIssues() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("GitHub Issues")
                    }
                    OutlinedButton(
                        onClick = { viewModel.openFeishuForm() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("飞书表单")
                    }
                }
            }
        }
    }
}

private fun FeedbackCategory.icon(): ImageVector = when (this) {
    FeedbackCategory.BUG -> Icons.Default.BugReport
    FeedbackCategory.FEATURE -> Icons.Default.Lightbulb
    FeedbackCategory.OTHER -> Icons.Default.MoreHoriz
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(bottom = 0.dp)
    )
}

@Composable
private fun SettingsGroupCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column {
            content()
        }
    }
}


