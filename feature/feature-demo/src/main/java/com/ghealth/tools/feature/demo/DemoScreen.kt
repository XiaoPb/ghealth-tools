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
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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

private const val MAX_DISPLAY_POINTS = 500

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

    // Track Y-axis offset for manual range: data shifted by yOffset, formatter adds it back
    var yOffset by remember { mutableStateOf(0f) }

    val yAxisFormatter = remember {
        CartesianValueFormatter { _, value, _ ->
            formatYAxisMixed(value.toDouble() + yOffset.toDouble())
        }
    }

    val frameLabel = if (frameIds.size >= 2) {
        "Frame: ${frameIds.first().toLong()} – ${frameIds.last().toLong()} (${frameIds.size}pts)"
    } else ""

    val dataRef = rememberUpdatedState(data)

    LaunchedEffect(Unit) {
        while (isActive) {
            val d = dataRef.value
            if (d.isNotEmpty()) {
                val windowed = if (d.size > MAX_DISPLAY_POINTS) d.takeLast(MAX_DISPLAY_POINTS) else d
                // Manual Y-axis: shift data to start from 0, label ticks with real values
                val yMin = windowed.min()
                val yMax = windowed.max()
                val range = yMax - yMin
                val pad = if (range > 0) range * 0.1f else 10f
                val yMinPad = yMin - pad
                yOffset = yMinPad
                val shifted = windowed.map { it - yMinPad }
                modelProducer.runTransaction {
                    lineSeries { series(y = shifted) }
                }
            }
            delay(500L)
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
                StatsGrid(stats)
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

            val lineLayer = rememberLineCartesianLayer(
                LineCartesianLayer.LineProvider.series(
                    listOf(LineCartesianLayer.Line(
                        fill = LineCartesianLayer.LineFill.single(Fill(lineColor))
                    ))
                )
            )

            CartesianChartHost(
                chart = rememberCartesianChart(
                    lineLayer,
                    startAxis = VerticalAxis.rememberStart(valueFormatter = yAxisFormatter),
                    bottomAxis = HorizontalAxis.rememberBottom(),
                    getXStep = { 100.0 },
                ),
                modelProducer = modelProducer,
                animationSpec = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
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
private fun StatsGrid(stats: WaveformStats) {
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp
    )
    val valueStyle = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp
    )
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("max", "min", "avg", "diff").forEach { label ->
                Text(
                    text = label,
                    style = labelStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf(stats.max, stats.min, stats.avg, stats.diff).forEach { value ->
                Text(
                    text = formatStat(value),
                    style = valueStyle,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Mixed-mode Y-axis label formatter:
 * - Scientific notation (e.g. 1.2E4) for |value| >= 10000 or |value| < 0.01 (non-zero)
 * - Standard decimal otherwise
 */
private fun formatYAxisMixed(value: Double): String {
    val absV = kotlin.math.abs(value)
    return when {
        absV == 0.0 -> "0"
        absV >= 10000.0 || (absV > 0.0 && absV < 0.01) ->
            DecimalFormat("0.##E0").format(value)
        else -> {
            val formatted = String.format("%.6f", absV)
                .trimEnd('0')
                .trimEnd('.')
            if (value < 0) "-$formatted" else formatted
        }
    }
}

@Composable
private fun AlgorithmResultCard(result: AlgorithmResult, compareHrResults: Map<Int, Int>) {
    val deviceColumns = remember(compareHrResults) {
        compareHrResults.keys.sorted().map { it to deviceRoleLabel(it) }
    }

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
                    AlgoGrid(
                        deviceColumns = deviceColumns,
                        rows = listOf(
                            "HR" to { idx: Int ->
                                val hr = compareHrResults[idx]
                                if (hr != null) "$hr BPM" else "--"
                            }
                        )
                    )
                }
                is AlgorithmResult.SPO2 -> {
                    val rDisplay = if (result.rValue > 0)
                        String.format("%.3f", result.rValue / 1000.0) else "--"
                    AlgoGrid(
                        deviceColumns = deviceColumns,
                        rows = listOf(
                            "SpO2" to { idx: Int ->
                                if (idx == 0) "${result.spo2}%" else "--"
                            },
                            "R" to { idx: Int ->
                                if (idx == 0) rDisplay else "--"
                            },
                            "HR" to { idx: Int ->
                                val hr = compareHrResults[idx]
                                if (hr != null) "$hr BPM" else "--"
                            }
                        )
                    )
                }
                is AlgorithmResult.HRV -> {
                    val validRris = result.rri.filter { it > 0 }
                    AlgoGrid(
                        deviceColumns = deviceColumns,
                        rows = listOf(
                            "RRI" to { idx: Int ->
                                if (idx == 0 && validRris.isNotEmpty())
                                    validRris.joinToString(", ") + " ms" else "--"
                            },
                            "Conf" to { idx: Int ->
                                if (idx == 0) result.confidence.toString() else "--"
                            },
                            "Valid" to { idx: Int ->
                                if (idx == 0) result.validNum.toString() else "--"
                            }
                        )
                    )
                }
                is AlgorithmResult.ADT -> {
                    val wearLabel = when (result.wearEvent) {
                        1 -> "Wear"
                        2 -> "Off"
                        else -> result.wearEvent.toString()
                    }
                    val statusLabel = when (result.detStatus) {
                        1 -> "Detecting"
                        2 -> "Detected"
                        else -> result.detStatus.toString()
                    }
                    AlgoGrid(
                        deviceColumns = deviceColumns,
                        rows = listOf(
                            "Wear" to { idx: Int ->
                                if (idx == 0) wearLabel else "--"
                            },
                            "Status" to { idx: Int ->
                                if (idx == 0) statusLabel else "--"
                            },
                            "Ctr" to { idx: Int ->
                                if (idx == 0) result.ctr.toString() else "--"
                            }
                        )
                    )
                }
                is AlgorithmResult.NADT -> {
                    AlgoGrid(
                        deviceColumns = deviceColumns,
                        rows = listOf(
                            "WearOff" to { idx: Int ->
                                if (idx == 0) result.wearOffDetectRes.toString() else "--"
                            },
                            "LiveBody" to { idx: Int ->
                                if (idx == 0) result.liveBodyConf.toString() else "--"
                            }
                        )
                    )
                }
            }
        }
    }
}

private fun deviceRoleLabel(index: Int): String = when (index) {
    0 -> "Master"
    1 -> "Slave"
    else -> "Cmp$index"
}

@Composable
private fun AlgoGrid(
    deviceColumns: List<Pair<Int, String>>,
    rows: List<Pair<String, (Int) -> String>>
) {
    val headerStyle = MaterialTheme.typography.labelSmall.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp
    )
    val cellStyle = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp
    )
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp
    )

    // Header row: empty col + device role labels
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(40.dp))
        deviceColumns.forEach { (_, label) ->
            Text(
                text = label,
                style = headerStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f)
            )
        }
    }

    Spacer(modifier = Modifier.height(2.dp))

    // Data rows
    rows.forEach { (rowLabel, values) ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = rowLabel,
                style = labelStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(40.dp)
            )
            deviceColumns.forEach { (idx, _) ->
                Text(
                    text = values(idx),
                    style = cellStyle,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
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
