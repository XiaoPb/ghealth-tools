package com.ghealth.tools.feature.demo

import com.ghealth.tools.ble.protocol.gh3036.GhFuncFrame
import com.ghealth.tools.core.model.DeviceType
import com.ghealth.tools.core.model.FunctionMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FunctionDataBuffersTest {

    private fun frame(
        algoData: IntArray = IntArray(0),
        rawdata: IntArray = IntArray(0),
        agcInfo: IntArray = IntArray(0),
        phyValue: IntArray = IntArray(0),
        gsData: IntArray = IntArray(0),
        frameCnt: Int = 0
    ) = GhFuncFrame().apply {
        this.frameCnt = frameCnt
        this.rawdata = rawdata
        this.phyValue = phyValue
        this.gsData = gsData
        this.algoData = algoData
        this.agcInfo = agcInfo
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
    fun `CH 列返回 rawdata 通道历史(GH3220 与 GH3300 用法)`() {
        val buffers = FunctionDataBuffers(capacity = 8)
        buffers.addFrame(FunctionMode.HR, frame(rawdata = intArrayOf(3, 4), frameCnt = 1))
        buffers.addFrame(FunctionMode.HR, frame(rawdata = intArrayOf(5, 6), frameCnt = 2))

        assertEquals(listOf(3f, 5f), buffers.getColumn(FunctionMode.HR, "CH0"))
        assertEquals(listOf(4f, 6f), buffers.getColumn(FunctionMode.HR, "CH1"))
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

    @Test
    fun `availableColumns 仅包含有数据的列 GH3036`() {
        val buffers = FunctionDataBuffers(capacity = 8)
        buffers.addFrame(
            FunctionMode.HR,
            frame(
                rawdata = IntArray(8) { it },
                phyValue = IntArray(4) { it },
                algoData = IntArray(2) { it },
                gsData = intArrayOf(1, 2, 3),
                frameCnt = 1
            )
        )
        val cols = buffers.availableColumns(FunctionMode.HR, DeviceType.GH3036)
        assertEquals(
            listOf(
                "ACCX", "ACCY", "ACCZ", "FRAME_ID",
                "Ipd0", "Ipd1", "Ipd2", "Ipd3",
                "Rawdata0", "Rawdata1", "Rawdata2", "Rawdata3", "Rawdata4", "Rawdata5", "Rawdata6", "Rawdata7",
                "ALGO_RESULT0", "ALGO_RESULT1"
            ),
            cols
        )
    }

    @Test
    fun `availableColumns GH3220 用 CH 列且无 Ipd 与 Rawdata`() {
        val buffers = FunctionDataBuffers(capacity = 8)
        buffers.addFrame(FunctionMode.HR, frame(rawdata = IntArray(2), gsData = intArrayOf(1), frameCnt = 1))
        val cols = buffers.availableColumns(FunctionMode.HR, DeviceType.GH3220)
        assertEquals(listOf("ACCX", "FRAME_ID", "CH0", "CH1"), cols)
    }

    @Test
    fun `availableColumns 无数据时返回空`() {
        val buffers = FunctionDataBuffers(capacity = 8)
        assertEquals(emptyList<String>(), buffers.availableColumns(FunctionMode.HR, DeviceType.GH3036))
    }

    @Test
    fun `availableColumns 随通道数增长动态扩展`() {
        val buffers = FunctionDataBuffers(capacity = 8)
        buffers.addFrame(FunctionMode.HR, frame(rawdata = IntArray(2), frameCnt = 1))
        assertEquals(
            listOf("FRAME_ID", "Rawdata0", "Rawdata1"),
            buffers.availableColumns(FunctionMode.HR, DeviceType.GH3036)
        )
        buffers.addFrame(FunctionMode.HR, frame(rawdata = IntArray(5), frameCnt = 2))
        assertEquals(
            listOf("FRAME_ID", "Rawdata0", "Rawdata1", "Rawdata2", "Rawdata3", "Rawdata4"),
            buffers.availableColumns(FunctionMode.HR, DeviceType.GH3036)
        )
    }

    @Test
    fun `availableColumns 清空后返回空`() {
        val buffers = FunctionDataBuffers(capacity = 8)
        buffers.addFrame(FunctionMode.HR, frame(rawdata = IntArray(2), frameCnt = 1))
        buffers.clear()
        assertEquals(emptyList<String>(), buffers.availableColumns(FunctionMode.HR, DeviceType.GH3036))
    }

    @Test
    fun `gh3220 mapped frame fills CH and AGC columns`() {
        val buffers = FunctionDataBuffers(capacity = 8)
        // 环形缓冲语义：每次 addFrame 每通道只追加 1 个点；两帧后 CH/ALGO_RESULT/ACC 历史长度均为 2。
        // agcInfo/phyValue 仅随帧携带（CSV 列），不产生 AGC_INFO_CH/AMB_CH 波形列（Task 7 范围外）。
        buffers.addFrame(
            FunctionMode.ADT,
            frame(
                rawdata = intArrayOf(0x01020304, 0x05060708),
                agcInfo = intArrayOf(0x010203),
                phyValue = intArrayOf(0x0A, 0x0B),
                algoData = intArrayOf(0xDEADBEEF.toInt()),
                gsData = intArrayOf(1, 2, 3),
                frameCnt = 1,
            )
        )
        buffers.addFrame(
            FunctionMode.ADT,
            frame(
                rawdata = intArrayOf(0x11121314, 0x15161718),
                agcInfo = intArrayOf(0x040506),
                phyValue = intArrayOf(0x0C, 0x0D),
                algoData = intArrayOf(0xCAFEBABE.toInt()),
                gsData = intArrayOf(4, 5, 6),
                frameCnt = 2,
            )
        )

        assertEquals(2, buffers.getColumn(FunctionMode.ADT, "CH0").size)
        assertEquals(2, buffers.getColumn(FunctionMode.ADT, "CH1").size)
        assertEquals(2, buffers.getColumn(FunctionMode.ADT, "ALGO_RESULT0").size)
        assertEquals(2, buffers.getColumn(FunctionMode.ADT, "ACCX").size)
        // 帧桥展开后的 CH 列按帧追加，值与 rawdata 槽位一致（Int→Float 同源转换，避免舍入误差）
        assertEquals(
            listOf(0x01020304.toFloat(), 0x11121314.toFloat()),
            buffers.getColumn(FunctionMode.ADT, "CH0"),
        )
        assertEquals(
            listOf(0x05060708.toFloat(), 0x15161718.toFloat()),
            buffers.getColumn(FunctionMode.ADT, "CH1"),
        )
        assertEquals(
            listOf(0xDEADBEEF.toInt().toFloat(), 0xCAFEBABE.toInt().toFloat()),
            buffers.getColumn(FunctionMode.ADT, "ALGO_RESULT0"),
        )
        assertEquals(listOf(1f, 4f), buffers.getColumn(FunctionMode.ADT, "ACCX"))
    }
}
