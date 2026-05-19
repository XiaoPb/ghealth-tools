package com.ghealth.tools.feature.demo

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ghealth.tools.core.model.DeviceType
import com.ghealth.tools.core.model.FunctionMode
import com.ghealth.tools.core.ui.component.EmptyStateView
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.Fill
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoScreen(viewModel: DemoViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    if (state.selectedFunction == null) {
        FunctionListScreen(
            functionDataMap = state.functionDataMap,
            isRecording = state.isRecording,
            testerName = state.testerName,
            scenario = state.scenario,
            testRound = state.testRound,
            onSelect = viewModel::selectFunction
        )
    } else {
        FunctionDetailScreen(
            state = state,
            onSelectWaveform1Column = viewModel::selectWaveform1Column,
            onSelectWaveform2Column = viewModel::selectWaveform2Column,
            onBack = viewModel::goBack
        )
    }
}

@Composable
private fun FunctionListScreen(
    functionDataMap: Map<FunctionMode, FunctionData>,
    isRecording: Boolean = false,
    testerName: String = "",
    scenario: String = "",
    testRound: Int = 0,
    onSelect: (FunctionMode) -> Unit
) {
    if (functionDataMap.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.ShowChart,
            title = "等待数据",
            subtitle = "连接设备并开始采集后，功能数据将显示在此处"
        )
        if (isRecording && testerName.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$testerName | $scenario | 第${testRound}次",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isRecording && testerName.isNotBlank()) {
                item(key = "tester_info") {
                    Text(
                        text = "$testerName | $scenario | 第${testRound}次",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                    )
                }
            }
            items(functionDataMap.values.toList(), key = { it.function.hashCode() }) { data ->
                FunctionRow(
                    data = data,
                    onClick = { onSelect(data.function) }
                )
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
                    text = data.algorithmResult.display,
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
    state: DemoUiState,
    onSelectWaveform1Column: (String) -> Unit,
    onSelectWaveform2Column: (String) -> Unit,
    onBack: () -> Unit
) {
    val function = state.selectedFunction ?: return
    val availableColumns = remember(state.chipType) {
        DemoViewModel.availableColumns(state.chipType)
    }

    var showColumnDialog1 by remember { mutableStateOf(false) }
    var showColumnDialog2 by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = function.displayName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        val algoResult = state.functionDataMap[function]?.algorithmResult ?: AlgorithmResult.None
        AlgorithmResultCard(result = algoResult, compareHrResults = state.compareHrResults)

        Spacer(modifier = Modifier.height(12.dp))

        WaveformPanel(
            title = "Waveform 1",
            columnName = state.waveform1Column,
            data = state.waveform1Data,
            frameIds = state.frameIds,
            stats = state.waveform1Stats,
            availableColumns = availableColumns,
            onColumnSelect = onSelectWaveform1Column,
            showDialog = showColumnDialog1,
            onShowDialogChange = { showColumnDialog1 = it }
        )

        Spacer(modifier = Modifier.height(12.dp))

        WaveformPanel(
            title = "Waveform 2",
            columnName = state.waveform2Column,
            data = state.waveform2Data,
            frameIds = state.frameIds,
            stats = state.waveform2Stats,
            availableColumns = availableColumns,
            onColumnSelect = onSelectWaveform2Column,
            showDialog = showColumnDialog2,
            onShowDialogChange = { showColumnDialog2 = it }
        )
    }

    if (showColumnDialog1) {
        ColumnSelectDialog(
            currentColumn = state.waveform1Column,
            columns = availableColumns,
            onSelect = {
                onSelectWaveform1Column(it)
                showColumnDialog1 = false
            },
            onDismiss = { showColumnDialog1 = false }
        )
    }

    if (showColumnDialog2) {
        ColumnSelectDialog(
            currentColumn = state.waveform2Column,
            columns = availableColumns,
            onSelect = {
                onSelectWaveform2Column(it)
                showColumnDialog2 = false
            },
            onDismiss = { showColumnDialog2 = false }
        )
    }
}

