@file:OptIn(ExperimentalApi::class, ExperimentalUuidApi::class)

package com.ghealth.tools.ble.connection

import com.ghealth.tools.ble.protocol.rpccore.ParseResult
import com.ghealth.tools.ble.protocol.gh3036.Gh3036RpcParser
import com.ghealth.tools.core.datastore.BlePreferences
import com.ghealth.tools.core.model.ConnectionState
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
    val parser: Gh3036RpcParser?
) {
    val address: String get() = peripheral.identifier.toString()
    val name: String? get() = peripheral.name
}

@Singleton
class BleConnectionManager @Inject constructor(
    private val blePreferences: BlePreferences,
    private val scope: CoroutineScope
) {
    private val _devices = MutableStateFlow<Map<String, ConnectedDevice>>(emptyMap())
    val devices: StateFlow<Map<String, ConnectedDevice>> = _devices.asStateFlow()

    private val _dataFlow = MutableSharedFlow<Pair<String, ParseResult>>(
        replay = 0,
        extraBufferCapacity = 64
    )
    val dataFlow: SharedFlow<Pair<String, ParseResult>> = _dataFlow.asSharedFlow()

    private val _connectionErrors = MutableSharedFlow<Pair<String, ConnectionError>>()
    val connectionErrors: SharedFlow<Pair<String, ConnectionError>> = _connectionErrors.asSharedFlow()

    private val _heartRateResults = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val heartRateResults: StateFlow<Map<Int, Int>> = _heartRateResults.asStateFlow()

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

            val parser = when (role) {
                DeviceRole.MASTER -> Gh3036RpcParser()
                DeviceRole.SLAVE -> Gh3036RpcParser()
                DeviceRole.COMPARE -> null
            }

            val gHealthPeripheral = GHealthPeripheral(
                peripheral = peripheral,
                role = role,
                parser = parser
            )
            peripherals[address] = gHealthPeripheral

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

                val notifyChar = characteristicOf(
                    service = serviceUuid,
                    characteristic = notifyUuid
                )

                peripheral.observe(notifyChar)
                    .onEach { data ->
                        onDataReceived(address, data)
                    }
                    .launchIn(scope)

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

    private fun onDataReceived(address: String, data: ByteArray) {
        Timber.v("Received ${data.size} bytes from $address")
        val gHealthPeripheral = peripherals[address] ?: return
        val parser = gHealthPeripheral.parser ?: return
        val results = parser.decode(data)

        scope.launch {
            for (result in results) {
                result.onSuccess { parsed ->
                    _dataFlow.emit(address to parsed)
                }
                result.onFailure { error ->
                    Timber.w("Parse error from $address: ${error.message}")
                }
            }
        }
    }

    suspend fun disconnect(address: String) {
        Timber.d("Disconnecting from $address")
        val gHealthPeripheral = peripherals[address] ?: return
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
    suspend fun sendCommand(address: String, key: String, param: ByteArray = ByteArray(0)) {
        val gHealthPeripheral = peripherals[address] ?: return
        val parser = gHealthPeripheral.parser ?: return
        val frame = parser.encode(key, param)
        writeToDevice(address, frame)
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
        Timber.d("Wrote ${data.size} bytes to $address")
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
        updateDeviceState(address, ConnectionState.DISCONNECTED)
        peripherals.remove(address)
        
        val device = _devices.value[address]
        if (device?.role == DeviceRole.COMPARE) {
            val index = getCompareDeviceIndex(address)
            if (index >= 0) {
                val newResults = _heartRateResults.value.toMutableMap()
                newResults.remove(index)
                _heartRateResults.value = newResults
            }
        }
    }
}
