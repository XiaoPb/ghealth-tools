package com.ghealth.tools.ble.scanner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager.adapter
    private val scanner by lazy { bluetoothAdapter?.bluetoothLeScanner }

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    val hasScanPermission: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    val hasConnectPermission: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    @SuppressLint("MissingPermission")
    fun scan(
        filters: List<ScanFilter> = emptyList(),
        settings: ScanSettings = defaultScanSettings(),
        minRssi: Int = -100
    ): Flow<BleDevice> = callbackFlow {
        if (scanner == null) {
            close(BleScanException("Bluetooth LE scanner not available"))
            return@callbackFlow
        }

        if (!isBluetoothEnabled) {
            close(BleScanException("Bluetooth is not enabled"))
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (result.rssi < minRssi) return

                val device = BleDevice(
                    name = result.device.name ?: result.scanRecord?.deviceName,
                    address = result.device.address,
                    rssi = result.rssi
                )
                trySend(device)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { result ->
                    if (result.rssi >= minRssi) {
                        val device = BleDevice(
                            name = result.device.name ?: result.scanRecord?.deviceName,
                            address = result.device.address,
                            rssi = result.rssi
                        )
                        trySend(device)
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                val errorMsg = when (errorCode) {
                    SCAN_FAILED_ALREADY_STARTED -> "Scan already started"
                    SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration failed"
                    SCAN_FAILED_INTERNAL_ERROR -> "Internal error"
                    SCAN_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
                    SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "Out of hardware resources"
                    else -> "Unknown error: $errorCode"
                }
                Timber.e("BLE scan failed: $errorMsg")
                close(BleScanException(errorCode, errorMsg))
            }
        }

        Timber.d("Starting BLE scan with minRssi=$minRssi")
        scanner?.startScan(filters, settings, callback)

        awaitClose {
            Timber.d("Stopping BLE scan")
            if (hasScanPermission) {
                scanner?.stopScan(callback)
            }
        }
    }

    private fun defaultScanSettings() = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .setReportDelay(0)
        .build()
}

class BleScanException : Exception {
    val errorCode: Int

    constructor(errorCode: Int) : super("BLE scan failed with code: $errorCode") {
        this.errorCode = errorCode
    }

    constructor(errorCode: Int, message: String) : super(message) {
        this.errorCode = errorCode
    }

    constructor(message: String) : super(message) {
        this.errorCode = -1
    }
}
