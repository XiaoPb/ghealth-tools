package com.ghealth.tools.feature.factory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ghealth.tools.feature.factory.engine.LogLevel
import com.ghealth.tools.feature.factory.model.TestType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FactoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: FactoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("产测") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Config selection ──
            ConfigSection(state, viewModel::selectProject)

            // ── Start button ──
            Button(
                onClick = { viewModel.startTest() },
                enabled = !state.isTestRunning && state.selectedProject != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Science, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (state.isTestRunning) "测试中..." else "启动测试")
            }

            if (!state.isDeviceConnected && state.selectedProject != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "未连接主设备，请先在主界面连接设备",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // ── Progress section ──
            if (state.isTestRunning || state.testCompleted) {
                ProgressSection(state)
            }

            // ── Results section ──
            if (state.results.isNotEmpty()) {
                ResultsSection(state)
            }

            // ── Log section ──
            if (state.logMessages.isNotEmpty()) {
                LogSection(state)
            }

            // ── Export info ──
            state.exportedFilePath?.let { path ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Done, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("CSV 已导出", style = MaterialTheme.typography.labelMedium)
                            Text(
                                path,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Dialogs ──
        if (state.showEnvironmentSwitchDialog) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("切换测试环境") },
                text = { Text("LPCTR 测试已完成，请切换测试环境后点击继续。") },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissEnvironmentDialog() }) {
                        Text("继续测试")
                    }
                }
            )
        }

        state.errorMessage?.let { error ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissError() },
                title = { Text("错误") },
                text = { Text(error) },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissError() }) { Text("确定") }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigSection(
    state: FactoryUiState,
    onSelectProject: (ProjectConfig) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("测试配置", style = MaterialTheme.typography.titleMedium)

            // Chip type display
            OutlinedTextField(
                value = state.chipType.uppercase(),
                onValueChange = {},
                label = { Text("芯片类型") },
                readOnly = true,
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(Icons.Default.Construction, contentDescription = null)
                }
            )

            // Project dropdown
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = state.selectedProject?.projectName ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("项目名称") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    placeholder = {
                        Text(
                            if (state.isLoadingConfigs) "加载中..."
                            else if (state.availableProjects.isEmpty()) "无可用配置"
                            else "请选择项目"
                        )
                    }
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    state.availableProjects.forEach { project ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(project.projectName, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "${project.chip.uppercase()} | ${project.factoryConfig.tests.count { it.value.enabled }} 项测试",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                onSelectProject(project)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressSection(state: FactoryUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("测试进度", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${state.currentStep}/${state.totalSteps}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { state.progressPercent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = state.currentStepDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )

            if (state.testCompleted) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (state.overallPassed) "结果: PASS" else "结果: FAIL",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (state.overallPassed) Color(0xFF2E7D32) else Color(0xFFC62828)
                )
            }
        }
    }
}

@Composable
private fun ResultsSection(state: FactoryUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("测试结果", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            state.results.forEach { (testType, channelResults) ->
                ExpandableResultCard(
                    testType = testType,
                    results = channelResults,
                    defaultExpanded = !testType.defaultCollapsed
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun ExpandableResultCard(
    testType: TestType,
    results: List<com.ghealth.tools.feature.factory.model.TestResult>,
    defaultExpanded: Boolean
) {
    var expanded by remember { mutableStateOf(defaultExpanded) }
    val passCount = results.count { it.passed }
    val totalCount = results.size

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (passCount == totalCount)
                Color(0xFFE8F5E9)
            else
                Color(0xFFFFEBEE)
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (passCount == totalCount) Icons.Default.Done
                        else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (passCount == totalCount) Color(0xFF2E7D32) else Color(0xFFC62828),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            "${testType.displayName}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "$passCount/$totalCount 通过",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (passCount == totalCount) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.rotate(if (expanded) 90f else 0f)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerLow,
                            RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                        )
                        .padding(12.dp)
                ) {
                    // Header row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("通道", style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(0.15f))
                        Text("值", style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(0.2f))
                        Text("单位", style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(0.15f))
                        Text("阈值", style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(0.25f))
                        Text("结果", style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(0.25f))
                    }

                    results.forEach { result ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${result.channelIndex}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(0.15f))
                            Text("${result.value}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(0.2f))
                            Text(result.unit,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(0.15f))
                            Text(result.threshold,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(0.25f))
                            Text(
                                if (result.passed) "PASS" else "FAIL",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (result.passed) Color(0xFF2E7D32) else Color(0xFFC62828),
                                modifier = Modifier.weight(0.25f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogSection(state: FactoryUiState) {
    val listState = rememberLazyListState()

    LaunchedEffect(state.logMessages.size) {
        if (state.logMessages.isNotEmpty()) {
            listState.animateScrollToItem(state.logMessages.size - 1)
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("日志", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        Color(0xFF1E1E1E),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp)
            ) {
                items(state.logMessages) { entry ->
                    val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
                    val timeStr = timeFormat.format(Date(entry.timestamp))
                    val color = when (entry.level) {
                        LogLevel.INFO -> Color(0xFFB0BEC5)
                        LogLevel.WARN -> Color(0xFFFFCA28)
                        LogLevel.ERROR -> Color(0xFFEF5350)
                    }
                    Text(
                        text = "$timeStr [${entry.level.name}] ${entry.message}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = color
                    )
                }
            }
        }
    }
}
