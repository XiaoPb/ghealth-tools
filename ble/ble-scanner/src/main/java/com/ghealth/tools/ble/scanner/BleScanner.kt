package com.ghealth.tools.ble.scanner

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.ghealth.tools.core.model.BleDevice
import com.juul.kable.Advertisement
import com.juul.kable.Scanner
import com.juul.kable.logs.Logging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var _isBluetoothEnabled: Boolean = true
    val isBluetoothEnabled: Boolean
        get() = _isBluetoothEnabled

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

    fun scan(
        filters: List<Any> = emptyList(),
        settings: Any = Unit,
        minRssi: Int = -100
    ): Flow<BleDevice> {
        Timber.d("Starting BLE scan with minRssi=$minRssi")
        return Scanner {
            logging {
                level = Logging.Level.Events
            }
        }
            .advertisements
            .filter { it.rssi >= minRssi }
            .map { advertisement ->
                _isBluetoothEnabled = true
                BleDevice(
                    name = advertisement.name,
                    address = advertisement.identifier.toString(),
                    rssi = advertisement.rssi
                )
            }
    }

    fun scanAdvertisements(minRssi: Int = -100): Flow<Advertisement> {
        Timber.d("Starting BLE advertisement scan with minRssi=$minRssi")
        return Scanner {
            logging {
                level = Logging.Level.Events
            }
        }
            .advertisements
            .filter { it.rssi >= minRssi }
    }
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
