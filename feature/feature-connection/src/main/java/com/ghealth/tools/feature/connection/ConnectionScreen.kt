package com.ghealth.tools.feature.connection

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.ghealth.tools.core.ui.adaptive.shouldUseLandscapeLayout
import com.ghealth.tools.core.ui.component.EmptyStateView
import com.ghealth.tools.core.ui.component.StatusBadge
import com.ghealth.tools.core.ui.component.ConnectionStatus
import com.ghealth.tools.core.ui.theme.ButtonShape

private object CommandRoutes {
    const val MAIN = "main"
    const val COMMAND_PANEL = "command_panel"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun ConnectionScreen(
    viewModel: ConnectionViewModel = hiltViewModel(),
    onFactoryTest: () -> Unit = {},
    onOtaUpgrade: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val windowSizeClass = calculateWindowSizeClass(context as Activity)
    val isLandscape = windowSizeClass.shouldUseLandscapeLayout

    if (isLandscape) {
        ConnectionScreenLandscape(viewModel, state, onFactoryTest, onOtaUpgrade)
    } else {
        ConnectionScreenCompact(viewModel, state, onFactoryTest, onOtaUpgrade)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionScreenLandscape(
    viewModel: ConnectionViewModel,
    state: ConnectionUiState,
    onFactoryTest: () -> Unit = {},
    onOtaUpgrade: () -> Unit = {},
) {
    var showCommandPanel by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(0.5f).fillMaxHeight()) {
            MainMenuContent(
                state = state,
                onScanMaster = { viewModel.startScan(DeviceRole.MASTER) },
                onScanSlave = { viewModel.startScan(DeviceRole.SLAVE) },
                onScanCompare = { viewModel.startScan(DeviceRole.COMPARE) },
                onDisconnectAll = viewModel::disconnectAll,
                onDisconnectDevice = viewModel::disconnectDevice,
                onWorkMode = viewModel::showWorkModeDialog,
                onAppConfig = viewModel::showAppConfigDialog,
                onCommand = { showCommandPanel = true },
                onFactoryTest = onFactoryTest,
                onOtaUpgrade = onOtaUpgrade,
            )
        }

        VerticalDivider()

        Box(modifier = Modifier.weight(0.5f).fillMaxHeight()) {
            when {
                state.isScanning -> {
                    ScanSection(
                        results = state.scanResults,
                        role = state.scanForRole,
                        minRssi = state.minRssi,
                        onSelect = { device, _ -> viewModel.connectDevice(device) },
                        onStop = viewModel::stopScan,
                        onRssiChange = viewModel::setMinRssi,
                        onSortByRssi = viewModel::sortScanResultsByRssi
                    )
                }
                showCommandPanel -> {
                    CommandPanelScreen(
                        commandExecutionStates = state.commandExecutionStates,
                        onNavigateBack = { showCommandPanel = false },
                        onExecute = { key, params -> viewModel.executeCommand(key, params) },
                        showBackButton = false,
                        chipName = state.selectedChip,
                        registerConfigDownloadState = state.registerConfigDownloadState,
                        onLoadRegisterConfigs = viewModel::loadRegisterConfigFiles,
                        onSelectRegisterConfigFile = viewModel::selectRegisterConfigFile,
                        onExecuteRegisterConfigDownload = viewModel::executeRegisterConfigDownload,
                        onResetRegisterConfigDownload = viewModel::resetRegisterConfigDownload
                    )
                }
                else -> {
                    EmptyStateView(
                        icon = Icons.Default.Bluetooth,
                        title = "主界面",
                        subtitle = "使用左侧菜单连接设备或执行操作"
                    )
                }
            }
        }
    }

    if (state.showWorkModeDialog) {
        WorkModeDialog(
            currentMode = state.currentWorkMode,
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
                TextButton(onClick = viewModel::clearScanError) { Text("确定") }
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
                TextButton(onClick = viewModel::clearConnectionError) { Text("确定") }
            }
        )
    }
    if (state.showAppConfigDialog) {
        AppConfigDialog(
            downloadState = state.registerConfigDownloadState,
            chipName = state.selectedChip,
            onReloadConfigs = { viewModel.loadRegisterConfigFiles(state.selectedChip) },
            onSelectAndDownload = viewModel::selectAndDownloadConfig,
            onDismiss = viewModel::dismissAppConfigDialog
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionScreenCompact(
    viewModel: ConnectionViewModel,
    state: ConnectionUiState,
    onFactoryTest: () -> Unit = {},
    onOtaUpgrade: () -> Unit = {},
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = CommandRoutes.MAIN
    ) {
        composable(CommandRoutes.MAIN) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (state.isScanning) {
                    ScanSection(
                        results = state.scanResults,
                        role = state.scanForRole,
                        minRssi = state.minRssi,
                        onSelect = { device, _ -> viewModel.connectDevice(device) },
                        onStop = viewModel::stopScan,
                        onRssiChange = viewModel::setMinRssi,
                        onSortByRssi = viewModel::sortScanResultsByRssi
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
                        onAppConfig = viewModel::showAppConfigDialog,
                        onCommand = { navController.navigate(CommandRoutes.COMMAND_PANEL) },
                        onFactoryTest = onFactoryTest,
                        onOtaUpgrade = onOtaUpgrade,
                    )
                }
            }

            if (state.showWorkModeDialog) {
                WorkModeDialog(
                    currentMode = state.currentWorkMode,
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
                        TextButton(onClick = viewModel::clearScanError) { Text("确定") }
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
                        TextButton(onClick = viewModel::clearConnectionError) { Text("确定") }
                    }
                )
            }
            if (state.showAppConfigDialog) {
                AppConfigDialog(
                    downloadState = state.registerConfigDownloadState,
                    chipName = state.selectedChip,
                    onReloadConfigs = { viewModel.loadRegisterConfigFiles(state.selectedChip) },
                    onSelectAndDownload = viewModel::selectAndDownloadConfig,
                    onDismiss = viewModel::dismissAppConfigDialog
                )
            }
        }
        composable(CommandRoutes.COMMAND_PANEL) {
            CommandPanelScreen(
                commandExecutionStates = state.commandExecutionStates,
                onNavigateBack = { navController.popBackStack() },
                onExecute = { key, params -> viewModel.executeCommand(key, params) },
                chipName = state.selectedChip,
                registerConfigDownloadState = state.registerConfigDownloadState,
                onLoadRegisterConfigs = viewModel::loadRegisterConfigFiles,
                onSelectRegisterConfigFile = viewModel::selectRegisterConfigFile,
                onExecuteRegisterConfigDownload = viewModel::executeRegisterConfigDownload,
                onResetRegisterConfigDownload = viewModel::resetRegisterConfigDownload
            )
        }
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
    onAppConfig: () -> Unit,
    onCommand: () -> Unit,
    onFactoryTest: () -> Unit = {},
    onOtaUpgrade: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DeviceStatusCard(state.connectedDevices.values.toList(), onDisconnectDevice)

