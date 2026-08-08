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
        agcInfoHigh: IntArray = intArrayOf(),
        frameCnt: Int = 0
    ) = GhFuncFrame(
        funcId = GhFuncId.TEST1,
        frameCnt = frameCnt,
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

    @Test
    fun `记录帧号序列并随快照返回`() {
        val buffers = TestRawDataBuffers()
        buffers.addFrame(frame(rawdata = intArrayOf(1), frameCnt = 10))
        buffers.addFrame(frame(rawdata = intArrayOf(2), frameCnt = 11))
        val data = buffers.snapshot()
        assertEquals(listOf(10, 11), data.frameCnts)
    }

    @Test
    fun `frameCount 等于已去重帧数`() {
        val buffers = TestRawDataBuffers()
        assertEquals(0, buffers.frameCount())
        buffers.addFrame(frame(frameCnt = 1))
        buffers.addFrame(frame(frameCnt = 2))
        assertEquals(2, buffers.frameCount())
    }

    @Test
    fun `lastConsecutiveCount 统计末尾连续帧号`() {
        val buffers = TestRawDataBuffers()
        assertEquals(0, buffers.lastConsecutiveCount())
        buffers.addFrame(frame(frameCnt = 1))
        assertEquals(1, buffers.lastConsecutiveCount())
        buffers.addFrame(frame(frameCnt = 2))
        buffers.addFrame(frame(frameCnt = 5)) // 断帧
        buffers.addFrame(frame(frameCnt = 6))
        buffers.addFrame(frame(frameCnt = 7))
        assertEquals(3, buffers.lastConsecutiveCount())
    }

    @Test
    fun `lastConsecutiveCount 兼容 32 位帧号回绕`() {
        val buffers = TestRawDataBuffers()
        buffers.addFrame(frame(frameCnt = Int.MAX_VALUE))
        buffers.addFrame(frame(frameCnt = Int.MIN_VALUE))
        assertEquals(2, buffers.lastConsecutiveCount())
    }

    @Test
    fun `lastConsecutiveCount 重复帧号不视为连续`() {
        val buffers = TestRawDataBuffers()
        buffers.addFrame(frame(frameCnt = 5))
        buffers.addFrame(frame(frameCnt = 5))
        buffers.addFrame(frame(frameCnt = 5))
        assertEquals(1, buffers.lastConsecutiveCount())
    }

    @Test
    fun `快照 frameCnts 独立于后续采集`() {
        val buffers = TestRawDataBuffers()
        buffers.addFrame(frame(frameCnt = 1))
        val data = buffers.snapshot()
        buffers.addFrame(frame(frameCnt = 2))
        assertEquals(listOf(1), data.frameCnts)
    }
}
