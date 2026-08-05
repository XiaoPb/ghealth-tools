package com.ghealth.tools.ble.protocol.gh3036

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AgcPhysicalCodecTest {

    @Test
    fun `decode 提取 gain 位于低 4 位`() {
        // agcL = 0x05 → gain=5
        val p = AgcPhysicalCodec.decode(0x05, 0)
        assertEquals(5, p.gain)
    }

    @Test
    fun `decode 提取 bg_cancel_level 位于 bit 4-5`() {
        // agcL = 0x10 → bg_cancel_level=1
        val p = AgcPhysicalCodec.decode(0x10, 0)
        assertEquals(1, p.bgCancelLevel)
    }

    @Test
    fun `decode 提取 dc_cancel_level 位于 bit 6-7`() {
        // agcL = 0x40 → dc_cancel_level=1
        val p = AgcPhysicalCodec.decode(0x40, 0)
        assertEquals(1, p.dcCancelLevel)
    }

    @Test
    fun `decode 提取 dc_cancel_code 位于 bit 8-15`() {
        // agcL = 0x0300 → dc_cancel_code=3
        val p = AgcPhysicalCodec.decode(0x0300, 0)
        assertEquals(3, p.dcCancelCode)
    }

    @Test
    fun `decode led_drv_fs=255 且 led_drv0=255 时 drv0 电流为 2550`() {
        // led_drv_fs=[23:16]=0xFF, led_drv0=[31:24]=0xFF
        val agcL = (0xFF shl 16) or (0xFF shl 24)
        val p = AgcPhysicalCodec.decode(agcL, 0)
        // led_drv0_ma = 10 * 255 * 255 / 255 = 2550
        assertEquals(2550, p.ledCurrentDrv0)
    }

    @Test
    fun `decode led_drv1 来自 agcH 低 8 位`() {
        // led_drv_fs=255, led_drv1=128
        val agcL = 0xFF shl 16
        val p = AgcPhysicalCodec.decode(agcL, 128)
        // led_drv1_ma = 10 * 128 * 255 / 255 = 1280
        assertEquals(1280, p.ledCurrentDrv1)
    }

    @Test
    fun `decode led_current_sum 等于 drv0 与 drv1 电流之和`() {
        val agcL = (0xFF shl 16) or (0xFF shl 24) // led_drv_fs=255, led_drv0=255
        val agcH = 128                            // led_drv1=128
        val p = AgcPhysicalCodec.decode(agcL, agcH)
        assertEquals(2550, p.ledCurrentDrv0)
        assertEquals(1280, p.ledCurrentDrv1)
        assertEquals(3830, p.ledCurrentSum)
    }

    @Test
    fun `decode led_drv_fs 为 0 时电流为 0`() {
        val agcL = (0xFF shl 24) // led_drv0=255, led_drv_fs=0
        val p = AgcPhysicalCodec.decode(agcL, 0xFF) // led_drv1=255
        assertEquals(0, p.ledCurrentDrv0)
        assertEquals(0, p.ledCurrentDrv1)
        assertEquals(0, p.ledCurrentSum)
    }

    @Test
    fun `decode agcL 高位为 1 时 led_drv0 正确提取为 128`() {
        // 0x80FF0000: led_drv0=[31:24]=0x80=128, led_drv_fs=[23:16]=0xFF=255
        // Int bit31=1 即负数，验证 ushr 不被符号截断
        val agcL = (0x80 shl 24) or (0xFF shl 16)
        val p = AgcPhysicalCodec.decode(agcL, 0)
        // led_drv0_ma = 10 * 128 * 255 / 255 = 1280
        assertEquals(1280, p.ledCurrentDrv0)
    }

    @Test
    fun `encodeAgcInfoColumn 打包 gain bg dc dc_code 与 led_current_sum 位域`() {
        val p = AgcPhysicalCodec.Physical(
            gain = 0xA,
            bgCancelLevel = 0x2,
            dcCancelLevel = 0x1,
            dcCancelCode = 0x37,
            ledCurrentSum = 0x1234,
            ledCurrentDrv0 = 0,
            ledCurrentDrv1 = 0
        )
        val encoded = AgcPhysicalCodec.encodeAgcInfoColumn(p)
        assertEquals(0xA, encoded and 0x0F)
        assertEquals(0x2, (encoded ushr 4) and 0x03)
        assertEquals(0x1, (encoded ushr 6) and 0x03)
        assertEquals(0x37, (encoded ushr 8) and 0xFF)
        assertEquals(0x1234, (encoded ushr 16) and 0x3FFF)
    }

    @Test
    fun `encodeLedInfoColumn 打包 drv0 与 drv1 位域`() {
        val p = AgcPhysicalCodec.Physical(
            gain = 0, bgCancelLevel = 0, dcCancelLevel = 0, dcCancelCode = 0,
            ledCurrentSum = 0,
            ledCurrentDrv0 = 0xABC,
            ledCurrentDrv1 = 0x123
        )
        val encoded = AgcPhysicalCodec.encodeLedInfoColumn(p)
        assertEquals(0xABC, encoded and 0x0FFF)
        assertEquals(0x123, (encoded ushr 12) and 0x0FFF)
    }

    @Test
    fun `encode 端到端保留 gain bg dc dc_code 位不变`() {
        // 原始 agcL 低 16 位 = gain|bg|dc|dcCode，encode 后这些位应不变
        val agcL = 0x37C5 // gain=5, bg=0, dc=3, dcCode=0x37
        val p = AgcPhysicalCodec.decode(agcL, 0)
        val encoded = AgcPhysicalCodec.encodeAgcInfoColumn(p)
        assertEquals(agcL and 0xFFFF, encoded and 0xFFFF)
    }

    @Test
    fun `encodeColumns 批量转换长度一致且逐通道正确`() {
        val agcInfo = intArrayOf(
            (0xFF shl 16) or (0xFF shl 24), // ch0: drv0=255, fs=255 → drv0_ma=2550
            0x00000000                      // ch1: 全 0
        )
        val agcInfoHigh = intArrayOf(128, 0) // ch0: drv1=128 → drv1_ma=1280
        val (packedAgc, packedLed) = AgcPhysicalCodec.encodeColumns(agcInfo, agcInfoHigh)
        assertEquals(2, packedAgc.size)
        assertEquals(2, packedLed.size)
        // ch0: led_current_sum=3830 → packedAgc[0] [29:16]=3830
        assertEquals(3830, (packedAgc[0] ushr 16) and 0x3FFF)
        assertEquals(2550, packedLed[0] and 0x0FFF)
        assertEquals(1280, (packedLed[0] ushr 12) and 0x0FFF)
        // ch1: 全 0
        assertEquals(0, packedAgc[1])
        assertEquals(0, packedLed[1])
    }

    @Test
    fun `encodeColumns 长度不一致时短数组按 0 补齐`() {
        val agcInfo = intArrayOf(0xFF shl 16) // led_drv_fs=255, led_drv0=0 → drv0_ma=0
        val agcInfoHigh = intArrayOf()        // 空
        val (packedAgc, _) = AgcPhysicalCodec.encodeColumns(agcInfo, agcInfoHigh)
        assertEquals(1, packedAgc.size)
        // agcH 视为 0 → led_drv1=0 → drv1_ma=0, sum=0
        assertEquals(0, (packedAgc[0] ushr 16) and 0x3FFF)
    }

    @Test
    fun `最大电流不超出位宽上限`() {
        // led_drv_fs=255, led_drv0=255, led_drv1=255
        val agcL = (0xFF shl 16) or (0xFF shl 24)
        val p = AgcPhysicalCodec.decode(agcL, 0xFF)
        assertEquals(2550, p.ledCurrentDrv0)
        assertEquals(2550, p.ledCurrentDrv1)
        assertEquals(5100, p.ledCurrentSum)
        val encodedAgc = AgcPhysicalCodec.encodeAgcInfoColumn(p)
        val encodedLed = AgcPhysicalCodec.encodeLedInfoColumn(p)
        assertEquals(5100, (encodedAgc ushr 16) and 0x3FFF)
        assertEquals(2550, encodedLed and 0x0FFF)
        assertEquals(2550, (encodedLed ushr 12) and 0x0FFF)
    }
}
