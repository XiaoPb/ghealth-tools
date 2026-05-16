package com.ghealth.tools.feature.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
    val error: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CommandExecutionState
        if (result != null) {
            if (other.result == null) return false
            if (!result.contentEquals(other.result)) return false
        } else if (other.result != null) return false
        return true
    }

    override fun hashCode(): Int = result?.contentHashCode() ?: 0
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandDetailScreen(
    commandKey: String,
    executionState: CommandExecutionState,
    onNavigateBack: () -> Unit,
    onExecute: (String, ByteArray) -> Unit
) {
    val command = Gh3036CommandMeta.getCommandByKey(commandKey)
    
    if (command == null) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回"
                    )
                }
                Text("命令不存在")
            }
            Text("未找到命令: $commandKey")
        }
        return
    }

    val paramValues = remember { mutableMapOf<String, Any>() }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }
            Text(
                text = command.displayName,
                style = MaterialTheme.typography.titleMedium
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "命令说明",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = command.description,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "命令键: ${command.key}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (command.params.isNotEmpty()) {
            Text(
                text = "参数设置",
                style = MaterialTheme.typography.titleMedium
            )
            
            command.params.forEach { param ->
                ParamInputField(
                    param = param,
                    onValueChange = { value ->
                        paramValues[param.name] = value
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                try {
                    val params = buildParams(command, paramValues)
                    onExecute(command.key, params)
                } catch (e: Exception) {
                    errorMessage = e.message ?: "参数错误"
                    showErrorDialog = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !executionState.isExecuting
        ) {
            if (executionState.isExecuting) {
                CircularProgressIndicator(
                    modifier = Modifier.width(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("执行中...")
            } else {
                Text("执行命令")
            }
        }

        if (executionState.result != null || executionState.error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            CommandResponseView(
                result = executionState.result,
                error = executionState.error
            )
        }
    }

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("参数错误") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = false }) {
                    Text("确定")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParamInputField(
    param: CommandParamDef,
    onValueChange: (Any) -> Unit
) {
    var textValue by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var selectedOption by remember { mutableStateOf<CommandParamDef.OptionItem?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        when {
            param.options != null && param.type == ParamType.U8 -> {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedOption?.label ?: "请选择",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(param.label) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        param.options!!.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    selectedOption = option
                                    expanded = false
                                    onValueChange(option.value)
                                }
                            )
                        }
                    }
                }
            }
            param.type == ParamType.TIMESTAMP -> {
                val currentTime = System.currentTimeMillis() / 1000
                var timestampValue by remember { mutableStateOf(currentTime.toString()) }
                
                OutlinedTextField(
                    value = timestampValue,
                    onValueChange = { 
                        timestampValue = it
                        it.toLongOrNull()?.let { ts -> onValueChange(ts.toInt()) }
                    },
                    label = { Text(param.label) },
                    supportingText = { 
                        Text(formatTimestamp(timestampValue.toLongOrNull() ?: 0))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            param.type == ParamType.U16_ARRAY || param.type == ParamType.U8_ARRAY -> {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { 
                        textValue = it
                        parseArrayValue(it, param.type)?.let { arr -> onValueChange(arr) }
                    },
                    label = { Text(param.label) },
                    supportingText = { 
                        Text(param.description ?: "十六进制值，空格分隔")
                    },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            else -> {
                val isHexInput = param.type in listOf(ParamType.U16, ParamType.U32, ParamType.I16, ParamType.I32)
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { 
                        textValue = it
                        parseValue(it, param.type)?.let { v -> onValueChange(v) }
                    },
                    label = { Text(param.label) },
                    supportingText = { 
                        Text(param.description ?: if (isHexInput) "十六进制输入" else "")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isHexInput) KeyboardType.Ascii else KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun formatTimestamp(ts: Long): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        sdf.format(Date(ts * 1000))
    } catch (e: Exception) {
        "无效时间戳"
    }
}

private fun parseValue(value: String, type: ParamType): Any? {
    return try {
        val cleanValue = value.trim()
        when (type) {
            ParamType.U8 -> cleanValue.toInt(16).toByte().toUByte()
            ParamType.U16 -> cleanValue.toInt(16).toShort().toUShort()
            ParamType.U32 -> cleanValue.toLong(16).toInt().toUInt()
            ParamType.I8 -> cleanValue.toByte()
            ParamType.I16 -> cleanValue.toShort()
            ParamType.I32 -> cleanValue.toInt()
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

private fun parseArrayValue(value: String, type: ParamType): Any? {
    return try {
        val parts = value.trim().split(Regex("\\s+"))
        when (type) {
            ParamType.U16_ARRAY -> parts.map { it.toInt(16).toShort() }.toShortArray()
            ParamType.U8_ARRAY -> parts.map { it.toInt(16).toByte() }.toByteArray()
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

private fun buildParams(command: CommandMeta, paramValues: Map<String, Any>): ByteArray {
    val bytes = mutableListOf<Byte>()
    
    command.params.forEach { param ->
        val value = paramValues[param.name] ?: param.defaultValue
            ?: throw IllegalArgumentException("参数 ${param.label} 未设置")
        
        when (param.type) {
            ParamType.U8, ParamType.I8 -> {
                bytes.add((value as Number).toByte())
            }
            ParamType.U16, ParamType.I16 -> {
                val v = (value as Number).toShort()
                bytes.add((v.toInt() and 0xFF).toByte())
                bytes.add((v.toInt() shr 8 and 0xFF).toByte())
            }
            ParamType.U32, ParamType.I32, ParamType.TIMESTAMP -> {
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
