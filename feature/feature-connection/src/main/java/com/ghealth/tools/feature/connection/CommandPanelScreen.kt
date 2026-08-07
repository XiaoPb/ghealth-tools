package com.ghealth.tools.feature.connection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import com.ghealth.tools.core.ui.component.CompactOutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ghealth.tools.ble.protocol.gh3036.CommandGroup
import com.ghealth.tools.ble.protocol.gh3036.CommandMeta
import com.ghealth.tools.ble.protocol.gh3036.CommandParamDef
import com.ghealth.tools.ble.protocol.gh3036.CommandPayloadBuilder
import com.ghealth.tools.ble.protocol.gh3036.Gh3036CommandMeta
import com.ghealth.tools.ble.protocol.gh3036.ParamType
import com.ghealth.tools.core.ui.theme.ButtonShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CommandExecutionState(
    val isExecuting: Boolean = false,
    val result: ByteArray? = null,
    val error: String? = null,
    val commandKey: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CommandExecutionState
        if (isExecuting != other.isExecuting) return false
        if (error != other.error) return false
        if (commandKey != other.commandKey) return false
        if (result != null) {
            if (other.result == null) return false
            if (!result.contentEquals(other.result)) return false
        } else if (other.result != null) return false
        return true
    }

    override fun hashCode(): Int {
        var hash = commandKey.hashCode()
        hash = 31 * hash + (result?.contentHashCode() ?: 0)
        hash = 31 * hash + (error?.hashCode() ?: 0)
        hash = 31 * hash + isExecuting.hashCode()
        return hash
    }
}

