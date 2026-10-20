package com.ghealth.tools.feature.ota.engine

import com.ghealth.tools.ble.connection.BleRawService
import java.util.UUID

/** OTA 升级协议。 */
enum class OtaProtocol(val displayName: String) {
    GOODIX_DFU("Goodix DFU"),
    NORDIC_SMP("Nordic SMP"),
}

/**
 * 根据已连接设备的 GATT 服务识别升级协议。
 *
 * 只做纯函数匹配，不依赖设备名称，便于单元测试。
 */
object OtaProtocolDetector {

    /** Goodix 普通 DFU 服务。 */
    val GOODIX_DFU_SERVICE_UUID: UUID = UUID.fromString("A6ED0401-D344-460A-8075-B9E8EC90D71B")

    /** Goodix Fast DFU（AppBootloader）服务。 */
    val GOODIX_FAST_DFU_SERVICE_UUID: UUID = UUID.fromString("A6ED0701-D344-460A-8075-B9E8EC90D71B")

    /** Nordic MCUmgr SMP 服务。 */
    val NORDIC_SMP_SERVICE_UUID: UUID = UUID.fromString("8D53DC1D-1DB7-4CD3-868B-8A527460AA84")

    /** Nordic MCUmgr SMP 特征（Notify + Write Without Response）。 */
    val NORDIC_SMP_CHARACTERISTIC_UUID: UUID = UUID.fromString("DA2E7828-FBCE-4E01-AE9E-261174997C48")

    /** BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE */
    private const val PROPERTY_WRITE_NO_RESPONSE = 0x04

    /** BluetoothGattCharacteristic.PROPERTY_NOTIFY */
    private const val PROPERTY_NOTIFY = 0x10

    fun detect(services: List<BleRawService>): Set<OtaProtocol> {
        if (services.isEmpty()) return emptySet()

        val result = mutableSetOf<OtaProtocol>()

        val hasGoodix = services.any { service ->
            service.uuid == GOODIX_DFU_SERVICE_UUID || service.uuid == GOODIX_FAST_DFU_SERVICE_UUID
        }
        if (hasGoodix) result += OtaProtocol.GOODIX_DFU

        val hasSmp = services.any { service ->
            service.uuid == NORDIC_SMP_SERVICE_UUID && service.characteristics.any { char ->
                char.uuid == NORDIC_SMP_CHARACTERISTIC_UUID &&
                    char.properties and PROPERTY_WRITE_NO_RESPONSE != 0 &&
                    char.properties and PROPERTY_NOTIFY != 0
            }
        }
        if (hasSmp) result += OtaProtocol.NORDIC_SMP

        return result
    }
}
