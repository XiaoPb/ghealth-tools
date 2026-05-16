package com.ghealth.tools.ble.connection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import com.ghealth.tools.ble.protocol.rpccore.ParseResult
import com.ghealth.tools.ble.protocol.gh3036.Gh3036RpcParser
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
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _devices = MutableStateFlow<Map<String, ConnectedDevice>>(emptyMap())
    val devices: StateFlow<Map<String, ConnectedDevice>> = _devices.asStateFlow()

    private val _dataFlow = MutableSharedFlow<Pair<String, ParseResult>>(
        replay = 0,
        extraBufferCapacity = 64
    )
    val dataFlow: SharedFlow<Pair<String, ParseResult>> = _dataFlow.asSharedFlow()

    private val parsers = mutableMapOf<String, Gh3036RpcParser>()
    private val gattConnections = mutableMapOf<String, BluetoothGatt>()

    fun getDeviceState(address: String): ConnectionState {
        return _devices.value[address]?.state ?: ConnectionState.DISCONNECTED
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String, name: String?, role: DeviceRole) {
        val device = ConnectedDevice(
            address = address,
            name = name,
            role = role,
            state = ConnectionState.CONNECTING
        )
        _devices.value = _devices.value + (address to device)
        parsers[address] = Gh3036RpcParser()

        Timber.d("Connecting to $address as $role")
        // Nordic BLE Library connection will be integrated here
        // For now, update state tracking
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
        // Will use Nordic BLE Library's write characteristic with queue
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
    }
}
