package com.ghealth.tools.ble.protocol.gh3036

import com.ghealth.tools.ble.protocol.rpccore.ChipFrameDecoder
import timber.log.Timber

class Gh3036FrameDecoder : ChipFrameDecoder<GhFuncFrame> {
    private var startFlag = true
    private val lastRawdata = IntArray(MAX_CHANNELS)
    private val lastPhyValue = IntArray(MAX_CHANNELS)
    private var lastTimestamp = 0
    private var lastTimestampHigh = 0
    private val lastGsData = IntArray(MAX_GS_DATA)
    private val lastFlags = IntArray(MAX_CHANNELS)
    private var lastFlagDataBits = 0
    private val lastAlgoData = IntArray(MAX_ALGO_DATA)
    private var lastAlgoDataSize = 0
    private val lastAgcInfo = IntArray(MAX_CHANNELS)
    private val lastAgcInfoHigh = IntArray(MAX_CHANNELS)
    private var lastAgcSize = 0

    override fun reset() {
        startFlag = true
        lastRawdata.fill(0); lastPhyValue.fill(0)
        lastTimestamp = 0; lastTimestampHigh = 0
        lastGsData.fill(0); lastFlags.fill(0)
        lastFlagDataBits = 0
        lastAlgoData.fill(0); lastAlgoDataSize = 0
        lastAgcInfo.fill(0); lastAgcInfoHigh.fill(0); lastAgcSize = 0
    }

    override fun decode(param: ByteArray): List<GhFuncFrame> {
        reset()
        val frames = mutableListOf<GhFuncFrame>()
        var pos = 0
        while (pos < param.size) {
            try {
                val (newPos, rawFrame) = decodeSingleFrame(param, pos)
                pos = newPos
                frames.add(processDelta(rawFrame))
            } catch (_: DecodeException) { break }
        }
        return frames
    }

    private fun decodeSingleFrame(buf: ByteArray, start: Int): Pair<Int, RawFrame> {
        var pos = start
        val raw = RawFrame()
        val (hdrRaw, p1) = readVarint(buf, pos); pos = p1
        raw.packHeader = PackHeader(zigzagDecode(hdrRaw))

        Timber.d("decodeSingleFrame: startPos=$start, packHeader bits=${raw.packHeader.bits}")

        if (raw.packHeader.rawdataEn) { val (sz, p) = readSigned(buf, pos); pos = p; val (arr, p2) = readSignedArray(buf, pos, sz.coerceIn(0, MAX_CHANNELS)); pos = p2; raw.rawdata = arr; Timber.d("  rawdata: sz=$sz, values=${arr.take(3).toList()}...") }
        if (raw.packHeader.phyValueEn) { val (sz, p) = readSigned(buf, pos); pos = p; val (arr, p2) = readSignedArray(buf, pos, sz.coerceIn(0, MAX_CHANNELS)); pos = p2; raw.phyValue = arr; Timber.d("  phyValue: sz=$sz, values=${arr.take(3).toList()}...") }
        if (raw.packHeader.gsDataEn) { val (sz, p) = readSigned(buf, pos); pos = p; val (arr, p2) = readSignedArray(buf, pos, sz.coerceIn(0, MAX_GS_DATA)); pos = p2; raw.gsData = arr; Timber.d("  gsData: sz=$sz, values=${arr.toList()}") }
        if (raw.packHeader.flagsEn) { val (sz, p) = readSigned(buf, pos); pos = p; val (arr, p2) = readSignedArray(buf, pos, sz.coerceIn(0, MAX_CHANNELS)); pos = p2; raw.flags = arr; Timber.d("  flags: sz=$sz") }
        if (raw.packHeader.algDataEn) { val (sz, p) = readSigned(buf, pos); pos = p; val (arr, p2) = readSignedArray(buf, pos, sz.coerceIn(0, MAX_ALGO_DATA)); pos = p2; raw.algoData = arr; Timber.d("  algoData: sz=$sz, values=${arr.toList()}") }
        if (raw.packHeader.agcInfoEn) { val (sz, p) = readSigned(buf, pos); pos = p; val size = sz.coerceIn(0, MAX_CHANNELS); val (arr, p2) = readSignedArray(buf, pos, size); pos = p2; val (arrH, p3) = readSignedArray(buf, pos, size); pos = p3; raw.agcInfo = arr; raw.agcInfoHigh = arrH; Timber.d("  agcInfo: sz=$size") }
        if (raw.packHeader.timestampEn) { val (tsL, p) = readSigned(buf, pos); pos = p; val (tsH, p2) = readSigned(buf, pos); pos = p2; raw.timestamp = tsL; raw.timestampHigh = tsH; Timber.d("  timestamp: tsL=$tsL, tsH=$tsH") }
        val (fid, pf) = readSigned(buf, pos); pos = pf; raw.frameId = fid
        if (raw.packHeader.funcIdEn) { val (v, p) = readSigned(buf, pos); pos = p; raw.functionId = v; Timber.d("  funcId: $v") }
        if (raw.packHeader.slotCfgEn) { val (v, p) = readSigned(buf, pos); pos = p; raw.slotCfg = v; Timber.d("  slotCfg: $v") }
        return Pair(pos, raw)
    }

