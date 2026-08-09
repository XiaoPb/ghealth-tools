package com.ghealth.tools.ble.gh3220.commands

import com.ghealth.tools.ble.itlvc.core.ItlvcError
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BasicCommandsTest {

    @Test
    fun `package test payload carries length prefix`() {
        assertContentEquals(
            byteArrayOf(0x04, 0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()),
            BasicCommands.packageTest(byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())),
        )
        val result = BasicCommands.parsePackageTest(
            byteArrayOf(0x04, 0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()),
        )
        assertTrue(result.isSuccess)
        assertContentEquals(byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()), result.getOrThrow())
    }

    @Test
    fun `package test rejects truncated data`() {
        val result = BasicCommands.parsePackageTest(byteArrayOf(0x04, 0x01))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ItlvcError.ParseError)
    }

    @Test
    fun `version request and response`() {
        assertContentEquals(byteArrayOf(0x01), BasicCommands.getVersion(0x01))
        val result = BasicCommands.parseVersion(byteArrayOf(0x01, 0x02, 0x41, 0x42))
        assertTrue(result.isSuccess)
        val info = result.getOrThrow()
        assertEquals(0x01, info.versionType)
        assertEquals("AB", info.text)
    }

    @Test
    fun `version response rejects truncated data`() {
        val result = BasicCommands.parseVersion(byteArrayOf(0x01, 0x02, 0x41))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ItlvcError.ParseError)
    }

    @Test
    fun `start hbd payload little-endian function`() {
        assertContentEquals(
            byteArrayOf(0x00, 0x00, 0x01, 0x00, 0x00, 0x00),
            BasicCommands.startHbd(on = true, mode = 0, function = 0x01),
        )
    }

    @Test
    fun `conn status has empty payload`() {
        assertContentEquals(ByteArray(0), BasicCommands.getConnStatus())
    }

    @Test
    fun `one-byte commands`() {
        assertContentEquals(byteArrayOf(0x5A), BasicCommands.chipCtrl(0x5A))
        assertContentEquals(byteArrayOf(0x00), BasicCommands.calibrateCurrent(0))
        assertContentEquals(byteArrayOf(0x00), BasicCommands.appModule(0))
        assertContentEquals(byteArrayOf(0x01), BasicCommands.switchChip(1))
    }

    @Test
    fun `status parse`() {
        assertEquals(0, BasicCommands.parseStatus(byteArrayOf(0x00), "x").getOrThrow())
        assertEquals(1, BasicCommands.parseStatus(byteArrayOf(0x01), "x").getOrThrow())
    }

    @Test
    fun `status parse rejects empty payload`() {
        val result = BasicCommands.parseStatus(ByteArray(0), "x")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ItlvcError.ParseError)
    }
}
