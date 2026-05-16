package com.ghealth.tools.feature.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CommandResponseView(
    result: ByteArray?,
    error: String?,
    modifier: Modifier = Modifier
) {
    var showAsHex by remember { mutableStateOf(true) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (error != null) 
                MaterialTheme.colorScheme.errorContainer 
            else 
                MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (error != null) "执行失败" else "执行结果",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (error != null) 
                        MaterialTheme.colorScheme.onErrorContainer 
                    else 
                        MaterialTheme.colorScheme.onPrimaryContainer
                )
                
                if (result != null) {
                    Row(
                        modifier = Modifier.toggleable(
                            value = showAsHex,
                            onValueChange = { showAsHex = it }
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "十六进制",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Switch(
                            checked = showAsHex,
                            onCheckedChange = { showAsHex = it }
                        )
                    }
                }
            }

            if (error != null) {
                SelectionContainer {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            } else if (result != null) {
                ResponseDataView(
                    data = result,
                    showAsHex = showAsHex,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ResponseDataView(
    data: ByteArray,
    showAsHex: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "数据长度: ${data.size} 字节",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )

        SelectionContainer {
            Text(
                text = formatData(data, showAsHex),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (data.size >= 2) {
            Text(
                text = "解析为 U16 数组:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            val u16Array = parseU16Array(data)
            SelectionContainer {
                Text(
                    text = if (showAsHex) {
                        u16Array.joinToString(", ") { "0x${it.toString(16).uppercase().padStart(4, '0')}" }
                    } else {
                        u16Array.joinToString(", ")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

private fun formatData(data: ByteArray, asHex: Boolean): String {
    return if (asHex) {
        data.joinToString(" ") { 
            (it.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0')
        }
    } else {
        data.joinToString(" ") { (it.toInt() and 0xFF).toString() }
    }
}

private fun parseU16Array(data: ByteArray): IntArray {
    val size = data.size / 2
    return IntArray(size) { i ->
        val low = (data[i * 2].toInt() and 0xFF)
        val high = (data[i * 2 + 1].toInt() and 0xFF)
        (high shl 8) or low
    }
}
