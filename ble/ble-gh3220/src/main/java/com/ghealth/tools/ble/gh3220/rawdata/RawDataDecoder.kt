package com.ghealth.tools.ble.gh3220.rawdata

import com.ghealth.tools.ble.itlvc.core.ItlvcError

/**
 * GH3220 rawdata 解码器。
 *
 * - 0x08：未压缩，payload = Rawdata_Package*，每包 [dataType][dataLen][FrameData...]
 * - 0x09/0x0A：压缩偶数/奇数包（Task 19）
 * - 0x0B：新结构（Task 20）
 * - 0x2A：FIFO 上报（Task 21）
 */
class RawDataDecoder(private val config: SamplingConfig) {

    private val diff = DiffDecoder(config.channelCount)
    private val agcDiff = DiffDecoder(config.channelCount)

    fun reset() {
        diff.reset()
        agcDiff.reset()
    }

    fun decode08(payload: ByteArray): Result<List<Gh3220RawDataFrame>> {
        val frames = ArrayList<Gh3220RawDataFrame>()
        var pos = 0
        while (pos < payload.size) {
            if (payload.size - pos < 2) {
                return Result.failure(ItlvcError.ParseError("0x08: package header truncated"))
            }
            val dataType = u8(payload, pos); pos++
            val dataLen = u8(payload, pos); pos++
            val end = pos + dataLen
            if (end > payload.size) {
                return Result.failure(ItlvcError.ParseError("0x08: package len overflow"))
            }
            while (pos < end) {
                val parsed = parseFrame08(dataType, payload, pos, end)
                    ?: return Result.failure(ItlvcError.ParseError("0x08: frame truncated"))
                pos = parsed.first
                frames.add(parsed.second)
            }
        }
        return Result.success(frames)
    }

    /** 0x09 压缩偶数包：payload = FrameData 序列，第 0 帧为绝对值。
     * 偶数包第 0 帧按文档 §3.7.7 解释为"绝对值"，以从 0 差分（类型 14 = 32bit 正差分）编码；
     * C 端 gh_zip.c 对偶数包首帧直接写 4B/通道原始绝对值（无 rawLen/tagFlag 前缀），真机未验证。 */
    fun decode09(payload: ByteArray): Result<List<Gh3220RawDataFrame>> = decodeZipFrames(payload)

    /** 0x0A 压缩奇数包：payload = FrameData 序列，全部为差分值。
     * 偶数包第 0 帧按文档 §3.7.7 解释为"绝对值"，以从 0 差分（类型 14 = 32bit 正差分）编码；
     * C 端 gh_zip.c 对偶数包首帧直接写 4B/通道原始绝对值（无 rawLen/tagFlag 前缀），真机未验证。 */
    fun decode0A(payload: ByteArray): Result<List<Gh3220RawDataFrame>> = decodeZipFrames(payload)

