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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ghealth.tools.core.ui.adaptive.CONTENT_MAX_WIDTH
import com.ghealth.tools.core.ui.adaptive.isWide
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun DeviceInfoScreen(
    onNavigateBack: () -> Unit,
    viewModel: DeviceInfoViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val windowSizeClass = calculateWindowSizeClass(context as Activity)
    val isWide = windowSizeClass.widthSizeClass.isWide
    val maxW = if (isWide) CONTENT_MAX_WIDTH else Dp.Infinity

    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = maxW)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (state.isReading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(4.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = { viewModel.refreshDeviceInfo() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            }

            ConnectionStatusCard(state)
            Spacer(modifier = Modifier.height(16.dp))
            VersionGroupCard(title = "基本版本", versions = state.basicVersions)
            Spacer(modifier = Modifier.height(12.dp))
            VersionGroupCard(title = "算法版本", versions = state.algoVersions)

            state.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(state: DeviceInfoUiState) {
    val listItemColors = ListItemDefaults.colors(containerColor = Color.Transparent)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = if (state.isConnected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (state.isConnected) "已连接" else "未连接",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (state.isConnected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
                )
            }

            if (state.isConnected) {
                Spacer(modifier = Modifier.height(16.dp))
                ListItem(
                    headlineContent = { Text("设备名称") },
                    trailingContent = { Text(state.deviceName) },
                    colors = listItemColors
                )
                ListItem(
                    headlineContent = { Text("MAC 地址") },
                    trailingContent = {
                        Text(
                            text = state.deviceAddress,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    colors = listItemColors
                )
            }
        }
    }
}

private fun getVersionIcon(label: String): ImageVector = when (label) {
    "固件版本" -> Icons.Default.Build
    "虚拟寄存器版本" -> Icons.Default.Memory
    "Bootloader版本" -> Icons.Default.DeveloperBoard
    "协议版本" -> Icons.Default.SyncAlt
    "驱动功能支持" -> Icons.Default.Functions
    "驱动版本" -> Icons.Default.DeveloperBoard
    "芯片版本" -> Icons.Default.Memory
    "BLE版本" -> Icons.Default.Bluetooth
    "算法Demo版本" -> Icons.Default.Timeline
    "HR" -> Icons.Default.Favorite
    "HRV" -> Icons.Default.Timeline
    "SpO2" -> Icons.Default.MonitorHeart
    "ADT", "NADT" -> Icons.Default.Sensors
    else -> Icons.Default.Memory
}

@Composable
private fun VersionGroupCard(
    title: String,
    versions: List<VersionEntry>,
    modifier: Modifier = Modifier,
) {
    val listItemColors = ListItemDefaults.colors(containerColor = Color.Transparent)
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column {
            ListItem(
                headlineContent = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                colors = listItemColors
            )

            if (versions.isEmpty()) {
                ListItem(
                    headlineContent = { Text("等待读取...") },
                    leadingContent = {
                        Icon(
                            Icons.Default.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = listItemColors
                )
            } else {
                versions.forEachIndexed { index, entry ->
                    if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = { Text(entry.label) },
                        supportingContent = {
                            if (entry.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = entry.value,
                                    fontFamily = if (entry.isError) null else FontFamily.Monospace,
                                    color = when {
                                        entry.isError -> MaterialTheme.colorScheme.error
                                        entry.value == "no_ver" -> MaterialTheme.colorScheme.onSurfaceVariant
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        },
                        leadingContent = {
                            Icon(
                                imageVector = getVersionIcon(entry.label),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        colors = listItemColors
                    )
                }
            }
        }
    }
}
