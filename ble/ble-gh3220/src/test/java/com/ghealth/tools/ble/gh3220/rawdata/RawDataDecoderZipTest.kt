package com.ghealth.tools.ble.gh3220.rawdata

import com.ghealth.tools.ble.itlvc.core.ItlvcError
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RawDataDecoderZipTest {

    private val decoder = RawDataDecoder(
        SamplingConfig(channelCount = 2, agcEnabled = true, algoEnabled = true),
    )

    /** 单通道解码器：0x09/0x0A 差分恢复测试使用（差分流按通道连续 nibble 打包）。 */
    private val singleChannel = RawDataDecoder(
        SamplingConfig(channelCount = 1, agcEnabled = true, algoEnabled = true),
    )

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    /** 手动构造一帧：frameId + rawLen + tagFlag(0) + diff + agcLen + agcDiff + result。 */
    private fun frame(
        frameId: Int,
        rawDiff: ByteArray,
        agcDiff: ByteArray,
        result: ByteArray,
    ): ByteArray = bytes(frameId, rawDiff.size + 1, 0) + rawDiff + bytes(agcDiff.size) + agcDiff + result

    @Test
    fun `decode09 even packet first frame absolute`() {
        // rawdata ch0/ch1 绝对值（从 last=0 差分编码：类型 14 = 32bit 正差分）
        // 差分流连续 nibble 打包：ch0 E+0x2265B1F5、ch1 E+0xF4BEA973
        val rawDiff = bytes(0xE2, 0x26, 0x5B, 0x1F, 0x5E, 0xF4, 0xBE, 0xA9, 0x73)
        // agc 同理（类型 14 = 32bit 正差分）：0x010203 / 0x050607，高位补 0
        val agcDiff = bytes(0xE0, 0x00, 0x10, 0x20, 0x3E, 0x00, 0x05, 0x06, 0x07)
        val frame0 = frame(0, rawDiff, agcDiff, bytes(0x00))
        val payload = frame0

        val result = decoder.decode09(payload)
        assertTrue(result.isSuccess, "result: $result")
        val frames = result.getOrThrow()
        assertEquals(1, frames.size)
        assertContentEquals(intArrayOf(0x2265B1F5, 0xF4BEA973.toInt()), frames[0].rawdata)
        assertContentEquals(intArrayOf(0x010203, 0x050607), frames[0].agc)
    }

    @Test
    fun `decode09 then decode0A recovers diffs`() {
        // 偶数包：帧0 绝对值 0x2265B1F5（单通道，类型 14 = 32bit 正差分）
        val evenPayload = frame(
            0,
            bytes(0xE2, 0x26, 0x5B, 0x1F, 0x50),
            bytes(0xE0, 0x00, 0x10, 0x20, 0x30),
            bytes(0x00),
        )
        val even = singleChannel.decode09(evenPayload).getOrThrow()
        assertContentEquals(intArrayOf(0x2265B1F5), even[0].rawdata)

        // 奇数包：差分 → 0x91B7584A（相对偶数包最后一帧 0x2265B1F5）
        val oddPayload = frame(1, bytes(0xE6, 0xF5, 0x1A, 0x65, 0x50), bytes(0x00), bytes(0x00))
        val odd = singleChannel.decode0A(oddPayload).getOrThrow()
        assertEquals(1, odd.size)
        assertContentEquals(intArrayOf(0x91B7584A.toInt()), odd[0].rawdata)
    }

    @Test
    fun `decode09 rejects truncated raw section`() {
        // rawLen=5 但只有 3 字节
        val payload = bytes(0x00, 0x05, 0x00, 0xE2, 0x26, 0x5B)
        val result = decoder.decode09(payload)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ItlvcError.ParseError)
    }
}
