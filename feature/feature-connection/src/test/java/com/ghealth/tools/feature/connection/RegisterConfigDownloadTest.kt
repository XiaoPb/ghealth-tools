package com.ghealth.tools.feature.connection

import com.ghealth.tools.ble.connection.BatteryStatus
import com.ghealth.tools.ble.connection.BleConnectionManager
import com.ghealth.tools.ble.connection.ConnectedDevice
import com.ghealth.tools.ble.connection.ConnectionError
import com.ghealth.tools.ble.connection.DeviceRole
import com.ghealth.tools.ble.connection.FirmwareVersionHolder
import com.ghealth.tools.ble.connection.FirmwareVersionState
import com.ghealth.tools.ble.protocol.rpccore.ParseResult
import com.ghealth.tools.ble.scanner.BleScanner
import com.ghealth.tools.core.datastore.BlePreferences
import com.ghealth.tools.core.datastore.UserPreferences
import com.ghealth.tools.core.model.ConnectionState
import com.ghealth.tools.core.model.DeviceType
import com.ghealth.tools.core.network.ConfigPathProvider
import com.ghealth.tools.core.storage.RecordingManager
import com.ghealth.tools.feature.factory.model.RegEntry
import com.ghealth.tools.feature.factory.model.RegisterConfig
import com.ghealth.tools.feature.factory.parser.RegisterConfigParser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterConfigDownloadTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        chip: String = "gh3036",
        parser: RegisterConfigParser = mockk<RegisterConfigParser>(relaxed = true),
    ): Triple<ConnectionViewModel, BleConnectionManager, MutableStateFlow<Map<String, ConnectedDevice>>> {
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
        every { blePreferences.selectedChip } returns flowOf(chip)
        val userPreferences = mockk<UserPreferences>(relaxed = true)
        val configPathProvider = mockk<ConfigPathProvider>(relaxed = true)

        val viewModel = ConnectionViewModel(
            bleScanner = scanner,
            connectionManager = connectionManager,
            firmwareVersionHolder = firmwareVersionHolder,
            recordingManager = recordingManager,
            blePreferences = blePreferences,
            userPreferences = userPreferences,
            registerConfigParser = parser,
            baseDir = File(System.getProperty("java.io.tmpdir")),
            configPathProvider = configPathProvider
        )
        return Triple(viewModel, connectionManager, devicesFlow)
    }

    private fun TestScope.connectMaster(devicesFlow: MutableStateFlow<Map<String, ConnectedDevice>>) {
        devicesFlow.value = mapOf(
            "AA:BB" to ConnectedDevice(
                address = "AA:BB",
                name = "GH3220 EVK",
                role = DeviceRole.MASTER,
                state = ConnectionState.CONNECTED,
                deviceType = DeviceType.GH3220,
            )
        )
        advanceUntilIdle()
    }

    /** 下载协程运行在 Dispatchers.IO（真实线程），轮询等待其回投到 Main 测试调度器。 */
    private fun TestScope.awaitDownloadSettled(viewModel: ConnectionViewModel) {
        val deadline = System.currentTimeMillis() + 5_000
        while (
            viewModel.uiState.value.registerConfigDownloadState.status == DownloadStatus.DOWNLOADING &&
            System.currentTimeMillis() < deadline
        ) {
            advanceUntilIdle()
            Thread.sleep(5)
        }
        advanceUntilIdle()
    }

    private fun writeConfig(): ConfigFileInfo {
        val file = File.createTempFile("reg", ".ini")
        file.writeText("0x33C0=0x0000\n0x33C2=0x0000\n0x33C4=0x0000\n")
        file.deleteOnExit()
        return ConfigFileInfo(
            fileName = "HR.ini",
            displayPath = file.absolutePath,
            fullPath = file,
            chipName = "gh3220",
        )
    }

    @Test
    fun `gh3220 config download routes to driver config with block stream`() = runTest(dispatcher) {
        val parser = mockk<RegisterConfigParser>(relaxed = true)
        coEvery { parser.parseByChip(any(), eq("gh3220"), any()) } returns RegisterConfig(
            listOf(
                RegEntry(0x33C0, 0x1234),
                RegEntry(0x33C2, 0x5678),
            )
        )
        val (viewModel, connectionManager, devicesFlow) = createViewModel(chip = "gh3220", parser = parser)
        connectMaster(devicesFlow)

        viewModel.selectRegisterConfigFile(writeConfig())
        coEvery { connectionManager.sendGh3220DriverConfig("AA:BB", any(), save = true, onProgress = any()) } returns Result.success(Unit)

        viewModel.executeRegisterConfigDownload()
        awaitDownloadSettled(viewModel)

        val expected = byteArrayOf(
            0x33, 0xC0.toByte(), 0x12, 0x34,
            0x33, 0xC2.toByte(), 0x56, 0x78,
        )
        coVerify(exactly = 1) {
            connectionManager.sendGh3220DriverConfig("AA:BB", expected, save = true, onProgress = any())
        }
        val state = viewModel.uiState.value.registerConfigDownloadState
        assertEquals(DownloadStatus.COMPLETED, state.status)
        assertEquals(setOf(DownloadStep.WRITE_REGS), state.completedSteps)
        assertNull(state.error)
    }
}
