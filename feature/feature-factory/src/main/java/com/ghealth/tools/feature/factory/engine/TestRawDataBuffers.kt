package com.ghealth.tools.feature.factory.engine

import com.ghealth.tools.ble.protocol.gh3036.AgcPhysicalCodec
import com.ghealth.tools.ble.protocol.gh3036.GhFuncFrame

/**
 * 按通道累积 TEST1 帧原始数据（rawdata、Ipd pA、LED 总电流 mA）。
 * 非线程安全，由 [TestRawDataCollector] 在单一协程内调用。
 */
class TestRawDataBuffers {

    private val rawdataByChannel = mutableMapOf<Int, MutableList<Int>>()
    private val ipdPaByChannel = mutableMapOf<Int, MutableList<Int>>()
    private val ledSumMaByChannel = mutableMapOf<Int, Double>()

    fun addFrame(frame: GhFuncFrame) {
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

    fun snapshot(): CollectedRawData = CollectedRawData(
        rawdataByChannel = rawdataByChannel.mapValues { it.value.toList() },
        ipdPaByChannel = ipdPaByChannel.mapValues { it.value.toList() },
        ledCurrentSumMaByChannel = ledSumMaByChannel.toMap()
    )
}
