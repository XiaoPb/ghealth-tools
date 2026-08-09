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
    fun `decode08 rejects package len overflow`() {
        // dataLen=0x10，剩余仅 1 字节，超出 payload
        assertTrue(decoder.decode08(bytes(0x0E, 0x10, 0x00)).isFailure)
    }

    @Test
    fun `decode08 rejects header truncated`() {
        assertTrue(decoder.decode08(bytes(0x0E)).isFailure)
    }

    @Test
    fun `decode08 accepts empty package`() {
        val result = decoder.decode08(bytes(0x00, 0x00))
        assertTrue(result.isSuccess, "result: $result")
        assertTrue(result.getOrThrow().isEmpty())
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

    @Test
    fun `decode08 parses signed acc`() {
        // dataType=0x01：仅 bit0(acc) 置位；单通道 frameId + acc(6B) + rawdata(4B)
        val singleChannel = RawDataDecoder(SamplingConfig(channelCount = 1))
        val payload = bytes(
            0x01, 1 + 6 + 4,
            0x00,
            0xF8, 0x00, 0x01, 0x02, 0x03, 0x04,
            0x10, 0x11, 0x12, 0x13,
        )
        val result = singleChannel.decode08(payload)
        assertTrue(result.isSuccess, "result: $result")
        val frames = result.getOrThrow()
        assertEquals(1, frames.size)
        // 0xF800 → 有符号 int16 → -2048
        assertContentEquals(intArrayOf(-2048, 0x0102, 0x0304), frames[0].acc)
    }

    @Test
    fun `decode08 rejects results segment not multiple of 5`() {
        // dataType=0x02：仅 bit1(results) 置位；单通道 frameId + rawdata(4B)。
        // byteNum=6，结果段 (tag+value4B)+0xAA 非 5 的倍数，应严格失败。
        val singleChannel = RawDataDecoder(SamplingConfig(channelCount = 1))
        val payload = bytes(
            0x02, 1 + 4 + 1 + 6,
            0x00,
            0x01, 0x02, 0x03, 0x04,
            0x06, 0x01, 0x2A, 0x00, 0x00, 0x00, 0xAA,
        )
        assertTrue(singleChannel.decode08(payload).isFailure)
    }

    @Test
    fun `decode08 parses multiple packages with non-zero funcId`() {
        // 包1: dataType=0x1E (funcId=1)，最小帧 = frameId + rawdata(4B) + agc(3B) + amb(3B) + byteNum=0
        // 包2: dataType=0x00 (funcId=0)，最小帧 = frameId + rawdata(4B)
        val singleChannel = RawDataDecoder(SamplingConfig(channelCount = 1))
        val package1 = bytes(
            0x1E, 1 + 4 + 3 + 3 + 1,
            0x00,
            0x01, 0x02, 0x03, 0x04,
            0x05, 0x06, 0x07,
            0x08, 0x09, 0x0A,
            0x00,
        )
        val package2 = bytes(0x00, 1 + 4, 0x01, 0x11, 0x12, 0x13, 0x14)
        val result = singleChannel.decode08(package1 + package2)
        assertTrue(result.isSuccess, "result: $result")
        val frames = result.getOrThrow()
        assertEquals(2, frames.size)
        assertEquals(0x01, frames[0].funcId)
        assertEquals(0x00, frames[1].funcId)
    }
}
