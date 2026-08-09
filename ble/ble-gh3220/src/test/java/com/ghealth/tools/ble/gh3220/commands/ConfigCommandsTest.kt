package com.ghealth.tools.ble.gh3220.commands

import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class ConfigCommandsTest {

    @Test
    fun `work mode payload`() {
        assertContentEquals(
            byteArrayOf(0x05, 0x80.toByte(), 0x00, 0x00, 0x00),
            ConfigCommands.workMode(mode = 5, function = 0x80),
        )
    }

    @Test
    fun `gsensor set payload`() {
        assertContentEquals(
            byteArrayOf(0x01, 0x00, 0x00, 0x19),
            ConfigCommands.gsensorSet(vendorId = 1, resolution = 0, sampleRate = 25),
        )
    }

    @Test
    fun `gsensor rejects out-of-range values`() {
        assertFailsWith<IllegalArgumentException> { ConfigCommands.gsensorSet(0x100, 0, 25) }
        assertFailsWith<IllegalArgumentException> { ConfigCommands.gsensorSet(1, 0, 0x10000) }
    }

    @Test
    fun `fifo threshold payload little-endian`() {
        assertContentEquals(byteArrayOf(0x00, 0x40), ConfigCommands.fifoThreshold(0x4000))
    }

    @Test
    fun `event set payload little-endian`() {
        assertContentEquals(byteArrayOf(0x02, 0x00), ConfigCommands.eventSet(0x0002))
    }

    @Test
    fun `function map requires 64 bytes`() {
        assertContentEquals(ByteArray(64), ConfigCommands.funcMap(ByteArray(64)))
        assertFailsWith<IllegalArgumentException> { ConfigCommands.funcMap(ByteArray(63)) }
    }

    @Test
    fun `sample rates pack id into high nibble`() {
        assertContentEquals(
            byteArrayOf(0x01, 0x10, 0x19), // 1 项：id=1, sr=25 → (1<<12)|25 = 0x1019
            ConfigCommands.sampleRates(listOf(1 to 25)),
        )
    }

    @Test
    fun `sample rates reject out-of-range id and sr`() {
        assertFailsWith<IllegalArgumentException> { ConfigCommands.sampleRates(listOf(0x10 to 25)) }
        assertFailsWith<IllegalArgumentException> { ConfigCommands.sampleRates(listOf(1 to 0x1000)) }
        assertFailsWith<IllegalArgumentException> { ConfigCommands.sampleRates(emptyList()) }
    }

    @Test
    fun `slot en payload`() {
        assertContentEquals(
            byteArrayOf(0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00),
            ConfigCommands.slotEn(slotEn = 1, addCmd = 0, function = 0x01, on = true),
        )
    }

    @Test
    fun `ecg ctrl and work mode set payloads`() {
        assertContentEquals(byteArrayOf(0x08, 0x00, 0x00, 0x00), ConfigCommands.ecgCtrl(0x08))
        assertContentEquals(byteArrayOf(0x01, 0x00, 0x00, 0x00), ConfigCommands.workModeSet(1))
    }
}