    private fun processDelta(raw: RawFrame): GhFuncFrame {
        val frame = GhFuncFrame()
        frame.frameCnt = raw.frameId
        frame.funcId = GhFuncId.from(raw.functionId)

        if (raw.packHeader.timestampEn) {
            if (startFlag) {
                frame.timestamp = (raw.timestamp.toLong() and 0xFFFFFFFFL) or ((raw.timestampHigh.toLong() and 0xFFFFFFFFL) shl 32)
                lastTimestamp = raw.timestamp; lastTimestampHigh = raw.timestampHigh
            } else {
                val lastTs = (lastTimestamp.toLong() and 0xFFFFFFFFL) or ((lastTimestampHigh.toLong() and 0xFFFFFFFFL) shl 32)
                val diff = (raw.timestamp.toLong() and 0xFFFFFFFFL) or ((raw.timestampHigh.toLong() and 0xFFFFFFFFL) shl 32)
                frame.timestamp = lastTs + diff
                lastTimestamp = (frame.timestamp and 0xFFFFFFFFL).toInt()
                lastTimestampHigh = ((frame.timestamp ushr 32) and 0xFFFFFFFFL).toInt()
            }
        }

        frame.rawdata = applyDelta(raw.rawdata, lastRawdata)
        frame.phyValue = applyDelta(raw.phyValue, lastPhyValue)
        frame.gsData = applyDelta(raw.gsData, lastGsData)

        // Flags: delta-accumulated but fallback to last when absent
        if (raw.flags.isNotEmpty() || lastFlagDataBits == 0) {
            frame.flags = applyDelta(raw.flags, lastFlags)
            if (raw.flags.isNotEmpty()) lastFlagDataBits = raw.flags.size
        } else {
            frame.flags = lastFlags.copyOf(lastFlagDataBits)
        }

        // agc_info: absolute values (not delta), fallback to last when absent
        if (raw.agcInfo.isNotEmpty()) {
            frame.agcInfo = raw.agcInfo.copyOf()
            frame.agcInfoHigh = raw.agcInfoHigh.copyOf()
            copyInto(raw.agcInfo, lastAgcInfo)
            copyInto(raw.agcInfoHigh, lastAgcInfoHigh)
            lastAgcSize = raw.agcInfo.size
        } else if (lastAgcSize > 0) {
            frame.agcInfo = lastAgcInfo.copyOf(lastAgcSize)
            frame.agcInfoHigh = lastAgcInfoHigh.copyOf(lastAgcSize)
        }

        // algo_data: absolute values (not delta), fallback to last when absent
        if (raw.algoData.isNotEmpty()) {
            frame.algoData = raw.algoData.copyOf()
            copyInto(raw.algoData, lastAlgoData)
            lastAlgoDataSize = raw.algoData.size
        } else if (lastAlgoDataSize > 0) {
            frame.algoData = lastAlgoData.copyOf(lastAlgoDataSize)
        }

        frame.slotCfg = raw.slotCfg

        startFlag = false
        return frame
    }

    private fun applyDelta(values: IntArray, last: IntArray): IntArray {
        val result = IntArray(values.size)
        for (i in values.indices) {
            result[i] = if (startFlag) values[i] else last.getOrElse(i) { 0 } + values[i]
            if (i < last.size) last[i] = result[i]
        }
        return result
    }

    private fun copyInto(src: IntArray, dst: IntArray) {
        for (i in src.indices) {
            if (i < dst.size) dst[i] = src[i]
        }
    }

    private class RawFrame {
        var packHeader = PackHeader(0)
        var rawdata = IntArray(0); var phyValue = IntArray(0); var gsData = IntArray(0)
        var flags = IntArray(0); var algoData = IntArray(0); var agcInfo = IntArray(0); var agcInfoHigh = IntArray(0)
        var timestamp = 0; var timestampHigh = 0; var frameId = 0; var functionId = 0; var slotCfg = 0
    }

    companion object {
        fun readVarint(buffer: ByteArray, startPos: Int): Pair<Int, Int> {
            var value = 0; var shift = 0; var pos = startPos
            while (true) {
                if (pos >= buffer.size) throw DecodeException("Insufficient data")
                val b = buffer[pos].toInt() and 0xFF; pos++
                value = value or ((b and 0x7F) shl shift)
                if ((b and 0x80) == 0) break
                shift += 7; if (shift >= 35) throw DecodeException("Invalid varint")
            }
            return Pair(value, pos)
        }
        fun zigzagDecode(x: Int): Int = (x ushr 1) xor (-(x and 1))
        fun readSigned(buffer: ByteArray, pos: Int): Pair<Int, Int> { val (raw, newPos) = readVarint(buffer, pos); return Pair(zigzagDecode(raw), newPos) }
        fun readSignedArray(buffer: ByteArray, startPos: Int, count: Int): Pair<IntArray, Int> {
            val result = IntArray(count); var pos = startPos
            for (i in 0 until count) { val (v, np) = readSigned(buffer, pos); result[i] = v; pos = np }
            return Pair(result, pos)
        }
    }
}

class DecodeException(message: String) : Exception(message)
