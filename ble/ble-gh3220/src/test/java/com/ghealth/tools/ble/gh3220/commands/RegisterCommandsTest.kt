package com.ghealth.tools.ble.gh3220.commands

import com.ghealth.tools.ble.itlvc.core.ItlvcError
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RegisterCommandsTest {

    @Test
    fun `reg read payload`() {
        assertContentEquals(
            byteArrayOf(0x00, 0x02, 0x10, 0x00),
            RegisterCommands.regRead(addr = 0x1000, count = 2),
        )
    }

    @Test
    fun `reg write payload big-endian register data`() {
        assertContentEquals(
            byteArrayOf(0x01, 0x02, 0x10, 0x00, 0x12, 0x34, 0x56, 0x78),
            RegisterCommands.regWrite(addr = 0x1000, values = intArrayOf(0x1234, 0x5678)),
        )
    }

    @Test
    fun `reg read response parse`() {
        val result = RegisterCommands.parseRegRead(
            byteArrayOf(0x00, 0x02, 0x10, 0x00, 0x12, 0x34, 0x56, 0x78),
        )
        assertTrue(result.isSuccess)
        assertContentEquals(intArrayOf(0x1234, 0x5678), result.getOrThrow())
    }

    @Test
    fun `reg read response rejects truncated data`() {
        assertTrue(RegisterCommands.parseRegRead(byteArrayOf(0x00, 0x02, 0x10, 0x00, 0x12)).isFailure)
    }

    @Test
    fun `reg read response rejects too-short payload`() {
        val result = RegisterCommands.parseRegRead(byteArrayOf(0x00, 0x02))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ItlvcError.ParseError)
    }

    @Test
    fun `reg read response rejects mode mismatch`() {
        val result = RegisterCommands.parseRegRead(byteArrayOf(0x01, 0x02, 0x10, 0x00))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ItlvcError.ParseError)
    }

    @Test
    fun `reg read rejects out-of-range count and address`() {
        assertFailsWith<IllegalArgumentException> { RegisterCommands.regRead(addr = 0x1000, count = 0) }
        assertFailsWith<IllegalArgumentException> { RegisterCommands.regRead(addr = 0x1000, count = 256) }
        assertFailsWith<IllegalArgumentException> { RegisterCommands.regRead(addr = 0x10000, count = 1) }
    }

    @Test
    fun `reg write rejects empty values and out-of-range address`() {
        assertFailsWith<IllegalArgumentException> { RegisterCommands.regWrite(addr = 0x1000, values = intArrayOf()) }
        assertFailsWith<IllegalArgumentException> { RegisterCommands.regWrite(addr = 0x10000, values = intArrayOf(0x1234)) }
    }

    @Test
    fun `reg array write packs 4-byte blocks`() {
        assertContentEquals(
            byteArrayOf(0x10, 0x00, 0x12, 0x34),
            RegisterCommands.regArrayWrite(listOf(intArrayOf(0x10, 0x00, 0x12, 0x34))),
        )
    }

    @Test
    fun `reg array write rejects empty blocks`() {
        assertFailsWith<IllegalArgumentException> { RegisterCommands.regArrayWrite(emptyList()) }
    }
}
