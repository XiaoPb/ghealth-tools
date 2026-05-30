package com.ghealth.tools.ble.protocol.gh3300

import com.ghealth.tools.ble.protocol.gh3036.DecodeException
import com.ghealth.tools.ble.protocol.gh3036.GhFuncFrame
import com.ghealth.tools.ble.protocol.gh3036.GhFuncId
import timber.log.Timber

class Gh3300FrameDecoder {

    private var startFlag = true
    private val lastRawdata = IntArray(MAX_CHANNELS_RAW_3300)

    fun reset() {
        startFlag = true
        lastRawdata.fill(0)
    }

    fun decode(param: ByteArray): List<GhFuncFrame> {
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
        raw.packHeader = Gh3300PackHeader(zigzagDecode(hdrRaw))

        if (debugLogEnabled) Timber.v("GH3300 decodeSingleFrame: startPos=$start, packHeader bits=${raw.packHeader.bits}")

        if (raw.packHeader.rawdataEn) {
            val (sz, p) = readSigned(buf, pos); pos = p
            val count = sz.coerceIn(0, MAX_CHANNELS_RAW_3300)
            val (arr, p2) = readSignedArray(buf, pos, count); pos = p2
            raw.rawdata = arr
            if (debugLogEnabled) Timber.v("  rawdata: sz=$sz, values=${arr.take(3).toList()}...")
        }
        if (raw.packHeader.gsDataEn) {
            val (sz, p) = readSigned(buf, pos); pos = p
            val (arr, p2) = readSignedArray(buf, pos, sz.coerceIn(0, MAX_GS_DATA_3300)); pos = p2
            raw.gsData = arr
            if (debugLogEnabled) Timber.v("  gsData: sz=$sz, values=${arr.toList()}")
        }
        if (raw.packHeader.flagsEn) {
            val (sz, p) = readSigned(buf, pos); pos = p
            val (arr, p2) = readSignedArray(buf, pos, sz.coerceIn(0, MAX_FLAG_DATA_3300)); pos = p2
            raw.flags = arr
            if (debugLogEnabled) Timber.v("  flags: sz=$sz")
        }
        if (raw.packHeader.algDataEn) {
            val (sz, p) = readSigned(buf, pos); pos = p
            val (arr, p2) = readSignedArray(buf, pos, sz.coerceIn(0, MAX_ALGO_DATA_3300)); pos = p2
            raw.algoData = arr
            if (debugLogEnabled) Timber.v("  algoData: sz=$sz, values=${arr.toList()}")
        }
        if (raw.packHeader.agcInfoEn) {
            val (sz, p) = readSigned(buf, pos); pos = p
            val (arr, p2) = readSignedArray(buf, pos, sz.coerceIn(0, MAX_CHANNELS_RAW_3300)); pos = p2
            raw.agcInfo = arr
            if (debugLogEnabled) Timber.v("  agcInfo: sz=$sz")
        }
        if (raw.packHeader.frameIdEn) {
            val (fid, pf) = readSigned(buf, pos); pos = pf
            raw.frameId = fid
            if (debugLogEnabled) Timber.v("  frameId: $fid")
        }
        if (raw.packHeader.funcIdEn) {
            val (v, p) = readSigned(buf, pos); pos = p
            raw.functionId = v
            if (debugLogEnabled) Timber.v("  funcId: $v")
        }
        return Pair(pos, raw)
    }

    private fun processDelta(raw: RawFrame): GhFuncFrame {
        val frame = GhFuncFrame()
        frame.frameCnt = raw.frameId

        val gh3300FuncId = Gh3300FuncId.from(raw.functionId)
        frame.funcId = mapToCommonFuncId(gh3300FuncId)

        // Only rawdata uses delta compression in GH3300 (matching C reference)
        frame.rawdata = applyRawdataDelta(raw.rawdata)

        // Other fields are absolute values (no delta per GH3300 C reference)
        frame.gsData = raw.gsData
        frame.flags = raw.flags
        frame.algoData = raw.algoData
        frame.agcInfo = raw.agcInfo
        frame.agcInfoHigh = IntArray(0)

        startFlag = false
        return frame
    }

    private fun applyRawdataDelta(values: IntArray): IntArray {
        val result = IntArray(values.size)
        for (i in values.indices) {
            result[i] = if (startFlag) values[i] else lastRawdata.getOrElse(i) { 0 } + values[i]
            if (i < lastRawdata.size) lastRawdata[i] = result[i]
        }
        return result
    }

    private class RawFrame {
        var packHeader = Gh3300PackHeader(0)
        var rawdata = IntArray(0)
        var gsData = IntArray(0)
        var flags = IntArray(0)
        var algoData = IntArray(0)
        var agcInfo = IntArray(0)
        var frameId = 0
        var functionId = 0
    }

    companion object {
        @Volatile var debugLogEnabled = false
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
        fun readSigned(buffer: ByteArray, pos: Int): Pair<Int, Int> {
            val (raw, newPos) = readVarint(buffer, pos); return Pair(zigzagDecode(raw), newPos)
        }
        fun readSignedArray(buffer: ByteArray, startPos: Int, count: Int): Pair<IntArray, Int> {
            val result = IntArray(count); var pos = startPos
            for (i in 0 until count) { val (v, np) = readSigned(buffer, pos); result[i] = v; pos = np }
            return Pair(result, pos)
        }

        fun mapToCommonFuncId(gh3300Id: Gh3300FuncId): GhFuncId = when (gh3300Id) {
            Gh3300FuncId.ADT -> GhFuncId.ADT
            Gh3300FuncId.HR -> GhFuncId.HR
            Gh3300FuncId.HRV -> GhFuncId.HRV
            Gh3300FuncId.HSM -> GhFuncId.HSM
            Gh3300FuncId.FPBP -> GhFuncId.FPBP
            Gh3300FuncId.PWA -> GhFuncId.PWA
            Gh3300FuncId.SPO2 -> GhFuncId.SPO2
            Gh3300FuncId.ECG -> GhFuncId.ECG
            Gh3300FuncId.PWTT -> GhFuncId.PWTT
            Gh3300FuncId.SOFT_ADT_GREEN -> GhFuncId.NADT_GREEN
            Gh3300FuncId.BT -> GhFuncId.BT
            Gh3300FuncId.RESP -> GhFuncId.RESP
            Gh3300FuncId.AF -> GhFuncId.AF
            Gh3300FuncId.TEST1 -> GhFuncId.TEST1
            Gh3300FuncId.TEST2 -> GhFuncId.TEST2
            Gh3300FuncId.SOFT_ADT_IR -> GhFuncId.NADT_IR
            Gh3300FuncId.BIA -> GhFuncId.BIA
            Gh3300FuncId.GSR -> GhFuncId.GSR
            Gh3300FuncId.LEAD -> GhFuncId.LEAD
            else -> GhFuncId.UNKNOWN
        }
    }
}
