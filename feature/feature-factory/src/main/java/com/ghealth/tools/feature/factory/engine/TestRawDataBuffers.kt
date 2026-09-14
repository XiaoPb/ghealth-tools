package com.ghealth.tools.feature.factory.engine

import com.ghealth.tools.ble.protocol.gh3036.AgcPhysicalCodec
import com.ghealth.tools.ble.protocol.gh3036.GhFuncFrame

/**
 * 按通道累积 TEST1 帧原始数据（rawdata、Ipd pA、LED 总电流 mA），并记录去重后帧号序列。
 * 非线程安全，由 [TestRawDataCollector] 在单一协程内调用。
 */
class TestRawDataBuffers {

    private val rawdataByChannel = mutableMapOf<Int, MutableList<Int>>()
    private val ipdPaByChannel = mutableMapOf<Int, MutableList<Int>>()
    private val ledSumMaByChannel = mutableMapOf<Int, Double>()
    private val agcPhysicalByChannel = mutableMapOf<Int, AgcPhysicalCodec.Physical>()
    private val frameCnts = mutableListOf<Int>()
    private var previousAgcInfo: IntArray? = null
    private var previousAgcInfoHigh: IntArray? = null
    private var stableAgcFrames = 0

    fun addFrame(frame: GhFuncFrame) {
        frameCnts.add(frame.frameCnt)
        updateStableAgcCount(frame)
        frame.rawdata.forEachIndexed { ch, v ->
            rawdataByChannel.getOrPut(ch) { mutableListOf() }.add(v)
        }
        frame.phyValue.forEachIndexed { ch, v ->
            ipdPaByChannel.getOrPut(ch) { mutableListOf() }.add(v)
        }
        for (ch in frame.agcInfo.indices) {
            val agcH = frame.agcInfoHigh.getOrElse(ch) { 0 }
            val physical = AgcPhysicalCodec.decode(frame.agcInfo[ch], agcH)
            ledSumMaByChannel[ch] = physical.ledCurrentSum / 10.0 // 0.1mA → mA
            agcPhysicalByChannel[ch] = physical
        }
    }

    /** 已去重有效帧数。 */
    fun frameCount(): Int = frameCnts.size

    /** 末尾有效 AGC 位不变的帧数；高位最高填充字节不参与比较。 */
    fun stableAgcFrameCount(): Int = stableAgcFrames

    /** 末尾连续帧号长度；空序列返回 0。`(cur - prev) and 0xFFFFFFFF == 1` 兼容 32 位回绕。 */
    fun lastConsecutiveCount(): Int {
        if (frameCnts.isEmpty()) return 0
        var count = 1
        var i = frameCnts.size - 1
        while (i > 0) {
            val prev = frameCnts[i - 1].toLong()
            val cur = frameCnts[i].toLong()
            if (((cur - prev) and 0xFFFFFFFFL) != 1L) break
            count++
            i--
        }
        return count
    }

    fun snapshot(): CollectedRawData = CollectedRawData(
        rawdataByChannel = rawdataByChannel.mapValues { it.value.toList() },
        ipdPaByChannel = ipdPaByChannel.mapValues { it.value.toList() },
        ledCurrentSumMaByChannel = ledSumMaByChannel.toMap(),
        agcPhysicalByChannel = agcPhysicalByChannel.toMap(),
        frameCnts = frameCnts.toList()
    )

    private fun updateStableAgcCount(frame: GhFuncFrame) {
        if (frame.agcInfo.isEmpty() && frame.agcInfoHigh.isEmpty()) {
            previousAgcInfo = null
            previousAgcInfoHigh = null
            stableAgcFrames = 0
            return
        }

        val normalizedHigh = IntArray(frame.agcInfoHigh.size) { index ->
            frame.agcInfoHigh[index] and AGC_HIGH_VALID_MASK
        }
        val unchanged = previousAgcInfo?.contentEquals(frame.agcInfo) == true &&
            previousAgcInfoHigh?.contentEquals(normalizedHigh) == true
        stableAgcFrames = if (unchanged) stableAgcFrames + 1 else 1
        previousAgcInfo = frame.agcInfo.copyOf()
        previousAgcInfoHigh = normalizedHigh
    }

    private companion object {
        const val AGC_HIGH_VALID_MASK = 0x00FFFFFF
    }
}
