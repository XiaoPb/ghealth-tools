package com.ghealth.tools.ble.gh3220

import com.ghealth.tools.ble.itlvc.codec.FrameLayout
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class Gh3220PayloadTest {

    @Test
    fun `layout matches itlvc default`() {
        assertSame(FrameLayout.GH3220, Gh3220Layout.layout)
    }

    @Test
    fun `command id constants cover the full matrix`() {
        assertEquals(0x00, Gh3220Cmd.NOP)
        assertEquals(0x01, Gh3220Cmd.ACK)
        assertEquals(0x02, Gh3220Cmd.GET_STATUS)
        assertEquals(0x03, Gh3220Cmd.REG_RW)
        assertEquals(0x04, Gh3220Cmd.IMPEDANCE)
        assertEquals(0x05, Gh3220Cmd.PACKAGE_TEST)
        assertEquals(0x07, Gh3220Cmd.READ_OTP)
        assertEquals(0x08, Gh3220Cmd.RAWDATA)
        assertEquals(0x09, Gh3220Cmd.RAWDATA_ZIP_EVEN)
        assertEquals(0x0A, Gh3220Cmd.RAWDATA_ZIP_ODD)
        assertEquals(0x0B, Gh3220Cmd.RAWDATA_NEW)
        assertEquals(0x0C, Gh3220Cmd.START_CTRL)
        assertEquals(0x0D, Gh3220Cmd.CURRENT_BATTERY)
        assertEquals(0x0E, Gh3220Cmd.ECG_VOLTAGE)
        assertEquals(0x0F, Gh3220Cmd.FW_UPGRADE)
        assertEquals(0x10, Gh3220Cmd.WORK_MODE)
        assertEquals(0x11, Gh3220Cmd.GSENSOR_SET)
        assertEquals(0x12, Gh3220Cmd.FIFO_THR)
        assertEquals(0x13, Gh3220Cmd.EVENT_SET)
        assertEquals(0x14, Gh3220Cmd.DEVICE_EVENT)
        assertEquals(0x15, Gh3220Cmd.FUNC_MAP)
        assertEquals(0x16, Gh3220Cmd.CHIP_EVENT_REPORT)
        assertEquals(0x17, Gh3220Cmd.CHIP_CTRL)
        assertEquals(0x18, Gh3220Cmd.CURRENT_CALIBRATE)
        assertEquals(0x19, Gh3220Cmd.GET_VER)
        assertEquals(0x1A, Gh3220Cmd.CONN_STATUS)
        assertEquals(0x1B, Gh3220Cmd.SAMPLE_RATE)
        assertEquals(0x1C, Gh3220Cmd.SLOT_EN)
        assertEquals(0x1D, Gh3220Cmd.ECG_CTRL)
        assertEquals(0x1E, Gh3220Cmd.WORK_MODE_SET)
        assertEquals(0x1F, Gh3220Cmd.DRV_CFG)
        assertEquals(0x20, Gh3220Cmd.APP_MODULE)
        assertEquals(0x21, Gh3220Cmd.SLAVE_LOG)
        assertEquals(0x22, Gh3220Cmd.LEAD_DET_FREQ)
        assertEquals(0x23, Gh3220Cmd.DUMP_MODE)
        assertEquals(0x24, Gh3220Cmd.SW_AGC)
        assertEquals(0x25, Gh3220Cmd.SAMPLING_STATUS)
        assertEquals(0x26, Gh3220Cmd.RTC_TIME)
        assertEquals(0x2A, Gh3220Cmd.RAWDATA_FIFO)
        assertEquals(0x2D, Gh3220Cmd.SPI_FLASH_TEST)
        assertEquals(0x2E, Gh3220Cmd.SWITCH_CHIP)
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

    @Test
    fun `u8 and u16le reject out-of-range values`() {
        assertFailsWith<IllegalArgumentException> { Gh3220Payload.u8(0x100) }
        assertFailsWith<IllegalArgumentException> { Gh3220Payload.u8(-1) }
        assertFailsWith<IllegalArgumentException> { Gh3220Payload.u16le(0x10000) }
        assertFailsWith<IllegalArgumentException> { Gh3220Payload.u16le(-1) }
    }
}

