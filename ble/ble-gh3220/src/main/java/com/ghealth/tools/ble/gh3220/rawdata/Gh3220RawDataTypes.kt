package com.ghealth.tools.ble.gh3220.rawdata

/**
 * 采样配置。channelCount 由 0x15 通道映射 / 0x0C 启动配置驱动；
 * 0x09/0x0A/0x0B 的字段存在性按此配置解析。
 */
data class SamplingConfig(
    val channelCount: Int = 1,
    val gsEnabled: Boolean = false,
    val agcEnabled: Boolean = false,
    val ambEnabled: Boolean = false,
    val algoEnabled: Boolean = false,
)

/** 单帧 rawdata 数据。 */
data class Gh3220RawDataFrame(
    val dataType: Int,
    val funcId: Int,
    val frameId: Int,
    val acc: IntArray?,
    val rawdata: IntArray?,
    val agc: IntArray?,
    val amb: IntArray?,
    val results: List<Gh3220Result>,
)

/** 算法结果项：ResultTag(1B) + ResultValue(4B LE)。 */
data class Gh3220Result(val tag: Int, val value: Int)

/** 0x2A FIFO 上报。 */
data class Gh3220FifoReport(val fifoId: Int, val rawdata: ByteArray)
