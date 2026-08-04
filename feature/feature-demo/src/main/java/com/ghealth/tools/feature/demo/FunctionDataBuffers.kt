package com.ghealth.tools.feature.demo

import com.ghealth.tools.ble.protocol.gh3036.GhFuncFrame
import com.ghealth.tools.core.model.DeviceType
import com.ghealth.tools.core.model.FunctionMode

/**
 * 按功能模式缓存各路波形/标量数据的环形缓冲区集合,供演示页波形列读取。
 *
 * 缓冲的列族:
 * - [GhFuncFrame.rawdata] → `RawdataN` / `CHN` 列(GH3036 用 Rawdata,GH3220/GH3300 用 CH)
 * - [GhFuncFrame.phyValue] → `IpdN` 列
 * - [GhFuncFrame.algoData] → `ALGO_RESULTN` 列
 * - `gsData` + `frameCnt` → `ACCX/ACCY/ACCZ` / `FRAME_ID` 标量列
 *
 * 每个功能模式独立缓冲;数据满 [capacity] 后按时间顺序滚动覆盖最早数据。
 */
internal class FunctionDataBuffers(private val capacity: Int = 500) {
    private val rawdataBuffers = mutableMapOf<FunctionMode, MultiChannelRingBuffer>()
    private val phyBuffers = mutableMapOf<FunctionMode, MultiChannelRingBuffer>()
    private val algoBuffers = mutableMapOf<FunctionMode, MultiChannelRingBuffer>()
    private val scalarBuffers = mutableMapOf<FunctionMode, MultiChannelRingBuffer>()
    private val maxGsChannels = mutableMapOf<FunctionMode, Int>()

    /** 将一帧数据写入对应功能模式的缓冲区(空数组跳过,标量帧始终写入)。 */
    fun addFrame(funcMode: FunctionMode, frame: GhFuncFrame) {
        if (frame.rawdata.isNotEmpty()) {
            rawdataBuffers.getOrPut(funcMode) { MultiChannelRingBuffer(maxChannels = 32, capacity = capacity) }
                .addFrame(frame.rawdata)
        }
        if (frame.phyValue.isNotEmpty()) {
            phyBuffers.getOrPut(funcMode) { MultiChannelRingBuffer(maxChannels = 32, capacity = capacity) }
                .addFrame(frame.phyValue)
        }
        if (frame.algoData.isNotEmpty()) {
            algoBuffers.getOrPut(funcMode) { MultiChannelRingBuffer(maxChannels = 32, capacity = capacity) }
                .addFrame(frame.algoData)
        }
        scalarBuffers.getOrPut(funcMode) { MultiChannelRingBuffer(maxChannels = 4, capacity = capacity) }
            .addFrame(
                intArrayOf(
                    frame.gsData.getOrNull(0) ?: 0,
                    frame.gsData.getOrNull(1) ?: 0,
                    frame.gsData.getOrNull(2) ?: 0,
                    frame.frameCnt
                )
            )
        maxGsChannels[funcMode] = maxOf(maxGsChannels[funcMode] ?: 0, frame.gsData.size)
    }

    /** 读取指定列名在指定功能模式下的历史数据点;列名无效或无数据时返回空列表。 */
    fun getColumn(funcMode: FunctionMode, columnName: String): List<Float> {
        val scalar = scalarBuffers[funcMode]
        when (columnName) {
            "ACCX" -> return scalar?.getChannel(0) ?: emptyList()
            "ACCY" -> return scalar?.getChannel(1) ?: emptyList()
            "ACCZ" -> return scalar?.getChannel(2) ?: emptyList()
            "FRAME_ID" -> return scalar?.getChannel(3) ?: emptyList()
        }
        val (prefix, index) = parseColumnName(columnName) ?: return emptyList()
        return when (prefix) {
            "Ipd" -> phyBuffers[funcMode]?.getChannel(index) ?: emptyList()
            "CH" -> rawdataBuffers[funcMode]?.getChannel(index) ?: emptyList()
            "Rawdata" -> rawdataBuffers[funcMode]?.getChannel(index) ?: emptyList()
            "ALGO_RESULT" -> algoBuffers[funcMode]?.getChannel(index) ?: emptyList()
            else -> emptyList()
        }
    }

    /** 读取指定功能模式的 FRAME_ID 历史(用于波形 X 轴)。 */
    fun frameIds(funcMode: FunctionMode): List<Float> =
        scalarBuffers[funcMode]?.getChannel(3) ?: emptyList()

    /**
     * 返回指定功能模式在指定芯片类型下「当前有数据」的可选列名,按
     * ACC → FRAME_ID → Ipd/CH → Rawdata → ALGO_RESULT 顺序排列。
     * 仅在该功能模式已收到至少一帧后返回非空列表;随通道数增长动态扩展。
     */
    fun availableColumns(funcMode: FunctionMode, chipType: DeviceType): List<String> {
        if (!scalarBuffers.containsKey(funcMode)) return emptyList()
        val columns = mutableListOf<String>()
        val gsCount = maxGsChannels[funcMode] ?: 0
        if (gsCount > 0) columns.add("ACCX")
        if (gsCount > 1) columns.add("ACCY")
        if (gsCount > 2) columns.add("ACCZ")
        columns.add("FRAME_ID")
        val phyCount = phyBuffers[funcMode]?.getMaxChannelCount() ?: 0
        val rawCount = rawdataBuffers[funcMode]?.getMaxChannelCount() ?: 0
        val algoCount = algoBuffers[funcMode]?.getMaxChannelCount() ?: 0
        when (chipType) {
            DeviceType.GH3036 -> {
                for (i in 0 until phyCount) columns.add("Ipd$i")
                for (i in 0 until rawCount) columns.add("Rawdata$i")
            }
            DeviceType.GH3220, DeviceType.GH3300 -> {
                for (i in 0 until rawCount) columns.add("CH$i")
            }
        }
        for (i in 0 until algoCount) columns.add("ALGO_RESULT$i")
        return columns
    }

    /** 清空所有功能模式的缓冲区(录制会话开始时调用)。 */
    fun clear() {
        rawdataBuffers.clear()
        phyBuffers.clear()
        algoBuffers.clear()
        scalarBuffers.clear()
        maxGsChannels.clear()
    }

    private fun parseColumnName(name: String): Pair<String, Int>? {
        val regex = Regex("""^(Ipd|CH|Rawdata|ALGO_RESULT)(\d+)$""")
        val match = regex.find(name) ?: return null
        val prefix = match.groupValues[1]
        val index = match.groupValues[2].toIntOrNull() ?: return null
        return prefix to index
    }
}
