package com.ghealth.tools.feature.ota.engine

import com.ghealth.tools.ble.connection.BleRawChar
import com.ghealth.tools.ble.connection.BleRawService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OtaProtocolDetectorTest {

    @Test
    fun `goodix service detected`() {
        val services = listOf(
            BleRawService(
                uuid = OtaProtocolDetector.GOODIX_DFU_SERVICE_UUID,
                characteristics = emptyList(),
            )
        )

        assertEquals(setOf(OtaProtocol.GOODIX_DFU), OtaProtocolDetector.detect(services))
    }

    @Test
    fun `nordic smp detected when characteristic supports write without response and notify`() {
        val services = listOf(
            smpService(properties = 0x04 or 0x10)
        )

        assertEquals(setOf(OtaProtocol.NORDIC_SMP), OtaProtocolDetector.detect(services))
    }

    @Test
    fun `nordic smp rejected when characteristic lacks notify`() {
        val services = listOf(smpService(properties = 0x04))

        assertTrue(OtaProtocolDetector.detect(services).isEmpty())
    }

    @Test
    fun `both protocols detected when services coexist`() {
        val services = listOf(
            BleRawService(
                uuid = OtaProtocolDetector.GOODIX_FAST_DFU_SERVICE_UUID,
                characteristics = emptyList(),
            ),
            smpService(properties = 0x04 or 0x10),
        )

        assertEquals(
            setOf(OtaProtocol.GOODIX_DFU, OtaProtocol.NORDIC_SMP),
            OtaProtocolDetector.detect(services),
        )
    }

    @Test
    fun `empty services yield no protocol`() {
        assertTrue(OtaProtocolDetector.detect(emptyList()).isEmpty())
    }

    private fun smpService(properties: Int) = BleRawService(
        uuid = OtaProtocolDetector.NORDIC_SMP_SERVICE_UUID,
        characteristics = listOf(
            BleRawChar(
                uuid = OtaProtocolDetector.NORDIC_SMP_CHARACTERISTIC_UUID,
                properties = properties,
            )
        ),
    )
}
