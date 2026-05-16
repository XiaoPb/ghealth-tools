package com.ghealth.tools.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ghealth.tools.core.ui.component.GHealthTopAppBar

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
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}
