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
        val payload = bytes(0x00, 0x0E, 0x00, 0x00, 0x00, 0x03, 0x00, dataLen) + frame0 + frame1

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
        val p1 = bytes(0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x04, 0x06) + bytes(0x00, 0x01, 0x01, 0x02, 0x03, 0x04)
        val r1 = decoder.decode0B(p1).getOrThrow()
        assertTrue(r1.multiFunction)
        assertEquals(1, r1.frames.size)
        assertEquals(0, r1.frames[0].channel)
        assertContentEquals(intArrayOf(0x01020304), r1.frames[0].rawdata)

        // 通道 1 的包：chMask=0x00000002
        val p2 = bytes(0x00, 0x00, 0x00, 0x00, 0x00, 0x02, 0x04, 0x06) + bytes(0x01, 0x02, 0x05, 0x06, 0x07, 0x08)
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
        val payload = bytes(0x00, 0x06, 0x00, 0x00, 0x00, 0x01, 0x01, frame.size) + frame

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
        assertTrue(decoder.decode0B(bytes(0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x0A)).isFailure)
        assertTrue(decoder.decode0B(bytes(0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x10, 0x00) + bytes(0x00)).isFailure)
    }

    @Test
    fun `decode0B rejects malformed compressed frame`() {
        val one = RawDataDecoder(SamplingConfig(channelCount = 1))
        // rawLen=1 < tagFlag(1)+tag(1B)=2，触发长度守卫
        val frame = bytes(0x00, 0x01, 0x01, 0x01)
        val payload = bytes(0x00, 0x06, 0x00, 0x00, 0x00, 0x01, 0x01, frame.size) + frame
        assertTrue(one.decode0B(payload).isFailure)
        assertTrue(one.decode0B(payload).exceptionOrNull() is ItlvcError.ParseError)
    }

    @Test
    fun `decode0B rejects empty channel mask`() {
        // chMask=0x00000000 → ParseError（避免 DiffDecoder 尺寸不匹配异常逃逸 Result）
        val payload = bytes(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        assertTrue(decoder.decode0B(payload).isFailure)
        assertTrue(decoder.decode0B(payload).exceptionOrNull() is ItlvcError.ParseError)
    }

    @Test
    fun `decode0B rejects multifunction with multiple channel bits`() {
        // chMask=0x00000003 + flag=0x04：多功能每包只允许 1 个通道位
        val payload = bytes(0x00, 0x00, 0x00, 0x00, 0x00, 0x03, 0x04, 0x00)
        assertTrue(decoder.decode0B(payload).isFailure)
        assertTrue(decoder.decode0B(payload).exceptionOrNull() is ItlvcError.ParseError)
    }

    @Test
    fun `decode0B rejects compressed multifunction`() {
        // flag=0x05（压缩 + 多功能）：当前显式拒绝
        val payload = bytes(0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x05, 0x00)
        assertTrue(decoder.decode0B(payload).isFailure)
        assertTrue(decoder.decode0B(payload).exceptionOrNull() is ItlvcError.ParseError)
    }

    @Test
    fun `decode0B adapts channel count to chMask when config differs`() {
        // 0x0B 包自描述通道数（chMask 置位数）：chMask=0x00000001（1 通道）但 config.channelCount=2 时
        // 按包内掩码重建差分解码器，不再按配置拒绝（真实设备 HR 4 通道 > 默认配置 1 通道）。
        val two = RawDataDecoder(SamplingConfig(channelCount = 2, agcEnabled = true, algoEnabled = true))
        // 绝对首帧（flag=0x03：压缩 + oddeven）：[frameId=0][rawdata 4B][agc 4B][result 0x00]
        val frame = bytes(0x00, 0x11, 0x22, 0x33, 0x44, 0x05, 0x06, 0x07, 0x08, 0x00)
        val payload = bytes(0x00, 0x06, 0x00, 0x00, 0x00, 0x01, 0x03, frame.size) + frame
        val pkg = two.decode0B(payload).getOrThrow()
        assertEquals(1, pkg.frames.size)
        assertContentEquals(intArrayOf(0x223344), pkg.frames[0].rawdata)
        assertContentEquals(intArrayOf(0x05060708), pkg.frames[0].agc)
    }

    @Test
    fun `decode0B rejects trailing bytes after data len`() {
        // dataLen=0 但 payload 末尾多 1 字节
        val payload = bytes(0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0xFF)
        assertTrue(decoder.decode0B(payload).isFailure)
        assertTrue(decoder.decode0B(payload).exceptionOrNull() is ItlvcError.ParseError)
    }

    @Test
    fun `decode0B parses compressed packet with splice flags`() {
        // chMask=0x1，flag=0x29（压缩 bit0 + 分包计数 1 bits3-4 + 分包结束 bit5，无 oddeven bit1 → 非首帧绝对值）
        val one = RawDataDecoder(SamplingConfig(channelCount = 1, agcEnabled = true, algoEnabled = true))
        // 帧 = [frameId=0][rawLen=6][tagFlag=0][E2 26 5B 1F 50][agcLen=0][result 0x00]
        val frame = bytes(0x00, 0x06, 0x00, 0xE2, 0x26, 0x5B, 0x1F, 0x50, 0x00, 0x00)
        val payload = bytes(0x00, 0x06, 0x00, 0x00, 0x00, 0x01, 0x29, frame.size) + frame
        val pkg = one.decode0B(payload).getOrThrow()
        assertTrue(pkg.compressed)
        assertEquals(false, pkg.oddPacket)
        assertEquals(1, pkg.splicePackCount)
        assertTrue(pkg.splicePackOver)
        assertContentEquals(intArrayOf(0x2265B1F5), pkg.frames[0].rawdata)
    }

    @Test
    fun `decode0B uncompressed frame syncs baseline for later compressed frame`() {
        val one = RawDataDecoder(SamplingConfig(channelCount = 1, agcEnabled = true, algoEnabled = true))
        // 未压缩绝对帧：dataType=0x00，frame = [frameId=0][0x2265B1F5]
        val absPayload = bytes(0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x05) + bytes(0x00, 0x22, 0x65, 0xB1, 0xF5)
        val abs = one.decode0B(absPayload).getOrThrow()
        assertContentEquals(intArrayOf(0x2265B1F5), abs.frames[0].rawdata)

        // 压缩差分帧 e6f51a6550（golden：0x2265B1F5 → 0x91B7584A）
        val diffFrame = bytes(0x01, 0x06, 0x00) + hexBytes("e6f51a6550") + bytes(0x00, 0x00)
        val pkg = one.decode0B(bytes(0x00, 0x06, 0x00, 0x00, 0x00, 0x01, 0x01, diffFrame.size) + diffFrame).getOrThrow()
        assertContentEquals(intArrayOf(0x91B7584A.toInt()), pkg.frames[0].rawdata)
    }

    @Test
    fun `decode0B rejects multifunction channel index beyond config`() {
        // chMask=0x00000004（bit2）+ config.channelCount=2：通道索引越界 → ParseError（避免 setBaselineChannel 越界逃逸 Result）
        // 帧完整 6 字节（frameId+fifoId+rawdata），删除防护时会真正抵达 setBaselineChannel(2, …) 抛 AIOOBE
        val dec2 = RawDataDecoder(SamplingConfig(channelCount = 2, agcEnabled = true, algoEnabled = true))
        val payload = bytes(0x00, 0x00, 0x00, 0x00, 0x00, 0x04, 0x04, 0x06, 0x00, 0x01, 0x01, 0x02, 0x03, 0x04)
        assertTrue(dec2.decode0B(payload).isFailure)
        assertTrue(dec2.decode0B(payload).exceptionOrNull() is ItlvcError.ParseError)
    }

    @Test
    fun `decode0B real HR frame from standard app log`() {
        // 标准 APP 抓包 2026-08-10：AA-11-0B-E3 | 01 07 00 00 00 0F 03 DB | 219B 数据 | 56
        // 头 = C 端 8B 格式 [FunctionID][dataType][chMask 4B BE][pkgFlag][dataLen]：
        //   FunctionID=0x01（HR）、dataType=0x07（gs+algo+agc）、chMask=0x0000000F（4 通道）、
        //   pkgFlag=0x03（zip + oddeven → 首帧绝对值）、dataLen=0xDB=219。
        // 逐字节校验见 gh_zip.c Gh2x2xUploadDataToMaster：首帧 rawdata/agc 各 4B/通道绝对值，
        // 后续帧 [rawLen][tagFlag][tag*][nibble 差分] + [agcLen][差分] + [result byteNum][内容]。
        val payload = hexBytes(
            "01070000000F03DB" +
                "0000000000000000C2B26E00DE75F700C60B3800D9B9C3051900000519000005190000051900000F" +
                "000000000002020000000300000000010000000000000D00" +
                "811D2B819FB281408F81CFD10500000000000A00000000000200000000020000000000000C00" +
                "6C70B812C296FC46814E85050000000000050000000000030000000000000B00" +
                "67FAD6C5C06BEF46FAA4050000000000050000000000040000000000000B00" +
                "638C9642DB67ED567789050000000000050000000000050000000000000C00" +
                "748FC910EF8724DC7B5050050000000000050000000000"
        )
        val dec = RawDataDecoder(SamplingConfig(channelCount = 4))
        val pkg = dec.decode0B(payload).getOrThrow()

        assertEquals(0x01, pkg.funcId)
        assertEquals(0x07, pkg.dataType)
        assertEquals(0x0000000F, pkg.channelMask)
        assertContentEquals(intArrayOf(0, 1, 2, 3), pkg.activeChannels)
        assertTrue(pkg.compressed)
        assertTrue(pkg.oddPacket)
        assertEquals(false, pkg.multiFunction)
        assertEquals(0, pkg.splicePackCount)
        assertEquals(false, pkg.splicePackOver)
        assertEquals(6, pkg.frames.size)
        pkg.frames.forEach { frame ->
            assertEquals(0x01, frame.funcId)
        }

        // 首帧（oddeven 置位）：rawdata 4B/通道绝对值（24bit 掩码），agc 4B/通道绝对值
        val f0 = pkg.frames[0]
        assertEquals(0x00, f0.frameId)
        assertContentEquals(intArrayOf(0xC2B26E, 0xDE75F7, 0xC60B38, 0xD9B9C3), f0.rawdata)
        assertContentEquals(intArrayOf(0x05190000, 0x05190000, 0x05190000, 0x05190000), f0.agc)
        assertContentEquals(intArrayOf(0, 0, 0), f0.acc)
        assertEquals(3, f0.results.size)
        assertEquals(0, f0.results[0].tag); assertEquals(0, f0.results[0].value)
        assertEquals(2, f0.results[1].tag); assertEquals(2, f0.results[1].value)
        assertEquals(3, f0.results[2].tag); assertEquals(0, f0.results[2].value)

        // 差分帧：ch0 = 0xC2B26E + 0x11D2B = 0xC3CF99；帧 2+ 结果段仅剩 flag0
        assertContentEquals(intArrayOf(0xC3CF99, 0xE015A9, 0xC74BC7, 0xDB8994), pkg.frames[1].rawdata)
        assertEquals(2, pkg.frames[1].results.size)
        assertEquals(0, pkg.frames[1].results[0].tag); assertEquals(0, pkg.frames[1].results[0].value)
        assertEquals(2, pkg.frames[1].results[1].tag); assertEquals(0, pkg.frames[1].results[1].value)
        assertContentEquals(intArrayOf(0xC496A4, 0xE141D2, 0xC8480D, 0xDCD819), pkg.frames[2].rawdata)
        assertContentEquals(intArrayOf(0xC51651, 0xE20792, 0xC90701, 0xDDD2BD), pkg.frames[3].rawdata)
        assertContentEquals(intArrayOf(0xC54F1A, 0xE24A6D, 0xC985D6, 0xDE4A46), pkg.frames[4].rawdata)
        assertContentEquals(intArrayOf(0xC5061E, 0xE13B75, 0xC960FA, 0xDD9541), pkg.frames[5].rawdata)
        assertEquals(5, pkg.frames[5].frameId)
    }
}
