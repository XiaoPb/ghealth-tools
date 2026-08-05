package com.ghealth.tools.feature.demo

import com.ghealth.tools.ble.connection.ConnectedDevice
import com.ghealth.tools.ble.connection.DeviceRole
import com.ghealth.tools.core.model.ConnectionState
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MasterReconnectDetectorTest {

    private fun master(state: ConnectionState): Map<String, ConnectedDevice> =
        mapOf("AA:BB:CC:DD:EE:FF" to ConnectedDevice(
            address = "AA:BB:CC:DD:EE:FF",
            name = "Master",
            role = DeviceRole.MASTER,
            state = state
        ))

    @Test
    fun `主设备从未连接变为已连接触发清空`() {
        assertTrue(shouldClearOnMasterReconnect(wasMasterConnected = false, devices = master(ConnectionState.CONNECTED)))
    }

    @Test
    fun `主设备已连接不触发清空`() {
        assertFalse(shouldClearOnMasterReconnect(wasMasterConnected = true, devices = master(ConnectionState.CONNECTED)))
    }

    @Test
    fun `主设备断开不触发清空`() {
        assertFalse(shouldClearOnMasterReconnect(wasMasterConnected = true, devices = emptyMap()))
    }

    @Test
    fun `主设备持续未连接不触发清空`() {
        assertFalse(shouldClearOnMasterReconnect(wasMasterConnected = false, devices = emptyMap()))
    }

    @Test
    fun `仅有从设备已连接不触发清空`() {
        val devices = mapOf("11:22:33:44:55:66" to ConnectedDevice(
            address = "11:22:33:44:55:66",
            name = "Slave",
            role = DeviceRole.SLAVE,
            state = ConnectionState.CONNECTED
        ))
        assertFalse(shouldClearOnMasterReconnect(wasMasterConnected = false, devices = devices))
    }

    @Test
    fun `主设备处于 CONNECTING 不触发清空`() {
        assertFalse(shouldClearOnMasterReconnect(wasMasterConnected = false, devices = master(ConnectionState.CONNECTING)))
    }
}
