package com.ghealth.tools.feature.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ghealth.tools.ble.connection.ConnectedDevice
import com.ghealth.tools.ble.connection.DeviceRole
import com.ghealth.tools.core.model.BleDevice
import com.ghealth.tools.core.model.ConnectionState
import com.ghealth.tools.core.ui.component.StatusBadge
import com.ghealth.tools.core.ui.component.ConnectionStatus

private object CommandRoutes {
    const val MAIN = "main"
    const val COMMAND_LIST = "command_list"
    const val COMMAND_DETAIL = "command_detail/{commandKey}"
    
    fun commandDetail(commandKey: String) = "command_detail/$commandKey"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(viewModel: ConnectionViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = CommandRoutes.MAIN
    ) {
        composable(CommandRoutes.MAIN) {
            ConnectionMainContent(
                viewModel = viewModel,
                state = state,
                onNavigateToCommands = {
                    navController.navigate(CommandRoutes.COMMAND_LIST)
                }
            )
        }
        composable(CommandRoutes.COMMAND_LIST) {
            CommandListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { commandKey ->
                    navController.navigate(CommandRoutes.commandDetail(commandKey))
                }
            )
        }
        composable(CommandRoutes.COMMAND_DETAIL) { backStackEntry ->
            val commandKey = backStackEntry.arguments?.getString("commandKey") ?: ""
            CommandDetailScreen(
                commandKey = commandKey,
                executionState = state.commandExecutionState,
                onNavigateBack = { navController.popBackStack() },
                onExecute = { key, params ->
                    viewModel.executeCommand(key, params)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionMainContent(
    viewModel: ConnectionViewModel,
    state: ConnectionUiState,
    onNavigateToCommands: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (state.isScanning) {
            ScanSection(
                results = state.scanResults,
                role = state.scanForRole,
                minRssi = state.minRssi,
                onSelect = { device, _ -> viewModel.connectDevice(device) },
                onStop = viewModel::stopScan,
                onRssiChange = viewModel::setMinRssi
            )
        } else {
            MainMenuContent(
                state = state,
                onScanMaster = { viewModel.startScan(DeviceRole.MASTER) },
                onScanSlave = { viewModel.startScan(DeviceRole.SLAVE) },
                onScanCompare = { viewModel.startScan(DeviceRole.COMPARE) },
                onDisconnectAll = viewModel::disconnectAll,
                onDisconnectDevice = viewModel::disconnectDevice,
                onWorkMode = viewModel::showWorkModeDialog,
                onCommand = onNavigateToCommands
            )
        }
    }

    if (state.showWorkModeDialog) {
        WorkModeDialog(
            onSelect = viewModel::setWorkMode,
            onDismiss = viewModel::dismissWorkModeDialog
        )
    }
    if (state.showFunctionDialog) {
        FunctionSelectDialog(
            selected = state.selectedFunctions,
            onConfirm = viewModel::setSelectedFunctions,
            onDismiss = viewModel::dismissFunctionDialog
        )
    }

    if (state.showTestConfigDialog && state.masterDeviceName != null) {
        TestConfigDialog(
            deviceName = state.masterDeviceName,
            onConfirm = viewModel::confirmTestConfig,
            onDismiss = viewModel::dismissTestConfigDialog
        )
    }

    state.scanError?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::clearScanError,
            title = { Text("扫描错误") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = viewModel::clearScanError) {
                    Text("确定")
                }
            }
        )
    }

    state.connectionError?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::clearConnectionError,
            title = { Text("连接错误") },
            text = {
                Column {
                    Text("设备: ${state.connectionErrorDevice ?: "未知"}")
                    Text(error)
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::clearConnectionError) {
                    Text("确定")
                }
            }
        )
    }
}

