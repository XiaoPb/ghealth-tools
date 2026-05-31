package com.ghealth.tools.feature.ota

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ghealth.tools.core.ui.theme.ButtonShape
import com.ghealth.tools.core.ui.adaptive.CONTENT_MAX_WIDTH
import com.ghealth.tools.core.ui.adaptive.isWide
import com.ghealth.tools.feature.ota.engine.DebugResult
import com.ghealth.tools.feature.ota.engine.FirmwareInfo
import com.ghealth.tools.feature.ota.engine.OtaEngine
import com.ghealth.tools.feature.ota.engine.OtaState
import com.ghealth.tools.feature.ota.model.DebugMenuAction
import com.ghealth.tools.feature.ota.model.StorageType
import com.ghealth.tools.feature.ota.model.UpgradeRegion

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun OtaScreen(
    onNavigateBack: () -> Unit,
    viewModel: OtaViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    val context = LocalContext.current
    val windowSizeClass = calculateWindowSizeClass(context as Activity)
    val maxW = if (windowSizeClass.widthSizeClass.isWide) CONTENT_MAX_WIDTH else Dp.Infinity

    val firmwareFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { viewModel.selectFirmwareFile(it) } }

    val resourceFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { viewModel.selectResourceFile(it) } }

    val logListState = rememberLazyListState()
    LaunchedEffect(Unit) {
        snapshotFlow { state.logLines.size }
            .collect { size ->
                if (size > 0) {
                    logListState.animateScrollToItem(size - 1)
                }
            }
    }

    if (state.showResultDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissResultDialog() },
            title = { Text(if (state.successMessage != null) "升级完成" else "升级失败") },
            text = { Text(state.successMessage ?: state.errorMessage ?: "") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissResultDialog()
                    onNavigateBack()
                }) { Text("返回") }
            },
            dismissButton = {
                if (state.successMessage == null) {
                    TextButton(onClick = {
                        viewModel.dismissResultDialog()
                        viewModel.resetState()
                    }) { Text("重试") }
                }
            }
        )
    }

    if (state.showControlPointDialog) {
        var hexText by remember { mutableStateOf(state.controlPointHex) }
        AlertDialog(
            onDismissRequest = { viewModel.dismissControlPointDialog() },
            title = { Text("写控制点") },
            text = {
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { hexText = it; viewModel.updateControlPointHex(it) },
                    label = { Text("Hex数据") },
                    placeholder = { Text("AA BB CC DD") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.writeControlPoint()
                }) { Text("发送") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissControlPointDialog() }) { Text("取消") }
            }
        )
    }

    if (state.isUpgrading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("正在升级") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
                        CircularProgressIndicator(
                            progress = { state.progressPercent.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 6.dp,
                        )
                        Text(
                            text = "${(state.progressPercent * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = when (state.otaState) {
                            OtaState.IDLE -> "等待开始"
                            OtaState.PREPARING -> "准备中..."
                            OtaState.CONNECTING -> "连接设备中..."
                            OtaState.TRANSFERRING -> "正在传输..."
                            OtaState.VERIFYING -> "校验中..."
                            OtaState.COMPLETED -> "升级完成 \u2713"
                            OtaState.CANCELLED -> "已取消"
                            OtaState.ERROR -> "升级失败 \u2717"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { viewModel.cancelUpgrade() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                ) {
                    Text("取消升级")
                }
            }
        )
    }

    BackHandler(enabled = state.isUpgrading) {
        viewModel.cancelUpgrade()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = maxW)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DeviceInfoBar(selectedDevice = state.selectedDevice)

            FirmwareInfoCard(
                uiState = state,
                onReadFwInfo = { viewModel.readFirmwareInfo() },
            )

            FirmwareUpgradeCard(
                fileInfo = state.firmwareFile,
                upgradeRegion = state.upgradeRegion,
                copyAddress = state.otaConfig.copyAddress,
                onSelectFile = { firmwareFilePicker.launch(arrayOf("*/*")) },
                onSelectRegion = viewModel::selectUpgradeRegion,
                onCopyAddressChange = viewModel::updateCopyAddress,
                onStartUpgrade = viewModel::startFirmwareUpgrade,
                enabled = !state.isUpgrading,
            )

            ResourceUpgradeCard(
                fileInfo = state.resourceFile,
                startAddress = state.resourceStartAddress,
                storageType = state.resourceStorageType,
                onSelectFile = { resourceFilePicker.launch(arrayOf("*/*")) },
                onStartAddressChange = viewModel::updateResourceStartAddress,
                onStorageTypeChange = viewModel::updateResourceStorageType,
                onStartUpgrade = viewModel::startResourceUpgrade,
                enabled = !state.isUpgrading,
            )

            state.activeDebugActions.forEach { action ->
                DebugActionCard(
                    action = action,
                    debugResult = state.debugResults[action],
                    onDebugRead = { engine -> viewModel.executeDebugCommand(action, engine) },
                    onDebugWrite = { engine -> viewModel.executeDebugWrite(action, engine) },
                )
            }

            LogCard(
                logLines = state.logLines,
                listState = logListState,
            )

            state.errorMessage?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::dismissError) { Text("关闭") }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun OtaTopBarMenu(viewModel: OtaViewModel) {
    val state by viewModel.uiState.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "菜单")
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("快速模式") },
                onClick = { viewModel.updateFastMode(!state.otaConfig.fastMode) },
                leadingIcon = {
                    Checkbox(
                        checked = state.otaConfig.fastMode,
                        onCheckedChange = null,
                    )
                },
            )
            DropdownMenuItem(
                text = { Text("自定义拷贝地址") },
                onClick = { viewModel.toggleCopyAddressEnabled() },
                leadingIcon = {
                    Checkbox(
                        checked = state.otaConfig.copyAddressEnabled,
                        onCheckedChange = null,
                    )
                },
            )
            DebugMenuAction.entries.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    onClick = { viewModel.toggleDebugAction(action) },
                    leadingIcon = {
                        Checkbox(
                            checked = state.activeDebugActions.contains(action),
                            onCheckedChange = null,
                        )
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("写控制点") },
                onClick = {
                    menuExpanded = false
                    viewModel.showControlPointDialog()
                },
            )
        }
    }
}

