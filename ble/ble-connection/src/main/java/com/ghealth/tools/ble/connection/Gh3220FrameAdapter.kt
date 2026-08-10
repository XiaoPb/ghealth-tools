package com.ghealth.tools.ble.connection

import com.ghealth.tools.ble.gh3220.rawdata.Gh3220RawDataFrame
import com.ghealth.tools.ble.gh3220.rawdata.Gh3220RawDataPackage
import com.ghealth.tools.ble.protocol.gh3220.Gh3220FrameDecoder
import com.ghealth.tools.ble.protocol.gh3220.Gh3220FuncId
import com.ghealth.tools.ble.protocol.gh3036.GhFuncFrame

/**
 * 把新 ITLVC GH3220 帧映射为演示层既有的 [GhFuncFrame]，列对齐 .claude/csv_rules/gh3220.yaml：
 * rawdata→CH{0-31}、acc→ACCX/Y/Z、agc→AGC_INFO_CH{0-31}、amb→AMB_CH{0-15}、results→ALGO_RESULT{0-15}。
 * 注意：0x0B 结果段为 (tag 1B + value 4B LE) 对，tag bit7=0 的是设备内部标志（0=Soft AGC 主通道、
 * 2/3/4=flag2/3/4），按 tag 填进 flags（FLAG{0-7} 列）；仅 tag bit7=1（低位=resultCnt）的算法结果
 * 按序映射进 algoData（ALGO_RESULT 列）。gyro（dataType bit4，紧接 GS 的 3×int16 BE）映射为 gyro。
 * funcId 必须经 [Gh3220FrameDecoder.mapToCommonFuncId] 翻译：GH3220 数字 ID 与公共 GhFuncId 空间不对齐
 * （如 GH3220 6=SPO2、7=ECG，公共 6=TEST1、7=TEST2），直接透传会让演示层按错误功能路由。
 *
 * 两种帧形态（映射行为不同）：
 * - 单功能包（0x08/09/0A 与 0x0B 非多功能，[Gh3220RawDataFrame.channel] == null）：帧内已含全部通道
 *   数组，直接映射；
 * - 多功能 0x0B（channel != null）：每帧仅含单个通道的 rawdata/agc/amb 值，映射时按通道索引展开到
 *   [RAW_CHANNEL_SLOTS]/[AGC_CHANNEL_SLOTS]/[AMB_CHANNEL_SLOTS] 槽位数组（数组索引即消费方通道列），
 *   其余槽位补 0。展开必须发生在 [toGhFuncFrame]：manager 只订阅 `client.rawdataFrames` 并逐帧转发，
 *   若仅在 [toGhFuncFrames] 展开，经 manager 路径的多功能帧通道列会错位。
 */
object Gh3220FrameAdapter {

    /** rawdata 槽位数，对应消费方 `DemoViewModel.toColumnMap`（DeviceType.GH3220/GH3300 分支）CH{0-31} 列。 */
    const val RAW_CHANNEL_SLOTS = 32

    /** agc 槽位数，对应消费方 `DemoViewModel.toColumnMap`（DeviceType.GH3220/GH3300 分支）AGC_INFO_CH{0-31} 列。 */
    const val AGC_CHANNEL_SLOTS = 32

    /** amb 槽位数，对应消费方 `DemoViewModel.toColumnMap`（DeviceType.GH3220/GH3300 分支）AMB_CH{0-15} 列。 */
    const val AMB_CHANNEL_SLOTS = 16

    /** flag 槽位数，对应 FLAG{0-7} 列；0x0B 结果段 tag bit7=0 的标志按 tag 索引填入。 */
    const val FLAG_SLOTS = 8

    /**
     * GH3220 结果段 flag2 的 bit1(0x02) = 首帧标志（frameCnt==0），即一次新测试的开始
     * （设备端 Gh3x2xSetFrameFlag2：`if (frameCnt==0) flag[2] |= 0x02`）。
     * RecordingManager 以此轮转 server CSV，而不再用 FRAME_ID（8 位帧计数每 256 帧自然回绕，
     * 用 FRAME_ID==0 会把同一个测试误切成多个文件）。
     */
    const val GH3220_FLAG2_NEW_TEST_MASK = 0x02

    /** 该帧是否标记了一次新测试的开始（FLAG2 bit1 置位）。 */
    fun isNewTestFrame(frame: GhFuncFrame): Boolean =
        (frame.flags.getOrNull(2) ?: 0) and GH3220_FLAG2_NEW_TEST_MASK != 0

    fun toGhFuncFrame(frame: Gh3220RawDataFrame): GhFuncFrame = GhFuncFrame().apply {
        funcId = Gh3220FrameDecoder.mapToCommonFuncId(Gh3220FuncId.from(frame.funcId))
        frameCnt = frame.frameId
        timestamp = System.currentTimeMillis()
        val channel = frame.channel
        rawdata = if (channel != null) {
            (frame.rawdata ?: IntArray(0)).expandToChannelSlots(channel, RAW_CHANNEL_SLOTS)
        } else {
            frame.rawdata ?: IntArray(0)
        }
        gsData = frame.acc ?: IntArray(0)
        gyro = frame.gyro ?: IntArray(0)
        flags = IntArray(FLAG_SLOTS).also { arr ->
            frame.results.forEach { if (it.tag < 0x80 && it.tag < FLAG_SLOTS) arr[it.tag] = it.value }
        }
        agcInfo = if (channel != null) {
            (frame.agc ?: IntArray(0)).expandToChannelSlots(channel, AGC_CHANNEL_SLOTS)
        } else {
            frame.agc ?: IntArray(0)
        }
        phyValue = if (channel != null) {
            (frame.amb ?: IntArray(0)).expandToChannelSlots(channel, AMB_CHANNEL_SLOTS)
        } else {
            frame.amb ?: IntArray(0)
        }
        // 结果段按 (tag 1B + value 4B LE) 组织：tag bit7=0 为内部标志（0=Soft AGC、2/3/4=flag2/3/4），
        // 仅 bit7=1（低位 resultCnt）的算法结果按序映射，避免标志值污染 ALGO_RESULT 列（如 HR 误显示 1 BPM）。
        algoData = frame.results.filter { (it.tag and 0x80) != 0 }.map { it.value }.toIntArray()
    }

    /**
     * 0x0B 包逐帧映射：单功能包（channel == null）直接映射；多功能包经 [toGhFuncFrame] 按通道索引展开，
     * 保证 FunctionDataBuffers 的 CH/AGC_INFO_CH/AMB_CH 列按通道对齐。
     */
    fun toGhFuncFrames(pkg: Gh3220RawDataPackage): List<GhFuncFrame> =
        pkg.frames.map { toGhFuncFrame(it) }

    /** 把本数组从 [channel] 槽位起顺次放入长度 [slotCount] 的槽位数组，越界截断，其余槽位为 0；channel 必须非负。 */
    private fun IntArray.expandToChannelSlots(channel: Int, slotCount: Int): IntArray {
        require(channel >= 0) { "channel must be non-negative: $channel" }
        val slots = IntArray(slotCount)
        for (i in indices) {
            val target = channel + i
            if (target >= slotCount) break
            slots[target] = this[i]
        }
        return slots
    }
}
