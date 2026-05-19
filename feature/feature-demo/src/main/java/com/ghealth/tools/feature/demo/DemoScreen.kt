package com.ghealth.tools.feature.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
private const val SPO2_MIN = 65f
private const val SPO2_MAX = 100f

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
            onBack = viewModel::goBack,
            onAddCompareDevice = viewModel::addManualCompareDevice,
            onStartEditCompareDevice = viewModel::startEditCompareDevice,
            onStopEditCompareDevice = viewModel::stopEditCompareDevice,
            onUpdateCompareSpo2 = viewModel::updateManualCompareSpo2,
            onRemoveCompareDevice = viewModel::removeManualCompareDevice
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
    onBack: () -> Unit,
    onAddCompareDevice: (String) -> Unit,
    onStartEditCompareDevice: (Int) -> Unit,
    onStopEditCompareDevice: () -> Unit,
    onUpdateCompareSpo2: (Int, Float?) -> Unit,
    onRemoveCompareDevice: (Int) -> Unit
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
            if (function == FunctionMode.SPO2) {
                Spo2CompareMenu(
                    devices = state.manualCompareDevices,
                    editingIndex = state.editingCompareDeviceIndex,
                    onAddDevice = onAddCompareDevice,
                    onStartEdit = onStartEditCompareDevice,
                    onStopEdit = onStopEditCompareDevice,
                    onUpdateSpo2 = onUpdateCompareSpo2,
                    onRemoveDevice = onRemoveCompareDevice
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        AlgorithmResultCard(
            masterResult = state.masterAlgoResult,
            slaveResult = state.slaveAlgoResult,
            compareHrResults = state.compareHrResults,
            manualCompareDevices = state.manualCompareDevices
        )

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

            // if (frameLabel.isNotEmpty()) {
            //     Text(
            //         text = frameLabel,
            //         style = MaterialTheme.typography.bodySmall.copy(
            //             fontFamily = FontFamily.Monospace,
            //             fontSize = 10.sp
            //         ),
            //         color = MaterialTheme.colorScheme.onSurfaceVariant
            //     )
            // }

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
private fun AlgorithmResultCard(
    masterResult: AlgorithmResult,
    slaveResult: AlgorithmResult?,
    compareHrResults: Map<Int, Int>,
    manualCompareDevices: List<ManualCompareDevice> = emptyList()
) {
    val isSpo2 = masterResult is AlgorithmResult.SPO2 || slaveResult is AlgorithmResult.SPO2
    val deviceColumns = remember(compareHrResults, slaveResult, manualCompareDevices, isSpo2) {
        buildList {
            add(0 to deviceRoleLabel(0))
            if (slaveResult != null) add(1 to deviceRoleLabel(1))
            if (isSpo2) {
                manualCompareDevices.indices.mapTo(this) { (it + 2) to manualCompareDevices[it].name }
            } else {
                compareHrResults.keys.sorted().mapTo(this) { it to deviceRoleLabel(it) }
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
            when {
                masterResult is AlgorithmResult.HR || slaveResult is AlgorithmResult.HR -> {
                    AlgoGrid(
                        deviceColumns = deviceColumns,
                        rows = listOf(
                            "HR" to { idx: Int ->
                                when (idx) {
                                    0 -> hrCell(masterResult)
                                    1 -> hrCell(slaveResult ?: AlgorithmResult.None)
                                    else -> compareHrResults[idx]?.let { "$it BPM" } ?: "--"
                                }
                            }
                        )
                    )
                }
                masterResult is AlgorithmResult.SPO2 || slaveResult is AlgorithmResult.SPO2 -> {
                    val mr = masterResult as? AlgorithmResult.SPO2
                    val sr = slaveResult as? AlgorithmResult.SPO2
                    AlgoGrid(
                        deviceColumns = deviceColumns,
                        rows = listOf(
                            "SpO2" to { idx: Int ->
                                val manual = manualCompareDevices.getOrNull(idx - 2)
                                if (manual != null) manual.spo2?.let { "${formatStat(it)}%" } ?: "--"
                                else spo2Cell(resultFor(idx, mr, sr))
                            },
                            "R" to { idx: Int ->
                                if (manualCompareDevices.getOrNull(idx - 2) != null) "--"
                                else rCell(resultFor(idx, mr, sr))
                            },
                            "HR" to { idx: Int ->
                                if (manualCompareDevices.getOrNull(idx - 2) != null) "--"
                                else spo2HrCell(resultFor(idx, mr, sr))
                            }
                        )
                    )
                }
                masterResult is AlgorithmResult.HRV || slaveResult is AlgorithmResult.HRV -> {
                    val mr = masterResult as? AlgorithmResult.HRV
                    val sr = slaveResult as? AlgorithmResult.HRV
                    AlgoGrid(
                        deviceColumns = deviceColumns,
                        rows = listOf(
                            "RRI" to { idx: Int -> rriCell(resultFor(idx, mr, sr)) },
                            "Conf" to { idx: Int -> confCell(resultFor(idx, mr, sr)) },
                            "Valid" to { idx: Int -> validCell(resultFor(idx, mr, sr)) }
                        )
                    )
                }
                masterResult is AlgorithmResult.ADT || slaveResult is AlgorithmResult.ADT -> {
                    val mr = masterResult as? AlgorithmResult.ADT
                    val sr = slaveResult as? AlgorithmResult.ADT
                    AlgoGrid(
                        deviceColumns = deviceColumns,
                        rows = listOf(
                            "Wear" to { idx: Int -> wearCell(resultFor(idx, mr, sr)) },
                            "Status" to { idx: Int -> detStatusCell(resultFor(idx, mr, sr)) },
                            "Ctr" to { idx: Int -> ctrCell(resultFor(idx, mr, sr)) }
                        )
                    )
                }
                masterResult is AlgorithmResult.NADT || slaveResult is AlgorithmResult.NADT -> {
                    val mr = masterResult as? AlgorithmResult.NADT
                    val sr = slaveResult as? AlgorithmResult.NADT
                    AlgoGrid(
                        deviceColumns = deviceColumns,
                        rows = listOf(
                            "Wear" to { idx: Int -> nadtWearCell(resultFor(idx, mr, sr)) },
                            "Live" to { idx: Int -> nadtLiveCell(resultFor(idx, mr, sr)) }
                        )
                    )
                }
                else -> {
                    Text("--", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// --- Per-mode cell helpers ---

private fun hrCell(r: AlgorithmResult): String {
    return if (r is AlgorithmResult.HR && r.heartRate > 0) "${r.heartRate} BPM" else "--"
}

private fun spo2Cell(r: AlgorithmResult): String {
    val s = r as? AlgorithmResult.SPO2 ?: return "--"
    if (s.spo2 <= 0) return "--"
    val v = if (s.spo2 > 10000) s.spo2 / 10000.0 else s.spo2.toDouble()
    return "${formatStat(v.toFloat())}%"
}

private fun rCell(r: AlgorithmResult): String {
    val s = r as? AlgorithmResult.SPO2 ?: return "--"
    return if (s.rValue > 0) String.format("%.3f", s.rValue / 10000.0) else "--"
}

private fun spo2HrCell(r: AlgorithmResult): String {
    val s = r as? AlgorithmResult.SPO2 ?: return "--"
    return if (s.hbMean > 0) "${s.hbMean} BPM" else "--"
}

private fun rriCell(r: AlgorithmResult): String {
    val h = r as? AlgorithmResult.HRV ?: return "--"
    val valid = h.rri.filter { it > 0 }
    return if (valid.isNotEmpty()) valid.joinToString(", ") + " ms" else "--"
}

private fun confCell(r: AlgorithmResult): String {
    val h = r as? AlgorithmResult.HRV ?: return "--"
    return if (h.confidence > 0) h.confidence.toString() else "--"
}

private fun validCell(r: AlgorithmResult): String {
    val h = r as? AlgorithmResult.HRV ?: return "--"
    return if (h.validNum > 0) h.validNum.toString() else "--"
}

private fun wearCell(r: AlgorithmResult): String {
    val a = r as? AlgorithmResult.ADT ?: return "--"
    return when (a.wearEvent) {
        1 -> "Wear"
        2 -> "Off"
        else -> a.wearEvent.toString()
    }
}

private fun detStatusCell(r: AlgorithmResult): String {
    val a = r as? AlgorithmResult.ADT ?: return "--"
    return when (a.detStatus) {
        1 -> "Detecting"
        2 -> "Detected"
        else -> a.detStatus.toString()
    }
}

private fun ctrCell(r: AlgorithmResult): String {
    val a = r as? AlgorithmResult.ADT ?: return "--"
    return a.ctr.toString()
}

private fun nadtWearCell(r: AlgorithmResult): String {
    val n = r as? AlgorithmResult.NADT ?: return "--"
    return n.wearOffDetectRes.toString()
}

private fun nadtLiveCell(r: AlgorithmResult): String {
    val n = r as? AlgorithmResult.NADT ?: return "--"
    return n.liveBodyConf.toString()
}

/** Pick the result for a given column index (0=Master, 1=Slave, >=2=compare). */
private fun <T : AlgorithmResult> resultFor(idx: Int, master: T?, slave: T?): AlgorithmResult {
    return when (idx) {
        0 -> master ?: AlgorithmResult.None
        1 -> slave ?: AlgorithmResult.None
        else -> AlgorithmResult.None
    }
}

private fun deviceRoleLabel(index: Int): String = when (index) {
    0 -> "Master"
    1 -> "Slave"
    else -> "Ref_${index - 1}" // compare devices start at UI index 2
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
        fontSize = 14.sp
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
        Spacer(modifier = Modifier.width(60.dp))
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
                modifier = Modifier.width(60.dp)
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

@Composable
private fun Spo2CompareMenu(
    devices: List<ManualCompareDevice>,
    editingIndex: Int?,
    onAddDevice: (String) -> Unit,
    onStartEdit: (Int) -> Unit,
    onStopEdit: () -> Unit,
    onUpdateSpo2: (Int, Float?) -> Unit,
    onRemoveDevice: (Int) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newDeviceName by remember { mutableStateOf("") }

    Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "对比设备菜单")
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("添加对比设备") },
                onClick = {
                    menuExpanded = false
                    showAddDialog = true
                    newDeviceName = ""
                },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
            )
            if (devices.isNotEmpty()) {
                HorizontalDivider()
                devices.forEachIndexed { index, device ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(device.name, modifier = Modifier.weight(1f))
                                if (device.spo2 != null) {
                                    Text(
                                        text = "${formatStat(device.spo2)}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        onClick = {
                            menuExpanded = false
                            onStartEdit(index)
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("添加对比设备") },
            text = {
                OutlinedTextField(
                    value = newDeviceName,
                    onValueChange = { newDeviceName = it },
                    label = { Text("设备名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newDeviceName.isNotBlank()) {
                            onAddDevice(newDeviceName.trim())
                            showAddDialog = false
                        }
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("取消") }
            }
        )
    }

    if (editingIndex != null && editingIndex in devices.indices) {
        val device = devices[editingIndex]
        EditSpo2Sheet(
            deviceName = device.name,
            spo2Value = device.spo2,
            onUpdate = { newValue -> onUpdateSpo2(editingIndex, newValue) },
            onDismiss = onStopEdit,
            onRemove = {
                onRemoveDevice(editingIndex)
                onStopEdit()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSpo2Sheet(
    deviceName: String,
    spo2Value: Float?,
    onUpdate: (Float?) -> Unit,
    onDismiss: () -> Unit,
    onRemove: () -> Unit
) {
    var spo2Text by remember(spo2Value) {
        mutableStateOf(spo2Value?.let { formatSpo2Text(it) } ?: "98")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = deviceName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onRemove) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = spo2Text,
                onValueChange = { spo2Text = it },
                label = { Text("血氧值 (%)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf(+5f, +3f, +1f, -1f, -3f, -5f).forEach { delta ->
                    val label = if (delta > 0) "+${delta.toInt()}" else "${delta.toInt()}"
                    TextButton(
                        onClick = {
                            val current = spo2Text.toFloatOrNull() ?: 98f
                            spo2Text = formatSpo2Text((current + delta).coerceIn(SPO2_MIN, SPO2_MAX))
                        }
                    ) { Text(label) }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val raw = spo2Text.toFloatOrNull()
                    val value = raw?.coerceIn(SPO2_MIN, SPO2_MAX)
                    onUpdate(value)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("更新") }
        }
    }
}

private fun formatSpo2Text(value: Float): String {
    return if (value == value.toLong().toFloat()) {
        value.toLong().toString()
    } else {
        "%.1f".format(value)
    }
}