@Composable
private fun DeviceInfoBar(selectedDevice: ConnectedDeviceInfo?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Default.PhoneAndroid,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        if (selectedDevice != null) {
            Text(
                text = selectedDevice.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = selectedDevice.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "暂无已连接设备，请先在主界面连接设备",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FirmwareInfoCard(
    uiState: OtaUiState,
    onReadFwInfo: () -> Unit,
) {
    val firmwareInfo = uiState.firmwareInfo
    val isLoading = uiState.isReadingFirmwareInfo
    val enabled = !uiState.isUpgrading && uiState.selectedDevice != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("固件信息", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (firmwareInfo != null) {
                FirmwareBranch(info = firmwareInfo, isRoot = true)
                firmwareInfo.imgList.forEachIndexed { index, imgInfo ->
                    val isLast = index == firmwareInfo.imgList.lastIndex
                    FirmwareBranch(info = imgInfo, isRoot = false, isLast = isLast)
                }
            } else {
                Text(
                    "点击按钮获取设备上已下载的固件信息",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onReadFwInfo, enabled = enabled && !isLoading, shape = ButtonShape) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("获取信息")
            }
        }
    }
}

@Composable
private fun FirmwareBranch(
    info: FirmwareInfo,
    isRoot: Boolean,
    isLast: Boolean = true,
) {
    val prefix = if (isRoot) "" else if (isLast) "  └─ " else "  ├─ "
    val name = if (isRoot) "BootInfo" else info.name.ifEmpty { "Unknown" }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$prefix$name",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = if (isRoot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(vertical = 2.dp),
        )
        InfoGrid(info = info, indent = !isRoot)
    }
}

