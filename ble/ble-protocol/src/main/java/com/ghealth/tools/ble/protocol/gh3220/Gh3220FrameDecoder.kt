package com.ghealth.tools.ble.protocol.gh3220

import com.ghealth.tools.ble.protocol.gh3036.DecodeException
import com.ghealth.tools.ble.protocol.gh3036.GhFuncFrame
import com.ghealth.tools.ble.protocol.gh3036.GhFuncId
import timber.log.Timber

/**
 * GH3220 G-protocol frame decoder.
 *
 * Decodes the varint+zigzag encoded byte stream into [GhFuncFrame] objects.
 * Handles delta compression (rawdata only, per GH3X spec) with fallback-to-last
 * for flags, algo_data, and agc_info when not present in a frame.
 *
 * Wire format (per frame, matching gh3220_data_package.h):
 *   pack_header → func_id? → frame_id? → rawdata_size rawdata[]?
 *   → gs_data_size gs_data[]? → flags_size flags[]?
 *   → algo_data_size algo_data[]? → agc_info_size agc_info[]?
 *
 * C reference: .claude/gh_protocol/c/gh3220/gh3220_data_package_decode.c
 */
class Gh3220FrameDecoder {

    private var startFlag = true

    // Delta accumulation state (only rawdata uses delta per GH3X spec)
    private val lastRawdata = IntArray(MAX_CHANNELS_RAW_3220)
    private var lastRawdataSize = 0

    // Fallback state (flags/algo/agc are only transmitted when changed)
    private val lastGsData = IntArray(MAX_GS_DATA_3220)
    private var lastGsDataSize = 0

    private val lastFlags = IntArray(MAX_FLAG_DATA_3220)
    private var lastFlagsSize = 0

    private val lastAlgoData = IntArray(MAX_ALGO_DATA_3220)
    private var lastAlgoSize = 0

    private val lastAgcInfo = IntArray(MAX_AGC_INFO_3220)
    private var lastAgcInfoSize = 0

    fun reset() {
        startFlag = true
        lastRawdata.fill(0); lastRawdataSize = 0
        lastGsData.fill(0); lastGsDataSize = 0
        lastFlags.fill(0); lastFlagsSize = 0
        lastAlgoData.fill(0); lastAlgoSize = 0
        lastAgcInfo.fill(0); lastAgcInfoSize = 0
    }

    /**
     * Decode a byte buffer containing one or more GH3220 G-protocol frames.
     * State is reset at the start of each decode call.
     */
    fun decode(param: ByteArray): List<GhFuncFrame> {
        reset()
        val frames = mutableListOf<GhFuncFrame>()
        var pos = 0
        while (pos < param.size) {
            try {
                val (newPos, rawFrame) = decodeSingleFrame(param, pos)
                pos = newPos
                frames.add(processDelta(rawFrame))
            } catch (_: DecodeException) {
                Timber.w("GH3220 decode stopped at pos=$pos, size=${param.size}")
                break
            }
        }
        return frames
    }

    // ---- Wire format deserialization ----

