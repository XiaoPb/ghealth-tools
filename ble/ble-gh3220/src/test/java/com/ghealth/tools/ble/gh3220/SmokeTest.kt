package com.ghealth.tools.ble.gh3220

import com.ghealth.tools.ble.itlvc.codec.Crc8
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SmokeTest {
    @Test
    fun `gh3220 module sees itlvc core`() {
        assertEquals(1, Crc8.size)
    }
}
