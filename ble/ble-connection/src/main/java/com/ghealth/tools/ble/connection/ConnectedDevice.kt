package com.ghealth.tools.ble.connection

import com.ghealth.tools.core.model.ConnectionState
import com.ghealth.tools.core.model.DeviceType

enum class DeviceRole {
    MASTER, SLAVE, COMPARE
}

data class ConnectedDevice(
    val address: String,
    val name: String?,
    val role: DeviceRole,
    val state: ConnectionState = ConnectionState.DISCONNECTED,
    val deviceType: DeviceType? = null
)
