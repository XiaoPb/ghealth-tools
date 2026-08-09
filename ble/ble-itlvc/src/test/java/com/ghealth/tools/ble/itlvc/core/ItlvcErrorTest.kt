package com.ghealth.tools.ble.itlvc.core

import com.ghealth.tools.ble.itlvc.state.SessionState
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ItlvcErrorTest {

    @Test
    fun `error hierarchy exposes expected variants`() {
        // 编译期目录：真实 code→错误映射为 Phase 2 工作。
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

    @Test
    fun `transport error carries message`() {
        val e = ItlvcError.TransportError("send failed")
        assertEquals("send failed", e.message)
    }
}
