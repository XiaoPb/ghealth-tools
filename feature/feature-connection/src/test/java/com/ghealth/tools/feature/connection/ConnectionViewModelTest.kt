package com.ghealth.tools.feature.connection

import com.ghealth.tools.ble.connection.BatteryStatus
import com.ghealth.tools.ble.connection.BleConnectionManager
import com.ghealth.tools.ble.connection.ConnectedDevice
import com.ghealth.tools.ble.connection.ConnectionError
import com.ghealth.tools.ble.connection.DeviceRole
import com.ghealth.tools.ble.connection.FirmwareVersionHolder
import com.ghealth.tools.ble.connection.FirmwareVersionState
import com.ghealth.tools.ble.protocol.gh3036.KEY_F_GET_MODE
import com.ghealth.tools.ble.protocol.rpccore.ParseResult
import com.ghealth.tools.ble.protocol.rpccore.ProtocolError
import com.ghealth.tools.ble.scanner.BleScanner
import com.ghealth.tools.core.datastore.BlePreferences
import com.ghealth.tools.core.datastore.UserPreferences
import com.ghealth.tools.core.model.ConnectionState
import com.ghealth.tools.core.network.ConfigPathProvider
import com.ghealth.tools.core.storage.RecordingManager
import com.ghealth.tools.feature.factory.parser.RegisterConfigParser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): Triple<ConnectionViewModel, BleConnectionManager, MutableStateFlow<Map<String, ConnectedDevice>>> {
        val scanner = mockk<BleScanner>(relaxed = true)
        every { scanner.isBluetoothEnabled } returns true
        every { scanner.hasScanPermission } returns true
        every { scanner.hasConnectPermission } returns true

        val devicesFlow = MutableStateFlow(emptyMap<String, ConnectedDevice>())
        val connectionManager = mockk<BleConnectionManager>(relaxed = true)
        every { connectionManager.devices } returns devicesFlow
        every { connectionManager.connectionErrors } returns MutableSharedFlow<Pair<String, ConnectionError>>()
        every { connectionManager.dataFlow } returns MutableSharedFlow<Pair<String, ParseResult>>()
        every { connectionManager.recordingStoppedEvents } returns MutableSharedFlow<Unit>()
        every { connectionManager.batteryStatus } returns MutableStateFlow(emptyMap<String, BatteryStatus>())

        val firmwareVersionHolder = mockk<FirmwareVersionHolder>(relaxed = true)
        every { firmwareVersionHolder.state } returns MutableStateFlow(FirmwareVersionState())

        val recordingManager = mockk<RecordingManager>(relaxed = true)

        val blePreferences = mockk<BlePreferences>(relaxed = true)
        every { blePreferences.autoReconnect } returns flowOf(false)
        every { blePreferences.lastDeviceAddress } returns flowOf(null)
        every { blePreferences.lastDeviceName } returns flowOf(null)
        every { blePreferences.selectedChip } returns flowOf("gh3036")

        val userPreferences = mockk<UserPreferences>(relaxed = true)
        val registerConfigParser = mockk<RegisterConfigParser>(relaxed = true)
        val configPathProvider = mockk<ConfigPathProvider>(relaxed = true)

        val viewModel = ConnectionViewModel(
            bleScanner = scanner,
            connectionManager = connectionManager,
            firmwareVersionHolder = firmwareVersionHolder,
            recordingManager = recordingManager,
            blePreferences = blePreferences,
            userPreferences = userPreferences,
            registerConfigParser = registerConfigParser,
            baseDir = File(System.getProperty("java.io.tmpdir")),
            configPathProvider = configPathProvider
        )
        return Triple(viewModel, connectionManager, devicesFlow)
    }

    private fun TestScope.connectMaster(devicesFlow: MutableStateFlow<Map<String, ConnectedDevice>>) {
        devicesFlow.value = mapOf(
            "AA:BB" to ConnectedDevice(
                address = "AA:BB",
                name = "TestDevice",
                role = DeviceRole.MASTER,
                state = ConnectionState.CONNECTED
            )
        )
        advanceUntilIdle()
    }

    @Test
    fun `timeout failure clears executing state and surfaces error toast`() = runTest(dispatcher) {
        val (viewModel, connectionManager, devicesFlow) = createViewModel()
        connectMaster(devicesFlow)

        coEvery { connectionManager.sendCommand(any(), eq(KEY_F_GET_MODE), any()) } returns
            Result.failure(ProtocolError.Timeout)

        viewModel.executeCommand(KEY_F_GET_MODE, byteArrayOf(0x01))
        advanceUntilIdle()

        val executionState = viewModel.uiState.value.commandExecutionStates[KEY_F_GET_MODE]
        assertNotNull(executionState)
        assertEquals(false, executionState?.isExecuting)
        assertNull(executionState?.result)
        assertTrue(executionState?.error?.contains("超时") == true)

        val toast = viewModel.uiState.value.commandErrorToast
        assertNotNull(toast)
        assertTrue(toast?.message?.contains("超时") == true)
    }

    @Test
    fun `successful command stores result without error toast`() = runTest(dispatcher) {
        val (viewModel, connectionManager, devicesFlow) = createViewModel()
        connectMaster(devicesFlow)

        coEvery { connectionManager.sendCommand(any(), eq(KEY_F_GET_MODE), any()) } returns
            Result.success(byteArrayOf(0x01, 0x00))

        viewModel.executeCommand(KEY_F_GET_MODE, byteArrayOf(0x01))
        advanceUntilIdle()

        val executionState = viewModel.uiState.value.commandExecutionStates[KEY_F_GET_MODE]
        assertNotNull(executionState)
        assertEquals(false, executionState?.isExecuting)
        assertTrue(executionState?.result?.contentEquals(byteArrayOf(0x01, 0x00)) == true)
        assertNull(viewModel.uiState.value.commandErrorToast)
    }

    @Test
    fun `duplicate execute while in flight is ignored`() = runTest(dispatcher) {
        val (viewModel, connectionManager, devicesFlow) = createViewModel()
        connectMaster(devicesFlow)

        val gate = CompletableDeferred<Result<ByteArray>>()
        coEvery { connectionManager.sendCommand(any(), any(), any()) } coAnswers { gate.await() }

        viewModel.executeCommand(KEY_F_GET_MODE, byteArrayOf(0x01))
        runCurrent()
        viewModel.executeCommand(KEY_F_GET_MODE, byteArrayOf(0x01))
        runCurrent()

        coVerify(exactly = 1) { connectionManager.sendCommand(any(), eq(KEY_F_GET_MODE), any()) }

        gate.complete(Result.failure(ProtocolError.Timeout))
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.commandExecutionStates[KEY_F_GET_MODE]?.isExecuting)
    }
}