@Composable
private fun InfoGrid(info: FirmwareInfo, indent: Boolean) {
    val indentModifier = if (indent) Modifier.padding(start = 16.dp) else Modifier
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val valueStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)

    Column(modifier = indentModifier.fillMaxWidth()) {
        val items = buildList {
            if (info.pattern != 0) {
                add("version" to "v${info.version}")
                add("pattern" to "0x${info.pattern.toString(16).uppercase()}")
            }
            add("binSize" to "${info.binSize} (${formatFileSize(info.binSize.toLong())})")
            add("checksum" to "0x${info.checksum.toString(16).uppercase()}")
            add("loadAddr" to "0x${info.loadAddr.toString(16).uppercase()}")
            add("runAddr" to "0x${info.runAddr.toString(16).uppercase()}")
            add("xqspiXipCmd" to "0x${info.xqspiXipCmd.toString(16).uppercase()}")
            add("xqspiSpeed" to xqspiSpeedLabel(info.xqspiSpeed))
            add("codeCopyMode" to copyModeLabel(info.codeCopyMode))
            add("systemClk" to systemClkLabel(info.systemClk))
            add("checkImage" to "${info.checkImage}")
            add("bootDelay" to "${info.bootDelay}")
            add("isDapBoot" to "${info.isDapBoot}")
        }
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { (label, value) ->
                    Row(modifier = Modifier.weight(1f)) {
                        Text(text = "$label: ", style = labelStyle, maxLines = 1)
                        Text(text = value, style = valueStyle, maxLines = 1)
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun xipCmdLabel(cmd: Int): String = when (cmd) {
    0x03 -> "Normal Read"
    0x0B -> "Fast Read"
    0x3B -> "Dual Out Fast Read"
    0xBB -> "Dual I/O Fast Read"
    0x6B -> "Quad Out Fast Read"
    0xEB -> "Quad I/O Fast Read"
    else -> "Unknown"
}

private fun xqspiSpeedLabel(speed: Int): String = when (speed) {
    0 -> "64MHz"
    1 -> "48MHz"
    2 -> "32MHz"
    3 -> "24MHz"
    4 -> "16MHz"
    else -> "Unknown($speed)"
}

private fun copyModeLabel(mode: Int): String = when (mode) {
    0 -> "XIP"
    1 -> "QSPI"
    else -> "Unknown($mode)"
}

private fun systemClkLabel(clk: Int): String = when (clk) {
    0 -> "64MHz"
    1 -> "48MHz"
    2 -> "32MHz"
    3 -> "24MHz"
    4 -> "16MHz"
    5 -> "32MHz(alt)"
    else -> "Unknown($clk)"
}

@Composable
private fun FirmwareUpgradeCard(
    fileInfo: FirmwareFileInfo,
    upgradeRegion: UpgradeRegion,
    copyAddress: Long,
    onSelectFile: () -> Unit,
    onSelectRegion: (UpgradeRegion) -> Unit,
    onCopyAddressChange: (Long) -> Unit,
    onStartUpgrade: () -> Unit,
    enabled: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("升级固件", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (fileInfo.fileName.isNotEmpty()) {
                Text(
                    text = fileInfo.fileName,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "大小: ${formatFileSize(fileInfo.fileSize)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (fileInfo.parseError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = fileInfo.parseError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            if (fileInfo.imgInfo != null && fileInfo.isValid) {
                Spacer(modifier = Modifier.height(4.dp))
                FwInfoGrid(info = fileInfo.imgInfo)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = upgradeRegion == UpgradeRegion.SINGLE,
                    onClick = { if (enabled) onSelectRegion(UpgradeRegion.SINGLE) },
                    enabled = enabled,
                    modifier = Modifier.size(20.dp),
                )
                Text("单区升级", style = MaterialTheme.typography.labelMedium)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = upgradeRegion == UpgradeRegion.DUAL,
                    onClick = { if (enabled) onSelectRegion(UpgradeRegion.DUAL) },
                    enabled = enabled,
                    modifier = Modifier.size(20.dp),
                )
                Text("双区升级", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { if (enabled) onSelectFile() },
                    enabled = enabled,
                    shape = ButtonShape,
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                    modifier = Modifier.height(28.dp),
                ) {
                    Text(
                        if (fileInfo.fileName.isNotEmpty()) "更换文件" else "选择文件",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (fileInfo.fileName.isNotEmpty() && fileInfo.isValid) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onStartUpgrade,
                        enabled = enabled,
                        shape = ButtonShape,
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                        modifier = Modifier.height(28.dp),
                    ) {
                        Text("升级", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (upgradeRegion == UpgradeRegion.DUAL) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "拷贝地址",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = if (copyAddress == 0L) "" else "0x${copyAddress.toString(16).uppercase()}",
                        onValueChange = { text ->
                            val parsed = text.removePrefix("0x").removePrefix("0X").toLongOrNull(16) ?: 0L
                            onCopyAddressChange(parsed)
                        },
                        placeholder = { Text("0x00000000", style = MaterialTheme.typography.labelSmall) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f).height(48.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
        }
    }
}

@Composable
private fun FwInfoGrid(info: FirmwareInfo) {
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val valueStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)

    Column(modifier = Modifier.fillMaxWidth()) {
        if (info.comments.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(modifier = Modifier.weight(1f)) {
                    Text(text = "comments: ", style = labelStyle, maxLines = 1)
                    Text(text = info.comments, style = valueStyle, maxLines = 1)
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        val items = buildList {
            add("version" to "v${info.version}")
            add("pattern" to "0x${info.pattern.toString(16).uppercase()}")
            add("binSize" to "${info.binSize} (${formatFileSize(info.binSize.toLong())})")
            add("checksum" to "0x${info.checksum.toString(16).uppercase()}")
            add("loadAddr" to "0x${info.loadAddr.toString(16).uppercase()}")
            add("runAddr" to "0x${info.runAddr.toString(16).uppercase()}")
            add("xqspiXipCmd" to "0x${info.xqspiXipCmd.toString(16).uppercase()}")
            add("xqspiSpeed" to xqspiSpeedLabel(info.xqspiSpeed))
            add("codeCopyMode" to copyModeLabel(info.codeCopyMode))
            add("systemClk" to systemClkLabel(info.systemClk))
            add("checkImage" to "${info.checkImage}")
            add("bootDelay" to "${info.bootDelay}")
            add("isDapBoot" to "${info.isDapBoot}")
        }
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { (label, value) ->
                    Row(modifier = Modifier.weight(1f)) {
                        Text(text = "$label: ", style = labelStyle, maxLines = 1)
                        Text(text = value, style = valueStyle, maxLines = 1)
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ResourceUpgradeCard(
    fileInfo: FirmwareFileInfo,
    startAddress: Long,
    storageType: StorageType,
    onSelectFile: () -> Unit,
    onStartAddressChange: (Long) -> Unit,
    onStorageTypeChange: (StorageType) -> Unit,
    onStartUpgrade: () -> Unit,
    enabled: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("升级资源数据", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "资源文件:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = fileInfo.fileName.ifEmpty { "未选择文件" },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "文件大小(Byte):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (fileInfo.fileSize > 0) "${fileInfo.fileSize}" else "-",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = if (startAddress == 0L) "" else "0x${startAddress.toString(16).uppercase()}",
                onValueChange = { text ->
                    val parsed = text.removePrefix("0x").removePrefix("0X").toLongOrNull(16) ?: 0L
                    onStartAddressChange(parsed)
                },
                label = { Text("起始地址(0x)") },
                placeholder = { Text("0x00000000") },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("存储器类型：", style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = storageType == StorageType.INTERNAL,
                        onClick = { if (enabled) onStorageTypeChange(StorageType.INTERNAL) },
                        enabled = enabled,
                    )
                    Text("内部", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = storageType == StorageType.EXTERNAL,
                        onClick = { if (enabled) onStorageTypeChange(StorageType.EXTERNAL) },
                        enabled = enabled,
                    )
                    Text("外部", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { if (enabled) onSelectFile() },
                    enabled = enabled,
                    shape = ButtonShape,
                ) {
                    Text(if (fileInfo.fileName.isNotEmpty()) "更换文件" else "选择文件")
                }
                if (fileInfo.fileName.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onStartUpgrade, enabled = enabled, shape = ButtonShape) {
                        Text("升级")
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugActionCard(
    action: DebugMenuAction,
    debugResult: String?,
    onDebugRead: (suspend (OtaEngine) -> DebugResult) -> Unit,
    onDebugWrite: (suspend (OtaEngine) -> DebugResult) -> Unit,
) {
    var addressText by remember { mutableStateOf("") }
    var lengthText by remember { mutableStateOf("") }
    var dataText by remember { mutableStateOf("") }

    fun parseAddress(): Int {
        val raw = addressText.trim().removePrefix("0x").removePrefix("0X")
        return raw.toLongOrNull(16)?.toInt() ?: 0
    }

    fun parseLength(): Int = lengthText.trim().toIntOrNull() ?: 64

    fun parseHexData(): ByteArray? {
        val raw = dataText.trim()
        if (raw.isEmpty()) return null
        val clean = raw.replace(" ", "").replace("\n", "").replace("\r", "")
        if (clean.length % 2 != 0) return null
        return ByteArray(clean.length / 2) { i ->
            ((Character.digit(clean[i * 2], 16) shl 4) + Character.digit(clean[i * 2 + 1], 16)).toByte()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(action.label, style = MaterialTheme.typography.titleMedium)
            Text(action.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))

            when (action) {
                DebugMenuAction.RAM_READ_WRITE, DebugMenuAction.FLASH_READ_WRITE -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = addressText,
                            onValueChange = { addressText = it },
                            label = { Text("地址 (Hex)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = lengthText,
                            onValueChange = { lengthText = it },
                            label = { Text("长度") },
                            modifier = Modifier.weight(0.5f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = dataText,
                        onValueChange = { dataText = it },
                        label = { Text("写入数据 (Hex)") },
                        placeholder = { Text("AA BB CC DD") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        singleLine = false,
                        maxLines = 3,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                            val addr = parseAddress()
                            val len = parseLength()
                            val readOp: suspend (OtaEngine) -> DebugResult =
                                if (action == DebugMenuAction.RAM_READ_WRITE)
                                    { engine -> engine.readRam(addr, len) }
                                else
                                    { engine -> engine.readFlash(addr, len) }
                            onDebugRead(readOp)
                        }, shape = ButtonShape) { Text("读取") }
                        OutlinedButton(
                            onClick = {
                            val addr = parseAddress()
                            val data = parseHexData() ?: return@OutlinedButton
                            val writeOp: suspend (OtaEngine) -> DebugResult =
                                if (action == DebugMenuAction.RAM_READ_WRITE)
                                    { engine -> engine.writeRam(addr, data) }
                                else
                                    { engine -> engine.writeFlash(addr, data) }
                            onDebugWrite(writeOp)
                        }, shape = ButtonShape) { Text("写入") }
                    }
                }
                DebugMenuAction.REGISTER_READ_WRITE -> {
                    OutlinedTextField(
                        value = addressText,
                        onValueChange = { addressText = it },
                        label = { Text("寄存器地址 (Hex)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = dataText,
                        onValueChange = { dataText = it },
                        label = { Text("写入数据 (Hex)") },
                        placeholder = { Text("AA BB CC DD") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        singleLine = false,
                        maxLines = 2,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                            val addr = parseAddress()
                            onDebugRead { engine -> engine.readRegister(addr) }
                        }, shape = ButtonShape) { Text("读取") }
                        OutlinedButton(
                            onClick = {
                            val addr = parseAddress()
                            val data = parseHexData() ?: return@OutlinedButton
                            onDebugWrite { engine -> engine.writeRegister(addr, data) }
                        }, shape = ButtonShape) { Text("写入") }
                    }
                }
                DebugMenuAction.READ_EFUSE -> {
                    Button(
                        onClick = { onDebugRead { engine -> engine.readEfuse() } },
                        shape = ButtonShape,
                    ) { Text("读取eFuse") }
                }
                DebugMenuAction.NVDS_READ_WRITE -> {
                    OutlinedTextField(
                        value = dataText,
                        onValueChange = { dataText = it },
                        label = { Text("写入数据 (Hex)") },
                        placeholder = { Text("AA BB CC DD") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        singleLine = false,
                        maxLines = 3,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onDebugRead { engine -> engine.readNvds() } },
                            shape = ButtonShape,
                        ) { Text("读取NVDS") }
                        OutlinedButton(
                            onClick = {
                            val data = parseHexData() ?: return@OutlinedButton
                            onDebugWrite { engine -> engine.writeNvds(data) }
                        }, shape = ButtonShape) { Text("写入NVDS") }
                    }
                }
                DebugMenuAction.READ_BOOT_INFO -> {
                    Button(
                        onClick = { onDebugRead { engine -> engine.readBootInfo() } },
                        shape = ButtonShape,
                    ) { Text("读取BootInfo") }
                }
            }

            debugResult?.let { result ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun LogCard(
    logLines: List<String>,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    Card(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("升级日志", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            if (logLines.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("暂无日志", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                    items(logLines) { line ->
                        Text(text = line, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
    }
}