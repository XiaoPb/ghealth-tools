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

    /** 0x09 压缩偶数包：payload = FrameData 序列，第 0 帧为绝对值。 */
    fun decode09(payload: ByteArray): Result<List<Gh3220RawDataFrame>> = decodeZipFrames(payload)

    /** 0x0A 压缩奇数包：payload = FrameData 序列，全部为差分值。 */
    fun decode0A(payload: ByteArray): Result<List<Gh3220RawDataFrame>> = decodeZipFrames(payload)

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

    /** 压缩帧：`[frameId][rawLen][tagFlag][tag*][rawDiff][agcLen][agcDiff][amb*][result]`。 */
    private fun parseZipFrame(data: ByteArray, start: Int, end: Int): Pair<Int, Gh3220RawDataFrame>? {
        var pos = start
        fun take(n: Int): ByteArray? {
            if (pos + n > end) return null
            val out = data.copyOfRange(pos, pos + n)
            pos += n
            return out
        }
        val frameId = take(1)?.let { u8(it, 0) } ?: return null
        val acc = if (config.gsEnabled) take(6)?.let { readAcc(it) } else null
        val rawLen = take(1)?.let { u8(it, 0) } ?: return null
        val tagFlag = take(1)?.let { u8(it, 0) } ?: return null
        val tagBytes = if (tagFlag != 0) {
            take(config.channelCount) ?: return null
        } else {
            ByteArray(0)
        }
        if (rawLen < 1 + tagBytes.size) return null
        val rawDiffBytes = take(rawLen - 1 - tagBytes.size) ?: return null
        val rawdata = diff.decode(rawDiffBytes).getOrNull() ?: return null
        val agcLen = take(1)?.let { u8(it, 0) } ?: return null
        val agcDiffBytes = take(agcLen) ?: return null
        val agc = if (agcLen == 0) null else agcDiff.decode(agcDiffBytes).getOrNull()
        val amb = if (config.ambEnabled) {
            take(config.channelCount * 3)?.let { readChannels24(it, config.channelCount) }
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
        )
        return Pair(pos, frame)
    }

    /** 解析 0x08 的一个 FrameData，返回 (新位置, 帧)。 */
    private fun parseFrame08(dataType: Int, data: ByteArray, start: Int, end: Int): Pair<Int, Gh3220RawDataFrame>? {
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
        val rawdata = take(config.channelCount * 4)?.let { readChannels32(it, config.channelCount) } ?: return null
        val agc = if (hasBit(dataType, 2)) {
            val agcBytes = take(config.channelCount * 3) ?: return null
            readChannels24(agcBytes, config.channelCount)
        } else null
        val amb = if (hasBit(dataType, 3)) {
            val ambBytes = take(config.channelCount * 3) ?: return null
            readChannels24(ambBytes, config.channelCount)
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

    private fun le32(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)
}
