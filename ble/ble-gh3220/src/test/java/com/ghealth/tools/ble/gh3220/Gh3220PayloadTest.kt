package com.ghealth.tools.ble.gh3220

import com.ghealth.tools.ble.itlvc.codec.FrameLayout
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class Gh3220PayloadTest {

    @Test
    fun `layout matches itlvc default`() {
        val l = Gh3220Layout.layout
        assertContentEquals(FrameLayout.GH3220.idBytes, l.idBytes)
        assertEquals(FrameLayout.GH3220.typeBytes, l.typeBytes)
        assertEquals(FrameLayout.GH3220.lenBytes, l.lenBytes)
        assertEquals(FrameLayout.GH3220.maxValueLen, l.maxValueLen)
        assertEquals(FrameLayout.GH3220.checksumLen, l.checksumLen)
    }

    @Test
    fun `command id constants cover the full matrix`() {
        assertEquals(0x00, Gh3220Cmd.NOP)
        assertEquals(0x01, Gh3220Cmd.ACK)
        assertEquals(0x08, Gh3220Cmd.RAWDATA)
        assertEquals(0x0B, Gh3220Cmd.RAWDATA_NEW)
        assertEquals(0x0F, Gh3220Cmd.FW_UPGRADE)
        assertEquals(0x16, Gh3220Cmd.CHIP_EVENT_REPORT)
        assertEquals(0x1F, Gh3220Cmd.DRV_CFG)
        assertEquals(0x2A, Gh3220Cmd.RAWDATA_FIFO)
        assertEquals(0xA1, Gh3220Cmd.REG_ARRAY_WRITE)
        assertEquals(0xA2, Gh3220Cmd.DEBUG_STATUS)
    }

    @Test
    fun `u16le and u32le little-endian`() {
        assertContentEquals(byteArrayOf(0x34, 0x12), Gh3220Payload.u16le(0x1234))
        assertContentEquals(byteArrayOf(0x78, 0x56, 0x34, 0x12), Gh3220Payload.u32le(0x12345678L))
        assertContentEquals(byteArrayOf(0x01, 0x00, 0x00, 0x00), Gh3220Payload.u32le(1L))
    }

    @Test
    fun `readU16le and readU32le little-endian`() {
        assertEquals(0x1234, Gh3220Payload.readU16le(byteArrayOf(0x34, 0x12), 0))
        assertEquals(0x12345678L, Gh3220Payload.readU32le(byteArrayOf(0x78, 0x56, 0x34, 0x12), 0))
    }
}
