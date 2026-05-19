package com.ghealth.tools.feature.connection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.ghealth.tools.ble.protocol.gh3036.Gh3036CommandMeta
import com.ghealth.tools.ble.protocol.gh3036.ParamType
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
        if (commandKey != other.commandKey) return false
        if (result != null) {
            if (other.result == null) return false
            if (!result.contentEquals(other.result)) return false
        } else if (other.result != null) return false
        return true
    }

    override fun hashCode(): Int = 31 * commandKey.hashCode() + (result?.contentHashCode() ?: 0)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CommandPanelScreen(
    commandExecutionStates: Map<String, CommandExecutionState>,
    onNavigateBack: () -> Unit,
    onExecute: (String, ByteArray) -> Unit
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                            onExecute = { params -> onExecute(command.key, params) }
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
    onExecute: (ByteArray) -> Unit
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
                                SingleRegReadInput { addr ->
                                    paramValues["regAddr"] = addr.toInt(16).toShort().toUShort()
                                    paramValues["readLen"] = 1
                                }
                            }
                        }
                    } else {
                        command.params.forEach { param ->
                            ParamInput(
                                param = param,
                                onValueChange = { value ->
                                    paramValues[param.name] = value
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // Execute button
                    Button(
                        onClick = {
                            val params = if (isRegWrite && multiReg) {
                                buildMultiRegWriteParams(regPairs)
                            } else if (isRegRead && multiReg) {
                                buildMultiRegReadParams(readStartAddr, readCount)
                            } else {
                                buildCommandParams(command, paramValues)
                            }
                            onExecute(params)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !executionState.isExecuting
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
    onValueChange: (Any) -> Unit
) {
    var textValue by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    when {
        // Multi-select func mode bits
        param.type == ParamType.FUNC_MODE_BITS -> {
            val selectedBits = remember { mutableStateMapOf<String, Boolean>() }
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
                    Gh3036CommandMeta.FUNC_MODE_BITS_GH3036.forEach { bit ->
                        val selected = selectedBits[bit.name] ?: false
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedBits[bit.name] = !selected
                                val mask = Gh3036CommandMeta.FUNC_MODE_BITS_GH3036
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
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(param.label) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
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
            OutlinedTextField(
                value = tsValue,
                onValueChange = {
                    tsValue = it
                    it.toLongOrNull()?.let { ts -> onValueChange(ts.toInt()) }
                },
                label = { Text(param.label) },
                supportingText = {
                    Text(formatTimestamp(tsValue.toLongOrNull() ?: 0))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Array input
        param.type == ParamType.U16_ARRAY || param.type == ParamType.U8_ARRAY -> {
            val desc = param.description
            OutlinedTextField(
                value = textValue,
                onValueChange = {
                    textValue = it
                    parseArrayValue(it, param.type)?.let { arr -> onValueChange(arr) }
                },
                label = { Text(param.label) },
                supportingText = { Text(desc ?: "十六进制值，空格分隔") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Scalar hex/number input
        else -> {
            val isHexInput = param.type in listOf(ParamType.U16, ParamType.U32, ParamType.I16, ParamType.I32)
            val desc = param.description
            OutlinedTextField(
                value = textValue,
                onValueChange = {
                    textValue = it
                    parseScalarValue(it, param.type)?.let { v -> onValueChange(v) }
                },
                label = { Text(param.label) },
                supportingText = { Text(desc ?: if (isHexInput) "十六进制输入" else "") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (isHexInput) KeyboardType.Ascii else KeyboardType.Number
                ),
                modifier = Modifier.fillMaxWidth()
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

private fun buildCommandParams(command: CommandMeta, paramValues: Map<String, Any>): ByteArray {
    val bytes = mutableListOf<Byte>()
    command.params.forEach { param ->
        val value = paramValues[param.name] ?: param.defaultValue
            ?: throw IllegalArgumentException("参数 ${param.label} 未设置")
        when (param.type) {
            ParamType.U8, ParamType.I8 -> bytes.add((value as Number).toByte())
            ParamType.U16, ParamType.I16 -> {
                val v = (value as Number).toShort()
                bytes.add((v.toInt() and 0xFF).toByte())
                bytes.add((v.toInt() shr 8 and 0xFF).toByte())
            }
            ParamType.U32, ParamType.I32, ParamType.TIMESTAMP, ParamType.FUNC_MODE_BITS -> {
                val v = (value as Number).toInt()
                bytes.add((v and 0xFF).toByte())
                bytes.add((v shr 8 and 0xFF).toByte())
                bytes.add((v shr 16 and 0xFF).toByte())
                bytes.add((v shr 24 and 0xFF).toByte())
            }
            ParamType.U16_ARRAY -> {
                val arr = value as ShortArray
                arr.forEach { v ->
                    bytes.add((v.toInt() and 0xFF).toByte())
                    bytes.add((v.toInt() shr 8 and 0xFF).toByte())
                }
            }
            ParamType.U8_ARRAY -> {
                val arr = value as ByteArray
                bytes.addAll(arr.toList())
            }
        }
    }
    return bytes.toByteArray()
}

// ── Single / Multi Register Inputs ─────────────────────────────────────

@Composable
private fun SingleRegWriteInput(onChange: (addr: String, value: String) -> Unit) {
    var addr by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = addr,
            onValueChange = { addr = it; onChange(addr, value) },
            label = { Text("寄存器地址 (十六进制)") },
            placeholder = { Text("如: 10") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = value,
            onValueChange = { value = it; onChange(addr, value) },
            label = { Text("写入值 (十六进制)") },
            placeholder = { Text("如: 1234") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
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
                OutlinedTextField(
                    value = addr,
                    onValueChange = { newAddr ->
                        val newList = pairs.toMutableList()
                        newList[index] = Pair(newAddr, value)
                        onChange(newList)
                    },
                    label = { Text("地址") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { newVal ->
                        val newList = pairs.toMutableList()
                        newList[index] = Pair(addr, newVal)
                        onChange(newList)
                    },
                    label = { Text("值") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
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
        }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text("添加寄存器")
        }
    }
}

@Composable
private fun SingleRegReadInput(onChange: (String) -> Unit) {
    var addr by remember { mutableStateOf("") }

    OutlinedTextField(
        value = addr,
        onValueChange = { addr = it; onChange(addr) },
        label = { Text("寄存器地址 (十六进制)") },
        placeholder = { Text("如: 10") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun MultiRegReadInput(
    addr: String,
    count: String,
    onAddrChange: (String) -> Unit,
    onCountChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = addr,
            onValueChange = onAddrChange,
            label = { Text("起始地址 (十六进制)") },
            placeholder = { Text("如: 10") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = count,
            onValueChange = onCountChange,
            label = { Text("读取个数") },
            placeholder = { Text("1-200") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

private fun buildMultiRegWriteParams(pairs: List<Pair<String, String>>): ByteArray {
    val shorts = mutableListOf<Short>()
    pairs.forEach { (addr, value) ->
        shorts.add(addr.trim().toInt(16).toShort())
        shorts.add(value.trim().toInt(16).toShort())
    }
    return buildCommandParams(
        Gh3036CommandMeta.getCommandByKey("GH3X_RegsWriteCmd")!!,
        mapOf("regs" to shorts.toShortArray())
    )
}

private fun buildMultiRegReadParams(addr: String, count: String): ByteArray {
    return buildCommandParams(
        Gh3036CommandMeta.getCommandByKey("GH3X_RegsReadCmd")!!,
        mapOf(
            "regAddr" to addr.trim().toInt(16).toShort().toUShort(),
            "readLen" to count.trim().toInt()
        )
    )
}

private fun groupIcon(group: CommandGroup): ImageVector = when (group) {
    CommandGroup.DEVICE_CONTROL -> Icons.Default.Settings
    CommandGroup.REGISTER -> Icons.Default.Memory
    CommandGroup.VERSION_STATUS -> Icons.Default.Update
    CommandGroup.TIME -> Icons.Default.Timer
    CommandGroup.FACTORY -> Icons.Default.Verified
    CommandGroup.OTHER -> Icons.Default.Settings
}
