package com.ghealth.tools.ble.gh3220.rawdata

import com.ghealth.tools.ble.itlvc.core.ItlvcError

/**
 * 差分 nibble 流读取器：每字节两个 4bit nibble，先高后低。
 * 对应设备端 gh_zip.c 的 nibble 打包顺序。
 */
class NibbleReader(private val data: ByteArray) {
    private var byteIndex = 0
    private var highNibble = true

    fun readNibble(): Int? {
        if (byteIndex >= data.size) return null
        val b = data[byteIndex].toInt() and 0xFF
        val v = if (highNibble) (b shr 4) else (b and 0x0F)
        if (highNibble) {
            highNibble = false
        } else {
            highNibble = true
            byteIndex++
        }
        return v
    }
}

/**
 * 差分解压：每个通道先读 4bit 类型 nibble，再读 ((type/2)+1) 个 4bit 大端值 nibble；
 * 偶类型 = 正差分，奇类型 = 负差分；按 32bit 回绕累加到该通道上一帧值。
 * 状态跨帧/跨包保持（0x09 偶数包基准帧 + 0x0A 奇数包差分）。
 */
class DiffDecoder(private val channelCount: Int) {

    init {
        require(channelCount > 0) { "channelCount must be positive: $channelCount" }
    }

    private var last = IntArray(channelCount)

    fun reset() {
        last = IntArray(channelCount)
    }

    fun decode(data: ByteArray): Result<IntArray> {
        val reader = NibbleReader(data)
        val out = IntArray(channelCount)
        for (ch in 0 until channelCount) {
            val type = reader.readNibble() ?: return Result.failure(ItlvcError.ParseError("diff: type nibble missing"))
            val valueNibbles = (type / 2) + 1
            var magnitude = 0
            repeat(valueNibbles) {
                val nibble = reader.readNibble() ?: return Result.failure(ItlvcError.ParseError("diff: value nibble missing"))
                magnitude = (magnitude shl 4) or nibble
            }
            val signed = if (type % 2 == 0) magnitude else -magnitude
            val value = last[ch] + signed
            out[ch] = value
        }
        last = out.copyOf()
        return Result.success(out)
    }
}
