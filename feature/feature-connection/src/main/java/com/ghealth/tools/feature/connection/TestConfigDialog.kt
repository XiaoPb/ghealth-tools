package com.ghealth.tools.feature.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ghealth.tools.core.model.TestConfig
import com.ghealth.tools.core.model.TestScenario

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestConfigDialog(
    deviceName: String,
    onConfirm: (TestConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var testerName by remember { mutableStateOf("") }
    var selectedScenario by remember { mutableStateOf(TestScenario.RESTING) }
    var testRound by remember { mutableIntStateOf(1) }
    var notes by remember { mutableStateOf("") }
    var scenarioExpanded by remember { mutableStateOf(false) }

    val isValid = testerName.isNotBlank() && testRound > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text("测试配置")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "设备: $deviceName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                OutlinedTextField(
                    value = testerName,
                    onValueChange = { testerName = it },
                    label = { Text("测试人员姓名") },
                    placeholder = { Text("请输入测试人员姓名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = scenarioExpanded,
                    onExpandedChange = { scenarioExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedScenario.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("测试场景") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = scenarioExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = scenarioExpanded,
                        onDismissRequest = { scenarioExpanded = false }
                    ) {
                        TestScenario.entries.forEach { scenario ->
                            DropdownMenuItem(
                                text = { Text(scenario.displayName) },
                                onClick = {
                                    selectedScenario = scenario
                                    scenarioExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "测试次数:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    OutlinedButton(
                        onClick = { if (testRound > 1) testRound-- }
                    ) {
                        Text("-")
                    }
                    OutlinedTextField(
                        value = testRound.toString(),
                        onValueChange = { 
                            it.toIntOrNull()?.let { v -> 
                                if (v > 0) testRound = v 
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .width(80.dp)
                            .padding(horizontal = 8.dp),
                        singleLine = true
                    )
                    OutlinedButton(
                        onClick = { testRound++ }
                    ) {
                        Text("+")
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("备注（可选）") },
                    placeholder = { Text("输入测试备注信息") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        TestConfig(
                            testerName = testerName,
                            scenario = selectedScenario,
                            testRound = testRound,
                            notes = notes
                        )
                    )
                },
                enabled = isValid
            ) {
                Text("开始测试")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
