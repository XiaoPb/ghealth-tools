@file:OptIn(ExperimentalApi::class, ExperimentalUuidApi::class)

package com.ghealth.tools.ble.connection

import com.ghealth.tools.ble.protocol.rpccore.GHealthExecutor
import com.ghealth.tools.ble.protocol.rpccore.ParseResult
import com.ghealth.tools.ble.protocol.rpccore.Unpackage
import com.ghealth.tools.ble.protocol.gh3036.Gh3036CommandMeta
import com.ghealth.tools.ble.protocol.gh3036.Gh3036Executor
import com.ghealth.tools.ble.protocol.gh3036.GhFuncFrame
import com.ghealth.tools.ble.protocol.gh3220.Gh3220Executor
import com.ghealth.tools.ble.protocol.gh3300.Gh3300Executor
import com.ghealth.tools.core.datastore.BlePreferences
import com.ghealth.tools.core.model.ConnectionState
import com.ghealth.tools.core.model.DeviceType
import com.ghealth.tools.core.model.TestConfig
import com.ghealth.tools.core.storage.LogManager
import com.juul.kable.Advertisement
import com.juul.kable.ExperimentalApi
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.State
import com.juul.kable.WriteType
import com.juul.kable.characteristicOf
import com.juul.kable.logs.Logging
import com.juul.kable.write
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

object BleUuids {
    @OptIn(ExperimentalUuidApi::class)
    val HEART_RATE_SERVICE_UUID: Uuid = Uuid.parse("0000180d-0000-1000-8000-00805f9b34fb")
    @OptIn(ExperimentalUuidApi::class)
    val HEART_RATE_MEASUREMENT_UUID: Uuid = Uuid.parse("00002a37-0000-1000-8000-00805f9b34fb")
}

sealed class ConnectionError {
    object ServiceNotFound : ConnectionError()
    object WriteCharacteristicNotFound : ConnectionError()
    object NotifyCharacteristicNotFound : ConnectionError()
    object HeartRateServiceNotFound : ConnectionError()
    data class ConnectionFailed(
        val errorMessage: String,
        val disconnectStatus: String? = null
    ) : ConnectionError()

    fun getMessage(): String = when (this) {
        is ServiceNotFound -> "服务发现失败，未发现任何 BLE 服务"
        is WriteCharacteristicNotFound -> "未找到写入特征UUID"
        is NotifyCharacteristicNotFound -> "未找到通知特征UUID"
        is HeartRateServiceNotFound -> "未找到心率服务"
        is ConnectionFailed -> buildString {
            append("连接失败: $errorMessage")
            if (!disconnectStatus.isNullOrBlank()) {
                append(" [断链标志: $disconnectStatus]")
            }
        }
    }
}

sealed class ConnectionConstraint {
    object MasterAlreadyConnected : ConnectionConstraint()
    object SlaveAlreadyConnected : ConnectionConstraint()
    object CompareLimitReached : ConnectionConstraint()
    data class Success(val canConnect: Boolean) : ConnectionConstraint()

    fun getMessage(): String = when (this) {
        is MasterAlreadyConnected -> "主设备已连接，请先断开现有连接"
        is SlaveAlreadyConnected -> "从设备已连接，请先断开现有连接"
        is CompareLimitReached -> "对比设备已达最大数量（5个）"
        is Success -> ""
    }
}

sealed class DfuConnectionState {
    object Idle : DfuConnectionState()
    data class Reconnecting(val oldAddress: String, val newAddress: String) : DfuConnectionState()
    data class Reconnected(val newAddress: String, val channel: BleRawChannel) : DfuConnectionState()
    data class Failed(val error: String) : DfuConnectionState()
}

data class GHealthPeripheral(
    val peripheral: Peripheral,
    val role: DeviceRole,
    val executor: GHealthExecutor?,
    val deviceType: DeviceType = DeviceType.GH3036
) {
    val address: String get() = peripheral.identifier.toString()
    val name: String? get() = peripheral.name
}

