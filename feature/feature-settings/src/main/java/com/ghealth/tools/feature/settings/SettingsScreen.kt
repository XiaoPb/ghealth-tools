package com.ghealth.tools.feature.settings

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ghealth.tools.core.ui.component.ErrorEffect
import com.ghealth.tools.core.ui.adaptive.CONTENT_MAX_WIDTH
import com.ghealth.tools.core.ui.adaptive.isWide
import com.ghealth.tools.core.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun SettingsScreen(
    onNavigateToDeviceinfo: () -> Unit = {},
    onSwitchProject: () -> Unit = {},
    onNavigateToProjectManage: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val ctx = LocalContext.current
    val windowSizeClass = calculateWindowSizeClass(ctx as Activity)
    val maxW = if (windowSizeClass.widthSizeClass.isWide) CONTENT_MAX_WIDTH else Dp.Infinity

    LaunchedEffect(state.exportedLogPath) {
        state.exportedLogPath?.let { path ->
            Toast.makeText(ctx, "日志已导出: $path", Toast.LENGTH_LONG).show()
            viewModel.clearExportMessage()
        }
    }

    ErrorEffect(
        errorMessage = state.operationMessage,
        onDismiss = viewModel::clearOperationMessage,
        useToast = true
    )

    var showDeleteDialog by remember { mutableStateOf(false) }

    BoxWithConstraints(
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
        var bleExpanded by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { bleExpanded = !bleExpanded }
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader("BLE UUID 配置", modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (bleExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (bleExpanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        AnimatedVisibility(visible = bleExpanded) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = state.serviceUuid,
                        onValueChange = viewModel::updateServiceUuid,
                        label = { Text("Service UUID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.writeUuid,
                        onValueChange = viewModel::updateWriteUuid,
                        label = { Text("Write Characteristic UUID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.notifyUuid,
                        onValueChange = viewModel::updateNotifyUuid,
                        label = { Text("Notify Characteristic UUID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader("连接设置")
        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("自动重连") },
                supportingContent = { Text("断开后自动尝试重新连接") },
                trailingContent = {
                    Switch(
                        checked = state.autoReconnect,
                        onCheckedChange = { viewModel.toggleAutoReconnect() }
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader("外观设置")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                var expanded by remember { mutableStateOf(false) }
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = state.themeMode.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("配色主题") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        leadingIcon = {
                            ThemeColorPreview(state.themeMode)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        state.availableThemes.forEach { theme ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        ThemeColorPreview(theme)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(theme.displayName)
                                    }
                                },
                                onClick = {
                                    viewModel.setThemeMode(theme)
                                    expanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader("设备信息")
        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("设备信息") },
                supportingContent = { Text("查看主设备版本信息") },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                trailingContent = {
                    TextButton(onClick = onNavigateToDeviceinfo) { Text("查看") }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader("支持的芯片")
        Card(modifier = Modifier.fillMaxWidth()) {
            val chipInfo = chipCompatibility[state.selectedChip] ?: chipCompatibility["gh3036"]!!
            ListItem(
                headlineContent = { Text(chipInfo.first) },
                supportingContent = { Text(chipInfo.second) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader("数据与日志")
        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("导出日志") },
                supportingContent = { Text("将日志打包为 ZIP 文件") },
                leadingContent = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                trailingContent = {
                    TextButton(onClick = viewModel::exportLogs) { Text("导出") }
                }
            )
        }

        if (state.isOnlineMode) {
            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader("项目管理")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    ListItem(
                        headlineContent = {
                            Text(state.selectedProjectName ?: "未选择项目")
                        },
                        supportingContent = { Text("当前在线项目") },
                        leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = {
                            Text(if (state.isSyncingConfig) "刷新中..." else "刷新配置")
                        },
                        supportingContent = { Text("从云端重新下载配置文件") },
                        leadingContent = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        trailingContent = {
                            TextButton(
                                onClick = viewModel::refreshConfig,
                                enabled = !state.isSyncingConfig
                            ) { Text("刷新") }
                        }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("管理项目") },
                        supportingContent = { Text("编辑、删除项目，查看 CSV 文件") },
                        leadingContent = { Icon(Icons.Default.Settings, contentDescription = null) },
                        trailingContent = {
                            TextButton(onClick = onNavigateToProjectManage) { Text("管理") }
                        }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("切换项目") },
                        supportingContent = { Text("选择其他项目") },
                        leadingContent = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                        trailingContent = {
                            TextButton(onClick = onSwitchProject) { Text("切换") }
                        }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = {
                            Text(
                                if (state.isDeletingProject) "删除中..." else "删除项目",
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        supportingContent = { Text("删除当前项目（不可恢复）") },
                        leadingContent = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        trailingContent = {
                            TextButton(
                                onClick = { showDeleteDialog = true },
                                enabled = !state.isDeletingProject
                            ) {
                                Text("删除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                }
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("确认删除项目") },
                text = { Text("确定要删除项目\"${state.selectedProjectName}\"吗？此操作不可恢复。") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteDialog = false
                        viewModel.deleteProject()
                    }) {
                        Text("确认删除", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader("关于")
        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("版本") },
                trailingContent = { Text(state.appVersion) }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("应用名称") },
                trailingContent = { Text("GHealth Tools") }
            )
        }
    }
    }
}

private val chipCompatibility = mapOf(
    "gh3036" to ("GH3036" to "GH3036 / GH3038 / GH3038Q"),
    "gh3300" to ("GH3300" to "GH3300 / GH3310 / GH3030"),
    "gh3220" to ("GH3220" to "GH3220 / GH3020 / GH3036 / GH3228T")
)

@Composable
private fun ThemeColorPreview(theme: ThemeMode) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(theme.previewColor)
    )
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(bottom = 0.dp)
    )
}
