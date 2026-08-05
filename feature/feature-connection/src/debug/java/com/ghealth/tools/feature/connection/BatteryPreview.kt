package com.ghealth.tools.feature.connection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ghealth.tools.ble.connection.BatteryStatus
import com.ghealth.tools.ble.connection.ConnectedDevice
import com.ghealth.tools.ble.connection.DeviceRole
import com.ghealth.tools.core.model.ConnectionState

/**
 * 调试专用电池预览（仅 debug 构建编译）：在「设备连接状态」卡片上方提供预览开关，
 * 注入 4 台模拟设备与不同电量/充放电状态，用于无真机时在模拟器验证 UI。
 * release 构建编译的是 src/release 下的空实现，本文件不会进入正式包。
 */
@Composable
internal fun BatteryPreviewScope(
    state: ConnectionUiState,
    content: @Composable (ConnectionUiState) -> Unit,
) {
    var previewing by remember { mutableStateOf(false) }

    val displayState = if (previewing) {
        state.copy(
            connectedDevices = MockBatteryDevices.associateBy { it.address },
            batteryStatusByAddress = MockBatteryStatus,
        )
    } else {
        state
    }

    Column {
        TextButton(
            onClick = { previewing = !previewing },
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text(if (previewing) "清除预览（调试）" else "预览电池（调试）")
        }
        content(displayState)
    }
}

private val MockBatteryDevices = listOf(
    ConnectedDevice(
        address = "AA:BB:CC:00:00:01",
        name = "Master 设备（模拟）",
        role = DeviceRole.MASTER,
        state = ConnectionState.CONNECTED,
    ),
    ConnectedDevice(
        address = "AA:BB:CC:00:00:02",
        name = "Slave 设备（模拟）",
        role = DeviceRole.SLAVE,
        state = ConnectionState.CONNECTED,
    ),
    ConnectedDevice(
        address = "AA:BB:CC:00:00:03",
        name = "Compare 设备（模拟）",
        role = DeviceRole.COMPARE,
        state = ConnectionState.CONNECTED,
    ),
    ConnectedDevice(
        address = "AA:BB:CC:00:00:04",
        name = "满电设备（模拟）",
        role = DeviceRole.MASTER,
        state = ConnectionState.CONNECTED,
    ),
)

private val MockBatteryStatus = mapOf(
    "AA:BB:CC:00:00:01" to BatteryStatus(level = 85),
    "AA:BB:CC:00:00:02" to BatteryStatus(level = 10),
    "AA:BB:CC:00:00:03" to BatteryStatus(level = 60, chargeState = BatteryStatus.ChargeState.Charging),
    "AA:BB:CC:00:00:04" to BatteryStatus(level = 100, chargeState = BatteryStatus.ChargeState.Full),
)
