package com.ghealth.tools.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ghealth.tools.core.ui.component.GHealthTopAppBar
import com.ghealth.tools.core.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToDeviceinfo: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.exportedLogPath) {
        state.exportedLogPath?.let { path ->
            snackbarHostState.showSnackbar("日志已导出: $path")
            viewModel.clearExportMessage()
        }
    }

    Scaffold(
        topBar = { GHealthTopAppBar(title = "设置") },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SectionHeader("BLE UUID 配置")
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
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}
