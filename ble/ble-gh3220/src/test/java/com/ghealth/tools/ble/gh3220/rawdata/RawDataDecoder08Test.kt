package com.ghealth.tools.ble.gh3220.rawdata

import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RawDataDecoder08Test {

    private val decoder = RawDataDecoder(
        SamplingConfig(channelCount = 2, gsEnabled = false, agcEnabled = true, ambEnabled = true, algoEnabled = true),
    )

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun `decode08 single package with two frames`() {
        // dataType = funcId 0 (bit4..7) + bit2(agc) + bit3(amb) + bit1(algo) = 0x0E
        // 帧0: frameId=0, rawdata ch0=0x01020304 ch1=0x05060708, agc ch0=0x101112 ch1=0x131415,
        //      amb ch0=0x202122 ch1=0x232425, result: [byteNum=5][tag=0x01][value=0x0000002A(LE)]
        val frame0 = bytes(
            0x00,
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
            0x10, 0x11, 0x12, 0x13, 0x14, 0x15,
            0x20, 0x21, 0x22, 0x23, 0x24, 0x25,
            0x05, 0x01, 0x2A, 0x00, 0x00, 0x00,
        )
        val frame1 = bytes(
            0x01,
            0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10,
            0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B,
            0x26, 0x27, 0x28, 0x29, 0x2A, 0x2B,
            0x00,
        )
        val payload = bytes(0x0E, frame0.size + frame1.size) + frame0 + frame1

        val result = decoder.decode08(payload)
        assertTrue(result.isSuccess, "result: $result")
        val frames = result.getOrThrow()
        assertEquals(2, frames.size)

        val f0 = frames[0]
        assertEquals(0x00, f0.funcId)
        assertEquals(0x00, f0.frameId)
        assertContentEquals(intArrayOf(0x01020304, 0x05060708), f0.rawdata)
        assertContentEquals(intArrayOf(0x101112, 0x131415), f0.agc)
        assertContentEquals(intArrayOf(0x202122, 0x232425), f0.amb)
        assertEquals(1, f0.results.size)
        assertEquals(0x01, f0.results[0].tag)
        assertEquals(0x2A, f0.results[0].value)

        val f1 = frames[1]
        assertEquals(0x01, f1.frameId)
        assertEquals(0, f1.results.size)
    }

    @Test
    fun `decode08 rejects truncated package`() {
        assertTrue(decoder.decode08(bytes(0x0E, 0x10, 0x00)).isFailure)
    }

    @Test
    fun `decode08 rejects truncated acc`() {
        // dataType=0x01：仅 bit0(acc) 置位；单通道 rawdata 需 4B。
        // acc 仅 4B（不足 6B），旧实现会把 acc 字节误当 rawdata 解析成功。
        val singleChannel = RawDataDecoder(SamplingConfig(channelCount = 1))
        val payload = bytes(0x01, 1 + 4, 0x00, 0x01, 0x02, 0x03, 0x04)
        assertTrue(singleChannel.decode08(payload).isFailure)
    }

    @Test
    fun `decode08 rejects truncated agc`() {
        // dataType=0x04：仅 bit2(agc) 置位；单通道 frameId + rawdata(4B)。
        // agc 位声明但字段缺失（0 字节），旧实现会静默接受 agc=null。
        val singleChannel = RawDataDecoder(SamplingConfig(channelCount = 1))
        val payload = bytes(0x04, 1 + 4, 0x00, 0x01, 0x02, 0x03, 0x04)
        assertTrue(singleChannel.decode08(payload).isFailure)
    }
}
