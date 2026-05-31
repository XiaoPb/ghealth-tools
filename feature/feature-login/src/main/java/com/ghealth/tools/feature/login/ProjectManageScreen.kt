package com.ghealth.tools.feature.login

import android.app.Activity
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import com.ghealth.tools.core.ui.theme.ButtonShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ghealth.tools.core.network.model.ProjectResponse
import com.ghealth.tools.core.ui.component.ErrorEffect
import com.ghealth.tools.core.ui.adaptive.CONTENT_MAX_WIDTH
import com.ghealth.tools.core.ui.adaptive.isWide

private enum class ProjectManageTab(val label: String) {
    PROJECTS("项目列表"),
    PROD_CONFIG("产测配置"),
    REGULAR_CONFIG("常规配置"),
    CSV_FILES("CSV 文件")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun ProjectManageScreen(
    onNavigateBack: () -> Unit,
    onEditProject: (Int, String) -> Unit,
    onViewCsvFiles: (Int, String) -> Unit,
    onUploadProdConfig: (Int, String) -> Unit = { _, _ -> },
    viewModel: ProjectManageViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(ProjectManageTab.PROJECTS) }
    var selectedProject by remember { mutableStateOf<ProjectResponse?>(null) }
    var deleteTarget by remember { mutableStateOf<ProjectResponse?>(null) }
    var archiveTarget by remember { mutableStateOf<ProjectResponse?>(null) }

    val context = LocalContext.current
    val windowSizeClass = calculateWindowSizeClass(context as Activity)
    val maxW = if (windowSizeClass.widthSizeClass.isWide) CONTENT_MAX_WIDTH else Dp.Infinity

    ErrorEffect(
        errorMessage = uiState.errorMessage,
        onDismiss = viewModel::clearMessages,
        useToast = true
    )
    ErrorEffect(
        errorMessage = uiState.successMessage,
        onDismiss = viewModel::clearMessages,
        useToast = true
    )

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("项目管理") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    }
                )
                ProjectManageTabRow(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(modifier = Modifier.widthIn(max = maxW).fillMaxSize()) {
                when {
                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    else -> {
                        when (selectedTab) {
                            ProjectManageTab.PROJECTS -> {
                                ProjectListContent(
                                    projects = uiState.projects,
                                    isArchiving = uiState.isArchiving,
                                    archivingProjectId = uiState.archivingProjectId,
                                    isExporting = uiState.isExporting,
                                    exportingProjectId = uiState.exportingProjectId,
                                    isDeleting = uiState.isDeleting,
                                    deletingProjectId = uiState.deletingProjectId,
                                    errorMessage = uiState.errorMessage,
                                    selectedProjectId = selectedProject?.id,
                                    modifier = Modifier,
                                    onEdit = { onEditProject(it.id, it.name) },
                                    onDelete = { deleteTarget = it },
                                    onArchive = { archiveTarget = it },
                                    onRestore = { viewModel.restoreProject(it) },
                                    onExport = { viewModel.exportProject(it) },
                                    onSelect = { selectedProject = it },
                                    onRetry = { viewModel.loadProjects() }
                                )
                            }
                            ProjectManageTab.PROD_CONFIG -> {
                                if (selectedProject != null) {
                                    ProdTestConfigManageContent(
                                        projectId = selectedProject!!.id,
                                        projectName = selectedProject!!.name,
                                        chipModel = selectedProject!!.chipModel,
                                        onUpload = {
                                            onUploadProdConfig(selectedProject!!.id, selectedProject!!.name)
                                        },
                                        modifier = Modifier
                                    )
                                } else {
                                    EmptyTabContent("请先在项目列表中选择一个项目")
                                }
                            }
                            ProjectManageTab.REGULAR_CONFIG -> {
                                if (selectedProject != null) {
                                    RegularConfigManageContent(
                                        projectId = selectedProject!!.id,
                                        projectName = selectedProject!!.name,
                                        chipModel = selectedProject!!.chipModel,
                                        modifier = Modifier
                                    )
                                } else {
                                    EmptyTabContent("请先在项目列表中选择一个项目")
                                }
                            }
                            ProjectManageTab.CSV_FILES -> {
                                if (selectedProject != null) {
                                    CsvFileManageContent(
                                        projectId = selectedProject!!.id,
                                        projectName = selectedProject!!.name,
                                        modifier = Modifier
                                    )
                                } else {
                                    EmptyTabContent("请先在项目列表中选择一个项目")
                                }
                            }
                        }
                    }
                }
            }
        }

        if (deleteTarget != null) {
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("确认删除项目") },
                text = { Text("确定要删除项目「${deleteTarget!!.name}」吗？此操作不可恢复，将同时删除所有关联的 CSV 文件和配置。") },
                confirmButton = {
                    TextButton(onClick = {
                        deleteTarget?.let { viewModel.deleteProject(it) }
                        deleteTarget = null
                    }) {
                        Text("确认删除", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) { Text("取消") }
                }
            )
        }

        if (archiveTarget != null) {
            AlertDialog(
                onDismissRequest = { archiveTarget = null },
                title = { Text("确认归档项目") },
                text = { Text("归档项目「${archiveTarget!!.name}」后，它将不再出现在默认项目列表中。可随时恢复。") },
                confirmButton = {
                    TextButton(onClick = {
                        archiveTarget?.let { viewModel.archiveProject(it) }
                        archiveTarget = null
                    }) {
                        Text("确认归档")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { archiveTarget = null }) { Text("取消") }
                }
            )
        }
    }
}

