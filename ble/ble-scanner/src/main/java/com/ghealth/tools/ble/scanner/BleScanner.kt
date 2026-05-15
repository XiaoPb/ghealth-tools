package com.ghealth.tools.ble.scanner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.ghealth.tools.core.model.BleDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val bluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val bluetoothAdapter by lazy { bluetoothManager.adapter }
    private val scanner by lazy { bluetoothAdapter.bluetoothLeScanner }

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun scan(
        filters: List<ScanFilter> = emptyList(),
        settings: ScanSettings = defaultScanSettings()
    ): Flow<BleDevice> = callbackFlow {
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = BleDevice(
                    name = result.device.name ?: result.scanRecord?.deviceName,
                    address = result.device.address,
                    rssi = result.rssi
                )
                trySend(device)
            }

            override fun onScanFailed(errorCode: Int) {
                Timber.e("BLE scan failed: $errorCode")
                close(BleScanException(errorCode))
            }
        }

        Timber.d("Starting BLE scan")
        scanner.startScan(filters, settings, callback)

        awaitClose {
            Timber.d("Stopping BLE scan")
            scanner.stopScan(callback)
        }
    }

    private fun defaultScanSettings() = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .setReportDelay(0)
        .build()
}

class BleScanException(val errorCode: Int) : Exception("BLE scan failed with code: $errorCode")
