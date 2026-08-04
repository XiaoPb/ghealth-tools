package com.ghealth.tools.feature.demo

import com.ghealth.tools.ble.protocol.gh3036.GhFuncFrame
import com.ghealth.tools.ble.protocol.gh3036.GhFuncId
import com.ghealth.tools.core.model.FunctionMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FunctionDataBuffersTest {

    private fun frame(
        algoData: IntArray = IntArray(0),
        rawdata: IntArray = IntArray(0),
        phyValue: IntArray = IntArray(0),
        gsData: IntArray = IntArray(0),
        frameCnt: Int = 0,
        funcId: GhFuncId = GhFuncId.HR
    ) = GhFuncFrame().apply {
        this.funcId = funcId
        this.frameCnt = frameCnt
        this.rawdata = rawdata
        this.phyValue = phyValue
        this.gsData = gsData
        this.algoData = algoData
    }

    @Test
    fun `ALGO_RESULT 列返回 algoData 通道历史`() {
        val buffers = FunctionDataBuffers(capacity = 8)
        buffers.addFrame(FunctionMode.HR, frame(algoData = intArrayOf(10, 20, 30), frameCnt = 1))
        buffers.addFrame(FunctionMode.HR, frame(algoData = intArrayOf(40, 50, 60), frameCnt = 2))

        assertEquals(listOf(10f, 40f), buffers.getColumn(FunctionMode.HR, "ALGO_RESULT0"))
        assertEquals(listOf(20f, 50f), buffers.getColumn(FunctionMode.HR, "ALGO_RESULT1"))
        assertEquals(listOf(30f, 60f), buffers.getColumn(FunctionMode.HR, "ALGO_RESULT2"))
    }

    @Test
    fun `空 algoData 时不缓冲 ALGO_RESULT 列`() {
        val buffers = FunctionDataBuffers(capacity = 8)
        buffers.addFrame(FunctionMode.HR, frame(algoData = IntArray(0), frameCnt = 1))

        assertTrue(buffers.getColumn(FunctionMode.HR, "ALGO_RESULT0").isEmpty())
    }

    @Test
    fun `Ipd 列返回 phyValue 通道历史`() {
        val buffers = FunctionDataBuffers(capacity = 8)
        buffers.addFrame(FunctionMode.HR, frame(phyValue = intArrayOf(7, 8), frameCnt = 1))
        buffers.addFrame(FunctionMode.HR, frame(phyValue = intArrayOf(9, 10), frameCnt = 2))

        assertEquals(listOf(7f, 9f), buffers.getColumn(FunctionMode.HR, "Ipd0"))
        assertEquals(listOf(8f, 10f), buffers.getColumn(FunctionMode.HR, "Ipd1"))
    }

    @Test
    fun `Rawdata 列返回 rawdata 通道历史`() {
        val buffers = FunctionDataBuffers(capacity = 8)
        buffers.addFrame(FunctionMode.HR, frame(rawdata = intArrayOf(1, 2), frameCnt = 1))

        assertEquals(listOf(1f), buffers.getColumn(FunctionMode.HR, "Rawdata0"))
    }

    @Test
    fun `FRAME_ID 与 ACC 列返回 scalar 历史`() {
        val buffers = FunctionDataBuffers(capacity = 8)
        buffers.addFrame(FunctionMode.HR, frame(gsData = intArrayOf(100, 200, 300), frameCnt = 5))

        assertEquals(listOf(5f), buffers.getColumn(FunctionMode.HR, "FRAME_ID"))
        assertEquals(listOf(100f), buffers.getColumn(FunctionMode.HR, "ACCX"))
        assertEquals(listOf(200f), buffers.getColumn(FunctionMode.HR, "ACCY"))
        assertEquals(listOf(300f), buffers.getColumn(FunctionMode.HR, "ACCZ"))
    }

    @Test
    fun `clear 清空所有列`() {
        val buffers = FunctionDataBuffers(capacity = 8)
        buffers.addFrame(FunctionMode.HR, frame(algoData = intArrayOf(10), frameCnt = 1))
        buffers.clear()

        assertTrue(buffers.getColumn(FunctionMode.HR, "ALGO_RESULT0").isEmpty())
        assertTrue(buffers.frameIds(FunctionMode.HR).isEmpty())
    }

    @Test
    fun `未知列名返回空`() {
        val buffers = FunctionDataBuffers(capacity = 8)
        buffers.addFrame(FunctionMode.HR, frame(algoData = intArrayOf(10), frameCnt = 1))

        assertTrue(buffers.getColumn(FunctionMode.HR, "Unknown0").isEmpty())
    }

    @Test
    fun `不同功能模式的缓冲相互隔离`() {
        val buffers = FunctionDataBuffers(capacity = 8)
        buffers.addFrame(FunctionMode.HR, frame(algoData = intArrayOf(10), frameCnt = 1))
        buffers.addFrame(FunctionMode.SPO2, frame(algoData = intArrayOf(20), frameCnt = 1))

        assertEquals(listOf(10f), buffers.getColumn(FunctionMode.HR, "ALGO_RESULT0"))
        assertEquals(listOf(20f), buffers.getColumn(FunctionMode.SPO2, "ALGO_RESULT0"))
    }
}
