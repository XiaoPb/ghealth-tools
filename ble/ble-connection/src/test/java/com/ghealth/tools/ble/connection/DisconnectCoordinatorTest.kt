package com.ghealth.tools.ble.connection

import com.juul.kable.State
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DisconnectCoordinatorTest {

    @Test
    fun `confirmed disconnect invokes onConfirmedDisconnected and does not close`() = runTest {
        val fake = FakePeripheral().apply { markConnected() }
        val coordinator = DisconnectCoordinator(disconnectTimeoutMs = 100)
        var disconnectingMarked = false
        var confirmed = 0
        var failed = 0

        coordinator.disconnect(
            address = fake.identifier,
            peripheral = fake,
            markDisconnecting = { disconnectingMarked = true },
            onConfirmedDisconnected = { confirmed++ },
            onDisconnectFailed = { failed++ },
        )

        assertTrue(disconnectingMarked)
        assertEquals(1, confirmed)
        assertEquals(0, failed)
        assertEquals(1, fake.disconnectCount)
        assertEquals(0, fake.closeCount)
        assertTrue(fake.state.value is State.Disconnected)
    }

    @Test
    fun `timeout without disconnected state keeps device and reports failure`() = runTest {
        val fake = FakePeripheral(autoDisconnect = false).apply { markConnected() }
        val coordinator = DisconnectCoordinator(disconnectTimeoutMs = 50)
        var confirmed = 0
        var failed = 0

        coordinator.disconnect(
            address = fake.identifier,
            peripheral = fake,
            markDisconnecting = {},
            onConfirmedDisconnected = { confirmed++ },
            onDisconnectFailed = { failed++ },
        )

        assertEquals(0, confirmed)
        assertEquals(1, failed)
        assertEquals(1, fake.closeCount)
        assertFalse(fake.state.value is State.Disconnected)
    }

    @Test
    fun `close fallback after timeout confirms disconnect`() = runTest {
        val fake = FakePeripheral(
            autoDisconnect = false,
            closeTriggersDisconnected = true,
        ).apply { markConnected() }
        val coordinator = DisconnectCoordinator(disconnectTimeoutMs = 50)
        var confirmed = 0
        var failed = 0

        coordinator.disconnect(
            address = fake.identifier,
            peripheral = fake,
            markDisconnecting = {},
            onConfirmedDisconnected = { confirmed++ },
            onDisconnectFailed = { failed++ },
        )

        assertEquals(1, confirmed)
        assertEquals(0, failed)
        assertEquals(1, fake.closeCount)
        assertTrue(fake.state.value is State.Disconnected)
    }

    @Test
    fun `disconnect exception falls back to close and confirms`() = runTest {
        val fake = FakePeripheral(
            disconnectThrows = true,
            closeTriggersDisconnected = true,
        ).apply { markConnected() }
        val coordinator = DisconnectCoordinator(disconnectTimeoutMs = 50)
        var confirmed = 0
        var failed = 0

        coordinator.disconnect(
            address = fake.identifier,
            peripheral = fake,
            markDisconnecting = {},
            onConfirmedDisconnected = { confirmed++ },
            onDisconnectFailed = { failed++ },
        )

        assertEquals(1, confirmed)
        assertEquals(0, failed)
        assertEquals(1, fake.closeCount)
        assertTrue(fake.state.value is State.Disconnected)
    }

    @Test
    fun `duplicate disconnect while in progress is skipped`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val fake = FakePeripheral(disconnectGate = gate).apply { markConnected() }
        val coordinator = DisconnectCoordinator(disconnectTimeoutMs = 5_000)
        var confirmed = 0

        val firstJob = launch {
            coordinator.disconnect(
                address = fake.identifier,
                peripheral = fake,
                markDisconnecting = {},
                onConfirmedDisconnected = { confirmed++ },
                onDisconnectFailed = {},
            )
        }
        runCurrent() // 让第一个调用运行到挂起在 disconnectGate 上

        // 第二个调用应被单飞拦截，立即返回、不触发任何回调、不重复 disconnect
        coordinator.disconnect(
            address = fake.identifier,
            peripheral = fake,
            markDisconnecting = {},
            onConfirmedDisconnected = { confirmed++ },
            onDisconnectFailed = {},
        )
        assertEquals(0, confirmed)
        assertEquals(1, fake.disconnectCount)

        gate.complete(Unit)
        firstJob.join()

        assertEquals(1, confirmed)
        assertEquals(1, fake.disconnectCount)
    }
}
