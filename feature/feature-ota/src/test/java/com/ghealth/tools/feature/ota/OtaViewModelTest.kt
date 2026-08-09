package com.ghealth.tools.feature.ota

import com.ghealth.tools.ble.connection.DeviceRole
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OtaViewModelTest {

    private val device = ConnectedDeviceInfo(
        address = "AA:BB:CC:DD:EE:FF",
        name = "GH3036",
        role = DeviceRole.MASTER
    )

    @Test
    fun `reconnect allowed when logged in and device selected`() {
        assertTrue(shouldReconnectOnCleared(isLoggedIn = true, device = device))
    }

    @Test
    fun `reconnect skipped after logout even with device selected`() {
        assertFalse(shouldReconnectOnCleared(isLoggedIn = false, device = device))
    }

    @Test
    fun `reconnect skipped when no device selected`() {
        assertFalse(shouldReconnectOnCleared(isLoggedIn = true, device = null))
    }
}