    /**
     * 0x0B 新结构包。包头：[dataType][chMask 4B BE][pkgFlag][dataLen]。
     * pkgFlag：bit0 压缩 / bit1 奇偶 / bit2 多功能 / bits3-4 分包计数 / bit5 分包结束。
     *
     * 与设备端 C 源码（gh_uprotocol.c / gh_zip.c）的已知偏差（真机抓包未验证）：
     * 1. C 端 uProtocol 载荷为 8 字节包头 [FunctionID][DataType][mask 4B][flag][len]；
     *    本实现按协议文档 §3.7.2 取 7 字节（FunctionID 并入 dataType 高 nibble）。
     * 2. Data Channel Num 按 C 端解释为大端通道位掩码；文档 §3.7.4 为 seq/chnlCnt 形式。
     * 3. 多功能标志按 C 端取 bit2；文档 §3.7.5 为 bit5。
     * 4. 压缩帧 agc/amb/result 存在性按 SamplingConfig 判定（与 0x09/0x0A 同款设计），
     *    非包头 dataType 位；C 端 agc/algo 恒开、amb 恒关。
     * 5. AGC 每通道 3 字节按文档；C 端按 4 字节打包。
     * 6. 多功能帧 [FifoID][RawData 4B] 按文档 §3.7.9；C 端 fifo 模式仅 1 字节 FifoID，
     *    真实样本走 0x2A。多功能 + 压缩组合当前显式拒绝（差分解码器按 config.channelCount 定长）。
     */
    fun decode0B(payload: ByteArray): Result<Gh3220RawDataPackage> {
        if (payload.size < 7) {
            return Result.failure(ItlvcError.ParseError("0x0B: header truncated"))
        }
        val dataType = u8(payload, 0)
        val channelMask = be32(payload, 1)
        val pkgFlag = u8(payload, 5)
        val dataLen = u8(payload, 6)
        val end = 7 + dataLen
        if (end > payload.size) {
            return Result.failure(ItlvcError.ParseError("0x0B: data len overflow"))
        }
        val compressed = (pkgFlag and 0x01) != 0
        val oddPacket = (pkgFlag and 0x02) != 0
        val multiFunction = (pkgFlag and 0x04) != 0
        val splicePackCount = (pkgFlag shr 3) and 0x03
        val splicePackOver = (pkgFlag and 0x20) != 0
        val activeChannels = activeChannelIndices(channelMask)
        if (activeChannels.isEmpty()) {
            return Result.failure(ItlvcError.ParseError("0x0B: empty channel mask"))
        }
        if (multiFunction && activeChannels.size != 1) {
            return Result.failure(ItlvcError.ParseError("0x0B: multifunction requires exactly one channel bit"))
        }
        if (multiFunction && compressed) {
            return Result.failure(ItlvcError.ParseError("0x0B: compressed multifunction unsupported"))
        }
        val channelCount = if (multiFunction) 1 else activeChannels.size
        if (!multiFunction && channelCount != config.channelCount) {
            return Result.failure(ItlvcError.ParseError("0x0B: channel mask count $channelCount != config ${config.channelCount}"))
        }
        val channel = if (multiFunction) Integer.numberOfTrailingZeros(channelMask) else 0
        if (multiFunction && channel >= config.channelCount) {
            return Result.failure(ItlvcError.ParseError("0x0B: multifunction channel $channel >= config ${config.channelCount}"))
        }
        val frames = ArrayList<Gh3220RawDataFrame>()
        var pos = 7
        while (pos < end) {
            val parsed = (if (compressed) {
                parseZipFrame(payload, pos, end, channelCount, if (multiFunction) channel else null)
            } else if (multiFunction) {
                parseFrame0BMulti(dataType, payload, pos, end, channel)
            } else {
                parseFrame08(dataType, payload, pos, end, channelCount)
            }) ?: return Result.failure(ItlvcError.ParseError("0x0B: frame truncated"))
            pos = parsed.first
            frames.add(parsed.second)
        }
        if (end != payload.size) {
            return Result.failure(ItlvcError.ParseError("0x0B: trailing bytes"))
        }
        return Result.success(
            Gh3220RawDataPackage(
                dataType = dataType,
                funcId = dataType shr 4,
                channelMask = channelMask,
                activeChannels = activeChannels,
                compressed = compressed,
                oddPacket = oddPacket,
                multiFunction = multiFunction,
                splicePackCount = splicePackCount,
                splicePackOver = splicePackOver,
                frames = frames,
            ),
        )
    }

    private fun decodeZipFrames(payload: ByteArray): Result<List<Gh3220RawDataFrame>> {
        val frames = ArrayList<Gh3220RawDataFrame>()
        var pos = 0
        while (pos < payload.size) {
            val parsed = parseZipFrame(payload, pos, payload.size)
                ?: return Result.failure(ItlvcError.ParseError("0x09/0x0A: frame truncated"))
            pos = parsed.first
            frames.add(parsed.second)
        }
        return Result.success(frames)
    }

