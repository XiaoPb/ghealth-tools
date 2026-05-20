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
import com.juul.kable.ExperimentalApi
import com.juul.kable.Peripheral
import com.juul.kable.State
import com.juul.kable.WriteType
import com.juul.kable.characteristicOf
import com.juul.kable.logs.Logging
import com.juul.kable.write
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
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
    data class ConnectionFailed(val errorMessage: String) : ConnectionError()

    fun getMessage(): String = when (this) {
        is ServiceNotFound -> "未找到指定的服务UUID"
        is WriteCharacteristicNotFound -> "未找到写入特征UUID"
        is NotifyCharacteristicNotFound -> "未找到通知特征UUID"
        is HeartRateServiceNotFound -> "未找到心率服务"
        is ConnectionFailed -> "连接失败: $errorMessage"
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
    private val scope: CoroutineScope
) {
    private val _devices = MutableStateFlow<Map<String, ConnectedDevice>>(emptyMap())
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
    private val pendingConnections = mutableMapOf<String, Peripheral>()

    fun getDeviceState(address: String): ConnectionState {
        return _devices.value[address]?.state ?: ConnectionState.DISCONNECTED
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

        val peripheral = pendingConnections[address]
        if (peripheral != null) {
            scope.launch {
                connect(peripheral, role)
            }
        } else {
            Timber.w("No peripheral found for address: $address")
            emitConnectionError(address, ConnectionError.ConnectionFailed("Device not found in scan results"))
        }
    }

    fun registerPeripheral(peripheral: Peripheral) {
        pendingConnections[peripheral.identifier.toString()] = peripheral
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun connect(peripheral: Peripheral, role: DeviceRole) {
        val address = peripheral.identifier.toString()

        val constraint = checkConnectionConstraint(role)
        if (constraint !is ConnectionConstraint.Success) {
            Timber.w("Connection constraint violated: $constraint")
            emitConnectionError(address, ConnectionError.ConnectionFailed(constraint.getMessage()))
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

        try {
            peripheral.connect()

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
            _devices.value = _devices.value + (address to _devices.value[address]!!.copy(deviceType = deviceType))

            peripheral.state.onEach { state ->
                when (state) {
                    is State.Connected -> {
                        updateDeviceState(address, ConnectionState.CONNECTED)
                        validateServices(peripheral, address, role)
                    }
                    is State.Disconnected -> {
                        onDeviceDisconnected(address)
                    }
                    else -> {}
                }
            }.launchIn(scope)

        } catch (e: Exception) {
            Timber.e(e, "Connection failed for $address")
            emitConnectionError(address, ConnectionError.ConnectionFailed(e.message ?: "Unknown error"))
            updateDeviceState(address, ConnectionState.DISCONNECTED)
        }
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
        if (data.isEmpty()) return
        val flags = data[0].toInt() and 0xFF
        val heartRate = if (flags and 0x01 == 0) {
            data[1].toInt() and 0xFF
        } else {
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
            disconnect(address)
            return
        }

        when (role) {
            DeviceRole.MASTER, DeviceRole.SLAVE -> {
                val serviceUuidStr = blePreferences.serviceUuid.first()
                val serviceUuid = Uuid.parse(serviceUuidStr)
                val writeUuidStr = blePreferences.writeCharUuid.first()
                val writeUuid = Uuid.parse(writeUuidStr)
                val notifyUuidStr = blePreferences.notifyCharUuid.first()
                val notifyUuid = Uuid.parse(notifyUuidStr)

                val service = services.find { it.serviceUuid == serviceUuid }

                if (service == null) {
                    Timber.e("Service not found: $serviceUuidStr")
                    emitConnectionError(address, ConnectionError.ServiceNotFound)
                    disconnect(address)
                    return
                }

                val writeCharacteristic = service.characteristics.find { it.characteristicUuid == writeUuid }

                if (writeCharacteristic == null) {
                    Timber.e("Write characteristic not found: $writeUuidStr")
                    emitConnectionError(address, ConnectionError.WriteCharacteristicNotFound)
                    disconnect(address)
                    return
                }

                val notifyCharacteristic = service.characteristics.find { it.characteristicUuid == notifyUuid }

                if (notifyCharacteristic == null) {
                    Timber.e("Notify characteristic not found: $notifyUuidStr")
                    emitConnectionError(address, ConnectionError.NotifyCharacteristicNotFound)
                    disconnect(address)
                    return
                }

                Timber.d("Notify characteristic properties: ${notifyCharacteristic.properties}")

                val notifyChar = characteristicOf(
                    service = serviceUuid,
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
                    Timber.i("Subscribed to notify characteristic $notifyUuidStr for $address")
                } catch (e: NoSuchElementException) {
                    Timber.e(e, "Notify characteristic does not support notify/indicate: $notifyUuidStr")
                    emitConnectionError(address, ConnectionError.NotifyCharacteristicNotFound)
                    disconnect(address)
                    return
                } catch (e: Exception) {
                    Timber.e(e, "Failed to observe notify characteristic: $notifyUuidStr")
                    emitConnectionError(address, ConnectionError.NotifyCharacteristicNotFound)
                    disconnect(address)
                    return
                }

                Timber.i("Device $address validated with custom service")
            }
            DeviceRole.COMPARE -> {
                val heartRateService = services.find { it.serviceUuid == BleUuids.HEART_RATE_SERVICE_UUID }

                if (heartRateService == null) {
                    Timber.e("Heart rate service not found")
                    emitConnectionError(address, ConnectionError.HeartRateServiceNotFound)
                    disconnect(address)
                    return
                }

                val heartRateMeasurement = heartRateService.characteristics.find {
                    it.characteristicUuid == BleUuids.HEART_RATE_MEASUREMENT_UUID
                }

                if (heartRateMeasurement == null) {
                    Timber.e("Heart rate measurement characteristic not found")
                    emitConnectionError(address, ConnectionError.HeartRateServiceNotFound)
                    disconnect(address)
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
    }

    suspend fun disconnect(address: String) {
        Timber.d("Disconnecting from $address")
        val gHealthPeripheral = peripherals[address] ?: return
        gHealthPeripheral.executor?.reset()
        updateDeviceState(address, ConnectionState.DISCONNECTING)
        try {
            gHealthPeripheral.peripheral.disconnect()
            gHealthPeripheral.peripheral.close()
        } catch (e: Exception) {
            Timber.e(e, "Error disconnecting from $address")
        }
    }

    fun disconnectAll() {
        peripherals.keys.forEach { address ->
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

        val serviceUuidStr = blePreferences.serviceUuid.first()
        val serviceUuid = Uuid.parse(serviceUuidStr)
        val writeUuidStr = blePreferences.writeCharUuid.first()
        val writeUuid = Uuid.parse(writeUuidStr)

        val writeChar = characteristicOf(
            service = serviceUuid,
            characteristic = writeUuid
        )

        gHealthPeripheral.peripheral.write(writeChar, data, WriteType.WithResponse)
        logManager.logBle(address, "TX", data)
        Timber.d("Wrote ${data.size} bytes to $address: ${data.toHexString()}")
    }

    private suspend fun createExecutor(address: String): Pair<GHealthExecutor, DeviceType> {
        val chipName = blePreferences.selectedChip.first()
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

    private fun onDeviceDisconnected(address: String) {
        val device = _devices.value[address]
        peripherals.remove(address)
        _devices.value = _devices.value - address
        
        if (device?.role == DeviceRole.COMPARE) {
            _heartRateResults.value = emptyMap()
        }
        Timber.d("Device disconnected and removed: $address")
    }
}

private fun ByteArray.toHexString(): String = joinToString(" ") { "%02X".format(it) }
