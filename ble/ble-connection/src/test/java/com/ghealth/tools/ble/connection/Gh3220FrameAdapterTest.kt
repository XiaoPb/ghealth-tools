package com.ghealth.tools.ble.connection

import com.ghealth.tools.ble.gh3220.rawdata.Gh3220RawDataFrame
import com.ghealth.tools.ble.gh3220.rawdata.Gh3220RawDataPackage
import com.ghealth.tools.ble.gh3220.rawdata.Gh3220Result
import com.ghealth.tools.ble.protocol.gh3036.GhFuncId
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * [Gh3220FrameAdapter] 帧桥映射测试：0x0B 分包/多通道帧槽位展开 + 单功能包直映射回归。
 *
 * - 多功能 0x0B（[Gh3220RawDataFrame.channel] != null）：每帧仅含单个通道的 rawdata/agc/amb 值，
 *   映射时按通道索引展开到 CH{0-31}/AGC_INFO_CH{0-31}/AMB_CH{0-15} 槽位（数组索引即演示层通道列），
 *   其余槽位补 0；展开必须发生在 [Gh3220FrameAdapter.toGhFuncFrame]（manager 只订阅 rawdataFrames
 *   逐帧转发，若仅在 toGhFuncFrames 展开则经理路径的通道列会错位）。
 * - 单功能包（channel == null，0x08/09/0A 与 0x0B 非多功能）：帧内已含全部通道数组，保持直接映射。
 */
class Gh3220FrameAdapterTest {

    /** 单帧构造 helper，参数顺序对应 Gh3220RawDataFrame(dataType, funcId, frameId, acc, rawdata, agc, amb, results, channel)。 */
    private fun frame(
        dataType: Int = 0,
        funcId: Int = 0,
        frameId: Int = 0,
        acc: IntArray? = null,
        rawdata: IntArray? = null,
        agc: IntArray? = null,
        amb: IntArray? = null,
        results: List<Gh3220Result> = emptyList(),
        channel: Int? = null,
    ) = Gh3220RawDataFrame(
        dataType = dataType,
        funcId = funcId,
        frameId = frameId,
        acc = acc,
        rawdata = rawdata,
        agc = agc,
        amb = amb,
        results = results,
        channel = channel,
    )

    @Test
    fun `0x0B package maps each channel frame to gh func frame with slot expansion`() {
        // 注：真实 0x0B 多功能是每包恰 1 个通道位、多包爆发（RawDataDecoder.decode0B 校验 multiFunction
        // 时 activeChannels.size == 1）；此处为包级便利构造，一次覆盖 0/2 双通道槽位展开，不代表单包可携带多通道。
        val pkg = Gh3220RawDataPackage(
            dataType = 0x0B,
            funcId = 6,
            channelMask = 0b101,
            activeChannels = intArrayOf(0, 2),
            compressed = false,
            oddPacket = false,
            multiFunction = true,
            splicePackCount = 0,
            splicePackOver = true,
            frames = listOf(
                frame(
                    funcId = 6,
                    frameId = 0,
                    acc = intArrayOf(1, 2, 3),
                    rawdata = intArrayOf(11),
                    agc = intArrayOf(101),
                    amb = intArrayOf(201),
                    results = listOf(Gh3220Result(1, 0xDEADBEEF.toInt())),
                    channel = 0,
                ),
                frame(
                    funcId = 6,
                    frameId = 1,
                    rawdata = intArrayOf(22),
                    agc = intArrayOf(102),
                    amb = intArrayOf(202),
                    channel = 2,
                ),
            ),
        )

        val out = Gh3220FrameAdapter.toGhFuncFrames(pkg)

        assertEquals(2, out.size)
        // funcId 翻译（GH3220 6=SPO2，公共 6=TEST1）与 frameCnt 透传
        assertEquals(GhFuncId.SPO2, out[0].funcId)
        assertEquals(0, out[0].frameCnt)
        assertEquals(1, out[1].frameCnt)
        // rawdata 展开到 CH{0-31} 槽位：值落在通道索引处，其余补 0
        val expectedRaw0 = IntArray(32).also { it[0] = 11 }
        val expectedRaw1 = IntArray(32).also { it[2] = 22 }
        assertContentEquals(expectedRaw0, out[0].rawdata)
        assertContentEquals(expectedRaw1, out[1].rawdata)
        // agc 展开到 AGC_INFO_CH{0-31}
        val expectedAgc0 = IntArray(32).also { it[0] = 101 }
        val expectedAgc1 = IntArray(32).also { it[2] = 102 }
        assertContentEquals(expectedAgc0, out[0].agcInfo)
        assertContentEquals(expectedAgc1, out[1].agcInfo)
        // amb 展开到 AMB_CH{0-15}
        val expectedAmb0 = IntArray(16).also { it[0] = 201 }
        val expectedAmb1 = IntArray(16).also { it[2] = 202 }
        assertContentEquals(expectedAmb0, out[0].phyValue)
        assertContentEquals(expectedAmb1, out[1].phyValue)
        // acc→gsData、results→algoData 直映射
        assertContentEquals(intArrayOf(1, 2, 3), out[0].gsData)
        assertContentEquals(intArrayOf(0xDEADBEEF.toInt()), out[0].algoData)
        assertEquals(0, out[1].algoData.size)
    }

