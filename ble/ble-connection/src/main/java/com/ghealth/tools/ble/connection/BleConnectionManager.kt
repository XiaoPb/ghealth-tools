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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.Collections
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
        is ServiceNotFound -> "未找到指定的服务UUID"
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

    private val peripherals = mutableMapOf<String, GHealthPeripheral>()
    private val userDisconnectingAddresses = Collections.synchronizedSet(mutableSetOf<String>())
    private val suppressDisconnectErrorAddresses = Collections.synchronizedSet(mutableSetOf<String>())

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
        val constraint = checkConnectionConstraint(role)
        if (constraint !is ConnectionConstraint.Success) {
            Timber.w("Connection constraint violated: $constraint")
            emitConnectionError(address, ConnectionError.ConnectionFailed(constraint.getMessage()))
            return
        }

        val advertisement = bleScanner.getCachedAdvertisement(address)
        if (advertisement != null) {
            scope.launch {
                try {
                    val peripheral = Peripheral(advertisement) {
                        logging { level = Logging.Level.Events }
                        onServicesDiscovered {
                            requestMtu(247)
                        }
                    }
                    connect(peripheral, role)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to create peripheral for $address")
                    emitConnectionError(address, ConnectionError.ConnectionFailed("创建设备连接失败: ${e.message}"))
                }
            }
        } else {
            Timber.w("No advertisement cached for address: $address")
            emitConnectionError(address, ConnectionError.ConnectionFailed("Device not found in scan results"))
        }
    }

    fun autoConnect(address: String, name: String?, suppressError: Boolean = false) {
        val targetAddress = address.uppercase()
        Timber.d("Auto-connect: scanning for $targetAddress")
        scope.launch {
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
            } catch (e: Exception) {
                Timber.w(e, "Auto-connect: failed to connect to $targetAddress")
            }
        }
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

        val constraint = checkConnectionConstraint(role)
        if (constraint !is ConnectionConstraint.Success) {
            Timber.w("Connection constraint violated: $constraint")
            if (!suppressError) {
                emitConnectionError(address, ConnectionError.ConnectionFailed(constraint.getMessage()))
            }
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
            updateDeviceState(address, ConnectionState.DISCONNECTED)
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
                    onDeviceDisconnected(address)
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
        peripherals.keys.toList().forEach { address ->
            scope.launch {
                disconnect(address)
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
            userDisconnectingAddresses.add(address)
        } else {
            Timber.d("Disconnecting after failure: $address")
        }
        gHealthPeripheral.executor?.reset()
        updateDeviceState(address, ConnectionState.DISCONNECTING)

        try {
            gHealthPeripheral.peripheral.disconnect()
            val disconnected = withTimeoutOrNull(DISCONNECT_TIMEOUT_MS) {
                gHealthPeripheral.peripheral.state
                    .filterIsInstance<State.Disconnected>()
                    .first()
            }
            if (disconnected == null) {
                Timber.w("Disconnect timeout fallback for $address after ${DISCONNECT_TIMEOUT_MS}ms")
                if (userInitiated) {
                    suppressDisconnectErrorAddresses.add(address)
                }
                onDeviceDisconnected(address)
            } else {
                Timber.i("State disconnected received for $address: status=${disconnected.status}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error disconnecting from $address")
            onDeviceDisconnected(address)
        } finally {
            try {
                Timber.d("Close after disconnected for $address")
                gHealthPeripheral.peripheral.close()
            } catch (e: Exception) {
                Timber.w(e, "Error closing peripheral for $address")
            } finally {
                if (userInitiated && _devices.value.containsKey(address)) {
                    userDisconnectingAddresses.remove(address)
                }
            }
        }
    }

    private fun onDeviceDisconnected(address: String) {
        val device = _devices.value[address]
        peripherals.remove(address)
        writeTypeByAddress.remove(address)
        clearWriteServiceUuid(address)
        _devices.value = _devices.value - address
        
        if (device?.role == DeviceRole.COMPARE) {
            _heartRateResults.value = emptyMap()
        }
        Timber.d("Device disconnected and removed: $address")
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
