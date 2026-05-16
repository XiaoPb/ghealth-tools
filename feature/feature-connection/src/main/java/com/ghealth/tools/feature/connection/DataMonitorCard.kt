package com.ghealth.tools.feature.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ghealth.tools.core.model.DataLogEntry
import com.ghealth.tools.core.model.TestConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DataMonitorState(
    val isMonitoring: Boolean = false,
    val testConfig: TestConfig? = null,
    val logEntries: List<DataLogEntry> = emptyList(),
    val errorCount: Int = 0,
    val lastError: String? = null
)

@Composable
fun DataMonitorCard(
    state: DataMonitorState,
    modifier: Modifier = Modifier
) {
    val config = state.testConfig
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (state.errorCount > 0)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (state.isMonitoring) "数据监听中" else "监听已停止",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (state.errorCount > 0)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
                if (state.errorCount > 0) {
                    Text(
                        text = "错误: ${state.errorCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (config != null) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "测试人员: ${config.testerName}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "场景: ${config.scenario.displayName}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "第${config.testRound}次",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (state.lastError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "最近错误: ${state.lastError}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (state.logEntries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "数据日志 (${state.logEntries.size}条)",
                    style = MaterialTheme.typography.labelMedium
                )
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(state.logEntries.reversed()) { entry ->
                        DataLogItem(entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun DataLogItem(
    entry: DataLogEntry,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = timeFormat.format(Date(entry.timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = entry.key,
            style = MaterialTheme.typography.labelSmall,
            color = if (entry.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            fontFamily = FontFamily.Monospace
        )
        if (entry.param.isNotEmpty()) {
            SelectionContainer {
                Text(
                    text = "[${entry.param.size}B]",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (entry.isError && entry.errorMessage != null) {
            Text(
                text = entry.errorMessage,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
