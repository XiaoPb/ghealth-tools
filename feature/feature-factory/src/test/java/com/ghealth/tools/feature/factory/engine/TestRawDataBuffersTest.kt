package com.ghealth.tools.feature.factory.engine

import com.ghealth.tools.ble.protocol.gh3036.GhFuncFrame
import com.ghealth.tools.ble.protocol.gh3036.GhFuncId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TestRawDataBuffersTest {

    private fun frame(
        rawdata: IntArray = intArrayOf(),
        phyValue: IntArray = intArrayOf(),
        agcInfo: IntArray = intArrayOf(),
        agcInfoHigh: IntArray = intArrayOf()
    ) = GhFuncFrame(
        funcId = GhFuncId.TEST1,
        rawdata = rawdata,
        phyValue = phyValue,
        agcInfo = agcInfo,
        agcInfoHigh = agcInfoHigh
    )

    @Test
    fun `累积 rawdata 与 Ipd pA 按通道保存`() {
        val buffers = TestRawDataBuffers()
        buffers.addFrame(frame(rawdata = intArrayOf(100, 200), phyValue = intArrayOf(1000, 2000)))
        buffers.addFrame(frame(rawdata = intArrayOf(101, 201), phyValue = intArrayOf(1001, 2001)))
        val data = buffers.snapshot()
        assertEquals(listOf(100, 101), data.rawdataByChannel[0])
        assertEquals(listOf(200, 201), data.rawdataByChannel[1])
        assertEquals(listOf(1000, 1001), data.ipdPaByChannel[0])
        assertEquals(listOf(2000, 2001), data.ipdPaByChannel[1])
    }

    @Test
    fun `从 AGC 信息解码 LED 总电流 mA`() {
        val buffers = TestRawDataBuffers()
        // agcL: led_drv0=0xFF [31:24], led_drv_fs=0xFF [23:16] → drv0 = 10*255*255/255 = 2550 (0.1mA)
        // agcH: led_drv1=0xFF → drv1 = 2550 (0.1mA) → sum = 5100 (0.1mA) = 510 mA
        buffers.addFrame(frame(
            agcInfo = intArrayOf((0xFF shl 24) or (0xFF shl 16)),
            agcInfoHigh = intArrayOf(0xFF)
        ))
        val data = buffers.snapshot()
        assertEquals(510.0, data.ledCurrentSumMaByChannel[0])
    }

    @Test
    fun `快照返回独立副本`() {
        val buffers = TestRawDataBuffers()
        buffers.addFrame(frame(rawdata = intArrayOf(1)))
        val data = buffers.snapshot()
        buffers.addFrame(frame(rawdata = intArrayOf(2)))
        assertEquals(listOf(1), data.rawdataByChannel[0])
    }

    @Test
    fun `channelCount 取最大通道索引加一`() {
        val buffers = TestRawDataBuffers()
        buffers.addFrame(frame(rawdata = intArrayOf(1, 2, 3)))
        val data = buffers.snapshot()
        assertEquals(3, data.channelCount)
        assertEquals(false, data.isEmpty)
    }
}
