package com.ghealth.tools.feature.factory.engine

import com.ghealth.tools.ble.protocol.gh3036.AgcPhysicalCodec

/**
 * TEST1 测试窗口内采集到的原始数据快照。
 */
data class CollectedRawData(
    val rawdataByChannel: Map<Int, List<Int>>,
    val ipdPaByChannel: Map<Int, List<Int>>,
    val ledCurrentSumMaByChannel: Map<Int, Double>,
    /** 各通道最后一帧 AGC 解码物理量（gain/取消等级/LED 电流等），用于产测计算核对。 */
    val agcPhysicalByChannel: Map<Int, AgcPhysicalCodec.Physical> = emptyMap(),
    /** 去重后帧号序列（与各通道序列同序）。 */
    val frameCnts: List<Int> = emptyList()
) {
    /** 实际通道数 = 最大通道索引 + 1（rawdata / Ipd pA 两者取大）。 */
    val channelCount: Int
        get() = maxOf(
            rawdataByChannel.keys.maxOrNull()?.plus(1) ?: 0,
            ipdPaByChannel.keys.maxOrNull()?.plus(1) ?: 0
        )

    val isEmpty: Boolean
        get() = rawdataByChannel.isEmpty() && ipdPaByChannel.isEmpty()

    companion object {
        val EMPTY = CollectedRawData(emptyMap(), emptyMap(), emptyMap())
    }
}
