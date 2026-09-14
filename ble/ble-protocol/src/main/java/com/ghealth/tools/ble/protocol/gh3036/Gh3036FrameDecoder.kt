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
            val frameStart = pos
            try {
                val (newPos, rawFrame) = decodeSingleFrame(param, pos)
                pos = newPos
                frames.add(processDelta(rawFrame))
            } catch (e: DecodeException) {
                throw DecodeException("Failed to decode G frame at offset $frameStart: ${e.message}", e)
            }
        }
        return frames
    }

    private fun decodeSingleFrame(buf: ByteArray, start: Int): Pair<Int, RawFrame> {
        var pos = start
        val raw = RawFrame()
        val (hdrRaw, p1) = readVarint(buf, pos); pos = p1
        raw.packHeader = PackHeader(zigzagDecode(hdrRaw))

        if (debugLogEnabled) Timber.v("decodeSingleFrame: startPos=$start, packHeader bits=${raw.packHeader.bits}")

        if (raw.packHeader.rawdataEn) { val (size, p) = readArraySize(buf, pos, "rawdata", MAX_CHANNELS); pos = p; val (arr, p2) = readSignedArray(buf, pos, size); pos = p2; raw.rawdata = arr; if (debugLogEnabled) Timber.v("  rawdata: sz=$size, values=${arr.take(3).toList()}...") }
        if (raw.packHeader.phyValueEn) { val (size, p) = readArraySize(buf, pos, "phyValue", MAX_CHANNELS); pos = p; val (arr, p2) = readSignedArray(buf, pos, size); pos = p2; raw.phyValue = arr; if (debugLogEnabled) Timber.v("  phyValue: sz=$size, values=${arr.take(3).toList()}...") }
        if (raw.packHeader.gsDataEn) { val (size, p) = readArraySize(buf, pos, "gsData", MAX_GS_DATA); pos = p; val (arr, p2) = readSignedArray(buf, pos, size); pos = p2; raw.gsData = arr; if (debugLogEnabled) Timber.v("  gsData: sz=$size, values=${arr.toList()}") }
        if (raw.packHeader.flagsEn) { val (size, p) = readArraySize(buf, pos, "flags", MAX_CHANNELS); pos = p; val (arr, p2) = readSignedArray(buf, pos, size); pos = p2; raw.flags = arr; if (debugLogEnabled) Timber.v("  flags: sz=$size") }
        if (raw.packHeader.algDataEn) { val (size, p) = readArraySize(buf, pos, "algoData", MAX_ALGO_DATA); pos = p; val (arr, p2) = readSignedArray(buf, pos, size); pos = p2; raw.algoData = arr; if (debugLogEnabled) Timber.v("  algoData: sz=$size, values=${arr.toList()}") }
        if (raw.packHeader.agcInfoEn) { val (size, p) = readArraySize(buf, pos, "agcInfo", MAX_CHANNELS); pos = p; val (arr, p2) = readSignedArray(buf, pos, size); pos = p2; val (arrH, p3) = readSignedArray(buf, pos, size); pos = p3; raw.agcInfo = arr; raw.agcInfoHigh = arrH; if (debugLogEnabled) Timber.v("  agcInfo: sz=$size") }
        if (raw.packHeader.timestampEn) { val (tsL, p) = readSigned(buf, pos); pos = p; val (tsH, p2) = readSigned(buf, pos); pos = p2; raw.timestamp = tsL; raw.timestampHigh = tsH; if (debugLogEnabled) Timber.v("  timestamp: tsL=$tsL, tsH=$tsH") }
        val (fid, pf) = readSigned(buf, pos); pos = pf; raw.frameId = fid
        if (raw.packHeader.funcIdEn) { val (v, p) = readSigned(buf, pos); pos = p; raw.functionId = v; if (debugLogEnabled) Timber.v("  funcId: $v") }
        if (raw.packHeader.slotCfgEn) { val (v, p) = readSigned(buf, pos); pos = p; raw.slotCfg = v; if (debugLogEnabled) Timber.v("  slotCfg: $v") }
        return Pair(pos, raw)
    }

    private fun readArraySize(buf: ByteArray, pos: Int, field: String, maxSize: Int): Pair<Int, Int> {
        val (size, newPos) = readSigned(buf, pos)
        if (size !in 0..maxSize) {
            throw DecodeException("$field size out of range: $size (expected 0..$maxSize)")
        }
        return Pair(size, newPos)
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
                val diff = raw.timestamp.toLong() and 0xFFFFFFFFL
                frame.timestamp = lastTs + diff
                lastTimestamp = (frame.timestamp and 0xFFFFFFFFL).toInt()
                lastTimestampHigh = ((frame.timestamp ushr 32) and 0xFFFFFFFFL).toInt()
            }
        }

        frame.rawdata = applyDelta(raw.rawdata, lastRawdata)
        frame.phyValue = applyDelta(raw.phyValue, lastPhyValue)
        frame.gsData = applyDelta(raw.gsData, lastGsData)

        // Sparse metadata fields are absolute when present and reuse the last value when absent.
        if (raw.packHeader.flagsEn) {
            frame.flags = rememberAbsolute(raw.flags, lastFlags)
            lastFlagDataBits = raw.flags.size
        } else if (lastFlagDataBits > 0) {
            frame.flags = lastFlags.copyOf(lastFlagDataBits)
        }

        if (raw.packHeader.agcInfoEn) {
            frame.agcInfo = rememberAbsolute(raw.agcInfo, lastAgcInfo)
            frame.agcInfoHigh = rememberAbsolute(raw.agcInfoHigh, lastAgcInfoHigh)
            lastAgcSize = raw.agcInfo.size
        } else if (lastAgcSize > 0) {
            frame.agcInfo = lastAgcInfo.copyOf(lastAgcSize)
            frame.agcInfoHigh = lastAgcInfoHigh.copyOf(lastAgcSize)
        }

        if (raw.packHeader.algDataEn) {
            frame.algoData = rememberAbsolute(raw.algoData, lastAlgoData)
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

    private fun rememberAbsolute(values: IntArray, last: IntArray): IntArray {
        values.copyInto(last)
        return values.copyOf()
    }

    private class RawFrame {
        var packHeader = PackHeader(0)
        var rawdata = IntArray(0); var phyValue = IntArray(0); var gsData = IntArray(0)
        var flags = IntArray(0); var algoData = IntArray(0); var agcInfo = IntArray(0); var agcInfoHigh = IntArray(0)
        var timestamp = 0; var timestampHigh = 0; var frameId = 0; var functionId = 0; var slotCfg = 0
    }

    companion object {
        @Volatile var debugLogEnabled = false
        fun readVarint(buffer: ByteArray, startPos: Int): Pair<Int, Int> {
            var value = 0; var shift = 0; var pos = startPos
            while (true) {
                if (pos >= buffer.size) throw DecodeException("Insufficient data")
                val b = buffer[pos].toInt() and 0xFF; pos++
                if (shift == 28 && (b and 0xF0) != 0) throw DecodeException("Varint exceeds 32 bits")
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

class DecodeException(message: String, cause: Throwable? = null) : Exception(message, cause)