@Composable
private fun WaveformPanel(
    title: String,
    columnName: String,
    data: List<Float>,
    frameIds: List<Float>,
    stats: WaveformStats?,
    availableColumns: List<String>,
    onColumnSelect: (String) -> Unit,
    showDialog: Boolean,
    onShowDialogChange: (Boolean) -> Unit
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val lineColor = MaterialTheme.colorScheme.primary.toArgb()
    val yAxisFormatter = remember {
        CartesianValueFormatter { _, value, _ ->
            val v = value.toDouble()
            val absV = kotlin.math.abs(v)
            if (absV == 0.0) {
                "0"
            } else {
                val plainStr = String.format("%.10f", absV)
                    .trimEnd('0')
                    .trimEnd('.')
                if (plainStr.length > 4) {
                    DecimalFormat("0.##E0").format(v)
                } else if (v < 0) {
                    "-$plainStr"
                } else {
                    plainStr
                }
            }
        }
    }

    val frameLabel = if (frameIds.size >= 2) {
        "Frame: ${frameIds.first().toLong()} – ${frameIds.last().toLong()} (${frameIds.size}pts)"
    } else ""

    LaunchedEffect(Unit) {
        snapshotFlow { data }
            .collect { d ->
                if (d.size >= 2) {
                    modelProducer.runTransaction {
                        lineSeries { series(y = d) }
                    }
                }
            }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = columnName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = { onShowDialogChange(true) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.UnfoldMore,
                        contentDescription = "切换列",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (stats != null) {
                Text(
                    text = buildString {
                        append("max:${formatStat(stats.max)} ")
                        append("min:${formatStat(stats.min)} ")
                        append("avg:${formatStat(stats.avg)} ")
                        append("diff:${formatStat(stats.diff)}")
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (frameLabel.isNotEmpty()) {
                Text(
                    text = frameLabel,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(
                        LineCartesianLayer.LineProvider.series(
                            listOf(object : LineCartesianLayer.Line(
                                LineCartesianLayer.LineFill.single(Fill(lineColor))
                            ) {})
                        )
                    ),
                    startAxis = VerticalAxis.rememberStart(valueFormatter = yAxisFormatter),
                    bottomAxis = HorizontalAxis.rememberBottom(),
                    getXStep = { 1.0 },
                ),
                modelProducer = modelProducer,
                animationSpec = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )
        }
    }
}

private fun formatStat(value: Float): String {
    return if (value == value.toLong().toFloat()) {
        value.toLong().toString()
    } else {
        "%.1f".format(value)
    }
}

@Composable
private fun AlgorithmResultCard(result: AlgorithmResult, compareHrResults: Map<Int, Int>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Algorithm Results",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            when (result) {
                is AlgorithmResult.None -> {
                    Text("--", style = MaterialTheme.typography.bodyMedium)
                }
                is AlgorithmResult.HR -> {
                    ResultRow("Heart Rate", "${result.heartRate} BPM")
                    ResultRow("Valid Score", result.validScore.toString())
                    ResultRow("SNR", result.snr.toString())
                    ResultRow("ACC Info", result.accInfo.toString())
                }
                is AlgorithmResult.SPO2 -> {
                    ResultRow("SpO2", "${result.spo2}%")
                    ResultRow("R Value", String.format("%.3f", result.rValue / 1000.0))
                    ResultRow("Confidence", result.confiCoeff.toString())
                    ResultRow("Valid Level", result.validLevel.toString())
                    ResultRow("Hb Mean", result.hbMean.toString())
                }
                is AlgorithmResult.HRV -> {
                    val validRris = result.rri.filter { it > 0 }
                    if (validRris.isNotEmpty()) {
                        ResultRow("RRI", validRris.joinToString(", ") + " ms")
                    }
                    ResultRow("Confidence", result.confidence.toString())
                    ResultRow("Valid Count", result.validNum.toString())
                }
                is AlgorithmResult.ADT -> {
                    val wearLabel = when (result.wearEvent) {
                        1 -> "Wear"
                        2 -> "Off"
                        else -> result.wearEvent.toString()
                    }
                    ResultRow("Wear Event", wearLabel)
                    val statusLabel = when (result.detStatus) {
                        1 -> "Detecting"
                        2 -> "Detected"
                        else -> result.detStatus.toString()
                    }
                    ResultRow("Det Status", statusLabel)
                    ResultRow("Counter", result.ctr.toString())
                }
                is AlgorithmResult.NADT -> {
                    ResultRow("Wear-Off Detect", result.wearOffDetectRes.toString())
                    ResultRow("Live Body Conf", result.liveBodyConf.toString())
                }
            }

            if (compareHrResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Compare HR",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                compareHrResults.entries.sortedBy { it.key }.forEach { (index, hr) ->
                    Text(
                        text = "Device $index: $hr BPM",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ColumnSelectDialog(
    currentColumn: String,
    columns: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择列") },
        text = {
            LazyColumn {
                items(columns, key = { it }) { column ->
                    val isSelected = column == currentColumn
                    Card(
                        onClick = { onSelect(column) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Text(
                            text = column,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}