        if (state.dataMonitorState.isMonitoring || state.dataMonitorState.testConfig != null) {
            DataMonitorCard(state = state.dataMonitorState)
        }

        MenuGroupCard {
            MenuItem(
                icon = Icons.Default.Bluetooth,
                title = "连接主设备",
                subtitle = "扫描并连接主 BLE 设备",
                onClick = onScanMaster
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            MenuItem(
                icon = Icons.Default.Cable,
                title = "连接从设备",
                subtitle = "扫描并连接从 BLE 设备",
                onClick = onScanSlave
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            MenuItem(
                icon = Icons.Default.CompareArrows,
                title = "连接对比设备",
                subtitle = "扫描并连接对比 BLE 设备",
                onClick = onScanCompare
            )
            if (state.connectedDevices.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                MenuItem(
                    icon = Icons.Default.LinkOff,
                    title = "断开全部",
                    subtitle = "断开所有已连接设备",
                    onClick = onDisconnectAll
                )
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            MenuItem(
                icon = Icons.Default.Tune,
                title = "工作模式",
                subtitle = state.currentWorkMode?.name ?: "未设置",
                onClick = onWorkMode
            )
            if (state.currentWorkMode == com.ghealth.tools.core.model.WorkMode.MCU_ONLINE) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                val configSubtitle = state.registerConfigDownloadState.selectedConfig?.fileName
                    ?: "选择并下载寄存器配置到设备"
                MenuItem(
                    icon = Icons.Default.Memory,
                    title = "应用配置",
                    subtitle = configSubtitle,
                    onClick = onAppConfig
                )
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            MenuItem(
                icon = Icons.Default.Terminal,
                title = "命令操作",
                subtitle = "发送 RPC 命令到设备",
                onClick = onCommand
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            MenuItem(
                icon = Icons.Default.Science,
                title = "产测",
                subtitle = "自动化产测流程",
                onClick = onFactoryTest
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            MenuItem(
                icon = Icons.Default.SystemUpdate,
                title = "OTA固件升级",
                subtitle = "选择固件进行OTA升级",
                onClick = onOtaUpgrade
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

@Composable
private fun MenuGroupCard(
    content: @Composable () -> Unit
) {
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

@Composable
private fun MenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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

@Composable
private fun ScanSection(
    results: List<BleDevice>,
    role: DeviceRole?,
    minRssi: Int,
    onSelect: (BleDevice, DeviceRole) -> Unit,
    onStop: () -> Unit,
    onRssiChange: (Int) -> Unit,
    onSortByRssi: () -> Unit
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
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onSortByRssi) {
                    Icon(
                        Icons.Default.Sort,
                        contentDescription = "RSSI排序",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { showFilter = !showFilter }) {
                    Icon(
                        Icons.Default.SignalCellularAlt,
                        contentDescription = "信号过滤",
                        tint = if (showFilter) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = onStop,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    shape = ButtonShape
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.height(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("停止", style = MaterialTheme.typography.labelMedium)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppConfigDialog(
    downloadState: RegisterConfigDownloadState,
    chipName: String,
    onReloadConfigs: () -> Unit,
    onSelectAndDownload: (ConfigFileInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val configs = downloadState.availableConfigs
    val isLoading = downloadState.status == DownloadStatus.LOADING_CONFIGS
    val isDownloading = downloadState.status == DownloadStatus.DOWNLOADING
    val isCompleted = downloadState.status == DownloadStatus.COMPLETED
    val isError = downloadState.status == DownloadStatus.ERROR
    val selectedConfig = downloadState.selectedConfig

    val groupedConfigs = remember(configs) {
        configs.groupBy { it.displayPath.substringBefore("/") }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isDownloading) onDismiss()
        },
        title = {
            Column {
                Text(
                    text = "应用配置",
                    style = MaterialTheme.typography.titleLarge
                )
                if (groupedConfigs.size == 1) {
                    val projectName = groupedConfigs.keys.first()
                    Text(
                        text = "$projectName - ${chipName.uppercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (chipName.isNotBlank()) {
                    Text(
                        text = chipName.uppercase(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("加载配置文件中...")
                    }
                }
            } else if (configs.isEmpty() && !isDownloading && !isCompleted) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "未找到配置文件",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = onReloadConfigs) {
                            Text("重新加载")
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "共 ${configs.size} 个配置文件",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Box(modifier = Modifier.heightIn(max = 320.dp)) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            groupedConfigs.forEach { (projectName, projectConfigs) ->
                                if (groupedConfigs.size > 1) {
                                    item(key = "header_$projectName") {
                                        Text(
                                            text = projectName,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(
                                                start = 4.dp,
                                                top = 8.dp,
                                                bottom = 4.dp
                                            )
                                        )
                                    }
                                }
                                items(projectConfigs, key = { it.displayPath }) { info ->
                                    val isSelected = info == selectedConfig
                                    ConfigFileItem(
                                        fileName = info.fileName,
                                        isSelected = isSelected,
                                        isDownloading = isDownloading,
                                        isCompleted = isCompleted,
                                        isError = isError,
                                        onClick = {
                                            if (!isDownloading && !isCompleted) {
                                                onSelectAndDownload(info)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (isDownloading) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "正在下载配置到设备...",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    if (isCompleted) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Done,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "配置下载完成",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        selectedConfig?.fileName ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    downloadState.error?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (isCompleted || isError) {
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        },
        dismissButton = {
            if (!isDownloading && !isCompleted) {
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigFileItem(
    fileName: String,
    isSelected: Boolean,
    isDownloading: Boolean,
    isCompleted: Boolean,
    isError: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected && isDownloading -> MaterialTheme.colorScheme.primaryContainer
                isSelected && isError -> MaterialTheme.colorScheme.errorContainer
                isSelected && isCompleted -> MaterialTheme.colorScheme.primaryContainer
                isSelected -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        enabled = !isDownloading && !isCompleted
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            if (isSelected && isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            } else if (isSelected && isCompleted) {
                Icon(
                    Icons.Default.Done,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