    /** 压缩帧：`[frameId][rawLen][tagFlag][tag*][rawDiff][agcLen][agcDiff][amb*][result]`；
     * multiChannel 非 null 时（0x0B 多功能模式）帧内读 1 字节 FifoID 作结构校验。 */
    private fun parseZipFrame(
        data: ByteArray,
        start: Int,
        end: Int,
        channelCount: Int = config.channelCount,
        multiChannel: Int? = null,
    ): Pair<Int, Gh3220RawDataFrame>? {
        var pos = start
        fun take(n: Int): ByteArray? {
            require(n >= 0)
            if (pos + n > end) return null
            val out = data.copyOfRange(pos, pos + n)
            pos += n
            return out
        }
        val frameId = take(1)?.let { u8(it, 0) } ?: return null
        val acc = if (config.gsEnabled) take(6)?.let { readAcc(it) } else null
        if (multiChannel != null) {
            take(1) ?: return null // FifoID，仅结构校验（通道归属以 chMask 为准）
        }
        val rawLen = take(1)?.let { u8(it, 0) } ?: return null
        val tagFlag = take(1)?.let { u8(it, 0) } ?: return null
        val tagBytes = if (tagFlag != 0) {
            take(channelCount) ?: return null
        } else {
            ByteArray(0)
        }
        if (rawLen < 1 + tagBytes.size) return null
        val rawDiffBytes = take(rawLen - 1 - tagBytes.size) ?: return null
        val rawdata = diff.decode(rawDiffBytes).getOrNull() ?: return null
        val agc = if (config.agcEnabled) {
            val agcLen = take(1)?.let { u8(it, 0) } ?: return null
            val agcDiffBytes = take(agcLen) ?: return null
            if (agcLen == 0) null else agcDiff.decode(agcDiffBytes).getOrNull() ?: return null
        } else null
        val amb = if (config.ambEnabled) {
            take(channelCount * 3)?.let { readChannels24(it, channelCount) }
        } else null
        val results = if (config.algoEnabled) {
            val byteNum = take(1)?.let { u8(it, 0) } ?: return null
            val resultBytes = take(byteNum) ?: return null
            parseResults(resultBytes) ?: return null
        } else {
            emptyList()
        }
        val frame = Gh3220RawDataFrame(
            dataType = 0,
            funcId = 0,
            frameId = frameId,
            acc = acc,
            rawdata = rawdata,
            agc = agc,
            amb = amb,
            results = results,
            channel = multiChannel,
        )
        return Pair(pos, frame)
    }

    /** 解析 0x08 的一个 FrameData，返回 (新位置, 帧)。 */
    private fun parseFrame08(
        dataType: Int,
        data: ByteArray,
        start: Int,
        end: Int,
        channelCount: Int = config.channelCount,
    ): Pair<Int, Gh3220RawDataFrame>? {
        var pos = start
        fun take(n: Int): ByteArray? {
            require(n >= 0)
            if (pos + n > end) return null
            val out = data.copyOfRange(pos, pos + n)
            pos += n
            return out
        }
        val frameId = take(1)?.let { u8(it, 0) } ?: return null
        val acc = if (hasBit(dataType, 0)) {
            val accBytes = take(6) ?: return null
            readAcc(accBytes)
        } else null
        val rawdata = take(channelCount * 4)?.let { readChannels32(it, channelCount) } ?: return null
        diff.setBaseline(rawdata)
        val agc = if (hasBit(dataType, 2)) {
            val agcBytes = take(channelCount * 3) ?: return null
            readChannels24(agcBytes, channelCount)
        } else null
        agc?.let { agcDiff.setBaseline(it) }
        val amb = if (hasBit(dataType, 3)) {
            val ambBytes = take(channelCount * 3) ?: return null
            readChannels24(ambBytes, channelCount)
        } else null
        val results = if (hasBit(dataType, 1)) {
            val byteNum = take(1)?.let { u8(it, 0) } ?: return null
            val resultBytes = take(byteNum) ?: return null
            parseResults(resultBytes) ?: return null
        } else {
            emptyList()
        }
        return Pair(pos, Gh3220RawDataFrame(dataType, dataType shr 4, frameId, acc, rawdata, agc, amb, results))
    }