    private fun decodeSingleFrame(buf: ByteArray, start: Int): Pair<Int, RawFrame> {
        var pos = start
        val raw = RawFrame()

        // 1. pack_header
        val (hdrRaw, p1) = readVarint(buf, pos); pos = p1
        raw.packHeader = Gh3220PackHeader(zigzagDecode(hdrRaw))
        if (debugLogEnabled) Timber.v("GH3220 frame @$start: header bits=${raw.packHeader.bits}")

        // 2. function_id
        if (raw.packHeader.funcIdEn) {
            val (v, p) = readSigned(buf, pos); pos = p
            raw.functionId = v
            if (debugLogEnabled) Timber.v("  funcId=$v (${Gh3220FuncId.from(v).label})")
        }

        // 3. frame_id
        if (raw.packHeader.frameIdEn) {
            val (v, p) = readSigned(buf, pos); pos = p
            raw.frameId = v
            if (debugLogEnabled) Timber.v("  frameId=$v")
        }

        // 4. rawdata (size + array)
        if (raw.packHeader.rawdataEn) {
            val (sz, p) = readSigned(buf, pos); pos = p
            val count = sz.coerceIn(0, MAX_CHANNELS_RAW_3220)
            val (arr, p2) = readSignedArray(buf, pos, count); pos = p2
            raw.rawdata = arr
            if (debugLogEnabled) Timber.v("  rawdata: sz=$count first=${arr.firstOrNull()}")
        }

        // 5. gs_data (size + array)
        if (raw.packHeader.gsDataEn) {
            val (sz, p) = readSigned(buf, pos); pos = p
            val count = sz.coerceIn(0, MAX_GS_DATA_3220)
            val (arr, p2) = readSignedArray(buf, pos, count); pos = p2
            raw.gsData = arr
            if (debugLogEnabled) Timber.v("  gsData: sz=$count values=${arr.toList()}")
        }

        // 6. flags (size + array)
        if (raw.packHeader.flagsEn) {
            val (sz, p) = readSigned(buf, pos); pos = p
            val count = sz.coerceIn(0, MAX_FLAG_DATA_3220)
            val (arr, p2) = readSignedArray(buf, pos, count); pos = p2
            raw.flags = arr
            if (debugLogEnabled) Timber.v("  flags: sz=$count")
        }

        // 7. algo_data (size + array)
        if (raw.packHeader.algoResEn) {
            val (sz, p) = readSigned(buf, pos); pos = p
            val count = sz.coerceIn(0, MAX_ALGO_DATA_3220)
            val (arr, p2) = readSignedArray(buf, pos, count); pos = p2
            raw.algoData = arr
            if (debugLogEnabled) Timber.v("  algoData: sz=$count values=${arr.toList()}")
        }

        // 8. agc_info (size + array)
        if (raw.packHeader.agcInfoEn) {
            val (sz, p) = readSigned(buf, pos); pos = p
            val count = sz.coerceIn(0, MAX_AGC_INFO_3220)
            val (arr, p2) = readSignedArray(buf, pos, count); pos = p2
            raw.agcInfo = arr
            if (debugLogEnabled) Timber.v("  agcInfo: sz=$count")
        }

        return Pair(pos, raw)
    }

    // ---- Delta accumulation and fallback handling ----

    private fun processDelta(raw: RawFrame): GhFuncFrame {
        val frame = GhFuncFrame()

        val gh3220FuncId = Gh3220FuncId.from(raw.functionId)
        frame.funcId = mapToCommonFuncId(gh3220FuncId)
        frame.frameCnt = raw.frameId

        // Rawdata: delta-accumulated
        frame.rawdata = applyDelta(raw.rawdata, lastRawdata)
        lastRawdataSize = raw.rawdata.size

        // Gsensor data: absolute values, fallback to last
        if (raw.gsData.isNotEmpty()) {
            frame.gsData = raw.gsData
            copyInto(raw.gsData, lastGsData); lastGsDataSize = raw.gsData.size
        } else if (lastGsDataSize > 0) {
            frame.gsData = lastGsData.copyOf(lastGsDataSize)
        }

        // Flags: absolute values, fallback to last
        if (raw.flags.isNotEmpty()) {
            frame.flags = raw.flags
            copyInto(raw.flags, lastFlags); lastFlagsSize = raw.flags.size
        } else if (lastFlagsSize > 0) {
            frame.flags = lastFlags.copyOf(lastFlagsSize)
        }

        // Algo data: absolute values, fallback to last
        if (raw.algoData.isNotEmpty()) {
            frame.algoData = raw.algoData
            copyInto(raw.algoData, lastAlgoData); lastAlgoSize = raw.algoData.size
        } else if (lastAlgoSize > 0) {
            frame.algoData = lastAlgoData.copyOf(lastAlgoSize)
        }

        // AGC info: absolute values, fallback to last (single array, no high/low split)
        if (raw.agcInfo.isNotEmpty()) {
            frame.agcInfo = raw.agcInfo
            copyInto(raw.agcInfo, lastAgcInfo); lastAgcInfoSize = raw.agcInfo.size
        } else if (lastAgcInfoSize > 0) {
            frame.agcInfo = lastAgcInfo.copyOf(lastAgcInfoSize)
        }
        frame.agcInfoHigh = IntArray(0)

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
        for (i in src.indices) { if (i < dst.size) dst[i] = src[i] }
    }

