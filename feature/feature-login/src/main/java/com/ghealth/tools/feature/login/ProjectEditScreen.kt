package com.ghealth.tools.feature.login

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import com.ghealth.tools.core.ui.theme.ButtonShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ghealth.tools.core.ui.component.ErrorEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectEditScreen(
    projectId: Int,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ProjectEditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    ErrorEffect(
        errorMessage = uiState.errorMessage,
        onDismiss = viewModel::clearError,
        useToast = true
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑项目") },
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
        if (uiState.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::updateName,
                label = { Text("项目名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            var chipExpanded by remember { mutableStateOf(false) }
            val chipOptions = listOf("gh3036" to "GH3036", "gh3300" to "GH3300", "gh3220" to "GH3220")
            ExposedDropdownMenuBox(
                expanded = chipExpanded,
                onExpandedChange = { chipExpanded = it }
            ) {
                OutlinedTextField(
                    value = chipOptions.find { it.first == uiState.chipModel }?.second ?: uiState.chipModel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("芯片型号") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = chipExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = chipExpanded,
                    onDismissRequest = { chipExpanded = false }
                ) {
                    chipOptions.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.updateChipModel(value)
                                chipExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.hardwareVersion,
                onValueChange = viewModel::updateHardwareVersion,
                label = { Text("硬件版本") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.testFrequency,
                onValueChange = viewModel::updateTestFrequency,
                label = { Text("测试频率") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("如: 100Hz") }
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.description,
                onValueChange = viewModel::updateDescription,
                label = { Text("项目描述") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                shape = ButtonShape,
                onClick = { viewModel.saveProject(onSaved) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSaving
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("保存修改")
                }
            }
        }
    }
}