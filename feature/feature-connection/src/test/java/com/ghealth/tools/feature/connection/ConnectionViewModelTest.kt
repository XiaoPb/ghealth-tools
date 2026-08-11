package com.ghealth.tools.feature.connection

import com.ghealth.tools.ble.connection.BatteryStatus
import com.ghealth.tools.ble.connection.BleConnectionManager
import com.ghealth.tools.ble.connection.ConnectedDevice
import com.ghealth.tools.ble.connection.ConnectionError
import com.ghealth.tools.ble.connection.DeviceRole
import com.ghealth.tools.ble.connection.FirmwareVersionHolder
import com.ghealth.tools.ble.connection.FirmwareVersionState
import com.ghealth.tools.ble.protocol.gh3036.KEY_F_GET_MODE
import com.ghealth.tools.ble.protocol.gh3036.KEY_GH3X_CHIP_CTRL
import com.ghealth.tools.ble.protocol.gh3036.KEY_GH_SET_WORK_MODE_CMD
import com.ghealth.tools.ble.protocol.rpccore.ParseResult
import com.ghealth.tools.ble.protocol.rpccore.ProtocolError
import com.ghealth.tools.ble.scanner.BleScanner
import com.ghealth.tools.core.datastore.BlePreferences
import com.ghealth.tools.core.datastore.UserInfo
import com.ghealth.tools.core.datastore.UserPreferences
import com.ghealth.tools.core.model.ConnectionState
import com.ghealth.tools.core.model.DeviceType
import com.ghealth.tools.core.model.TestConfig
import com.ghealth.tools.core.model.TestScenario
import com.ghealth.tools.core.model.WorkMode
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

    private lateinit var recordingManagerMock: RecordingManager
    private lateinit var firmwareVersionHolderMock: FirmwareVersionHolder

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(chip: String = "gh3036"): Triple<ConnectionViewModel, BleConnectionManager, MutableStateFlow<Map<String, ConnectedDevice>>> {
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
        coEvery { firmwareVersionHolder.awaitVersionRead() } returns
            FirmwareVersionState(version = "V1", sdkVersion = "SDK1", hrVersion = "HR1")
        firmwareVersionHolderMock = firmwareVersionHolder

        val recordingManager = mockk<RecordingManager>(relaxed = true)
        recordingManagerMock = recordingManager

        val blePreferences = mockk<BlePreferences>(relaxed = true)
        every { blePreferences.autoReconnect } returns flowOf(false)
        every { blePreferences.lastDeviceAddress } returns flowOf(null)
        every { blePreferences.lastDeviceName } returns flowOf(null)
        every { blePreferences.selectedChip } returns flowOf(chip)

        val userPreferences = mockk<UserPreferences>(relaxed = true)
        every { userPreferences.selectedProjectId } returns flowOf(0)
        every { userPreferences.selectedProjectName } returns flowOf("")
        every { userPreferences.userInfo } returns flowOf(UserInfo(username = "tester"))
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
    fun `master 连接后先等待版本读取完成再弹出测试配置框`() = runTest(dispatcher) {
        val (viewModel, connectionManager, devicesFlow) = createViewModel(chip = "gh3220")
        devicesFlow.value = mapOf(
            "AA:BB" to ConnectedDevice(
                address = "AA:BB",
                name = "TestDevice",
                role = DeviceRole.MASTER,
                state = ConnectionState.CONNECTED,
                deviceType = DeviceType.GH3220
            )
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showTestConfigDialog)
        assertEquals("TestDevice", viewModel.uiState.value.masterDeviceName)
        coVerify { firmwareVersionHolderMock.awaitVersionRead() }
    }

    @Test
    fun `confirmTestConfig passes master device chip to recording session`() = runTest(dispatcher) {
        val (viewModel, connectionManager, devicesFlow) = createViewModel(chip = "gh3220")
        devicesFlow.value = mapOf(
            "AA:BB" to ConnectedDevice(
                address = "AA:BB",
                name = "TestDevice",
                role = DeviceRole.MASTER,
                state = ConnectionState.CONNECTED,
                deviceType = DeviceType.GH3220
            )
        )
        advanceUntilIdle()

        viewModel.confirmTestConfig(
            TestConfig(testerName = "tester", scenario = TestScenario.RESTING, testRound = 1)
        )
        advanceUntilIdle()

        coVerify {
            recordingManagerMock.startSession(
                config = TestConfig(testerName = "tester", scenario = TestScenario.RESTING, testRound = 1),
                chip = "gh3220",
                masterDeviceName = "TestDevice",
                masterDeviceAddress = "AA:BB",
                slaveDevices = emptyMap(),
                compareDeviceNames = emptyList(),
                compareDeviceAddresses = emptyList(),
                projectName = "",
                projectId = 0,
                username = "tester",
                sdkVersion = null,
                hrVersion = null,
                spo2Version = null,
                nadtVersion = null,
                hrvVersion = null
            )
        }
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

    @Test
    fun `no master connected reports error without sending command`() = runTest(dispatcher) {
        val (viewModel, connectionManager, _) = createViewModel()

        viewModel.executeCommand(KEY_F_GET_MODE, byteArrayOf(0x01))
        advanceUntilIdle()

        val executionState = viewModel.uiState.value.commandExecutionStates[KEY_F_GET_MODE]
        assertNotNull(executionState)
        assertEquals(false, executionState?.isExecuting)
        assertEquals("未连接主设备", executionState?.error)

        val toast = viewModel.uiState.value.commandErrorToast
        assertNotNull(toast)
        assertEquals("未连接主设备", toast?.message)

        coVerify(exactly = 0) { connectionManager.sendCommand(any(), any(), any()) }
    }

    @Test
    fun `thrown exception surfaces user friendly error and toast`() = runTest(dispatcher) {
        val (viewModel, connectionManager, devicesFlow) = createViewModel()
        connectMaster(devicesFlow)

        coEvery { connectionManager.sendCommand(any(), any(), any()) } throws RuntimeException("boom")

        viewModel.executeCommand(KEY_F_GET_MODE, byteArrayOf(0x01))
        advanceUntilIdle()

        val executionState = viewModel.uiState.value.commandExecutionStates[KEY_F_GET_MODE]
        assertNotNull(executionState)
        assertEquals(false, executionState?.isExecuting)
        assertTrue(executionState?.error?.contains("boom") == true)

        val toast = viewModel.uiState.value.commandErrorToast
        assertNotNull(toast)
        assertTrue(toast?.message?.contains("boom") == true)
    }

    @Test
    fun `dismissCommandErrorToast clears the toast`() = runTest(dispatcher) {
        val (viewModel, connectionManager, devicesFlow) = createViewModel()
        connectMaster(devicesFlow)

        coEvery { connectionManager.sendCommand(any(), any(), any()) } returns
            Result.failure(ProtocolError.Timeout)

        viewModel.executeCommand(KEY_F_GET_MODE, byteArrayOf(0x01))
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.commandErrorToast)

        viewModel.dismissCommandErrorToast()

        assertNull(viewModel.uiState.value.commandErrorToast)
    }

    @Test
    fun `gh3220 command executes via itlvc bridge`() = runTest(dispatcher) {
        val (viewModel, connectionManager, devicesFlow) = createViewModel(chip = "gh3220")
        connectMaster(devicesFlow)

        coEvery { connectionManager.sendGh3220Command(any(), any(), any()) } returns
            Result.success(byteArrayOf(0x00))

        viewModel.executeCommand("GH3220_CONN_STATUS", ByteArray(0))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            connectionManager.sendGh3220Command(any(), eq(0x1A), match { it.isEmpty() })
        }
        val executionState = viewModel.uiState.value.commandExecutionStates["GH3220_CONN_STATUS"]
        assertNotNull(executionState)
        assertEquals(false, executionState?.isExecuting)
        assertTrue(executionState?.result?.contentEquals(byteArrayOf(0x00)) == true)
        assertNull(viewModel.uiState.value.commandErrorToast)
    }

    @Test
    fun `gh3220 command failure backfills error state`() = runTest(dispatcher) {
        val (viewModel, connectionManager, devicesFlow) = createViewModel(chip = "gh3220")
        connectMaster(devicesFlow)

        coEvery { connectionManager.sendGh3220Command(any(), any(), any()) } returns
            Result.failure(ProtocolError.Timeout)

        viewModel.executeCommand("GH3220_CONN_STATUS", ByteArray(0))
        advanceUntilIdle()

        val executionState = viewModel.uiState.value.commandExecutionStates["GH3220_CONN_STATUS"]
        assertNotNull(executionState)
        assertEquals(false, executionState?.isExecuting)
        assertTrue(executionState?.error?.contains("超时") == true)
        assertNotNull(viewModel.uiState.value.commandErrorToast)
    }

    @Test
    fun `gh3036 chip still uses rpc sendCommand path`() = runTest(dispatcher) {
        val (viewModel, connectionManager, devicesFlow) = createViewModel(chip = "gh3036")
        connectMaster(devicesFlow)

        coEvery { connectionManager.sendCommand(any(), eq(KEY_F_GET_MODE), any()) } returns
            Result.success(byteArrayOf(0x01))

        viewModel.executeCommand(KEY_F_GET_MODE, byteArrayOf(0x01))
        advanceUntilIdle()

        coVerify(exactly = 1) { connectionManager.sendCommand(any(), eq(KEY_F_GET_MODE), any()) }
        coVerify(exactly = 0) { connectionManager.sendGh3220Command(any(), any(), any()) }
    }

    @Test
    fun `unknown gh3220 key fails gracefully`() = runTest(dispatcher) {
        val (viewModel, connectionManager, devicesFlow) = createViewModel(chip = "gh3220")
        connectMaster(devicesFlow)

        viewModel.executeCommand("GH3220_NOT_A_COMMAND", ByteArray(0))
        advanceUntilIdle()

        val executionState = viewModel.uiState.value.commandExecutionStates["GH3220_NOT_A_COMMAND"]
        assertNotNull(executionState)
        assertEquals(false, executionState?.isExecuting)
        assertTrue(executionState?.error?.contains("Unknown GH3220 command") == true)

        coVerify(exactly = 0) { connectionManager.sendGh3220Command(any(), any(), any()) }
        coVerify(exactly = 0) { connectionManager.sendCommand(any(), any(), any()) }
    }

    @Test
    fun `setWorkMode pass through sends real gh3036 work mode command`() = runTest(dispatcher) {
        val (viewModel, connectionManager, devicesFlow) = createViewModel(chip = "gh3036")
        connectMaster(devicesFlow)

        coEvery { connectionManager.sendCommand(any(), eq(KEY_GH_SET_WORK_MODE_CMD), any()) } returns
            Result.success(ByteArray(0))

        viewModel.setWorkMode(WorkMode.PASS_THROUGH)
        advanceUntilIdle()

        coVerify(timeout = 5_000, exactly = 1) {
            connectionManager.sendCommand(eq("AA:BB"), eq(KEY_GH_SET_WORK_MODE_CMD), match {
                it.contentEquals(byteArrayOf(0x05))
            })
        }
        coVerify(exactly = 0) { connectionManager.sendGh3220Command(any(), any(), any()) }
        assertEquals(WorkMode.PASS_THROUGH, viewModel.uiState.value.currentWorkMode)
    }

    @Test
    fun `setWorkMode pass through sends gh3220 0x10 with full function mask`() = runTest(dispatcher) {
        val (viewModel, connectionManager, devicesFlow) = createViewModel(chip = "gh3220")
        connectMaster(devicesFlow)

        coEvery { connectionManager.sendGh3220Command(any(), any(), any()) } returns
            Result.success(byteArrayOf(0x00))

        viewModel.setWorkMode(WorkMode.PASS_THROUGH)
        advanceUntilIdle()

        coVerify(timeout = 5_000, exactly = 1) {
            connectionManager.sendGh3220Command(
                eq("AA:BB"),
                eq(0x10),
                match { it.contentEquals(byteArrayOf(0x05, 0xFF.toByte(), 0xFF.toByte(), 0x0F, 0x00)) }
            )
        }
        coVerify(exactly = 0) { connectionManager.sendCommand(any(), any(), any()) }
        assertNull(viewModel.uiState.value.commandErrorToast)
    }

    @Test
    fun `setWorkMode mcu online resets gh3220 chip via 0x17 first`() = runTest(dispatcher) {
        val (viewModel, connectionManager, devicesFlow) = createViewModel(chip = "gh3220")
        connectMaster(devicesFlow)

        coEvery { connectionManager.sendGh3220Command(any(), any(), any()) } returns
            Result.success(ByteArray(0))

        viewModel.setWorkMode(WorkMode.MCU_ONLINE)
        advanceUntilIdle()

        coVerify(timeout = 5_000, exactly = 1) {
            connectionManager.sendGh3220Command(
                eq("AA:BB"), eq(0x17), match { it.contentEquals(byteArrayOf(0x5A)) }
            )
        }
        // 配置下载前不应直接下发 0x10
        coVerify(exactly = 0) {
            connectionManager.sendGh3220Command(eq("AA:BB"), eq(0x10), any())
        }
    }

    @Test
    fun `setWorkMode mcu online resets gh3036 chip via chip ctrl`() = runTest(dispatcher) {
        val (viewModel, connectionManager, devicesFlow) = createViewModel(chip = "gh3036")
        connectMaster(devicesFlow)

        coEvery { connectionManager.sendCommand(any(), eq(KEY_GH3X_CHIP_CTRL), any()) } returns
            Result.success(ByteArray(0))

        viewModel.setWorkMode(WorkMode.MCU_ONLINE)
        advanceUntilIdle()

        coVerify(timeout = 5_000, exactly = 1) {
            connectionManager.sendCommand(eq("AA:BB"), eq(KEY_GH3X_CHIP_CTRL), match {
                it.contentEquals(byteArrayOf(0x5A))
            })
        }
    }

    @Test
    fun `setWorkMode without master device shows toast and sends nothing`() = runTest(dispatcher) {
        val (viewModel, connectionManager, devicesFlow) = createViewModel(chip = "gh3220")

        viewModel.setWorkMode(WorkMode.PASS_THROUGH)
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.commandErrorToast)
        coVerify(exactly = 0) { connectionManager.sendGh3220Command(any(), any(), any()) }
        coVerify(exactly = 0) { connectionManager.sendCommand(any(), any(), any()) }
    }
}
