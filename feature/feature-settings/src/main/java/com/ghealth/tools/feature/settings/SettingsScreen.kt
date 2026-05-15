package com.ghealth.tools.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ghealth.tools.core.ui.component.GHealthTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { GHealthTopAppBar(title = "设置") }
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
