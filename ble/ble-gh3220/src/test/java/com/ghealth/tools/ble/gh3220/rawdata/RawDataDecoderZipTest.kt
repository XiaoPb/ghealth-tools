package com.ghealth.tools.ble.gh3220.rawdata

import com.ghealth.tools.ble.itlvc.core.ItlvcError
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RawDataDecoderZipTest {

    private val decoder = RawDataDecoder(
        SamplingConfig(channelCount = 2, agcEnabled = true, algoEnabled = true),
    )

    /** 单通道解码器：0x09/0x0A 差分恢复与 reset 测试使用（差分流按通道连续 nibble 打包）。 */
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

        // 奇数包：差分 → 0x91B7584A（相对偶数包最后一帧 0x2265B1F5，golden 1ch 向量）
        val oddPayload = frame(1, bytes(0xE6, 0xF5, 0x1A, 0x65, 0x50), bytes(0x00), bytes(0x00))
        val odd = singleChannel.decode0A(oddPayload).getOrThrow()
        assertEquals(1, odd.size)
        assertContentEquals(intArrayOf(0x91B7584A.toInt()), odd[0].rawdata)

        // 后续差分帧：0x91B7584A → 0xD8F16ADF（golden 1ch 向量 e473a12950）
        val nextPayload = frame(2, bytes(0xE4, 0x73, 0xA1, 0x29, 0x50), bytes(0x00), bytes(0x00))
        val next = singleChannel.decode0A(nextPayload).getOrThrow()
        assertContentEquals(intArrayOf(0xD8F16ADF.toInt()), next[0].rawdata)
    }

    @Test
    fun `decode09 rejects truncated raw section`() {
        // rawLen=5 但只有 3 字节
        val payload = bytes(0x00, 0x05, 0x00, 0xE2, 0x26, 0x5B)
        val result = decoder.decode09(payload)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ItlvcError.ParseError)
    }

    @Test
    fun `decode09 parses frame with tag flag and tag bytes`() {
        // tagFlag=1 时 rawLen 包含 tag* 字节（channelCount 个），rawdata 仍应正确解析
        val rawDiff = bytes(0xE2, 0x26, 0x5B, 0x1F, 0x5E, 0xF4, 0xBE, 0xA9, 0x73)
        // rawLen = 1(tagFlag) + 2(tag*) + 9(rawDiff)
        val payload = bytes(0x00, 0x0C, 0x01, 0x01, 0x02) + rawDiff + bytes(0x00, 0x00)

        val result = decoder.decode09(payload)
        assertTrue(result.isSuccess, "result: $result")
        val frames = result.getOrThrow()
        assertEquals(1, frames.size)
        assertContentEquals(intArrayOf(0x2265B1F5, 0xF4BEA973.toInt()), frames[0].rawdata)
        assertNull(frames[0].agc)
    }

    @Test
    fun `decode09 parses multiple frames in one payload`() {
        // 多帧包：单 payload 含 2 帧，帧1 相对帧0 差分（golden 2ch 向量）
        val frame0 = bytes(0x00, 0x0A, 0x00) +
            bytes(0xEF, 0x4B, 0xEA, 0x97, 0x3E, 0xDC, 0xF4, 0xBB, 0x99) +
            bytes(0x00, 0x00)
        val frame1 = bytes(0x01, 0x09, 0x00) +
            bytes(0xD2, 0x19, 0xD6, 0xF8, 0xD3, 0x99, 0x0B, 0xD1) +
            bytes(0x00, 0x00)
        val payload = frame0 + frame1

        val result = decoder.decode09(payload)
        assertTrue(result.isSuccess, "result: $result")
        val frames = result.getOrThrow()
        assertEquals(2, frames.size)
        assertEquals(0x00, frames[0].frameId)
        assertEquals(0x01, frames[1].frameId)
        assertContentEquals(intArrayOf(0xF4BEA973.toInt(), 0xDCF4BB99.toInt()), frames[0].rawdata)
        assertContentEquals(intArrayOf(0xF2A4D27B.toInt(), 0xD95BAFC8.toInt()), frames[1].rawdata)
    }

    @Test
    fun `decode09 agcLen zero yields null agc`() {
        // agcLen=0 → agc 为 null（agcEnabled 配置下读取长度后不消费差分字节）
        val payload = bytes(0x00, 0x06, 0x00) + bytes(0xE2, 0x26, 0x5B, 0x1F, 0x50) + bytes(0x00, 0x00)

        val result = singleChannel.decode09(payload)
        assertTrue(result.isSuccess, "result: $result")
        val frames = result.getOrThrow()
        assertEquals(1, frames.size)
        assertContentEquals(intArrayOf(0x2265B1F5), frames[0].rawdata)
        assertNull(frames[0].agc)
    }

    @Test
    fun `reset restores diff baseline`() {
        // 帧0 绝对值 0x2265B1F5；reset 后再次解码同一绝对帧仍为 0x2265B1F5（未累加为 0x44CB63EA）
        val absolute = frame(0, bytes(0xE2, 0x26, 0x5B, 0x1F, 0x50), bytes(0x00), bytes(0x00))
        val first = singleChannel.decode09(absolute).getOrThrow()
        assertContentEquals(intArrayOf(0x2265B1F5), first[0].rawdata)

        singleChannel.reset()
        val second = singleChannel.decode09(absolute).getOrThrow()
        assertContentEquals(intArrayOf(0x2265B1F5), second[0].rawdata)
    }

    @Test
    fun `decode09 rejects agcLen beyond remaining bytes`() {
        // agcLen=5 声明超长，剩余仅 1 字节 → take 越界 → 解析失败
        val payload = bytes(0x00, 0x06, 0x00) + bytes(0xE2, 0x26, 0x5B, 0x1F, 0x50) + bytes(0x05, 0x00)

        val result = singleChannel.decode09(payload)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ItlvcError.ParseError)
    }

    @Test
    fun `decode09 rejects corrupt agc diff segment`() {
        // rawdata 正常（2ch 绝对值），agcDiff 差分字节损坏（类型 14 值 nibble 截断）→ 解析失败
        val rawDiff = bytes(0xE2, 0x26, 0x5B, 0x1F, 0x5E, 0x00, 0x00, 0x00, 0x00)
        val corruptAgc = bytes(0xE1, 0x02, 0x03, 0x40)
        val payload = bytes(0x00, 0x0A, 0x00) + rawDiff + bytes(0x04) + corruptAgc + bytes(0x00)

        val result = decoder.decode09(payload)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ItlvcError.ParseError)
    }

    @Test
    fun `decode09 with agc disabled skips agc section`() {
        val noAgc = RawDataDecoder(SamplingConfig(channelCount = 1, algoEnabled = true))
        // 帧 = [frameId=0][rawLen=6][tagFlag=0][rawDiff 5B][result byteNum=0]（无 agcLen/agcDiff 字节）
        val payload = bytes(0x00, 0x06, 0x00, 0xE2, 0x26, 0x5B, 0x1F, 0x50, 0x00)
        val frames = noAgc.decode09(payload).getOrThrow()
        assertContentEquals(intArrayOf(0x2265B1F5), frames[0].rawdata)
        assertEquals(null, frames[0].agc)
    }
}