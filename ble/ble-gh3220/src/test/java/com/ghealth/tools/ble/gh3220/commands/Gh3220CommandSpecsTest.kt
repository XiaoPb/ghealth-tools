package com.ghealth.tools.ble.gh3220.commands

import com.ghealth.tools.ble.gh3220.Gh3220Cmd
import com.ghealth.tools.ble.itlvc.core.CommandSpec
import com.ghealth.tools.ble.itlvc.core.ItlvcConfig
import com.ghealth.tools.ble.itlvc.core.ItlvcError
import com.ghealth.tools.ble.itlvc.core.ItlvcSession
import com.ghealth.tools.ble.itlvc.codec.ItlvcFrameCodec
import com.ghealth.tools.ble.gh3220.Gh3220Layout
import com.ghealth.tools.ble.itlvc.transport.InMemoryTransport
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class Gh3220CommandSpecsTest {

    @Test
    fun `all command specs cover the full matrix`() {
        val types = Gh3220CommandSpecs.all.map { it.type[0].toInt() and 0xFF }.toSet()
        val expected = setOf(
            0x00, 0x05, 0x03, 0x0C, 0x0F, 0x10, 0x11, 0x12, 0x13, 0x15,
            0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x20,
            0xA1, 0x2E,
        )
        assertEquals(expected, types)
    }

    @Test
    fun `pass-through whitelist matches doc section 4_3_5`() {
        val whitelist = Gh3220CommandSpecs.passThroughWhitelist.map { it.toInt() and 0xFF }.toSet()
        assertEquals(setOf(0x19, 0x1A, 0x1E, 0x21, 0x2A), whitelist)
        assertTrue(Gh3220CommandSpecs.GET_VER.allowedInPassThrough)
        assertTrue(Gh3220CommandSpecs.CONN_STATUS.allowedInPassThrough)
        assertTrue(Gh3220CommandSpecs.WORK_MODE_SET.allowedInPassThrough)
    }

    @Test
    fun `pass-through mode blocks config command but allows whitelist`() = runTest {
        val transport = InMemoryTransport()
        val session = ItlvcSession(
            ItlvcFrameCodec(Gh3220Layout.layout),
            ItlvcConfig(passThroughMode = true),
        )
        session.attach(transport, this)

        val blocked = session.execute(Gh3220CommandSpecs.START_CTRL, byteArrayOf(0x00, 0x00, 0x01, 0x00, 0x00, 0x00))
        assertTrue(blocked.isFailure)
        assertIs<ItlvcError.CommandError.Unsupported>(blocked.exceptionOrNull())
        assertEquals(0, transport.sent.size)

        // 白名单命令允许发送（无响应则超时，但已写入传输）
        val allowed = session.execute(Gh3220CommandSpecs.CONN_STATUS, ByteArray(0))
        assertEquals(1, transport.sent.size)
        assertTrue(allowed.isFailure) // 无响应 → Timeout
        assertIs<ItlvcError.CommandError.Timeout>(allowed.exceptionOrNull())
        session.detach()
    }

    @Test
    fun `spec timeout defaults and fw override`() {
        assertEquals(1000L, Gh3220CommandSpecs.CONN_STATUS.timeoutMs)
        assertEquals(3000L, Gh3220CommandSpecs.FW_UPGRADE.timeoutMs)
        assertEquals(3000L, Gh3220CommandSpecs.DRV_CFG.timeoutMs)
    }
}
