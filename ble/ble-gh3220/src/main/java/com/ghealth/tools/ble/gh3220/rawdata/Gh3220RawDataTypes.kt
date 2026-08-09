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

/**
 * 单帧 rawdata 数据。
 *
 * - [acc]：每通道有符号 int16（大端）。
 * - [rawdata]：原始无符号大端 32bit 位型。
 * - [agc]/[amb]：原始无符号大端 24bit 值。
 * - [results] 的 [Gh3220Result.value]：原始 32bit 小端位型。
 * - [channel]：多功能模式（0x0B bit2=1）下该帧所属通道索引；单功能模式为 null。
 */
data class Gh3220RawDataFrame(
    val dataType: Int,
    val funcId: Int,
    val frameId: Int,
    val acc: IntArray?,
    val rawdata: IntArray?,
    val agc: IntArray?,
    val amb: IntArray?,
    val results: List<Gh3220Result>,
    val channel: Int? = null,
)

/** 算法结果项：ResultTag(1B) + ResultValue(4B LE)；[value] 为原始 32bit 小端位型。 */
data class Gh3220Result(val tag: Int, val value: Int)

/**
 * 0x0B 新结构 rawdata 包。
 *
 * - [channelMask]：大端通道位掩码（设备端 gh_uprotocol.c 实现），bit n = 通道 n 有数据。
 * - [activeChannels]：位掩码置位通道索引（升序）。
 * - [splicePackCount]：Rawdata Flag bits3-4 分包计数。
 * - [splicePackOver]：Rawdata Flag bit5 分包结束标志。
 */
data class Gh3220RawDataPackage(
    val dataType: Int,
    val funcId: Int,
    val channelMask: Int,
    val activeChannels: IntArray,
    val compressed: Boolean,
    val oddPacket: Boolean,
    val multiFunction: Boolean,
    val splicePackCount: Int,
    val splicePackOver: Boolean,
    val frames: List<Gh3220RawDataFrame>,
)

/** 0x2A FIFO 上报。 */
data class Gh3220FifoReport(val fifoId: Int, val rawdata: ByteArray)