data class CommandErrorToast(
    val id: Long,
    val message: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CommandPanelScreen(
    commandExecutionStates: Map<String, CommandExecutionState>,
    onNavigateBack: () -> Unit,
    onExecute: (String, ByteArray) -> Unit,
    showBackButton: Boolean = true,
    chipName: String = "gh3036",
    registerConfigDownloadState: RegisterConfigDownloadState = RegisterConfigDownloadState(),
    onLoadRegisterConfigs: (String) -> Unit = {},
    onSelectRegisterConfigFile: (ConfigFileInfo) -> Unit = {},
    onExecuteRegisterConfigDownload: () -> Unit = {},
    onResetRegisterConfigDownload: () -> Unit = {}
) {
    var expandedKey by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = if (showBackButton) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Close,
                    contentDescription = if (showBackButton) "返回" else "关闭"
                )
            }
            Text("命令面板", style = MaterialTheme.typography.titleMedium)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CommandGroup.entries.forEach { group ->
                val commands = Gh3036CommandMeta.getCommandsByGroup(group)
                if (commands.isNotEmpty()) {
                    SectionHeader(group)
                    commands.forEach { command ->
                        CommandCard(
                            command = command,
                            isExpanded = expandedKey == command.key,
                            executionState = commandExecutionStates[command.key]
                                ?: CommandExecutionState(),
                            onToggle = {
                                expandedKey = if (expandedKey == command.key) null else command.key
                            },
                            onExecute = { params -> onExecute(command.key, params) },
                            chipName = chipName
                        )
                    }
                    if (group == CommandGroup.FACTORY) {
                        val downloadExpanded = expandedKey == "REGISTER_CONFIG_DOWNLOAD"
                        RegisterConfigDownloadCard(
                            chipName = chipName,
                            downloadState = registerConfigDownloadState,
                            isExpanded = downloadExpanded,
                            onToggle = {
                                expandedKey = if (downloadExpanded) null else "REGISTER_CONFIG_DOWNLOAD"
                                if (!downloadExpanded) {
                                    onLoadRegisterConfigs(chipName)
                                }
                            },
                            onLoadConfigs = onLoadRegisterConfigs,
                            onSelectConfig = onSelectRegisterConfigFile,
                            onExecute = onExecuteRegisterConfigDownload,
                            onReset = onResetRegisterConfigDownload
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(group: CommandGroup) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = groupIcon(group),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = group.displayName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CommandCard(
    command: CommandMeta,
    isExpanded: Boolean,
    executionState: CommandExecutionState,
    onToggle: () -> Unit,
    onExecute: (ByteArray) -> Unit,
    chipName: String = "gh3036"
) {
    val paramValues = remember { mutableStateMapOf<String, Any>() }
    val isRegWrite = command.key == "GH3X_RegsWriteCmd"
    val isRegRead = command.key == "GH3X_RegsReadCmd"
    var multiReg by remember { mutableStateOf(false) }

    // Multi-register state for write
    var regPairs by remember { mutableStateOf(listOf(Pair("", ""), Pair("", ""))) }
    // Multi-register state for read
    var readStartAddr by remember { mutableStateOf("") }
    var readCount by remember { mutableStateOf("1") }

    Card(
        onClick = if (!isExpanded) onToggle else ({ }),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpanded)
                MaterialTheme.colorScheme.surfaceContainerHigh
            else
                MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = command.displayName,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = command.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandMore
                        else Icons.Default.ChevronRight,
                        contentDescription = if (isExpanded) "收起" else "展开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expanded content
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    // Single/Multi toggle for register commands
                    if (isRegWrite || isRegRead) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (multiReg) "多寄存器模式" else "单寄存器模式",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Switch(
                                checked = multiReg,
                                onCheckedChange = { multiReg = it }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        if (isRegWrite) {
                            if (multiReg) {
                                MultiRegWriteInput(regPairs) { regPairs = it }
                            } else {
                                SingleRegWriteInput { addr, value ->
                                    paramValues["regs"] = shortArrayOf(
                                        addr.toShort(16),
                                        value.toShort(16)
                                    )
                                }
                            }
                        } else if (isRegRead) {
                            if (multiReg) {
                                MultiRegReadInput(readStartAddr, readCount,
                                    onAddrChange = { readStartAddr = it },
                                    onCountChange = { readCount = it }
                                )
                            } else {
                                val readResult = executionState.result?.let { bytesToHexString(it) }
                                SingleRegReadInput(
                                    readResult = readResult
                                ) { addr ->
                                    paramValues["regAddr"] = addr.toInt(16).toShort().toUShort()
                                    paramValues["readLen"] = 1
                                }
                            }
                        }
                    } else {
                        command.params.chunked(2).forEach { rowParams ->
                            val hasSpecialParam = rowParams.any {
                                it.type == ParamType.FUNC_MODE_BITS || it.options != null
                            }
                            if (hasSpecialParam) {
                                rowParams.forEach { param ->
                                    ParamInput(
                                        param = param,
                                        chipName = chipName,
                                        onValueChange = { value ->
                                            paramValues[param.name] = value
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowParams.forEach { param ->
                                        ParamInput(
                                            param = param,
                                            chipName = chipName,
                                            onValueChange = { value ->
                                                paramValues[param.name] = value
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }

                    // Execute button
                    Button(
                        onClick = {
                            val params = if (isRegWrite && multiReg) {
                                CommandPayloadBuilder.buildMultiRegWriteParams(regPairs)
                            } else if (isRegRead && multiReg) {
                                CommandPayloadBuilder.buildMultiRegReadParams(readStartAddr, readCount)
                            } else {
                                CommandPayloadBuilder.buildCommandParams(command, paramValues)
                            }
                            onExecute(params)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !executionState.isExecuting,
                        shape = ButtonShape
                    ) {
                        if (executionState.isExecuting) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("执行中...")
                        } else {
                            Text("执行命令")
                        }
                    }

                    // Response
                    if (executionState.result != null || executionState.error != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        CommandResponseView(
                            result = executionState.result,
                            error = executionState.error,
                            commandKey = executionState.commandKey
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ParamInput(
    param: CommandParamDef,
    onValueChange: (Any) -> Unit,
    chipName: String = "gh3036",
    modifier: Modifier = Modifier
) {
    var textValue by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val fieldModifier = Modifier.height(40.dp)

    when {
        // Multi-select func mode bits
        param.type == ParamType.FUNC_MODE_BITS -> {
            val selectedBits = remember { mutableStateMapOf<String, Boolean>() }
            val funcBits = remember(chipName) { Gh3036CommandMeta.getFuncModeBits(chipName) }
            Column {
                Text(
                    text = param.label,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    funcBits.forEach { bit ->
                        val selected = selectedBits[bit.name] ?: false
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedBits[bit.name] = !selected
                                val mask = funcBits
                                    .filter { selectedBits[it.name] == true }
                                    .fold(0) { acc, b -> acc or (1 shl b.bit) }
                                onValueChange(mask)
                            },
                            label = { Text(bit.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
                val desc = param.description
                if (desc != null) {
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Dropdown single-select
        param.options != null -> {
            var selectedLabel by remember { mutableStateOf("请选择") }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = modifier
            ) {
                CompactOutlinedTextField(
                    value = selectedLabel,
                    onValueChange = {},
                    readOnly = true,
                    placeholder = { Text(param.label) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .height(40.dp),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    param.options!!.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                selectedLabel = option.label
                                expanded = false
                                onValueChange(option.value)
                            }
                        )
                    }
                }
            }
        }

        // Timestamp
        param.type == ParamType.TIMESTAMP -> {
            val currentTime = System.currentTimeMillis() / 1000
            var tsValue by remember { mutableStateOf(currentTime.toString()) }
            CompactOutlinedTextField(
                value = tsValue,
                onValueChange = {
                    tsValue = it
                    it.toLongOrNull()?.let { ts -> onValueChange(ts.toInt()) }
                },
                placeholder = { Text(param.label) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = modifier.then(fieldModifier),
            )
        }

        // Array input
        param.type == ParamType.U16_ARRAY || param.type == ParamType.U8_ARRAY -> {
            CompactOutlinedTextField(
                value = textValue,
                onValueChange = {
                    textValue = it
                    parseArrayValue(it, param.type)?.let { arr -> onValueChange(arr) }
                },
                placeholder = { Text(param.label) },
                modifier = modifier.then(fieldModifier),
            )
        }

        // Scalar hex/number input
        else -> {
            val isHexInput = param.type in listOf(ParamType.U16, ParamType.U32, ParamType.I16, ParamType.I32)
            CompactOutlinedTextField(
                value = textValue,
                onValueChange = {
                    textValue = it
                    parseScalarValue(it, param.type)?.let { v -> onValueChange(v) }
                },
                placeholder = { Text(param.label) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (isHexInput) KeyboardType.Ascii else KeyboardType.Number
                ),
                modifier = modifier.then(fieldModifier),
            )
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────

private fun formatTimestamp(ts: Long): String = try {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    sdf.format(Date(ts * 1000))
} catch (_: Exception) { "无效时间戳" }

private fun parseScalarValue(value: String, type: ParamType): Any? = try {
    val clean = value.trim()
    when (type) {
        ParamType.U8 -> clean.toInt(16).toByte().toUByte()
        ParamType.U16 -> clean.toInt(16).toShort().toUShort()
        ParamType.U32 -> clean.toLong(16).toInt().toUInt()
        ParamType.I8 -> clean.toByte()
        ParamType.I16 -> clean.toShort()
        ParamType.I32 -> clean.toInt()
        else -> null
    }
} catch (_: Exception) { null }

private fun parseArrayValue(value: String, type: ParamType): Any? = try {
    val parts = value.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    when (type) {
        ParamType.U16_ARRAY -> parts.map { it.toInt(16).toShort() }.toShortArray()
        ParamType.U8_ARRAY -> parts.map { it.toInt(16).toByte() }.toByteArray()
        else -> null
    }
} catch (_: Exception) { null }

// ── Single / Multi Register Inputs ─────────────────────────────────────

@Composable
private fun SingleRegWriteInput(onChange: (addr: String, value: String) -> Unit) {
    var addr by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CompactOutlinedTextField(
            value = addr,
            onValueChange = { addr = it; onChange(addr, value) },
            placeholder = { Text("地址(hex)") },
            modifier = Modifier.weight(1f).height(40.dp),
        )
        CompactOutlinedTextField(
            value = value,
            onValueChange = { value = it; onChange(addr, value) },
            placeholder = { Text("写入值(hex)") },
            modifier = Modifier.weight(1f).height(40.dp),
        )
    }
}

@Composable
private fun MultiRegWriteInput(
    pairs: List<Pair<String, String>>,
    onChange: (List<Pair<String, String>>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        pairs.forEachIndexed { index, (addr, value) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactOutlinedTextField(
                    value = addr,
                    onValueChange = { newAddr ->
                        val newList = pairs.toMutableList()
                        newList[index] = Pair(newAddr, value)
                        onChange(newList)
                    },
                    placeholder = { Text("地址") },
                    modifier = Modifier.weight(1f).height(40.dp),
                )
                CompactOutlinedTextField(
                    value = value,
                    onValueChange = { newVal ->
                        val newList = pairs.toMutableList()
                        newList[index] = Pair(addr, newVal)
                        onChange(newList)
                    },
                    placeholder = { Text("值") },
                    modifier = Modifier.weight(1f).height(40.dp),
                )
                if (pairs.size > 1) {
                    IconButton(onClick = {
                        onChange(pairs.toMutableList().also { it.removeAt(index) })
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "删除")
                    }
                }
            }
        }
        OutlinedButton(onClick = {
            onChange(pairs + Pair("", ""))
        }, modifier = Modifier.fillMaxWidth(), shape = ButtonShape) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text("添加寄存器")
        }
    }
}

@Composable
private fun SingleRegReadInput(
    readResult: String?,
    onChange: (String) -> Unit
) {
    var addr by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CompactOutlinedTextField(
            value = addr,
            onValueChange = { addr = it; onChange(addr) },
            placeholder = { Text("地址(hex)") },
            modifier = Modifier.weight(1f).height(40.dp),
        )
        CompactOutlinedTextField(
            value = readResult ?: "",
            onValueChange = {},
            readOnly = true,
            placeholder = { Text("读取结果") },
            modifier = Modifier.weight(1f).height(40.dp),
        )
    }
}

@Composable
private fun MultiRegReadInput(
    addr: String,
    count: String,
    onAddrChange: (String) -> Unit,
    onCountChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CompactOutlinedTextField(
            value = addr,
            onValueChange = onAddrChange,
            placeholder = { Text("起始地址(hex)") },
            modifier = Modifier.weight(1f).height(40.dp),
        )
        CompactOutlinedTextField(
            value = count,
            onValueChange = onCountChange,
            placeholder = { Text("读取个数") },
            modifier = Modifier.weight(1f).height(40.dp),
        )
    }
}

// ── 寄存器配置下载卡片 ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegisterConfigDownloadCard(
    chipName: String,
    downloadState: RegisterConfigDownloadState,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onLoadConfigs: (String) -> Unit,
    onSelectConfig: (ConfigFileInfo) -> Unit,
    onExecute: () -> Unit,
    onReset: () -> Unit
) {
    var configExpanded by remember { mutableStateOf(false) }
    var selectedLabel by remember(downloadState.selectedConfig) {
        mutableStateOf(downloadState.selectedConfig?.displayPath ?: "请选择配置文件")
    }

    Card(
        onClick = if (!isExpanded) onToggle else ({ }),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpanded)
                MaterialTheme.colorScheme.surfaceContainerHigh
            else
                MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "寄存器配置下载",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "从配置文件加载寄存器并批量写入芯片",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandMore
                        else Icons.Default.ChevronRight,
                        contentDescription = if (isExpanded) "收起" else "展开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    // Config file selection
                    OutlinedButton(
                        onClick = { onLoadConfigs(chipName) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = ButtonShape,
                        enabled = downloadState.status != DownloadStatus.LOADING_CONFIGS
                                && downloadState.status != DownloadStatus.DOWNLOADING
                    ) {
                        if (downloadState.status == DownloadStatus.LOADING_CONFIGS) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("加载配置文件 ($chipName)")
                    }

                    if (downloadState.availableConfigs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        ExposedDropdownMenuBox(
                            expanded = configExpanded,
                            onExpandedChange = {
                                if (downloadState.status != DownloadStatus.DOWNLOADING) {
                                    configExpanded = !configExpanded
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CompactOutlinedTextField(
                                value = selectedLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("选择配置") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = configExpanded) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = configExpanded,
                                onDismissRequest = { configExpanded = false }
                            ) {
                                downloadState.availableConfigs.forEach { info ->
                                    DropdownMenuItem(
                                        text = { Text(info.displayPath) },
                                        onClick = {
                                            selectedLabel = info.displayPath
                                            configExpanded = false
                                            onSelectConfig(info)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Step progress
                    val isDownloading = downloadState.status == DownloadStatus.DOWNLOADING
                    val isCompleted = downloadState.status == DownloadStatus.COMPLETED
                    val isError = downloadState.status == DownloadStatus.ERROR
                    val activeStep = downloadState.activeStep
                    val completedSteps = downloadState.completedSteps

                    if (isDownloading || isCompleted || (isError && completedSteps.isNotEmpty())) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StepIndicator(
                                label = "开始配置",
                                isActive = activeStep == DownloadStep.START_CONFIG,
                                isCompleted = DownloadStep.START_CONFIG in completedSteps,
                                isError = isError && activeStep == DownloadStep.START_CONFIG
                            )
                            Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            StepIndicator(
                                label = "写入寄存器",
                                isActive = activeStep == DownloadStep.WRITE_REGS,
                                isCompleted = DownloadStep.WRITE_REGS in completedSteps,
                                isError = isError && activeStep == DownloadStep.WRITE_REGS
                            )
                            Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            StepIndicator(
                                label = "结束配置",
                                isActive = activeStep == DownloadStep.END_CONFIG,
                                isCompleted = DownloadStep.END_CONFIG in completedSteps,
                                isError = isError && activeStep == DownloadStep.END_CONFIG
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Execute button
                    Button(
                        onClick = onExecute,
                        modifier = Modifier.fillMaxWidth(),
                        shape = ButtonShape,
                        enabled = downloadState.selectedConfig != null
                                && downloadState.status != DownloadStatus.DOWNLOADING
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("下载中...")
                        } else {
                            Text("开始下载")
                        }
                    }

                    // Status messages
                    downloadState.error?.let { error ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (downloadState.status == DownloadStatus.COMPLETED) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "寄存器配置下载完成 (${downloadState.availableConfigs.find { it == downloadState.selectedConfig }?.let { "${it.displayPath}" } ?: ""})",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Reset button
                    if (isCompleted || isError) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                selectedLabel = "请选择配置文件"
                                onReset()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = ButtonShape
                        ) {
                            Text("重置")
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun StepIndicator(
    label: String,
    isActive: Boolean,
    isCompleted: Boolean,
    isError: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            isActive -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
            isError -> Text(
                text = "✗",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleSmall
            )
            isCompleted -> Text(
                text = "✓",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall
            )
            else -> Text(
                text = "○",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleSmall
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = when {
                isActive -> MaterialTheme.colorScheme.primary
                isCompleted -> MaterialTheme.colorScheme.primary
                isError -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

private fun groupIcon(group: CommandGroup): ImageVector = when (group) {
    CommandGroup.DEVICE_CONTROL -> Icons.Default.Settings
    CommandGroup.REGISTER -> Icons.Default.Memory
    CommandGroup.VERSION_STATUS -> Icons.Default.Update
    CommandGroup.TIME -> Icons.Default.Timer
    CommandGroup.FACTORY -> Icons.Default.Verified
    CommandGroup.OTHER -> Icons.Default.Settings
}

private fun bytesToHexString(bytes: ByteArray): String {
    return bytes.joinToString(" ") { "%02X".format(it) }
}
