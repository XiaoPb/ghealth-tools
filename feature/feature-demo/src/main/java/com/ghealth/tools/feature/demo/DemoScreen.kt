package com.ghealth.tools.feature.demo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ghealth.tools.core.model.FunctionMode
import com.ghealth.tools.core.ui.component.EmptyStateView
import com.ghealth.tools.core.ui.component.GHealthTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoScreen(viewModel: DemoViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    if (state.selectedFunction == null) {
        FunctionListScreen(
            functionDataMap = state.functionDataMap,
            onSelect = viewModel::selectFunction
        )
    } else {
        FunctionDetailScreen(
            function = state.selectedFunction!!,
            waveformData = state.waveformData,
            isRecording = state.isRecording,
            onToggleRecording = viewModel::toggleRecording,
            onBack = viewModel::goBack
        )
    }
}

@Composable
private fun FunctionListScreen(
    functionDataMap: Map<FunctionMode, FunctionData>,
    onSelect: (FunctionMode) -> Unit
) {
    Scaffold(
        topBar = { GHealthTopAppBar(title = "数据演示") }
    ) { padding ->
        if (functionDataMap.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.ShowChart,
                title = "等待数据",
                subtitle = "连接设备并开始采集后，功能数据将显示在此处"
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(functionDataMap.values.toList(), key = { it.function }) { data ->
                    FunctionRow(
                        data = data,
                        onClick = { onSelect(data.function) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FunctionRow(data: FunctionData, onClick: () -> Unit) {
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
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = data.function.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = data.function.displayName,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = data.algorithmResult,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${data.frameCount} 帧",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun FunctionMode.icon(): ImageVector = when (this) {
    FunctionMode.HR -> Icons.Default.Favorite
    FunctionMode.HRV -> Icons.Default.MonitorHeart
    FunctionMode.SPO2 -> Icons.Default.WaterDrop
    FunctionMode.ECG -> Icons.Default.MonitorHeart
    FunctionMode.ADT -> Icons.Default.Sensors
    FunctionMode.GSR -> Icons.Default.Thermostat
    FunctionMode.BIA -> Icons.Default.Speed
    else -> Icons.Default.ShowChart
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FunctionDetailScreen(
    function: FunctionMode,
    waveformData: List<Float>,
    isRecording: Boolean,
    onToggleRecording: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            GHealthTopAppBar(
                title = function.displayName,
                actions = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            WaveformChart(
                data = waveformData,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            WaveformChart(
                data = waveformData.map { it * 0.5f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                lineColor = MaterialTheme.colorScheme.tertiary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                FilledTonalButton(onClick = onToggleRecording) {
                    Icon(
                        if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isRecording) "停止录制" else "开始录制")
                }
            }
        }
    }
}

@Composable
private fun WaveformChart(
    data: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary
) {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Canvas(modifier = modifier) {
        drawRect(color = surfaceVariant)

        if (data.size < 2) return@Canvas

        val min = data.min()
        val max = data.max()
        val range = (max - min).coerceAtLeast(1f)
        val stepX = size.width / (data.size - 1).coerceAtLeast(1)

        val path = Path()
        data.forEachIndexed { index, value ->
            val x = index * stepX
            val y = size.height - ((value - min) / range) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(path, color = lineColor, style = Stroke(width = 2f))
    }
}
