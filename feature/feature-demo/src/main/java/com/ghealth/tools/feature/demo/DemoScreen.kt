package com.ghealth.tools.feature.demo

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ghealth.tools.core.model.FunctionMode
import com.ghealth.tools.core.ui.component.GHealthTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoScreen(viewModel: DemoViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    if (state.selectedFunction == null) {
        FunctionListScreen(
            functions = state.availableFunctions,
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
    functions: List<FunctionMode>,
    onSelect: (FunctionMode) -> Unit
) {
    Scaffold(
        topBar = { GHealthTopAppBar(title = "数据演示") }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding)
        ) {
            items(functions) { func ->
                Card(
                    onClick = { onSelect(func) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.ShowChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = func.displayName,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }
    }
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