@Composable
private fun ProjectManageTabRow(
    selectedTab: ProjectManageTab,
    onTabSelected: (ProjectManageTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ProjectManageTab.entries.forEach { tab ->
            TextButton(onClick = { onTabSelected(tab) }) {
                Text(
                    text = tab.label,
                    color = if (selectedTab == tab) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = if (selectedTab == tab) MaterialTheme.typography.labelLarge
                    else MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun ProjectListContent(
    projects: List<ProjectResponse>,
    isArchiving: Boolean,
    archivingProjectId: Int?,
    isExporting: Boolean,
    exportingProjectId: Int?,
    isDeleting: Boolean,
    deletingProjectId: Int?,
    errorMessage: String? = null,
    selectedProjectId: Int? = null,
    modifier: Modifier = Modifier,
    onEdit: (ProjectResponse) -> Unit,
    onDelete: (ProjectResponse) -> Unit,
    onArchive: (ProjectResponse) -> Unit,
    onRestore: (ProjectResponse) -> Unit,
    onExport: (ProjectResponse) -> Unit,
    onSelect: (ProjectResponse) -> Unit,
    onRetry: () -> Unit = {}
) {
    if (errorMessage != null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("加载失败", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onRetry) {
                    Text("重试")
                }
            }
        }
        return
    }

    if (projects.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("暂无项目", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "请确认已在项目选择页创建项目",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(projects) { project ->
            val isProcessing = (isArchiving && archivingProjectId == project.id) ||
                    (isDeleting && deletingProjectId == project.id) ||
                    (isExporting && exportingProjectId == project.id)

            ProjectManageCard(
                project = project,
                isProcessing = isProcessing,
                isSelected = project.id == selectedProjectId,
                onEdit = { onEdit(project) },
                onDelete = { onDelete(project) },
                onArchive = { onArchive(project) },
                onExport = { onExport(project) },
                onSelect = { onSelect(project) }
            )
        }
    }
}

@Composable
private fun ProjectManageCard(
    project: ProjectResponse,
    isProcessing: Boolean,
    isSelected: Boolean = false,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
    onExport: () -> Unit,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${project.chipModelDisplay} | HW: ${project.hardwareVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "CSV: ${project.csvCount} | 配置: ${project.regularConfigCount} | 产测: ${if (project.hasProdConfig) "已上传" else "未上传"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "编辑",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onArchive, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.Archive,
                        contentDescription = "归档",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
                IconButton(onClick = onExport, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "导出",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                IconButton(onClick = onSelect, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "查看文件",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTabContent(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProdTestConfigManageContent(
    projectId: Int,
    projectName: String,
    chipModel: String = "",
    onUpload: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val configViewModel: ProdTestConfigManageViewModel = hiltViewModel()
    val configState by configViewModel.uiState.collectAsState()

    LaunchedEffect(projectId) {
        configViewModel.loadConfig(projectId, projectName, chipModel)
    }

    ErrorEffect(
        errorMessage = configState.errorMessage,
        onDismiss = configViewModel::clearMessages,
        useToast = true
    )
    ErrorEffect(
        errorMessage = configState.successMessage,
        onDismiss = configViewModel::clearMessages,
        useToast = true
    )

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "产测配置 - $projectName",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onUpload,
                enabled = !configState.isLoading
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (configState.config != null) "更新配置" else "上传配置")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        when {
            configState.isLoading -> {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            configState.config == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无产测配置", style = MaterialTheme.typography.bodyLarge)
                }
            }
            else -> {
                val config = configState.config!!
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        InfoRow("硬件版本", config.hardwareVersion)
                        InfoRow("测试频率", config.testFrequency ?: "未设置")
                        InfoRow("文件数量", "${config.fileCount}")
                        InfoRow("完整性", if (config.isComplete) "完整" else "不完整")
                        InfoRow("上传时间", config.uploadedAt ?: "未上传")

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { configViewModel.downloadConfig() },
                                enabled = !configState.isDownloading
                            ) {
                                if (configState.isDownloading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text("下载配置")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = { configViewModel.deleteConfig() },
                                enabled = !configState.isDeleting,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                if (configState.isDeleting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text("删除配置")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RegularConfigManageContent(
    projectId: Int,
    projectName: String,
    chipModel: String,
    modifier: Modifier = Modifier
) {
    val configViewModel: RegularConfigListViewModel = hiltViewModel()
    val configState by configViewModel.uiState.collectAsState()

    LaunchedEffect(projectId) {
        configViewModel.loadConfigs(projectId, projectName, chipModel)
    }

    ErrorEffect(
        errorMessage = configState.errorMessage,
        onDismiss = configViewModel::clearMessages,
        useToast = true
    )
    ErrorEffect(
        errorMessage = configState.successMessage,
        onDismiss = configViewModel::clearMessages,
        useToast = true
    )

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "常规配置 - $projectName",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { configViewModel.uploadConfig() },
                enabled = !configState.isUploading
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("上传配置")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        when {
            configState.isLoading -> {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            configState.configs.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("暂无常规配置", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "点击「上传配置」添加配置文件",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(configState.configs) { config ->
                        RegularConfigItem(
                            config = config,
                            isDownloading = configState.downloadingConfigId == config.id,
                            isDeleting = configState.deletingConfigId == config.id,
                            onDownload = { configViewModel.downloadConfig(config) },
                            onDelete = { configViewModel.deleteConfig(config) }
                        )
                    }
                }
            }
        }
    }

    if (configState.showUploadDialog) {
        RegularConfigUploadDialog(
            projectName = projectName,
            chipModel = configState.chipModel,
            isUploading = configState.isUploading,
            onDismiss = { configViewModel.dismissUploadDialog() },
            onUpload = { uri, fileName, version, description, overwrite ->
                configViewModel.performUpload(uri, fileName, version, description, overwrite)
            }
        )
    }
}

@Composable
private fun RegularConfigItem(
    config: com.ghealth.tools.core.network.model.RegularConfigResponse,
    isDownloading: Boolean,
    isDeleting: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = config.filename,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "v${config.version} | ${config.fileSizeDisplay ?: "${config.fileSize} B"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "上传者: ${config.uploadedByName} | ${config.uploadedAt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isDownloading || isDeleting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDownload, enabled = !isDownloading && !isDeleting) {
                Text("下载")
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                onClick = onDelete,
                enabled = !isDownloading && !isDeleting,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("删除")
            }
        }
    }
}

@Composable
private fun RegularConfigUploadDialog(
    projectName: String,
    chipModel: String,
    isUploading: Boolean,
    onDismiss: () -> Unit,
    onUpload: (android.net.Uri, String, String, String, Boolean) -> Unit
) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("1.0.0") }
    var description by remember { mutableStateOf("") }
    var overwrite by remember { mutableStateOf(false) }
    val fileSuffix = if (chipModel == "gh3220") ".ini" else ".config"

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        selectedFileName = it.getString(nameIndex)
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        title = { Text("上传常规配置 - $projectName") },
        text = {
            Column {
                Text(
                    text = "支持的文件格式: $fileSuffix，最大 5MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    shape = ButtonShape,
                    onClick = { filePickerLauncher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isUploading
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (selectedFileName.isEmpty()) "选择配置文件" else selectedFileName)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = version,
                    onValueChange = { version = it },
                    label = { Text("版本号") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isUploading
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isUploading
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = overwrite,
                        onCheckedChange = { overwrite = it },
                        enabled = !isUploading
                    )
                    Text("覆盖同名文件", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedUri?.let { onUpload(it, selectedFileName, version, description, overwrite) }
                },
                enabled = !isUploading && selectedUri != null
            ) {
                if (isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("上传")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isUploading
            ) { Text("取消") }
        }
    )
}

@Composable
private fun CsvFileManageContent(
    projectId: Int,
    projectName: String,
    modifier: Modifier = Modifier
) {
    val csvViewModel: CsvFileManageViewModel = hiltViewModel()
    val csvState by csvViewModel.uiState.collectAsState()

    LaunchedEffect(projectId) {
        csvViewModel.loadCsvFiles(projectId, projectName)
    }

    ErrorEffect(
        errorMessage = csvState.errorMessage,
        onDismiss = csvViewModel::clearMessages,
        useToast = true
    )
    ErrorEffect(
        errorMessage = csvState.successMessage,
        onDismiss = csvViewModel::clearMessages,
        useToast = true
    )

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CSV 文件 - $projectName",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { csvViewModel.uploadCsv() },
                enabled = !csvState.isUploading
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("上传CSV")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        when {
            csvState.isLoading -> {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            csvState.csvFiles.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("暂无 CSV 文件", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "点击「上传CSV」添加文件",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(csvState.csvFiles) { file ->
                        CsvFileItem(
                            file = file,
                            isDownloading = csvState.downloadingFileId == file.id,
                            isDeleting = csvState.deletingFileId == file.id,
                            onDownload = { csvViewModel.downloadFile(file) },
                            onDelete = { csvViewModel.deleteFile(file) }
                        )
                    }
                }
            }
        }
    }

    if (csvState.showUploadDialog) {
        CsvFileUploadDialog(
            projectName = projectName,
            isUploading = csvState.isUploading,
            onDismiss = { csvViewModel.dismissUploadDialog() },
            onUpload = { uri, fileName, overwrite ->
                csvViewModel.performUpload(uri, fileName, overwrite)
            }
        )
    }
}

@Composable
private fun CsvFileItem(
    file: com.ghealth.tools.core.network.model.CsvFileResponse,
    isDownloading: Boolean,
    isDeleting: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.filename,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "行数: ${file.rowCount} | 上传者: ${file.uploadedByName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "上传时间: ${file.uploadedAt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isDownloading || isDeleting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDownload, enabled = !isDownloading && !isDeleting) {
                Text("下载")
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                onClick = onDelete,
                enabled = !isDownloading && !isDeleting,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("删除")
            }
        }
    }
}

@Composable
private fun CsvFileUploadDialog(
    projectName: String,
    isUploading: Boolean,
    onDismiss: () -> Unit,
    onUpload: (android.net.Uri, String, Boolean) -> Unit
) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var overwrite by remember { mutableStateOf(false) }

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        selectedFileName = it.getString(nameIndex)
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        title = { Text("上传 CSV 文件 - $projectName") },
        text = {
            Column {
                Text(
                    text = "支持 .csv 文件，最大 100MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    shape = ButtonShape,
                    onClick = { filePickerLauncher.launch("text/csv") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isUploading
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (selectedFileName.isEmpty()) "选择 CSV 文件" else selectedFileName)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = overwrite,
                        onCheckedChange = { overwrite = it },
                        enabled = !isUploading
                    )
                    Text("覆盖同名文件", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedUri?.let { onUpload(it, selectedFileName, overwrite) }
                },
                enabled = !isUploading && selectedUri != null
            ) {
                if (isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("上传")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isUploading
            ) { Text("取消") }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}