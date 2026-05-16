package com.ghealth.tools.ble.connection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.ghealth.tools.ble.protocol.rpccore.ParseResult
import com.ghealth.tools.ble.protocol.gh3036.Gh3036RpcParser
import com.ghealth.tools.core.datastore.BlePreferences
import com.ghealth.tools.core.model.ConnectionState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

object BleUuids {
    val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    val HEART_RATE_MEASUREMENT_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

sealed class ConnectionError {
    object ServiceNotFound : ConnectionError()
    object WriteCharacteristicNotFound : ConnectionError()
    object NotifyCharacteristicNotFound : ConnectionError()
    object HeartRateServiceNotFound : ConnectionError()
    data class GattError(val status: Int) : ConnectionError()

    fun getMessage(): String = when (this) {
        is ServiceNotFound -> "未找到指定的服务UUID"
        is WriteCharacteristicNotFound -> "未找到写入特征UUID"
        is NotifyCharacteristicNotFound -> "未找到通知特征UUID"
        is HeartRateServiceNotFound -> "未找到心率服务"
        is GattError -> "GATT错误: $status"
    }
}

@Singleton
class BleConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val blePreferences: BlePreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _devices = MutableStateFlow<Map<String, ConnectedDevice>>(emptyMap())
    val devices: StateFlow<Map<String, ConnectedDevice>> = _devices.asStateFlow()

    private val _dataFlow = MutableSharedFlow<Pair<String, ParseResult>>(
        replay = 0,
        extraBufferCapacity = 64
    )
    val dataFlow: SharedFlow<Pair<String, ParseResult>> = _dataFlow.asSharedFlow()

    private val _connectionErrors = MutableSharedFlow<Pair<String, ConnectionError>>()
    val connectionErrors: SharedFlow<Pair<String, ConnectionError>> = _connectionErrors.asSharedFlow()

    private val parsers = mutableMapOf<String, Gh3036RpcParser>()
    private val gattConnections = mutableMapOf<String, BluetoothGatt>()
    private val characteristicMap = mutableMapOf<String, BluetoothGattCharacteristic>()

    fun getDeviceState(address: String): ConnectionState {
        return _devices.value[address]?.state ?: ConnectionState.DISCONNECTED
    }

    @SuppressLint("MissingPermission")
    fun connect(bluetoothDevice: BluetoothDevice, role: DeviceRole) {
        val address = bluetoothDevice.address
        val device = ConnectedDevice(
            address = address,
            name = bluetoothDevice.name,
            role = role,
            state = ConnectionState.CONNECTING
        )
        _devices.value = _devices.value + (address to device)
        parsers[address] = Gh3036RpcParser()

        Timber.d("Connecting to $address as $role")

        val gattCallback = createGattCallback(address, role)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            bluetoothDevice.connectGatt(
                context,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
        } else {
            bluetoothDevice.connectGatt(
                context,
                false,
                gattCallback
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String, name: String?, role: DeviceRole) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(address)

        if (bluetoothDevice == null) {
            Timber.e("Device not found: $address")
            updateDeviceState(address, ConnectionState.DISCONNECTED)
            return
        }

        connect(bluetoothDevice, role)
    }

    private fun createGattCallback(address: String, role: DeviceRole) = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Timber.d("Connected to $address, discovering services")
                    updateDeviceState(address, ConnectionState.CONNECTED)
                    gattConnections[address] = gatt
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Timber.d("Disconnected from $address, status=$status")
                    onDeviceDisconnected(address)
                    gatt.close()
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Timber.e("Service discovery failed for $address: $status")
                emitConnectionError(address, ConnectionError.GattError(status))
                disconnect(address)
                return
            }

            Timber.d("Services discovered for $address")

