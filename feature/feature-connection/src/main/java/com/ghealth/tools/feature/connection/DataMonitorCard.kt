package com.ghealth.tools.feature.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ghealth.tools.core.model.DataLogEntry
import com.ghealth.tools.core.model.TestConfig

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

            if (state.lastError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "最近错误: ${state.lastError}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
