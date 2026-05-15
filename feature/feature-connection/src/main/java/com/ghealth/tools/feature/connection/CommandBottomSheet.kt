package com.ghealth.tools.feature.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class DeviceCommand(
    val name: String,
    val key: String,
    val param: ByteArray = ByteArray(0)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeviceCommand) return false
        return name == other.name && key == other.key && param.contentEquals(other.param)
    }
    override fun hashCode() = 31 * name.hashCode() + key.hashCode()
}

val defaultCommands = listOf(
    DeviceCommand("读取设备信息", "ver"),
    DeviceCommand("启动采集", "S", byteArrayOf(0x01)),
    DeviceCommand("停止采集", "S", byteArrayOf(0x00)),
    DeviceCommand("复位设备", "rst"),
    DeviceCommand("读取版本号", "V"),
    DeviceCommand("读取电池电量", "bat"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandBottomSheet(
    onSendCommand: (String, ByteArray) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "命令操作",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            defaultCommands.forEach { cmd ->
                FilledTonalButton(
                    onClick = { onSendCommand(cmd.key, cmd.param) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(cmd.name)
                }
            }
        }
    }
}
