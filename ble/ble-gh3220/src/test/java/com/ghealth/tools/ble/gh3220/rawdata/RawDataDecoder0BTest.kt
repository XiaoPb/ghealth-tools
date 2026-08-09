package com.ghealth.tools.ble.gh3220.rawdata

import com.ghealth.tools.ble.itlvc.core.ItlvcError
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RawDataDecoder0BTest {

    private val decoder = RawDataDecoder(
        SamplingConfig(channelCount = 2, gsEnabled = false, agcEnabled = true, ambEnabled = true, algoEnabled = true),
    )

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private fun hexBytes(s: String): ByteArray {
        require(s.length % 2 == 0)
        return ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    @Test
    fun `decode0B uncompressed single function parses header and frames`() {
        // dataType=0x0E（agc+amb+algo），chMask=0x00000003（通道 0、1），flag=0x00（未压缩/偶数/单功能）
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
        val dataLen = frame0.size + frame1.size
        val payload = bytes(0x0E, 0x00, 0x00, 0x00, 0x03, 0x00, dataLen) + frame0 + frame1

        val pkg = decoder.decode0B(payload).getOrThrow()
        assertEquals(0x0E, pkg.dataType)
        assertEquals(0, pkg.funcId)
        assertEquals(0x00000003, pkg.channelMask)
        assertContentEquals(intArrayOf(0, 1), pkg.activeChannels)
        assertEquals(false, pkg.compressed)
        assertEquals(false, pkg.oddPacket)
        assertEquals(false, pkg.multiFunction)
        assertEquals(0, pkg.splicePackCount)
        assertEquals(false, pkg.splicePackOver)

        assertEquals(2, pkg.frames.size)
        val f0 = pkg.frames[0]
        assertEquals(0x00, f0.frameId)
        assertEquals(null, f0.channel)
        assertContentEquals(intArrayOf(0x01020304, 0x05060708), f0.rawdata)
        assertContentEquals(intArrayOf(0x101112, 0x131415), f0.agc)
        assertContentEquals(intArrayOf(0x202122, 0x232425), f0.amb)
        assertEquals(1, f0.results.size)
        assertEquals(0x01, f0.results[0].tag)
        assertEquals(0x2A, f0.results[0].value)
        assertEquals(0x01, pkg.frames[1].frameId)
    }

    @Test
    fun `decode0B multifunction attributes channel from mask`() {
        // 通道 0 的包：chMask=0x00000001，flag=0x04（bit2 多功能），帧 = [frameId][fifoId][rawdata 4B]
        val p1 = bytes(0x00, 0x00, 0x00, 0x00, 0x01, 0x04, 0x06) + bytes(0x00, 0x01, 0x01, 0x02, 0x03, 0x04)
        val r1 = decoder.decode0B(p1).getOrThrow()
        assertTrue(r1.multiFunction)
        assertEquals(1, r1.frames.size)
        assertEquals(0, r1.frames[0].channel)
        assertContentEquals(intArrayOf(0x01020304), r1.frames[0].rawdata)

        // 通道 1 的包：chMask=0x00000002
        val p2 = bytes(0x00, 0x00, 0x00, 0x00, 0x02, 0x04, 0x06) + bytes(0x01, 0x02, 0x05, 0x06, 0x07, 0x08)
        val r2 = decoder.decode0B(p2).getOrThrow()
        assertEquals(1, r2.frames.size)
        assertEquals(1, r2.frames[0].channel)
        assertContentEquals(intArrayOf(0x05060708), r2.frames[0].rawdata)
    }

    @Test
    fun `decode0B compressed even packet recovers absolute values`() {
        val one = RawDataDecoder(SamplingConfig(channelCount = 1, agcEnabled = true, algoEnabled = true))
        // 帧 = [frameId=0][rawLen=6][tagFlag=0][E2 26 5B 1F 50][agcLen=0][result 0x00]
        val frame = bytes(0x00, 0x06, 0x00, 0xE2, 0x26, 0x5B, 0x1F, 0x50, 0x00, 0x00)
        val payload = bytes(0x00, 0x00, 0x00, 0x00, 0x01, 0x01, frame.size) + frame

        val pkg = one.decode0B(payload).getOrThrow()
        assertTrue(pkg.compressed)
        assertEquals(false, pkg.oddPacket)
        assertContentEquals(intArrayOf(0x2265B1F5), pkg.frames[0].rawdata)
    }

    @Test
    fun `decode08 absolute frame syncs diff baseline for later zip frames`() {
        val dec = RawDataDecoder(SamplingConfig(channelCount = 1, agcEnabled = true, algoEnabled = true))
        // 0x08 未压缩绝对值帧：dataType=0x00，frame = [frameId=0][0x2265B1F5]
        val abs = dec.decode08(bytes(0x00, 0x05, 0x00, 0x22, 0x65, 0xB1, 0xF5)).getOrThrow()
        assertContentEquals(intArrayOf(0x2265B1F5), abs[0].rawdata)

        // 0x0A 差分帧 e6f51a6550（golden：0x2265B1F5 → 0x91B7584A）
        val diffFrame = bytes(0x01, 0x06, 0x00) + hexBytes("e6f51a6550") + bytes(0x00, 0x00)
        val odd = dec.decode0A(diffFrame).getOrThrow()
        assertContentEquals(intArrayOf(0x91B7584A.toInt()), odd[0].rawdata)
    }

    @Test
    fun `decode0B rejects truncated header and data overflow`() {
        assertTrue(decoder.decode0B(bytes(0x0E, 0x00, 0x00)).isFailure)
        assertTrue(decoder.decode0B(bytes(0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x0A, 0x00)).isFailure)
        assertTrue(decoder.decode0B(bytes(0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x10) + bytes(0x00)).isFailure)
    }

    @Test
    fun `decode0B rejects malformed compressed frame`() {
        val one = RawDataDecoder(SamplingConfig(channelCount = 1))
        // rawLen=1 < tagFlag(1)+tag(1B)=2，触发长度守卫
        val frame = bytes(0x00, 0x01, 0x01, 0x01)
        val payload = bytes(0x00, 0x00, 0x00, 0x00, 0x01, 0x01, frame.size) + frame
        assertTrue(one.decode0B(payload).isFailure)
        assertTrue(one.decode0B(payload).exceptionOrNull() is ItlvcError.ParseError)
    }

    @Test
    fun `decode0B rejects empty channel mask`() {
        // chMask=0x00000000 → ParseError（避免 DiffDecoder 尺寸不匹配异常逃逸 Result）
        val payload = bytes(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        assertTrue(decoder.decode0B(payload).isFailure)
        assertTrue(decoder.decode0B(payload).exceptionOrNull() is ItlvcError.ParseError)
    }

    @Test
    fun `decode0B rejects multifunction with multiple channel bits`() {
        // chMask=0x00000003 + flag=0x04：多功能每包只允许 1 个通道位
        val payload = bytes(0x00, 0x00, 0x00, 0x00, 0x03, 0x04, 0x00)
        assertTrue(decoder.decode0B(payload).isFailure)
        assertTrue(decoder.decode0B(payload).exceptionOrNull() is ItlvcError.ParseError)
    }

    @Test
    fun `decode0B rejects compressed multifunction`() {
        // flag=0x05（压缩 + 多功能）：当前显式拒绝
        val payload = bytes(0x00, 0x00, 0x00, 0x00, 0x01, 0x05, 0x00)
        assertTrue(decoder.decode0B(payload).isFailure)
        assertTrue(decoder.decode0B(payload).exceptionOrNull() is ItlvcError.ParseError)
    }

    @Test
    fun `decode0B rejects channel count mismatch with config`() {
        // chMask=0x00000001（1 通道）但 config.channelCount=2 → ParseError
        val payload = bytes(0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00)
        assertTrue(decoder.decode0B(payload).isFailure)
        assertTrue(decoder.decode0B(payload).exceptionOrNull() is ItlvcError.ParseError)
    }

    @Test
    fun `decode0B rejects trailing bytes after data len`() {
        // dataLen=0 但 payload 末尾多 1 字节
        val payload = bytes(0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0xFF)
        assertTrue(decoder.decode0B(payload).isFailure)
        assertTrue(decoder.decode0B(payload).exceptionOrNull() is ItlvcError.ParseError)
    }

    @Test
    fun `decode0B parses compressed odd packet with splice flags`() {
        // chMask=0x1，flag=0x2B（压缩 bit0 + 奇数包 bit1 + 分包计数 1 bits3-4 + 分包结束 bit5）
        // 注：审查稿 flag=0x3B 的 bits3-4=3，与 splicePackCount=1 断言矛盾，此处用 0x2B 使语义一致
        val one = RawDataDecoder(SamplingConfig(channelCount = 1, agcEnabled = true, algoEnabled = true))
        // 帧 = [frameId=0][rawLen=6][tagFlag=0][E2 26 5B 1F 50][agcLen=0][result 0x00]
        val frame = bytes(0x00, 0x06, 0x00, 0xE2, 0x26, 0x5B, 0x1F, 0x50, 0x00, 0x00)
        val payload = bytes(0x00, 0x00, 0x00, 0x00, 0x01, 0x2B, frame.size) + frame
        val pkg = one.decode0B(payload).getOrThrow()
        assertTrue(pkg.compressed)
        assertTrue(pkg.oddPacket)
        assertEquals(1, pkg.splicePackCount)
        assertTrue(pkg.splicePackOver)
        assertContentEquals(intArrayOf(0x2265B1F5), pkg.frames[0].rawdata)
    }

    @Test
    fun `decode0B uncompressed frame syncs baseline for later compressed frame`() {
        val one = RawDataDecoder(SamplingConfig(channelCount = 1, agcEnabled = true, algoEnabled = true))
        // 未压缩绝对帧：dataType=0x00，frame = [frameId=0][0x2265B1F5]
        val absPayload = bytes(0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x05) + bytes(0x00, 0x22, 0x65, 0xB1, 0xF5)
        val abs = one.decode0B(absPayload).getOrThrow()
        assertContentEquals(intArrayOf(0x2265B1F5), abs.frames[0].rawdata)

        // 压缩差分帧 e6f51a6550（golden：0x2265B1F5 → 0x91B7584A）
        val diffFrame = bytes(0x01, 0x06, 0x00) + hexBytes("e6f51a6550") + bytes(0x00, 0x00)
        val pkg = one.decode0B(bytes(0x00, 0x00, 0x00, 0x00, 0x01, 0x01, diffFrame.size) + diffFrame).getOrThrow()
        assertContentEquals(intArrayOf(0x91B7584A.toInt()), pkg.frames[0].rawdata)
    }
}