    /** 解析 0x0B 多功能未压缩帧：`[frameId][ACC?][FifoID(1B)][RawData(4B)][AgcData(3B)?][AmbData(3B)?][Result?]`；
     * 通道归属以 chMask 为准，FifoID 仅作结构校验。 */
    private fun parseFrame0BMulti(
        dataType: Int,
        data: ByteArray,
        start: Int,
        end: Int,
        channel: Int,
    ): Pair<Int, Gh3220RawDataFrame>? {
        var pos = start
        fun take(n: Int): ByteArray? {
            require(n >= 0)
            if (pos + n > end) return null
            val out = data.copyOfRange(pos, pos + n)
            pos += n
            return out
        }
        val frameId = take(1)?.let { u8(it, 0) } ?: return null
        val acc = if (hasBit(dataType, 0)) {
            val accBytes = take(6) ?: return null
            readAcc(accBytes)
        } else null
        take(1) ?: return null // FifoID，仅结构校验
        val rawdata = take(4)?.let { readChannels32(it, 1) } ?: return null
        diff.setBaselineChannel(channel, rawdata[0])
        val agc = if (hasBit(dataType, 2)) {
            val agcBytes = take(3) ?: return null
            readChannels24(agcBytes, 1)
        } else null
        agc?.let { agcDiff.setBaselineChannel(channel, it[0]) }
        val amb = if (hasBit(dataType, 3)) {
            val ambBytes = take(3) ?: return null
            readChannels24(ambBytes, 1)
        } else null
        val results = if (hasBit(dataType, 1)) {
            val byteNum = take(1)?.let { u8(it, 0) } ?: return null
            val resultBytes = take(byteNum) ?: return null
            parseResults(resultBytes) ?: return null
        } else {
            emptyList()
        }
        return Pair(pos, Gh3220RawDataFrame(dataType, dataType shr 4, frameId, acc, rawdata, agc, amb, results, channel = channel))
    }

    /** Result 段：[ResultByteNum(1)][(tag 1B + value 4B LE)*]，段长非 5 的倍数时返回 null。 */
    private fun parseResults(data: ByteArray): List<Gh3220Result>? {
        val results = ArrayList<Gh3220Result>()
        var pos = 0
        while (pos + 5 <= data.size) {
            val tag = u8(data, pos)
            val value = le32(data, pos + 1)
            results.add(Gh3220Result(tag, value))
            pos += 5
        }
        return if (pos == data.size) results else null
    }

    /** ACC 6 字节 → 3 × int16 大端有符号（与设备端 FillGsensorData 一致）。 */
    private fun readAcc(data: ByteArray): IntArray = IntArray(3) { i ->
        val v = ((data[i * 2].toInt() and 0xFF) shl 8) or (data[i * 2 + 1].toInt() and 0xFF)
        v.toShort().toInt()
    }

    /** 每通道 4 字节大端 → Int（32bit 位型，高位在前）。 */
    private fun readChannels32(data: ByteArray, count: Int): IntArray = IntArray(count) { i ->
        val off = i * 4
        ((data[off].toInt() and 0xFF) shl 24) or
            ((data[off + 1].toInt() and 0xFF) shl 16) or
            ((data[off + 2].toInt() and 0xFF) shl 8) or
            (data[off + 3].toInt() and 0xFF)
    }

    /** 每通道 3 字节大端 → Int（Gain/Current0/Current1）。 */
    private fun readChannels24(data: ByteArray, count: Int): IntArray = IntArray(count) { i ->
        val off = i * 3
        ((data[off].toInt() and 0xFF) shl 16) or
            ((data[off + 1].toInt() and 0xFF) shl 8) or
            (data[off + 2].toInt() and 0xFF)
    }

    private fun hasBit(byte: Int, bit: Int): Boolean = (byte and (1 shl bit)) != 0

    private fun u8(data: ByteArray, offset: Int): Int = data[offset].toInt() and 0xFF

    /** 大端 4 字节 → Int（0x0B Data Channel Num 通道位掩码）。 */
    private fun be32(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)

    /** 通道位掩码置位索引（0..31 升序）。 */
    private fun activeChannelIndices(mask: Int): IntArray {
        val out = ArrayList<Int>()
        for (bit in 0 until 32) {
            if ((mask and (1 shl bit)) != 0) out.add(bit)
        }
        return out.toIntArray()
    }

    private fun le32(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)
}
