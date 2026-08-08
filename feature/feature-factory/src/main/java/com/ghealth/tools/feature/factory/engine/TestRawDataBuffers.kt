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
    private val frameCnts = mutableListOf<Int>()

    fun addFrame(frame: GhFuncFrame) {
        frameCnts.add(frame.frameCnt)
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
        }
    }

    /** 已去重有效帧数。 */
    fun frameCount(): Int = frameCnts.size

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
        frameCnts = frameCnts.toList()
    )
}
