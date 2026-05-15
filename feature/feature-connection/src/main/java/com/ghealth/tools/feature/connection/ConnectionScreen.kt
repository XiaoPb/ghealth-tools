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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ghealth.tools.ble.connection.ConnectedDevice
import com.ghealth.tools.ble.connection.DeviceRole
import com.ghealth.tools.core.model.BleDevice
import com.ghealth.tools.core.model.ConnectionState
import com.ghealth.tools.core.ui.component.GHealthTopAppBar
import com.ghealth.tools.core.ui.component.StatusBadge
import com.ghealth.tools.core.ui.component.ConnectionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(viewModel: ConnectionViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { GHealthTopAppBar(title = "GHealth Tools") }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.isScanning) {
                ScanSection(
                    results = state.scanResults,
                    role = state.scanForRole,
                    onSelect = { device, _ -> viewModel.connectDevice(device) },
                    onStop = viewModel::stopScan
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
                    onCommand = viewModel::showCommandSheet
                )
            }
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
    if (state.showCommandSheet) {
        CommandBottomSheet(
            onSendCommand = viewModel::sendCommand,
            onDismiss = viewModel::dismissCommandSheet
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
                        StatusBadge(status = ConnectionStatus.Connected)
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
    onSelect: (BleDevice, DeviceRole) -> Unit,
    onStop: () -> Unit
) {
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
            FilledTonalButton(onClick = onStop) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("停止")
            }
        }

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
            Text(
                text = "${device.rssi} dBm",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun ConnectionState.toUiStatus(): ConnectionStatus = when (this) {
    ConnectionState.CONNECTED -> ConnectionStatus.Connected
    ConnectionState.CONNECTING -> ConnectionStatus.Connecting
    ConnectionState.DISCONNECTED, ConnectionState.DISCONNECTING -> ConnectionStatus.Disconnected
}
