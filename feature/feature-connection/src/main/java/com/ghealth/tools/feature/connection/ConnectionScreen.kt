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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ghealth.tools.ble.connection.ConnectedDevice
import com.ghealth.tools.ble.connection.DeviceRole
import com.ghealth.tools.core.model.BleDevice
import com.ghealth.tools.core.model.ConnectionState
import com.ghealth.tools.core.ui.component.EmptyStateView
import com.ghealth.tools.core.ui.component.GHealthTopAppBar
import com.ghealth.tools.core.ui.component.StatusBadge
import com.ghealth.tools.core.ui.component.ConnectionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(viewModel: ConnectionViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            GHealthTopAppBar(
                title = "设备连接",
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "菜单")
                    }
                    ConnectionMenu(
                        expanded = menuExpanded,
                        onDismiss = { menuExpanded = false },
                        onScanMaster = { menuExpanded = false; viewModel.startScan(DeviceRole.MASTER) },
                        onScanSlave = { menuExpanded = false; viewModel.startScan(DeviceRole.SLAVE) },
                        onScanCompare = { menuExpanded = false; viewModel.startScan(DeviceRole.COMPARE) },
                        onDisconnectAll = { menuExpanded = false; viewModel.disconnectAll() },
                        onWorkMode = { menuExpanded = false; viewModel.showWorkModeDialog() },
                        onCommand = { menuExpanded = false; viewModel.showCommandSheet() }
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.connectedDevices.isNotEmpty()) {
                DeviceCards(
                    devices = state.connectedDevices.values.toList(),
                    onDisconnect = viewModel::disconnectDevice
                )
            }

            if (state.isScanning) {
                ScanSection(
                    results = state.scanResults,
                    role = state.scanForRole,
                    onSelect = viewModel::connectDevice,
                    onStop = viewModel::stopScan
                )
            } else if (state.connectedDevices.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.Bluetooth,
                    title = "未连接设备",
                    subtitle = "点击菜单扫描并连接 BLE 设备"
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
private fun ConnectionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onScanMaster: () -> Unit,
    onScanSlave: () -> Unit,
    onScanCompare: () -> Unit,
    onDisconnectAll: () -> Unit,
    onWorkMode: () -> Unit,
    onCommand: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("连接主设备") }, onClick = onScanMaster)
        DropdownMenuItem(text = { Text("连接从设备") }, onClick = onScanSlave)
        DropdownMenuItem(text = { Text("连接对比设备") }, onClick = onScanCompare)
        DropdownMenuItem(text = { Text("断开全部") }, onClick = onDisconnectAll)
        DropdownMenuItem(text = { Text("工作模式") }, onClick = onWorkMode)
        DropdownMenuItem(text = { Text("命令操作") }, onClick = onCommand)
    }
}

@Composable
private fun DeviceCards(
    devices: List<ConnectedDevice>,
    onDisconnect: (String) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        devices.forEach { device ->
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = device.name ?: device.address,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusBadge(
                                status = device.state.toUiStatus(),
                                label = device.role.name
                            )
                        }
                    }
                    TextButton(onClick = { onDisconnect(device.address) }) {
                        Text("断开")
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanSection(
    results: List<BleDevice>,
    role: DeviceRole?,
    onSelect: (BleDevice) -> Unit,
    onStop: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.BluetoothSearching, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "扫描中 - ${role?.name ?: ""}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(onClick = onStop) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("停止")
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(results, key = { it.address }) { device ->
                ScanResultItem(device = device, onClick = { onSelect(device) })
            }
        }
    }
}

@Composable
private fun ScanResultItem(device: BleDevice, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