    @Test
    fun `toGhFuncFrame expands single channel frame into channel slots`() {
        val gh = Gh3220FrameAdapter.toGhFuncFrame(
            frame(frameId = 5, rawdata = intArrayOf(77), agc = intArrayOf(88), amb = intArrayOf(99), channel = 4),
        )
        val expectedRaw = IntArray(32).also { it[4] = 77 }
        val expectedAgc = IntArray(32).also { it[4] = 88 }
        val expectedAmb = IntArray(16).also { it[4] = 99 }
        assertContentEquals(expectedRaw, gh.rawdata)
        assertContentEquals(expectedAgc, gh.agcInfo)
        assertContentEquals(expectedAmb, gh.phyValue)
    }

    @Test
    fun `slot expansion places values from channel index and truncates at boundary`() {
        val gh = Gh3220FrameAdapter.toGhFuncFrame(
            frame(
                frameId = 3,
                rawdata = intArrayOf(11, 22, 33),
                agc = intArrayOf(44, 55),
                amb = intArrayOf(66, 77),
                channel = 30,
            ),
        )
        // rawdata 32 槽：槽 30=11、槽 31=22，第 3 个值越界截断
        val expectedRaw = IntArray(32).also { it[30] = 11; it[31] = 22 }
        assertContentEquals(expectedRaw, gh.rawdata)
        // agc 32 槽：槽 30=44、槽 31=55
        val expectedAgc = IntArray(32).also { it[30] = 44; it[31] = 55 }
        assertContentEquals(expectedAgc, gh.agcInfo)
        // amb 仅 16 槽：channel=30 全部越界 → 全 0
        assertContentEquals(IntArray(16), gh.phyValue)
    }

    @Test
    fun `negative channel slot expansion is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Gh3220FrameAdapter.toGhFuncFrame(
                frame(frameId = 9, rawdata = intArrayOf(1), channel = -1),
            )
        }
    }

    @Test
    fun `0x0B non multifunction package keeps full channel arrays direct`() {
        val pkg = Gh3220RawDataPackage(
            dataType = 0x0B,
            funcId = 7,
            channelMask = 0b11,
            activeChannels = intArrayOf(0, 1),
            compressed = false,
            oddPacket = false,
            multiFunction = false,
            splicePackCount = 0,
            splicePackOver = true,
            frames = listOf(
                frame(
                    funcId = 7,
                    frameId = 0,
                    rawdata = intArrayOf(11, 22),
                    agc = intArrayOf(101, 102),
                    amb = intArrayOf(201, 202),
                ),
                frame(funcId = 7, frameId = 1, rawdata = intArrayOf(33, 44)),
            ),
        )

        val out = Gh3220FrameAdapter.toGhFuncFrames(pkg)

        assertEquals(2, out.size)
        // GH3220 7=ECG，公共 7=TEST2
        assertEquals(GhFuncId.ECG, out[0].funcId)
        assertContentEquals(intArrayOf(11, 22), out[0].rawdata)
        assertContentEquals(intArrayOf(101, 102), out[0].agcInfo)
        assertContentEquals(intArrayOf(201, 202), out[0].phyValue)
        assertContentEquals(intArrayOf(33, 44), out[1].rawdata)
    }

    @Test
    fun `0x08 single frame maps directly without slot expansion`() {
        val gh = Gh3220FrameAdapter.toGhFuncFrame(
            frame(
                funcId = 6,
                frameId = 7,
                acc = intArrayOf(1, 2, 3),
                rawdata = intArrayOf(0x01020304, 0x05060708),
                agc = intArrayOf(0x010203),
                amb = intArrayOf(0x0A, 0x0B),
                results = listOf(Gh3220Result(1, 0xDEADBEEF.toInt())),
                channel = null,
            ),
        )
        // GH3220 6=SPO2：映射到公共枚举必须是 SPO2（公共 6 是 TEST1），直接透传会路由错误。
        assertEquals(GhFuncId.SPO2, gh.funcId)
        assertEquals(7, gh.frameCnt)
        assertContentEquals(intArrayOf(0x01020304, 0x05060708), gh.rawdata)
        assertContentEquals(intArrayOf(1, 2, 3), gh.gsData)
        assertContentEquals(intArrayOf(0x010203), gh.agcInfo)
        assertContentEquals(intArrayOf(0x0A, 0x0B), gh.phyValue)
        assertContentEquals(intArrayOf(0xDEADBEEF.toInt()), gh.algoData)
    }
}