@Singleton
class BleConnectionManager @Inject constructor(
    private val blePreferences: BlePreferences,
    private val logManager: LogManager,
    private val scope: CoroutineScope,
    private val bleScanner: com.ghealth.tools.ble.scanner.BleScanner
) {

    companion object {
        private const val MAX_CONNECT_RETRIES = 3
        private const val CONNECT_RETRY_DELAY_MS = 500L
        private const val DISCONNECT_TIMEOUT_MS = 5_000L
        private const val RECONNECT_SETTLE_DELAY_MS = 500L
        // BLE 特征值属性位掩码（与 BluetoothGattCharacteristic 定义一致）
        private const val PROP_WRITE = 0x08
        private const val PROP_WRITE_NO_RESPONSE = 0x04
        private const val PROP_NOTIFY = 0x10
        private const val PROP_INDICATE = 0x20
    }
    private val _devices = MutableStateFlow<Map<String, ConnectedDevice>>(emptyMap())

    // 缓存每个设备写入特征值所支持的 WriteType，自动适配 write / writeWithoutResponse
    private val writeTypeByAddress = Collections.synchronizedMap(mutableMapOf<String, WriteType>())

    @OptIn(ExperimentalUuidApi::class)
    private val writeServiceUuidByAddress = Collections.synchronizedMap(mutableMapOf<String, Uuid>())

    @OptIn(ExperimentalUuidApi::class)
    private fun clearWriteServiceUuid(address: String) {
        writeServiceUuidByAddress.remove(address)
    }

    val devices: StateFlow<Map<String, ConnectedDevice>> = _devices.asStateFlow()

    private val _dataFlow = MutableSharedFlow<Pair<String, ParseResult>>(
        replay = 0,
        extraBufferCapacity = 64
    )
    val dataFlow: SharedFlow<Pair<String, ParseResult>> = _dataFlow.asSharedFlow()

    private val _ghFrameFlow = MutableSharedFlow<Pair<String, GhFuncFrame>>(
        replay = 0,
        extraBufferCapacity = 64
    )
    val ghFrameFlow: SharedFlow<Pair<String, GhFuncFrame>> = _ghFrameFlow.asSharedFlow()

    private val _recordingStoppedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val recordingStoppedEvents: SharedFlow<Unit> = _recordingStoppedEvents.asSharedFlow()

    private val _connectionErrors = MutableSharedFlow<Pair<String, ConnectionError>>()
    val connectionErrors: SharedFlow<Pair<String, ConnectionError>> = _connectionErrors.asSharedFlow()

    private val _heartRateResults = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val heartRateResults: StateFlow<Map<Int, Int>> = _heartRateResults.asStateFlow()

    private val _batteryStatus = MutableStateFlow<Map<String, BatteryStatus>>(emptyMap())
    /** 按 MAC 地址索引的电池状态；仅当设备暴露 Battery Service (0x180F) 时出现。 */
    val batteryStatus: StateFlow<Map<String, BatteryStatus>> = _batteryStatus.asStateFlow()

    private val _testConfig = MutableStateFlow<TestConfig?>(null)
    val testConfig: StateFlow<TestConfig?> = _testConfig.asStateFlow()

    fun setTestConfig(config: TestConfig) {
        _testConfig.value = config
    }

    fun notifyRecordingStopped() {
        _recordingStoppedEvents.tryEmit(Unit)
    }

    fun resetFrameDecoders() {
        peripherals.values.forEach { it.executor?.resetFrameDecoder() }
    }

    private val peripherals = ConcurrentHashMap<String, GHealthPeripheral>()
    private val userDisconnectingAddresses = Collections.synchronizedSet(mutableSetOf<String>())
    private val suppressDisconnectErrorAddresses = Collections.synchronizedSet(mutableSetOf<String>())
    private val connectJobs = ConcurrentHashMap<String, Job>()
    private val connectingPeripherals = ConcurrentHashMap<String, Peripheral>()
    private val connectSingleFlight = ConnectSingleFlight()
    private val disconnectCoordinator = DisconnectCoordinator(disconnectTimeoutMs = DISCONNECT_TIMEOUT_MS)

    fun getDeviceState(address: String): ConnectionState {
        return _devices.value[address]?.state ?: ConnectionState.DISCONNECTED
    }

    fun getPeripheral(address: String): Peripheral? = peripherals[address]?.peripheral

    fun getRawChannel(address: String): BleRawChannel? {
        return peripherals[address]?.let { KableRawChannel(it.peripheral) }
    }

    fun checkConnectionConstraint(role: DeviceRole): ConnectionConstraint {
        return when (role) {
            DeviceRole.MASTER -> {
                val hasMaster = _devices.value.values.any {
                    it.role == DeviceRole.MASTER && it.state == ConnectionState.CONNECTED
                }
                if (hasMaster) ConnectionConstraint.MasterAlreadyConnected
                else ConnectionConstraint.Success(true)
            }
            DeviceRole.SLAVE -> {
                val hasSlave = _devices.value.values.any {
                    it.role == DeviceRole.SLAVE && it.state == ConnectionState.CONNECTED
                }
                if (hasSlave) ConnectionConstraint.SlaveAlreadyConnected
                else ConnectionConstraint.Success(true)
            }
            DeviceRole.COMPARE -> {
                val compareCount = _devices.value.values.count {
                    it.role == DeviceRole.COMPARE && it.state == ConnectionState.CONNECTED
                }
                if (compareCount >= 5) ConnectionConstraint.CompareLimitReached
                else ConnectionConstraint.Success(true)
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun connect(address: String, name: String?, role: DeviceRole) {
        if (connectSingleFlight.isActive(address)) {
            Timber.w("Connect skipped for $address: already connecting/connected")
            return
        }
        val constraint = checkConnectionConstraint(role)
        if (constraint !is ConnectionConstraint.Success) {
            Timber.w("Connection constraint violated: $constraint")
            emitConnectionError(address, ConnectionError.ConnectionFailed(constraint.getMessage()))
            return
        }

        val advertisement = bleScanner.getCachedAdvertisement(address)
        if (advertisement != null) {
            val job = scope.launch {
                try {
                    val peripheral = Peripheral(advertisement) {
                        logging { level = Logging.Level.Events }
                        onServicesDiscovered {
                            requestMtu(247)
                        }
                    }
                    connect(peripheral, role)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to create peripheral for $address")
                    emitConnectionError(address, ConnectionError.ConnectionFailed("创建设备连接失败: ${e.message}"))
                }
            }
            connectJobs[address] = job
            job.invokeOnCompletion { connectJobs.remove(address, job) }
        } else {
            Timber.w("No advertisement cached for address: $address")
            emitConnectionError(address, ConnectionError.ConnectionFailed("Device not found in scan results"))
        }
    }

    fun autoConnect(address: String, name: String?, suppressError: Boolean = false) {
        val targetAddress = address.uppercase()
        if (connectSingleFlight.isActive(targetAddress)) {
            Timber.w("Auto-connect skipped for $targetAddress: already connecting/connected")
            return
        }
        Timber.d("Auto-connect: scanning for $targetAddress")
        val job = scope.launch {
            val advertisement = withTimeoutOrNull(15_000L) {
                Scanner {
                    logging { level = Logging.Level.Warnings }
                }.advertisements.first { it.address.equals(targetAddress, ignoreCase = true) }
            }
            if (advertisement == null) {
                Timber.d("Auto-connect: device $targetAddress not found within timeout")
                return@launch
            }
            Timber.d("Auto-connect: found $targetAddress, connecting...")
            try {
                val peripheral = Peripheral(advertisement) {
                    logging { level = Logging.Level.Events }
                    onServicesDiscovered { requestMtu(247) }
                }
                connect(peripheral, DeviceRole.MASTER, suppressError = suppressError)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Auto-connect: failed to connect to $targetAddress")
            }
        }
        connectJobs[targetAddress] = job
        job.invokeOnCompletion { connectJobs.remove(targetAddress, job) }
    }

    /**
     * 后台主动断连并重连指定设备(用于 OTA 退出后恢复普通 GHealth 连接)。
     * - 断连阶段标记为用户主动断开,抑制断连错误弹窗。
     * - 重连阶段复用 autoConnect(suppressError=true),扫描失败与连接尝试失败均仅记日志、不弹窗。
     * 整个过程在 BleConnectionManager 自身的 Singleton 协程作用域中执行,不依赖调用方作用域。
     */
    fun reconnectInBackground(address: String, name: String?) {
        scope.launch {
            Timber.i("reconnectInBackground: 断连 $address")
            try {
                disconnect(address)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "reconnectInBackground: 断连异常 $address")
            }
            delay(RECONNECT_SETTLE_DELAY_MS)
            Timber.i("reconnectInBackground: 重连 $address")
            autoConnect(address, name, suppressError = true)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun connect(peripheral: Peripheral, role: DeviceRole, suppressError: Boolean = false) {
        val address = peripheral.identifier.toString()
        if (!connectSingleFlight.tryAcquire(address, peripheral)) {
            Timber.w("Connect skipped for $address: already connecting/connected")
            return
        }
        // 清除上一轮断连遗留的标记，避免误伤本轮连接的意外断链判定。
        userDisconnectingAddresses.remove(address)
        suppressDisconnectErrorAddresses.remove(address)
        // 记录正在连接中的 peripheral：disconnectAll 取消 CONNECTING 设备时需要 close() 中断 Kable 连接动作。
        connectingPeripherals[address] = peripheral

        val constraint = checkConnectionConstraint(role)
        if (constraint !is ConnectionConstraint.Success) {
            Timber.w("Connection constraint violated: $constraint")
            if (!suppressError) {
                emitConnectionError(address, ConnectionError.ConnectionFailed(constraint.getMessage()))
            }
            connectingPeripherals.remove(address)
            connectSingleFlight.release(address, peripheral)
            return
        }

        val device = ConnectedDevice(
            address = address,
            name = peripheral.name,
            role = role,
            state = ConnectionState.CONNECTING
        )
        _devices.value = _devices.value + (address to device)

        Timber.d("Connecting to $address as $role")

        var lastException: Exception? = null
        var disconnectStatus: String? = null

        for (attempt in 1..MAX_CONNECT_RETRIES) {
            try {
                Timber.d("Connection attempt $attempt/$MAX_CONNECT_RETRIES for $address")
                peripheral.connect()
                lastException = null
                disconnectStatus = null
                break
            } catch (e: CancellationException) {
                connectingPeripherals.remove(address)
                connectSingleFlight.release(address, peripheral)
                throw e
            } catch (e: Exception) {
                lastException = e
                Timber.w(e, "Connection attempt $attempt/$MAX_CONNECT_RETRIES failed for $address")
                if (attempt < MAX_CONNECT_RETRIES) {
                    kotlinx.coroutines.delay(CONNECT_RETRY_DELAY_MS)
                }
            }
        }

        if (lastException != null) {
            Timber.e(lastException, "All $MAX_CONNECT_RETRIES connection attempts failed for $address")
            if (!suppressError) {
                emitConnectionError(
                    address,
                    ConnectionError.ConnectionFailed(
                        errorMessage = lastException.message ?: "Unknown error",
                        disconnectStatus = disconnectStatus
                    )
                )
            }
            connectingPeripherals.remove(address)
            updateDeviceState(address, ConnectionState.DISCONNECTED)
            connectSingleFlight.release(address, peripheral)
            return
        }

        val (executor, deviceType) = when (role) {
            DeviceRole.MASTER -> createExecutor(address)
            DeviceRole.SLAVE -> createExecutor(address)
            DeviceRole.COMPARE -> null to DeviceType.GH3036
        }

        val gHealthPeripheral = GHealthPeripheral(
            peripheral = peripheral,
            role = role,
            executor = executor,
            deviceType = deviceType
        )
        peripherals[address] = gHealthPeripheral
        connectingPeripherals.remove(address)
        val currentDevice = _devices.value[address]
        if (currentDevice != null) {
            _devices.value = _devices.value + (address to currentDevice.copy(deviceType = deviceType))
        }

        peripheral.state.onEach { state ->
            when (state) {
                is State.Connected -> {
                    // 先完成服务发现与 notify 订阅，再标记 CONNECTED。
                    // CONNECTED 即代表命令通道就绪，避免上层（如 SettingsViewModel）立即下发命令时
                    // 因 notify 未订阅导致响应丢失/超时（表现为固件版本读到 "no_ver"）。
                    validateServices(peripheral, address, role)
                    // validateServices 失败时会调用 disconnectAfterFailure 将状态置为 DISCONNECTING，
                    // 仅当仍处于 CONNECTING（即校验成功）时才升级为 CONNECTED。
                    if (_devices.value[address]?.state == ConnectionState.CONNECTING) {
                        updateDeviceState(address, ConnectionState.CONNECTED)
                        if (role == DeviceRole.MASTER) {
                            scope.launch {
                                blePreferences.setLastDeviceAddress(address)
                                blePreferences.setLastDeviceName(peripheral.name ?: "")
                            }
                        }
                        // 电池服务与角色无关；CONNECTED 后 fire-and-forget 读取/订阅，不阻塞命令通道。
                        scope.launch { readBatteryService(peripheral, address) }
                    }
                }
                is State.Disconnected -> {
                    val status = state.status
                    val userInitiated = userDisconnectingAddresses.remove(address) ||
                            suppressDisconnectErrorAddresses.remove(address)
                    if (userInitiated) {
                        Timber.i("State disconnected received for user disconnect: $address, status=$status")
                    } else {
                        if (status != null) {
                            Timber.w("Device $address disconnected with status: $status")
                        }
                        emitConnectionError(
                            address,
                            ConnectionError.ConnectionFailed(
                                errorMessage = "设备断开连接",
                                disconnectStatus = status?.toString()
                            )
                        )
                    }
                    onDeviceDisconnected(address, peripheral)
                }
                else -> {}
            }
        }.launchIn(scope)
    }

    fun setPrimaryCompareDevice(address: String) {
        val device = _devices.value[address] ?: return
        if (device.role != DeviceRole.COMPARE) return

        _devices.value = _devices.value.mapValues { (key, value) ->
            if (value.role == DeviceRole.COMPARE) {
                value.copy(isPrimaryCompare = key == address)
            } else {
                value
            }
        }
        Timber.d("Set primary compare device: $address")
    }

    private fun getCompareDeviceIndex(address: String): Int {
        val compareDevices = _devices.value.values
            .filter { it.role == DeviceRole.COMPARE && it.state == ConnectionState.CONNECTED }
            .sortedByDescending { it.isPrimaryCompare }
        return compareDevices.indexOfFirst { it.address == address }
    }

    private fun onHeartRateReceived(address: String, data: ByteArray) {
        if (data.size < 2) return
        val flags = data[0].toInt() and 0xFF
        val heartRate = if (flags and 0x01 == 0) {
            data[1].toInt() and 0xFF
        } else {
            if (data.size < 3) return
            ((data[2].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        }

        val index = getCompareDeviceIndex(address)
        if (index in 0..4) {
            _heartRateResults.value = _heartRateResults.value + (index to heartRate)
            Timber.d("Heart rate from $address (index $index): $heartRate bpm")
        }
    }

    /**
     * 读取并订阅 Battery Service (0x180F)。
     * - 0x2A19 Battery Level：先读一次，若支持 notify/indicate 则订阅以持续刷新。
     * - 0x2A1E Battery Level Status：若存在且支持 notify/indicate 则订阅充放电状态。
     * 无 Battery Level 特征时直接返回（卡片不显示电池）。
     * fire-and-forget：不阻塞 CONNECTED 状态迁移，失败仅记日志。
     */
    @OptIn(ExperimentalUuidApi::class)
    private suspend fun readBatteryService(peripheral: Peripheral, address: String) {
        val services = try {
            peripheral.services.first() ?: return
        } catch (e: Exception) {
            Timber.w(e, "Battery: service discovery not available for $address")
            return
        }

        val refs = services.flatMap { service ->
            service.characteristics.map { char ->
                DiscoveredCharacteristicRef(service.serviceUuid, char.characteristicUuid)
            }
        }
        val match = BatteryServiceMatcher.match(refs)
        val levelServiceUuid = match.batteryLevelServiceUuid ?: return

        val levelChar = characteristicOf(
            service = levelServiceUuid,
            characteristic = BatteryServiceUuids.BATTERY_LEVEL_UUID
        )

        // 初次读取电量
        try {
            val data = peripheral.read(levelChar)
            BatteryLevelStatusParser.parseLevel(data)?.let { level ->
                updateBatteryStatus(address) { existing ->
                    (existing ?: BatteryStatus()).copy(level = level)
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Battery: read level failed for $address")
        }

        // 订阅 0x2A19 通知（若支持）
        val levelCharacteristic = services
            .flatMap { it.characteristics }
            .firstOrNull { it.characteristicUuid == BatteryServiceUuids.BATTERY_LEVEL_UUID }
        if (levelCharacteristic != null && supportsNotify(levelCharacteristic.properties.value)) {
            try {
                peripheral.observe(levelChar)
                    .onEach { data ->
                        BatteryLevelStatusParser.parseLevel(data)?.let { level ->
                            updateBatteryStatus(address) { existing ->
                                (existing ?: BatteryStatus()).copy(level = level)
                            }
                        }
                    }
                    .catch { e -> Timber.e(e, "Battery level observe error for $address") }
                    .onCompletion { cause ->
                        if (cause != null) {
                            Timber.w("Battery level observation ended with cause: $cause")
                        }
                    }
                    .launchIn(scope)
            } catch (e: Exception) {
                Timber.w(e, "Battery: observe level failed for $address")
            }
        }

        // 订阅 0x2A1E 充放电状态通知（若存在且支持）
        match.batteryLevelStatusServiceUuid?.let { statusServiceUuid ->
            val statusChar = characteristicOf(
                service = statusServiceUuid,
                characteristic = BatteryServiceUuids.BATTERY_LEVEL_STATUS_UUID
            )
            val statusCharacteristic = services
                .flatMap { it.characteristics }
                .firstOrNull { it.characteristicUuid == BatteryServiceUuids.BATTERY_LEVEL_STATUS_UUID }
            if (statusCharacteristic != null && supportsNotify(statusCharacteristic.properties.value)) {
                try {
                    peripheral.observe(statusChar)
                        .onEach { data ->
                            val state = BatteryLevelStatusParser.parseChargeState(data)
                            updateBatteryStatus(address) { existing ->
                                (existing ?: BatteryStatus()).copy(chargeState = state)
                            }
                        }
                        .catch { e -> Timber.e(e, "Battery status observe error for $address") }
                        .onCompletion { cause ->
                            if (cause != null) {
                                Timber.w("Battery status observation ended with cause: $cause")
                            }
                        }
                        .launchIn(scope)
                } catch (e: Exception) {
                    Timber.w(e, "Battery: observe level status failed for $address")
                }
            }
        }

        Timber.i("Battery service enabled for $address (levelService=$levelServiceUuid, statusService=${match.batteryLevelStatusServiceUuid})")
    }

    private fun updateBatteryStatus(
        address: String,
        transform: (BatteryStatus?) -> BatteryStatus,
    ) {
        _batteryStatus.update { currentMap ->
            currentMap + (address to transform(currentMap[address]))
        }
    }

    private fun supportsNotify(propertiesValue: Int): Boolean {
        return (propertiesValue and (PROP_NOTIFY or PROP_INDICATE)) != 0
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun validateServices(
        peripheral: Peripheral,
        address: String,
        role: DeviceRole
    ) {
        val services = peripheral.services.first() ?: run {
            Timber.e("Service discovery failed for $address")
            emitConnectionError(address, ConnectionError.ServiceNotFound)
            disconnectAfterFailure(address)
            return
        }

        Timber.d("=== Discovered services for $address ===")
        services.forEach { service ->
            Timber.d("  Service: ${service.serviceUuid}")
            service.characteristics.forEach { char ->
                Timber.d("    Characteristic: ${char.characteristicUuid} [properties=${char.properties}]")
            }
        }
        Timber.d("=== End of services for $address ===")

        when (role) {
            DeviceRole.MASTER, DeviceRole.SLAVE -> {
                val writeUuidStr = blePreferences.writeCharUuid.first()
                val writeUuid = Uuid.parse(writeUuidStr)
                val notifyUuidStr = blePreferences.notifyCharUuid.first()
                val notifyUuid = Uuid.parse(notifyUuidStr)

                // 服务 UUID 不再参与匹配：把所有已发现服务的特征拍平后按特征 UUID 查找。
                val refs = services.flatMap { service ->
                    service.characteristics.map { char ->
                        DiscoveredCharacteristicRef(
                            serviceUuid = service.serviceUuid,
                            characteristicUuid = char.characteristicUuid,
                        )
                    }
                }

                when (val result = CharacteristicMatcher.match(refs, writeUuid, notifyUuid)) {
                    is CharacteristicMatcher.Result.WriteNotFound -> {
                        Timber.e("Write characteristic not found in any service: $writeUuidStr")
                        emitConnectionError(address, ConnectionError.WriteCharacteristicNotFound)
                        disconnectAfterFailure(address)
                        return
                    }
                    is CharacteristicMatcher.Result.NotifyNotFound -> {
                        Timber.e("Notify characteristic not found in any service: $notifyUuidStr")
                        emitConnectionError(address, ConnectionError.NotifyCharacteristicNotFound)
                        disconnectAfterFailure(address)
                        return
                    }
                    is CharacteristicMatcher.Result.Matched -> {
                        val writeService = services.first { it.serviceUuid == result.writeServiceUuid }
                        val writeCharacteristic = writeService.characteristics
                            .first { it.characteristicUuid == writeUuid }

                        // 根据写入特征值属性自动选择 WriteType：
                        // 优先 WithResponse（更可靠），回退 WithoutResponse（write no response）
                        val writeProps = writeCharacteristic.properties.value
                        val supportsWriteWithResponse = (writeProps and PROP_WRITE) != 0
                        val supportsWriteWithoutResponse = (writeProps and PROP_WRITE_NO_RESPONSE) != 0
                        if (!supportsWriteWithResponse && !supportsWriteWithoutResponse) {
                            Timber.e("Write characteristic $writeUuidStr has no write property: props=0x${writeProps.toString(16)}")
                            emitConnectionError(address, ConnectionError.WriteCharacteristicNotFound)
                            disconnectAfterFailure(address)
                            return
                        }
                        val writeType = if (supportsWriteWithResponse) WriteType.WithResponse else WriteType.WithoutResponse
                        writeTypeByAddress[address] = writeType
                        // 记录写入特征实际所属服务 UUID，供 writeToDevice 构建 characteristicOf 使用
                        // （配置的 serviceUuid 可能与设备实际服务不一致）。
                        writeServiceUuidByAddress[address] = result.writeServiceUuid
                        Timber.i("Write characteristic $writeUuidStr in service ${result.writeServiceUuid} props=0x${writeProps.toString(16)} using writeType=$writeType")

                        val notifyService = services.first { it.serviceUuid == result.notifyServiceUuid }
                        val notifyCharacteristic = notifyService.characteristics
                            .first { it.characteristicUuid == notifyUuid }
                        Timber.d("Notify characteristic properties: ${notifyCharacteristic.properties}")

                        val notifyChar = characteristicOf(
                            service = result.notifyServiceUuid,
                            characteristic = notifyUuid
                        )

                        try {
                            peripheral.observe(notifyChar)
                                .onEach { data ->
                                    Timber.d("Notify received ${data.size} bytes from $address")
                                    onDataReceived(address, data)
                                }
                                .onCompletion { cause ->
                                    if (cause != null) {
                                        Timber.w("Notify observation completed with error: $cause")
                                    } else {
                                        Timber.d("Notify observation completed for $address")
                                    }
                                }
                                .launchIn(scope)
                            Timber.i("Subscribed to notify characteristic $notifyUuidStr (service ${result.notifyServiceUuid}) for $address")
                        } catch (e: NoSuchElementException) {
                            Timber.e(e, "Notify characteristic does not support notify/indicate: $notifyUuidStr")
                            emitConnectionError(address, ConnectionError.NotifyCharacteristicNotFound)
                            disconnectAfterFailure(address)
                            return
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to observe notify characteristic: $notifyUuidStr")
                            emitConnectionError(address, ConnectionError.NotifyCharacteristicNotFound)
                            disconnectAfterFailure(address)
                            return
                        }

                        Timber.i("Device $address validated (write service=${result.writeServiceUuid}, notify service=${result.notifyServiceUuid})")
                    }
                }
            }
            DeviceRole.COMPARE -> {
                val heartRateService = services.find { it.serviceUuid == BleUuids.HEART_RATE_SERVICE_UUID }

                if (heartRateService == null) {
                    Timber.e("Heart rate service not found")
                    emitConnectionError(address, ConnectionError.HeartRateServiceNotFound)
                    disconnectAfterFailure(address)
                    return
                }

                val heartRateMeasurement = heartRateService.characteristics.find {
                    it.characteristicUuid == BleUuids.HEART_RATE_MEASUREMENT_UUID
                }

                if (heartRateMeasurement == null) {
                    Timber.e("Heart rate measurement characteristic not found")
                    emitConnectionError(address, ConnectionError.HeartRateServiceNotFound)
                    disconnectAfterFailure(address)
                    return
                }

                val heartRateChar = characteristicOf(
                    service = BleUuids.HEART_RATE_SERVICE_UUID,
                    characteristic = BleUuids.HEART_RATE_MEASUREMENT_UUID
                )

                peripheral.observe(heartRateChar)
                    .onEach { data ->
                        onHeartRateReceived(address, data)
                    }
                    .launchIn(scope)

                Timber.i("Device $address validated with heart rate service")
            }
        }
    }

    private suspend fun onDataReceived(address: String, data: ByteArray) {
        Timber.v("Received ${data.size} bytes from $address")
        logManager.logBle(address, "RX", data)
        val executor = peripherals[address]?.executor ?: return

        try {
            val results = executor.process(data)
            for (result in results) {
                result.onSuccess { parsed ->
                    Timber.d("Parsed frame from $address: key=${parsed.key}, param=${parsed.param.size} bytes, secure=${parsed.isSecure}")
                    _dataFlow.emit(address to parsed)
                }
                result.onFailure { error ->
                    Timber.w("Parse error from $address: ${error.message}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error processing BLE data from $address (${data.size} bytes)")
        }
    }

    suspend fun disconnect(address: String) {
        disconnectInternal(address, userInitiated = true)
    }

    fun disconnectAll() {
        // 同时覆盖：已连接/断连中的设备（_devices）与正在连接/扫描中的任务（connectJobs）。
        val addresses = (_devices.value.keys + connectJobs.keys).toSet().toList()
        Timber.i("Disconnect all requested for ${addresses.size} device(s): $addresses")
        addresses.forEach { address ->
            scope.launch {
                if (peripherals.containsKey(address)) {
                    disconnect(address)
                } else {
                    // 仍在 CONNECTING（尚未写入 peripherals）的设备：取消连接任务，并 close() 底层
                    // Kable peripheral 以中断其连接动作（仅取消 manager 侧 job 无法停止 Kable 连接）。
                    connectJobs.remove(address)?.cancel()
                    val inFlightPeripheral = connectingPeripherals.remove(address)
                    if (inFlightPeripheral != null) {
                        try {
                            inFlightPeripheral.close()
                        } catch (e: Exception) {
                            Timber.w(e, "Error closing connecting peripheral for $address")
                        }
                        _devices.value = _devices.value - address
                        Timber.i("Cancelled connecting device: $address")
                    } else if (peripherals.containsKey(address)) {
                        // 竞态：连接在分支判断后已完成，走正常断连流程收尾。
                        disconnect(address)
                    } else {
                        _devices.value = _devices.value - address
                        Timber.i("Removed stale connecting device entry: $address")
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun sendCommand(address: String, key: String, param: ByteArray = ByteArray(0)): Result<ByteArray> {
        val executor = peripherals[address]?.executor
            ?: return Result.failure(Exception("Executor not available for $address"))
        val meta = Gh3036CommandMeta.getCommandByKey(key)
        val format = meta?.requestFormat ?: return Result.failure(Exception("Unknown command: $key"))
        val hasResponse = meta.hasResponse
        return if (hasResponse) {
            executor.call(key, format, param).map { raw ->
                val respFormat = meta.responseFormat ?: format
                Timber.d("Response raw: key=$key, len=${raw.size}, hex=${raw.toHexString()}, unpackFmt=$respFormat")
                Unpackage.unpackWithFormat(raw, respFormat).getOrThrow()
            }
        } else {
            executor.send(key, format, param).map { ByteArray(0) }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun writeToDevice(address: String, data: ByteArray) {
        val gHealthPeripheral = peripherals[address] ?: return

        val writeUuidStr = blePreferences.writeCharUuid.first()
        val writeUuid = Uuid.parse(writeUuidStr)

        // 使用 validateServices 中记录的、写入特征实际所属服务 UUID。
        // 服务 UUID 不再要求与配置一致；若无缓存（异常路径）则回退到配置值以防崩溃。
        val serviceUuid = writeServiceUuidByAddress[address]
            ?: run {
                val configured = blePreferences.serviceUuid.first()
                Timber.w("No cached write service UUID for $address, falling back to configured $configured")
                Uuid.parse(configured)
            }

        val writeChar = characteristicOf(
            service = serviceUuid,
            characteristic = writeUuid
        )

        // 使用 validateServices 中按特征值属性缓存的 WriteType，兼容 write / writeWithoutResponse
        val writeType = writeTypeByAddress[address] ?: WriteType.WithResponse
        gHealthPeripheral.peripheral.write(writeChar, data, writeType)
        logManager.logBle(address, "TX", data)
        Timber.d("Wrote ${data.size} bytes to $address (writeType=$writeType): ${data.toHexString()}")
    }

    private suspend fun createExecutor(address: String): Pair<GHealthExecutor, DeviceType> {
        val chipName = blePreferences.effectiveChip.first()
        val deviceType = DeviceType.entries.find { it.chipName == chipName } ?: DeviceType.GH3036
        val executor: GHealthExecutor = when (deviceType) {
            DeviceType.GH3300 -> Gh3300Executor()
            DeviceType.GH3220 -> com.ghealth.tools.ble.protocol.gh3220.Gh3220Executor()
            else -> Gh3036Executor()
        }
        setupExecutor(executor, address)
        return executor to deviceType
    }

    private fun setupExecutor(executor: GHealthExecutor, address: String) {
        executor.setSendFunction { data ->
            try {
                kotlinx.coroutines.runBlocking {
                    writeToDevice(address, data)
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to write to device $address")
                Result.failure(e)
            }
        }
        executor.registerFrameCallback { frame ->
            onGhFuncFrame(address, frame)
        }
        scope.launch {
            executor.registerGHandler()
        }
    }

    private fun onGhFuncFrame(address: String, frame: GhFuncFrame) {
        _ghFrameFlow.tryEmit(address to frame)
    }

    private fun emitConnectionError(address: String, error: ConnectionError) {
        scope.launch {
            _connectionErrors.emit(address to error)
        }
    }

    private fun updateDeviceState(address: String, state: ConnectionState) {
        val current = _devices.value[address] ?: return
        _devices.value = _devices.value + (address to current.copy(state = state))
    }

    private suspend fun disconnectAfterFailure(address: String) {
        disconnectInternal(address, userInitiated = false)
    }

    private suspend fun disconnectInternal(address: String, userInitiated: Boolean) {
        val gHealthPeripheral = peripherals[address] ?: return
        if (userInitiated) {
            Timber.i("User disconnect start: $address")
            // 同时加入两个标记：state 收集器先消费 userDisconnectingAddresses；
            // 若收集器晚于本次断连收尾，则由 suppressDisconnectErrorAddresses 兜底抑制错误弹窗。
            userDisconnectingAddresses.add(address)
            suppressDisconnectErrorAddresses.add(address)
        } else {
            Timber.d("Disconnecting after failure: $address")
        }
        gHealthPeripheral.executor?.reset()
        var ranDisconnect = false
        try {
            ranDisconnect = disconnectCoordinator.disconnect(
                address = address,
                peripheral = gHealthPeripheral.peripheral,
                markDisconnecting = { updateDeviceState(address, ConnectionState.DISCONNECTING) },
                onConfirmedDisconnected = { onDeviceDisconnected(it, gHealthPeripheral.peripheral) },
                onDisconnectFailed = {
                    // 断连失败：清理标记避免污染后续断连事件，并按实际状态恢复 UI，避免永久卡在「断开中」。
                    userDisconnectingAddresses.remove(address)
                    suppressDisconnectErrorAddresses.remove(address)
                    when (gHealthPeripheral.peripheral.state.value) {
                        is State.Disconnected -> onDeviceDisconnected(address, gHealthPeripheral.peripheral)
                        is State.Connected -> {
                            updateDeviceState(address, ConnectionState.CONNECTED)
                            if (userInitiated) {
                                emitConnectionError(
                                    address,
                                    ConnectionError.ConnectionFailed(errorMessage = "断开失败，设备仍处于连接状态")
                                )
                            }
                        }
                        else -> {
                            updateDeviceState(address, ConnectionState.CONNECTING)
                            if (userInitiated) {
                                emitConnectionError(
                                    address,
                                    ConnectionError.ConnectionFailed(errorMessage = "断开失败，设备仍处于连接状态")
                                )
                            }
                        }
                    }
                },
            )
        } catch (e: CancellationException) {
            // 断连流程被取消（如调用方协程取消）时也必须兜底 close，避免后台连接残留。
            try {
                gHealthPeripheral.peripheral.close()
            } catch (closeError: Exception) {
                Timber.w(closeError, "Error closing peripheral for $address after cancelled disconnect")
            }
            throw e
        }
        // 仅当本次调用真正执行了断连时才兜底 close；被单飞跳过的重复调用由执行者负责收尾，
        // 避免 close() 与正在进行的 disconnect() 竞态。
        if (ranDisconnect) {
            try {
                gHealthPeripheral.peripheral.close()
            } catch (e: Exception) {
                Timber.w(e, "Error closing peripheral for $address")
            }
        }
    }

    private fun onDeviceDisconnected(address: String, disconnectedPeripheral: Peripheral? = null) {
        val slot = peripherals[address]
        // 竞态防护：若 peripherals 中已换成更新的 peripheral（期间发生重连），本次断连事件属于旧连接，
        // 不应移除新连接的状态与条目。
        if (disconnectedPeripheral != null && slot != null && slot.peripheral !== disconnectedPeripheral) {
            Timber.i("Ignoring stale disconnect for $address: newer connection owns the slot")
            return
        }
        val device = _devices.value[address]
        val newConnectInFlight = connectingPeripherals.containsKey(address)
        // 仅当 slot 仍是本次断连的 peripheral 时才移除，避免并发完成的新连接被误删。
        if (slot != null) {
            peripherals.remove(address, slot)
        }
        writeTypeByAddress.remove(address)
        clearWriteServiceUuid(address)
        _batteryStatus.update { it - address }
        if (device?.role == DeviceRole.COMPARE) {
            _heartRateResults.value = emptyMap()
        }
        if (newConnectInFlight) {
            // 新连接仍在 CONNECTING，保留其 _devices 条目与 connect job，仅移除旧 peripheral。
            Timber.d("Device $address disconnected (new connect in progress, kept entry)")
        } else {
            connectJobs.remove(address)
            _devices.value = _devices.value - address
            Timber.d("Device disconnected and removed: $address")
        }
        // 槽位按归属释放：过期断连回调（owner 已被新连接替换）自动忽略，不误释放新连接槽位。
        disconnectedPeripheral?.let { connectSingleFlight.release(address, it) }
    }

    private val _dfuState = MutableStateFlow<DfuConnectionState>(DfuConnectionState.Idle)
    val dfuState: StateFlow<DfuConnectionState> = _dfuState.asStateFlow()

    suspend fun scanForDeviceWithMac(
        targetMac: String,
        timeoutMs: Long = 31_000,
    ): BleRawChannel? {
        Timber.i("DFU scanForDeviceWithMac: target=$targetMac, timeout=${timeoutMs}ms")
        val targetAddress = targetMac.uppercase()
        val scanner = Scanner {
            logging { level = Logging.Level.Warnings }
        }
        return try {
            withTimeoutOrNull(timeoutMs) {
                scanner.advertisements
                    .first { it.address.equals(targetAddress, ignoreCase = true) }
                    .let { advertisement ->
                        Timber.d("DFU scan found device: ${advertisement.address} name=${advertisement.name}")
                        val peripheral = Peripheral(advertisement) {
                            logging { level = Logging.Level.Warnings }
                            onServicesDiscovered {
                                requestMtu(247)
                            }
                        }
                        peripheral.connect()
                        KableRawChannel(peripheral)
                    }
            }.also { result ->
                if (result == null) {
                    Timber.w("DFU scanForDeviceWithMac: 未找到设备 $targetMac (超时 ${timeoutMs}ms)")
                } else {
                    Timber.i("DFU scanForDeviceWithMac: 成功连接设备 $targetMac")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "DFU scanForDeviceWithMac failed for $targetMac")
            null
        }
    }

    suspend fun notifyDfuReconnect(oldAddress: String, channel: BleRawChannel) {
        val newAddress = channel.address
        val kableChannel = channel as KableRawChannel
        val newPeripheral = kableChannel.kablePeripheral
        Timber.i("DFU notifyDfuReconnect: $oldAddress -> $newAddress")
        peripherals.remove(oldAddress)
        writeTypeByAddress.remove(oldAddress)
        clearWriteServiceUuid(oldAddress)
        _batteryStatus.update { it - oldAddress }
        val oldDevice = _devices.value[oldAddress]
        val role = oldDevice?.role ?: DeviceRole.MASTER
        val deviceType = oldDevice?.deviceType ?: DeviceType.GH3036

        peripherals[newAddress] = GHealthPeripheral(
            peripheral = newPeripheral,
            role = role,
            executor = null,
            deviceType = deviceType
        )
        _devices.value = _devices.value - oldAddress + (newAddress to ConnectedDevice(
            address = newAddress,
            name = newPeripheral.name,
            role = role,
            state = ConnectionState.CONNECTING,
            deviceType = deviceType
        ))
        _dfuState.value = DfuConnectionState.Reconnected(newAddress, channel)
        Timber.d("DFU notifyDfuReconnect: 设备列表已更新, role=$role, deviceType=$deviceType")
    }
}

private fun ByteArray.toHexString(): String = joinToString(" ") { "%02X".format(it) }
