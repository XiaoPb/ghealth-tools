package com.ghealth.tools.ble.protocol.gh3036

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class RegisterCommandPayloadBuilderTest {
    @Test
    fun `build u16 array payload prefixes element count`() {
        val payload = RegisterCommandPayloadBuilder.buildU16ArrayPayload(
            intArrayOf(0x1000, 0x1234, 0x1002, 0x5678)
        )

        assertArrayEquals(
            byteArrayOf(
                0x04, 0x00,
                0x00, 0x10,
                0x34, 0x12,
                0x02, 0x10,
                0x78, 0x56
            ),
            payload
        )
    }
}