            when (role) {
                DeviceRole.MASTER, DeviceRole.SLAVE -> {
                    scope.launch {
                        val serviceUuid = blePreferences.serviceUuid.first()
                        val writeUuid = blePreferences.writeCharUuid.first()
                        val notifyUuid = blePreferences.notifyCharUuid.first()

                        if (!validateCustomService(gatt, address, serviceUuid, writeUuid, notifyUuid)) {
                            disconnect(address)
                        }
                    }
                }
                DeviceRole.COMPARE -> {
                    if (!validateHeartRateService(gatt, address)) {
                        disconnect(address)
                    }
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.value
            Timber.v("Received ${data.size} bytes from ${gatt.device.address}")
            onDataReceived(gatt.device.address, data)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Timber.v("Write successful to ${characteristic.uuid}")
            } else {
                Timber.w("Write failed to ${characteristic.uuid}: $status")
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Timber.v("Descriptor write successful")
            } else {
                Timber.w("Descriptor write failed: $status")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun validateCustomService(
        gatt: BluetoothGatt,
        address: String,
        serviceUuid: String,
        writeUuid: String,
        notifyUuid: String
    ): Boolean {
        val service = gatt.services.find {
            it.uuid.toString().equals(serviceUuid, ignoreCase = true)
        }

        if (service == null) {
            Timber.e("Service not found: $serviceUuid")
            emitConnectionError(address, ConnectionError.ServiceNotFound)
            return false
        }

        val writeCharacteristic = service.characteristics.find {
            it.uuid.toString().equals(writeUuid, ignoreCase = true)
        }

        if (writeCharacteristic == null) {
            Timber.e("Write characteristic not found: $writeUuid")
            emitConnectionError(address, ConnectionError.WriteCharacteristicNotFound)
            return false
        }

        val notifyCharacteristic = service.characteristics.find {
            it.uuid.toString().equals(notifyUuid, ignoreCase = true)
        }

        if (notifyCharacteristic == null) {
            Timber.e("Notify characteristic not found: $notifyUuid")
            emitConnectionError(address, ConnectionError.NotifyCharacteristicNotFound)
            return false
        }

        characteristicMap[address] = writeCharacteristic

        val success = gatt.setCharacteristicNotification(notifyCharacteristic, true)
        if (success) {
            val descriptor = notifyCharacteristic.getDescriptor(BleUuids.CCCD_UUID)
            descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
            Timber.d("Enabled notifications for $notifyUuid")
        }

        Timber.i("Device $address validated with custom service")
        return true
    }

    @SuppressLint("MissingPermission")
    private fun validateHeartRateService(gatt: BluetoothGatt, address: String): Boolean {
        val heartRateService = gatt.services.find {
            it.uuid == BleUuids.HEART_RATE_SERVICE_UUID
        }

        if (heartRateService == null) {
            Timber.e("Heart rate service not found")
            emitConnectionError(address, ConnectionError.HeartRateServiceNotFound)
            return false
        }

        val heartRateMeasurement = heartRateService.characteristics.find {
            it.uuid == BleUuids.HEART_RATE_MEASUREMENT_UUID
        }

        if (heartRateMeasurement == null) {
            Timber.e("Heart rate measurement characteristic not found")
            emitConnectionError(address, ConnectionError.HeartRateServiceNotFound)
            return false
        }

        val success = gatt.setCharacteristicNotification(heartRateMeasurement, true)
        if (success) {
            val descriptor = heartRateMeasurement.getDescriptor(BleUuids.CCCD_UUID)
            descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
            Timber.d("Enabled heart rate notifications")
        }

        Timber.i("Device $address validated with heart rate service")
        return true
    }

    private fun emitConnectionError(address: String, error: ConnectionError) {
        scope.launch {
            _connectionErrors.emit(address to error)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect(address: String) {
        Timber.d("Disconnecting from $address")
        gattConnections[address]?.disconnect()
        updateDeviceState(address, ConnectionState.DISCONNECTING)
    }

    @SuppressLint("MissingPermission")
    fun disconnectAll() {
        _devices.value.keys.forEach { disconnect(it) }
    }

    @SuppressLint("MissingPermission")
    fun sendCommand(address: String, key: String, param: ByteArray = ByteArray(0)) {
        val parser = parsers[address] ?: return
        val frame = parser.encode(key, param)
        writeToDevice(address, frame)
    }

    fun onDataReceived(address: String, data: ByteArray) {
        val parser = parsers[address] ?: return
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

    @SuppressLint("MissingPermission")
    private fun writeToDevice(address: String, data: ByteArray) {
        val gatt = gattConnections[address] ?: return
        val characteristic = characteristicMap[address] ?: return

        characteristic.value = data
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        gatt.writeCharacteristic(characteristic)

        Timber.d("Write ${data.size} bytes to $address")
    }

    private fun updateDeviceState(address: String, state: ConnectionState) {
        val current = _devices.value[address] ?: return
        _devices.value = _devices.value + (address to current.copy(state = state))
    }

    private fun onDeviceDisconnected(address: String) {
        updateDeviceState(address, ConnectionState.DISCONNECTED)
        gattConnections.remove(address)
        parsers.remove(address)
        characteristicMap.remove(address)
    }
}