    // ---- Internal: wire format intermediate representation ----

    private class RawFrame {
        var packHeader = Gh3220PackHeader(0)
        var functionId = 0
        var frameId = 0
        var rawdata = IntArray(0)
        var gsData = IntArray(0)
        var flags = IntArray(0)
        var algoData = IntArray(0)
        var agcInfo = IntArray(0)
    }

    // ---- Companion: varint+zigzag decoding ----

    companion object {
        @Volatile var debugLogEnabled = false
        /** Read a single unsigned varint from buffer. */
        fun readVarint(buffer: ByteArray, startPos: Int): Pair<Int, Int> {
            var value = 0
            var shift = 0
            var pos = startPos
            while (true) {
                if (pos >= buffer.size) throw DecodeException("Insufficient data at pos=$pos")
                val b = buffer[pos].toInt() and 0xFF; pos++
                value = value or ((b and 0x7F) shl shift)
                if ((b and 0x80) == 0) break
                shift += 7
                if (shift >= 35) throw DecodeException("Invalid varint at pos=$startPos")
            }
            return Pair(value, pos)
        }

        /** ZigZag decode: unsigned varint → signed int32. */
        fun zigzagDecode(x: Int): Int = (x ushr 1) xor (-(x and 1))

        /** Read one varint+zigzag-encoded signed int32. */
        fun readSigned(buffer: ByteArray, pos: Int): Pair<Int, Int> {
            val (raw, newPos) = readVarint(buffer, pos)
            return Pair(zigzagDecode(raw), newPos)
        }

        /** Read `count` varint+zigzag-encoded signed int32 values. */
        fun readSignedArray(buffer: ByteArray, startPos: Int, count: Int): Pair<IntArray, Int> {
            val result = IntArray(count)
            var pos = startPos
            for (i in 0 until count) {
                val (v, np) = readSigned(buffer, pos)
                result[i] = v
                pos = np
            }
            return Pair(result, pos)
        }

        /**
         * Map GH3220 function ID to common [GhFuncId].
         *
         * GH3220 IDs (gh_drv.h):
         *   0=ADT, 1=HR, 2=HRV, 3=HSM, 4=FPBP, 5=PWA, 6=SPO2, 7=ECG,
         *   8=PWTT, 9=SOFT_ADT_GREEN, 10=BT, 11=RESP, 12=AF,
         *   13=TEST1, 14=TEST2, 15=SOFT_ADT_IR, 16=RS0, 17=RS1,
         *   18=RS2, 19=LEAD_DET
         */
        fun mapToCommonFuncId(gh3220Id: Gh3220FuncId): GhFuncId = when (gh3220Id) {
            Gh3220FuncId.ADT            -> GhFuncId.ADT
            Gh3220FuncId.HR             -> GhFuncId.HR
            Gh3220FuncId.HRV            -> GhFuncId.HRV
            Gh3220FuncId.HSM            -> GhFuncId.HSM
            Gh3220FuncId.FPBP           -> GhFuncId.FPBP
            Gh3220FuncId.PWA            -> GhFuncId.PWA
            Gh3220FuncId.SPO2           -> GhFuncId.SPO2
            Gh3220FuncId.ECG            -> GhFuncId.ECG
            Gh3220FuncId.PWTT           -> GhFuncId.PWTT
            Gh3220FuncId.SOFT_ADT_GREEN -> GhFuncId.NADT_GREEN
            Gh3220FuncId.BT             -> GhFuncId.BT
            Gh3220FuncId.RESP           -> GhFuncId.RESP
            Gh3220FuncId.AF             -> GhFuncId.AF
            Gh3220FuncId.TEST1          -> GhFuncId.TEST1
            Gh3220FuncId.TEST2          -> GhFuncId.TEST2
            Gh3220FuncId.SOFT_ADT_IR    -> GhFuncId.NADT_IR
            Gh3220FuncId.LEAD_DET       -> GhFuncId.LEAD
            // RS0/RS1/RS2 are GH3220-specific test modes with no common equivalent
            else -> GhFuncId.UNKNOWN
        }
    }
}
