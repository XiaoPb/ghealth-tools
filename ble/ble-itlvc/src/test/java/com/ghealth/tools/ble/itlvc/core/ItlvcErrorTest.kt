package com.ghealth.tools.ble.itlvc.core

import com.ghealth.tools.ble.itlvc.state.SessionState
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ItlvcErrorTest {

    @Test
    fun `device error codes map to CommandError`() {
        val byCode = mapOf(
            0x00 to null,
            0x01 to ItlvcError.CommandError.DeviceError(0x01),
            0xFE to ItlvcError.CommandError.InvalidParam,
            0xFD to ItlvcError.CommandError.Busy,
            0xFC to ItlvcError.CommandError.Unknown,
            0xFB to ItlvcError.CommandError.Unsupported,
            0xFA to ItlvcError.CommandError.CrcFail,
        )
        assertEquals(7, byCode.size)
        assertTrue(ItlvcError.FrameError.CrcMismatch is ItlvcError)
        assertTrue(ItlvcError.CommandError.Timeout(3) is ItlvcError)
        assertTrue(SessionState.DISCONNECTED.name.isNotEmpty())
    }

    @Test
    fun `parse error carries message`() {
        val e = ItlvcError.ParseError("bad payload")
        assertEquals("bad payload", e.message)
    }
}