@Composable
private fun MainMenuContent(
    state: ConnectionUiState,
    onScanMaster: () -> Unit,
    onScanSlave: () -> Unit,
    onScanCompare: () -> Unit,
    onDisconnectAll: () -> Unit,
    onDisconnectDevice: (String) -> Unit,
    onWorkMode: () -> Unit,
    onCommand: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DeviceStatusCard(state.connectedDevices.values.toList(), onDisconnectDevice)

        if (state.dataMonitorState.isMonitoring || state.dataMonitorState.testConfig != null) {
            DataMonitorCard(
                state = state.dataMonitorState
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        MenuItemCard(
            icon = Icons.Default.Bluetooth,
            title = "连接主设备",
            subtitle = "扫描并连接主 BLE 设备",
            onClick = onScanMaster
        )
        MenuItemCard(
            icon = Icons.Default.Cable,
            title = "连接从设备",
            subtitle = "扫描并连接从 BLE 设备",
            onClick = onScanSlave
        )
        MenuItemCard(
            icon = Icons.Default.CompareArrows,
            title = "连接对比设备",
            subtitle = "扫描并连接对比 BLE 设备",
            onClick = onScanCompare
        )
        MenuItemCard(
            icon = Icons.Default.Tune,
            title = "工作模式",
            subtitle = state.currentWorkMode?.name ?: "未设置",
            onClick = onWorkMode
        )
        MenuItemCard(
            icon = Icons.Default.Terminal,
            title = "命令操作",
            subtitle = "发送 RPC 命令到设备",
            onClick = onCommand
        )

        if (state.connectedDevices.isNotEmpty()) {
            MenuItemCard(
                icon = Icons.Default.LinkOff,
                title = "断开全部",
                subtitle = "断开所有已连接设备",
                onClick = onDisconnectAll
            )
        }
    }
}

@Composable
private fun DeviceStatusCard(
    devices: List<ConnectedDevice>,
    onDisconnect: (String) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (devices.isNotEmpty())
                        Icons.Default.BluetoothConnected
                    else
                        Icons.Default.BluetoothDisabled,
                    contentDescription = null,
                    tint = if (devices.isNotEmpty())
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "设备连接状态",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (devices.isEmpty()) {
                Text(
                    text = "暂无已连接设备",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                devices.forEach { device ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${device.role.name} - ${device.name}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = device.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusBadge(status = device.state.toUiStatus())
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuItemCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ScanSection(
    results: List<BleDevice>,
    role: DeviceRole?,
    minRssi: Int,
    onSelect: (BleDevice, DeviceRole) -> Unit,
    onStop: () -> Unit,
    onRssiChange: (Int) -> Unit
) {
    var showFilter by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BluetoothSearching,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "扫描中 (${role?.name ?: ""})",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { showFilter = !showFilter }
                ) {
                    Icon(Icons.Default.SignalCellularAlt, contentDescription = null)
                }
                FilledTonalButton(onClick = onStop) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("停止")
                }
            }
        }

        if (showFilter) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("信号强度过滤", style = MaterialTheme.typography.bodyMedium)
                        Text("$minRssi dBm", style = MaterialTheme.typography.bodyMedium)
                    }
                    Slider(
                        value = minRssi.toFloat(),
                        onValueChange = { onRssiChange(it.toInt()) },
                        valueRange = -100f..-40f,
                        steps = 12
                    )
                    Text(
                        "仅显示信号强度 ≥ $minRssi dBm 的设备",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Text(
            text = "发现 ${results.size} 个设备",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(results) { device ->
                ScanResultItem(
                    device = device,
                    onClick = { role?.let { onSelect(device, it) } }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanResultItem(device: BleDevice, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: "Unknown",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${device.rssi} dBm",
                    style = MaterialTheme.typography.labelMedium,
                    color = getRssiColor(device.rssi)
                )
                RssiIndicator(rssi = device.rssi)
            }
        }
    }
}

@Composable
private fun RssiIndicator(rssi: Int) {
    val color = getRssiColor(rssi)
    val bars = when {
        rssi >= -50 -> 4
        rssi >= -60 -> 3
        rssi >= -70 -> 2
        rssi >= -80 -> 1
        else -> 1
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(4) { index ->
            val height = (8 + index * 4).dp
            val isActive = index < bars
            Card(
                modifier = Modifier
                    .width(4.dp)
                    .height(height),
                colors = CardDefaults.cardColors(
                    containerColor = if (isActive) color
                    else MaterialTheme.colorScheme.outlineVariant
                )
            ) {}
        }
    }
}

@Composable
private fun getRssiColor(rssi: Int) = when {
    rssi >= -50 -> MaterialTheme.colorScheme.primary
    rssi >= -60 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    rssi >= -70 -> MaterialTheme.colorScheme.tertiary
    rssi >= -80 -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
    else -> MaterialTheme.colorScheme.outline
}

private fun ConnectionState.toUiStatus(): ConnectionStatus = when (this) {
    ConnectionState.CONNECTED -> ConnectionStatus.Connected
    ConnectionState.CONNECTING -> ConnectionStatus.Connecting
    ConnectionState.DISCONNECTED, ConnectionState.DISCONNECTING -> ConnectionStatus.Disconnected
}
