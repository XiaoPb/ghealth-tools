package com.ghealth.tools.ble.scanner

import com.ghealth.tools.core.model.BleDevice
import com.juul.kable.Scanner
import com.juul.kable.logs.Logging
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GHealthScanner @Inject constructor() {

    fun scan(minRssi: Int = -80): Flow<BleDevice> {
        Timber.d("Starting BLE scan with minRssi=$minRssi")
        return Scanner {
            logging {
                level = Logging.Level.Events
            }
        }
            .advertisements
            .filter { it.rssi >= minRssi }
            .map { advertisement ->
                BleDevice(
                    name = advertisement.name,
                    address = advertisement.identifier.toString(),
                    rssi = advertisement.rssi
                )
            }
    }

    fun scanWithNameFilter(name: String, minRssi: Int = -80): Flow<BleDevice> {
        Timber.d("Starting BLE scan with name filter: $name")
        return Scanner {
            logging {
                level = Logging.Level.Events
            }
        }
            .advertisements
            .filter { it.name == name && it.rssi >= minRssi }
            .map { advertisement ->
                BleDevice(
                    name = advertisement.name,
                    address = advertisement.identifier.toString(),
                    rssi = advertisement.rssi
                )
            }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun scanWithServiceUuidFilter(serviceUuid: String, minRssi: Int = -80): Flow<BleDevice> {
        Timber.d("Starting BLE scan with service UUID filter: $serviceUuid")
        val uuid = Uuid.parse(serviceUuid)
        return Scanner {
            logging {
                level = Logging.Level.Events
            }
        }
            .advertisements
            .filter { advertisement ->
                advertisement.uuids?.contains(uuid) == true && advertisement.rssi >= minRssi
            }
            .map { advertisement ->
                BleDevice(
                    name = advertisement.name,
                    address = advertisement.identifier.toString(),
                    rssi = advertisement.rssi
                )
            }
    }

    fun scanWithAddressFilter(address: String, minRssi: Int = -80): Flow<BleDevice> {
        Timber.d("Starting BLE scan with address filter: $address")
        return Scanner {
            logging {
                level = Logging.Level.Events
            }
        }
            .advertisements
            .filter { it.identifier.toString() == address && it.rssi >= minRssi }
            .map { advertisement ->
                BleDevice(
                    name = advertisement.name,
                    address = advertisement.identifier.toString(),
                    rssi = advertisement.rssi
                )
            }
    }
}
