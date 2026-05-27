package com.ghealth.tools.feature.login

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

private data class ConfigFileField(
    val label: String,
    val hint: String,
    val uri: Uri?,
    val fileName: String,
    val isRequired: Boolean
)

private val testFrequencyOptions = listOf("100Hz", "200Hz", "500Hz", "1kHz", "2kHz", "5kHz")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectConfigUploadScreen(
    projectId: Int,
    projectName: String,
    onNavigateBack: () -> Unit,
    onUploadComplete: () -> Unit,
    viewModel: ProjectConfigUploadViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(projectId, projectName) {
        viewModel.initProject(projectId, projectName)
    }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onUploadComplete()
        }
    }

    val jsonPicker = rememberFilePicker { uri, name -> viewModel.setJsonConfig(uri, name) }
    val baseNoisePicker = rememberFilePicker { uri, name -> viewModel.setBaseNoiseConfig(uri, name) }
    val lpctrPicker = rememberFilePicker { uri, name -> viewModel.setLpctrConfig(uri, name) }
    val lplctrPicker = rememberFilePicker { uri, name -> viewModel.setLplctrConfig(uri, name) }
    val ppgNoisePicker = rememberFilePicker { uri, name -> viewModel.setPpgNoiseConfig(uri, name) }

    var freqExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("上传配置文件") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "项目: $projectName",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "上传产测配置文件，至少需要 factory_config.json",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            ConfigFilePickerCard(
                fileField = ConfigFileField(
                    label = "factory_config.json",
                    hint = "JSON 格式产测流程配置文件",
                    uri = uiState.jsonConfigUri,
                    fileName = uiState.jsonConfigName,
                    isRequired = true
                ),
                onPick = { jsonPicker.launch(arrayOf("*/*")) }
            )
            Spacer(modifier = Modifier.height(8.dp))

            ConfigFilePickerCard(
                fileField = ConfigFileField(
                    label = "Base_Noise_*.config",
                    hint = "底噪配置文件",
                    uri = uiState.baseNoiseUri,
                    fileName = uiState.baseNoiseName,
                    isRequired = false
                ),
                onPick = { baseNoisePicker.launch(arrayOf("*/*")) }
            )
            Spacer(modifier = Modifier.height(8.dp))

            ConfigFilePickerCard(
                fileField = ConfigFileField(
                    label = "LPCTR_*.config",
                    hint = "LPCTR 配置文件",
                    uri = uiState.lpctrUri,
                    fileName = uiState.lpctrName,
                    isRequired = false
                ),
                onPick = { lpctrPicker.launch(arrayOf("*/*")) }
            )
            Spacer(modifier = Modifier.height(8.dp))

            ConfigFilePickerCard(
                fileField = ConfigFileField(
                    label = "LPLCTR_*.config",
                    hint = "LPLCTR 配置文件",
                    uri = uiState.lplctrUri,
                    fileName = uiState.lplctrName,
                    isRequired = false
                ),
                onPick = { lplctrPicker.launch(arrayOf("*/*")) }
            )
            Spacer(modifier = Modifier.height(8.dp))

            ConfigFilePickerCard(
                fileField = ConfigFileField(
                    label = "PPG_Noise_*.config",
                    hint = "PPG 噪声配置文件",
                    uri = uiState.ppgNoiseUri,
                    fileName = uiState.ppgNoiseName,
                    isRequired = false
                ),
                onPick = { ppgNoisePicker.launch(arrayOf("*/*")) }
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.hardwareVersion,
                onValueChange = viewModel::updateHardwareVersion,
                label = { Text("硬件版本") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isUploading,
                placeholder = { Text("例如: V1.0") }
            )
            Spacer(modifier = Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = freqExpanded,
                onExpandedChange = { if (!uiState.isUploading) freqExpanded = it }
            ) {
                OutlinedTextField(
                    value = uiState.testFrequency,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("测试频率") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = freqExpanded) },
                    enabled = !uiState.isUploading
                )
                ExposedDropdownMenu(
                    expanded = freqExpanded,
                    onDismissRequest = { freqExpanded = false }
                ) {
                    testFrequencyOptions.forEach { freq ->
                        DropdownMenuItem(
                            text = { Text(freq) },
                            onClick = {
                                viewModel.updateTestFrequency(freq)
                                freqExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = { viewModel.uploadConfig(onUploadComplete) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isUploading && uiState.jsonConfigUri != null
            ) {
                if (uiState.isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("上传中...")
                } else {
                    Text("上传配置文件")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onUploadComplete,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("跳过，稍后上传")
            }
        }
    }
}

@Composable
private fun ConfigFilePickerCard(
    fileField: ConfigFileField,
    onPick: () -> Unit
) {
    val isSelected = fileField.uri != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.AttachFile,
                contentDescription = null,
                tint = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileField.label,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (isSelected) {
                    Text(
                        text = fileField.fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!fileField.isRequired) {
                    Text(
                        text = fileField.hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onPick,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp,
                    vertical = 4.dp
                )
            ) {
                Text(if (isSelected) "重选" else "选择")
            }
        }
    }
}

@Composable
private fun rememberFilePicker(onResult: (Uri, String) -> Unit) =
    rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val fileName = uri.lastPathSegment ?: "unknown"
            onResult(uri, fileName)
        }
    }