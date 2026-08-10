package com.ghealth.tools.ble.gh3220.rawdata

import com.ghealth.tools.ble.itlvc.core.ItlvcError

/**
 * GH3220 rawdata 解码器。
 *
 * - 0x08：未压缩，payload = Rawdata_Package*，每包 [dataType][dataLen][FrameData...]
 * - 0x09/0x0A：压缩偶数/奇数包（Task 19；C 端偶数包首帧为 4B/通道绝对值，与 0x0B 同构，
 *   真机样本走 0x0B，0x09/0x0A 仍按纯差分处理）
 * - 0x0B：新结构（Task 20，8B 头 + C 端帧布局，2026-08-10 标准 APP 抓包验证）
 * - 0x2A：FIFO 上报（Task 21）
 */
class RawDataDecoder(private val config: SamplingConfig) {

    private var diff = DiffDecoder(config.channelCount)
    private var agcDiff = DiffDecoder(config.channelCount)

    fun reset() {
        diff = DiffDecoder(config.channelCount)
        agcDiff = DiffDecoder(config.channelCount)
    }

    /** 0x0B 包自描述通道数（chMask 置位数）。与解码器当前尺寸不一致时按包大小重建差分
     *  解码器；跨包差分基准仅在通道数一致时保留（通道数变化即采样配置改变，旧基准无意义）。 */
    private fun ensureChannelCount(count: Int) {
        if (count != diff.channelCount) {
            diff = DiffDecoder(count)
            agcDiff = DiffDecoder(count)
        }
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
     * 0x0B 新结构包。包头 8B（设备端 gh_uprotocol.c `Gh2x2xPackPakcageHeader`，真机抓包已裁决）：
     * `[FunctionID][dataType][chMask 4B BE][pkgFlag][dataLen]`。
     *
     * - FunctionID：GH3X2X_FUNCTION_* 位偏移序号（HR=1），决定帧路由功能；
     * - dataType 位域：bit0=GS / bit1=algo / bit2=AGC / bit3=amb / bit4=gyro / bit5=cap / bit6=temp；
     * - chMask：大端 32 位通道位掩码（bit n = 通道 n 有数据）；
     * - pkgFlag：bit0 压缩 / bit1 oddeven（置位时本包首帧为绝对值）/ bit2 fifo 模式（多功能）/
     *   bits3-4 分包计数 / bit5 分包结束。
     *
     * 帧布局（C 端 gh_zip.c `Gh2x2xUploadDataToMaster`，2026-08-10 标准 APP 抓包逐字节验证）：
     * - 压缩 + oddeven 的首帧：`[frameId][GS?][rawdata 4B/通道绝对值][agc 4B/通道绝对值][amb?][result]`，
     *   rawdata 高字节为 tag（LED adj 等标志），值本体 24bit，作差分解压基准时掩码到 24bit；
     * - 其余压缩帧：`[frameId][GS?][rawLen][tagFlag][tag×通道?][nibble 差分][agcLen][agc 差分][amb?][result]`；
     * - 未压缩帧：`[frameId][GS?][rawdata 4B/通道][agc 3B/通道][amb 3B/通道][result]`；
     * - result：`[byteNum][内容 byteNum 字节]`，内容为 flag0/flag2/flag3 与算法结果（tag+4B LE）。
     */
    fun decode0B(payload: ByteArray): Result<Gh3220RawDataPackage> {
        if (payload.size < 8) {
            return Result.failure(ItlvcError.ParseError("0x0B: header truncated"))
        }
        val funcId = u8(payload, 0)
        val dataType = u8(payload, 1)
        val channelMask = be32(payload, 2)
        val pkgFlag = u8(payload, 6)
        val dataLen = u8(payload, 7)
        val end = 8 + dataLen
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
        if (!multiFunction) ensureChannelCount(channelCount)
        val channel = if (multiFunction) Integer.numberOfTrailingZeros(channelMask) else 0
        if (multiFunction && channel >= config.channelCount) {
            return Result.failure(ItlvcError.ParseError("0x0B: multifunction channel $channel >= config ${config.channelCount}"))
        }
        // 字段存在性以包头 dataType 位为准（C 端按同一位置打包）
        val gsEnabled = hasBit(dataType, 0)
        val algoEnabled = hasBit(dataType, 1)
        val agcEnabled = hasBit(dataType, 2)
        val ambEnabled = hasBit(dataType, 3)
        // C 端 g_uchOddEvenChangeFlag 置位时本包首帧为绝对值（rawdata/agc 各 4B/通道）
        val evenFirst = compressed && oddPacket
        val frames = ArrayList<Gh3220RawDataFrame>()
        var pos = 8
        var first = true
        while (pos < end) {
            val parsed = when {
                compressed && first && evenFirst ->
                    parseEvenFirstFrame(payload, pos, end, channelCount, funcId, dataType, gsEnabled, agcEnabled, ambEnabled, algoEnabled)
                compressed ->
                    parseZipFrame(payload, pos, end, channelCount, null, funcId, dataType, gsEnabled, agcEnabled, ambEnabled, algoEnabled)
                multiFunction ->
                    parseFrame0BMulti(dataType, payload, pos, end, channel, funcId)
                else ->
                    parseFrame08(dataType, payload, pos, end, channelCount, funcId)
            } ?: return Result.failure(ItlvcError.ParseError("0x0B: frame truncated"))
            pos = parsed.first
            frames.add(parsed.second)
            first = false
        }
        if (end != payload.size) {
            return Result.failure(ItlvcError.ParseError("0x0B: trailing bytes"))
        }
        return Result.success(
            Gh3220RawDataPackage(
                dataType = dataType,
                funcId = funcId,
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

    /**
     * 0x2A FIFO 上报：[fifoId(1B)][len u32le(4B)][rawdata]。
     *
     * 与设备端 C 源码的已知偏差（真机抓包未验证）：C demo 端载荷为
     * [fifoId][len 1B][idChangeFlag 1B][data...]（数据同样从偏移 5 开始），
     * len 字段解释与文档 §3.35 的 4 字节小端不同。
     * 按当前实现，C demo 格式的 0x2A 包（idChangeFlag 非 0 时 len 字段被放大）
     * 将因 len overflow 被拒绝；接入真实数据流前需抓包裁决格式。
     */
    fun decode2A(payload: ByteArray): Result<Gh3220FifoReport> {
        if (payload.size < 5) return Result.failure(ItlvcError.ParseError("0x2A: header truncated"))
        val fifoId = u8(payload, 0)
        val len = le32(payload, 1)
        if (len < 0 || len > payload.size - 5) {
            return Result.failure(ItlvcError.ParseError("0x2A: len overflow"))
        }
        return Result.success(Gh3220FifoReport(fifoId, payload.copyOfRange(5, 5 + len)))
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

    /** 压缩帧：`[frameId][GS?][rawLen][tagFlag][tag*][rawDiff][agcLen][agcDiff][amb*][result]`；
     * 字段存在性按 0x0B 包头 dataType 位（无头帧 0x09/0x0A 回退 SamplingConfig）；
     * multiChannel 非 null 时（0x0B 多功能模式）帧内读 1 字节 FifoID 作结构校验。 */
    private fun parseZipFrame(
        data: ByteArray,
        start: Int,
        end: Int,
        channelCount: Int = config.channelCount,
        multiChannel: Int? = null,
        funcId: Int = 0,
        dataType: Int = 0,
        gsEnabled: Boolean = config.gsEnabled,
        agcEnabled: Boolean = config.agcEnabled,
        ambEnabled: Boolean = config.ambEnabled,
        algoEnabled: Boolean = config.algoEnabled,
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
        val acc = if (gsEnabled) take(6)?.let { readAcc(it) } else null
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
        val agc = if (agcEnabled) {
            val agcLen = take(1)?.let { u8(it, 0) } ?: return null
            val agcDiffBytes = take(agcLen) ?: return null
            if (agcLen == 0) null else agcDiff.decode(agcDiffBytes).getOrNull() ?: return null
        } else null
        val amb = if (ambEnabled) {
            take(channelCount * 3)?.let { readChannels24(it, channelCount) }
        } else null
        val results = if (algoEnabled) {
            val byteNum = take(1)?.let { u8(it, 0) } ?: return null
            val resultBytes = take(byteNum) ?: return null
            parseResults(resultBytes) ?: return null
        } else {
            emptyList()
        }
        val frame = Gh3220RawDataFrame(
            dataType = dataType,
            funcId = funcId,
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

    /** 0x0B 压缩包首帧（oddeven 置位时）：rawdata/agc 各 4B/通道绝对值；
     * rawdata 掩码 24bit 作差分解压基准（C 端 last 值即 rawdata & 0x00FFFFFF，高字节为 tag/标志）。 */
    private fun parseEvenFirstFrame(
        data: ByteArray,
        start: Int,
        end: Int,
        channelCount: Int,
        funcId: Int,
        dataType: Int,
        gsEnabled: Boolean,
        agcEnabled: Boolean,
        ambEnabled: Boolean,
        algoEnabled: Boolean,
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
        val acc = if (gsEnabled) take(6)?.let { readAcc(it) } else null
        val rawBytes = take(channelCount * 4) ?: return null
        val rawdata = readChannels32(rawBytes, channelCount).map { it and 0x00FFFFFF }.toIntArray()
        diff.setBaseline(rawdata)
        val agc = if (agcEnabled) {
            val agcBytes = take(channelCount * 4) ?: return null
            val values = readChannels32(agcBytes, channelCount)
            agcDiff.setBaseline(values)
            values
        } else null
        val amb = if (ambEnabled) {
            take(channelCount * 3)?.let { readChannels24(it, channelCount) }
        } else null
        val results = if (algoEnabled) {
            val byteNum = take(1)?.let { u8(it, 0) } ?: return null
            val resultBytes = take(byteNum) ?: return null
            parseResults(resultBytes) ?: return null
        } else {
            emptyList()
        }
        return Pair(pos, Gh3220RawDataFrame(dataType, funcId, frameId, acc, rawdata, agc, amb, results))
    }

    /** 解析 0x08 的一个 FrameData，返回 (新位置, 帧)。 */
    private fun parseFrame08(
        dataType: Int,
        data: ByteArray,
        start: Int,
        end: Int,
        channelCount: Int = config.channelCount,
        funcId: Int = dataType shr 4,
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
        return Pair(pos, Gh3220RawDataFrame(dataType, funcId, frameId, acc, rawdata, agc, amb, results))
    }

    /** 解析 0x0B 多功能未压缩帧：`[frameId][ACC?][FifoID(1B)][RawData(4B)][AgcData(3B)?][AmbData(3B)?][Result?]`；
     * 通道归属以 chMask 为准，FifoID 仅作结构校验。 */
    private fun parseFrame0BMulti(
        dataType: Int,
        data: ByteArray,
        start: Int,
        end: Int,
        channel: Int,
        funcId: Int = dataType shr 4,
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
        return Pair(pos, Gh3220RawDataFrame(dataType, funcId, frameId, acc, rawdata, agc, amb, results, channel = channel))
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
