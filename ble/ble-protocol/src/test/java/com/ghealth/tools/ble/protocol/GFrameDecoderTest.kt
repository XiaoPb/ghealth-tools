package com.ghealth.tools.ble.protocol

import com.ghealth.tools.ble.protocol.gh3036.Gh3036FrameDecoder
import com.ghealth.tools.ble.protocol.gh3036.DecodeException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GFrameDecoderTest {
    private lateinit var decoder: Gh3036FrameDecoder

    @BeforeEach
    fun setup() {
        decoder = Gh3036FrameDecoder()
    }

    @Test
    fun `varint decode single byte`() {
        val buf = byteArrayOf(0x05)
        val (value, pos) = Gh3036FrameDecoder.readVarint(buf, 0)
        assertEquals(5, value)
        assertEquals(1, pos)
    }

    @Test
    fun `varint decode multi byte`() {
        val buf = byteArrayOf(0xAC.toByte(), 0x02)
        val (value, pos) = Gh3036FrameDecoder.readVarint(buf, 0)
        assertEquals(300, value)
        assertEquals(2, pos)
    }

    @Test
    fun `zigzag decode positive`() {
        assertEquals(1, Gh3036FrameDecoder.zigzagDecode(2))
        assertEquals(2, Gh3036FrameDecoder.zigzagDecode(4))
        assertEquals(150, Gh3036FrameDecoder.zigzagDecode(300))
    }

    @Test
    fun `zigzag decode negative`() {
        assertEquals(-1, Gh3036FrameDecoder.zigzagDecode(1))
        assertEquals(-2, Gh3036FrameDecoder.zigzagDecode(3))
        assertEquals(-150, Gh3036FrameDecoder.zigzagDecode(299))
    }

    @Test
    fun `zigzag decode zero`() {
        assertEquals(0, Gh3036FrameDecoder.zigzagDecode(0))
    }

    @Test
    fun `decode simple frame with rawdata only`() {
        val data = buildGFrameData(
            packHeaderBits = 0x01,
            rawdataSize = 2,
            rawdata = intArrayOf(100, -50)
        )
        val frames = decoder.decode(data)
        assertEquals(1, frames.size)
        assertEquals(2, frames[0].rawdata.size)
        assertEquals(100, frames[0].rawdata[0])
        assertEquals(-50, frames[0].rawdata[1])
    }

    @Test
    fun `delta compression within single batch`() {
        val data1 = buildGFrameData(
            packHeaderBits = 0x01,
            rawdataSize = 1,
            rawdata = intArrayOf(1000),
            frameId = 0
        )
        val data2 = buildGFrameData(
            packHeaderBits = 0x01,
            rawdataSize = 1,
            rawdata = intArrayOf(5),
            frameId = 1
        )
        val combined = data1 + data2
        val frames = decoder.decode(combined)
        assertEquals(2, frames.size)
        assertEquals(1000, frames[0].rawdata[0])
        assertEquals(1005, frames[1].rawdata[0])
    }

    @Test
    fun `state resets across decode calls`() {
        val data1 = buildGFrameData(
            packHeaderBits = 0x01,
            rawdataSize = 1,
            rawdata = intArrayOf(1000),
            frameId = 0
        )
        val data2 = buildGFrameData(
            packHeaderBits = 0x01,
            rawdataSize = 1,
            rawdata = intArrayOf(5),
            frameId = 0
        )
        val frames1 = decoder.decode(data1)
        assertEquals(1000, frames1[0].rawdata[0])
        val frames2 = decoder.decode(data2)
        assertEquals(5, frames2[0].rawdata[0])
    }

    @Test
    fun `AGC updates are absolute and missing updates reuse the last value`() {
        val first = buildGFrameData(
            packHeaderBits = 1 shl 5,
            agcInfo = intArrayOf(0x00A00000, 0x00A00000),
            agcInfoHigh = intArrayOf(0x20000000, 0x20000000),
            frameId = 0
        )
        val changedPadding = buildGFrameData(
            packHeaderBits = 1 shl 5,
            agcInfo = intArrayOf(0x00A00000, 0x00A00000),
            agcInfoHigh = intArrayOf(0, 0),
            frameId = 1
        )
        val noUpdate = buildGFrameData(packHeaderBits = 0, frameId = 2)

        val frames = decoder.decode(first + changedPadding + noUpdate)

        assertArrayEquals(intArrayOf(0x00A00000, 0x00A00000), frames[1].agcInfo)
        assertArrayEquals(intArrayOf(0, 0), frames[1].agcInfoHigh)
        assertArrayEquals(frames[1].agcInfo, frames[2].agcInfo)
        assertArrayEquals(frames[1].agcInfoHigh, frames[2].agcInfoHigh)
    }

    @Test
    fun `flags updates are absolute and missing updates reuse the last value`() {
        val first = buildGFrameData(packHeaderBits = 1 shl 3, flags = intArrayOf(1, 2), frameId = 0)
        val second = buildGFrameData(packHeaderBits = 1 shl 3, flags = intArrayOf(2, 4), frameId = 1)
        val noUpdate = buildGFrameData(packHeaderBits = 0, frameId = 2)

        val frames = decoder.decode(first + second + noUpdate)

        assertArrayEquals(intArrayOf(2, 4), frames[1].flags)
        assertArrayEquals(frames[1].flags, frames[2].flags)
    }

    @Test
    fun `algorithm updates are absolute and missing updates reuse the last value`() {
        val first = buildGFrameData(packHeaderBits = 1 shl 4, algoData = intArrayOf(60, 90), frameId = 0)
        val second = buildGFrameData(packHeaderBits = 1 shl 4, algoData = intArrayOf(61, 91), frameId = 1)
        val noUpdate = buildGFrameData(packHeaderBits = 0, frameId = 2)

        val frames = decoder.decode(first + second + noUpdate)

        assertArrayEquals(intArrayOf(61, 91), frames[1].algoData)
        assertArrayEquals(frames[1].algoData, frames[2].algoData)
    }

    @Test
    fun `negative array size rejects the complete G payload`() {
        val data = buildGFrameData(packHeaderBits = 1, rawdataSize = -1, frameId = 0)

        val error = assertThrows(DecodeException::class.java) { decoder.decode(data) }

        assertTrue(error.message.orEmpty().contains("rawdata size"))
    }

    @Test
    fun `oversized array rejects the complete G payload`() {
        val data = buildGFrameData(
            packHeaderBits = 1,
            rawdataSize = 33,
            rawdata = IntArray(33),
            frameId = 0
        )

        val error = assertThrows(DecodeException::class.java) { decoder.decode(data) }

        assertTrue(error.message.orEmpty().contains("rawdata size"))
    }

    @Test
    fun `truncated frame reports its starting offset`() {
        val complete = buildGFrameData(packHeaderBits = 1, rawdataSize = 1, rawdata = intArrayOf(10), frameId = 0)
        val truncated = complete.copyOf(complete.size - 1)

        val error = assertThrows(DecodeException::class.java) { decoder.decode(truncated) }

        assertTrue(error.message.orEmpty().contains("offset 0"))
    }

    @Test
    fun `varint rejects values wider than 32 bits`() {
        val overflow = byteArrayOf(
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x10
        )

        assertThrows(DecodeException::class.java) {
            Gh3036FrameDecoder.readVarint(overflow, 0)
        }
    }

    private fun buildGFrameData(
        packHeaderBits: Int,
        rawdataSize: Int = 0,
        rawdata: IntArray = IntArray(0),
        flags: IntArray = IntArray(0),
        algoData: IntArray = IntArray(0),
        agcInfo: IntArray = IntArray(0),
        agcInfoHigh: IntArray = IntArray(0),
        frameId: Int = 0
    ): ByteArray {
        val buf = mutableListOf<Byte>()
        writeSignedVarint(buf, packHeaderBits)
        if ((packHeaderBits and 0x01) != 0) {
            writeSignedVarint(buf, rawdataSize)
            for (v in rawdata) writeSignedVarint(buf, v)
        }
        if ((packHeaderBits and (1 shl 3)) != 0) {
            writeSignedVarint(buf, flags.size)
            for (v in flags) writeSignedVarint(buf, v)
        }
        if ((packHeaderBits and (1 shl 4)) != 0) {
            writeSignedVarint(buf, algoData.size)
            for (v in algoData) writeSignedVarint(buf, v)
        }
        if ((packHeaderBits and (1 shl 5)) != 0) {
            require(agcInfo.size == agcInfoHigh.size)
            writeSignedVarint(buf, agcInfo.size)
            for (v in agcInfo) writeSignedVarint(buf, v)
            for (v in agcInfoHigh) writeSignedVarint(buf, v)
        }
        writeSignedVarint(buf, frameId)
        return buf.toByteArray()
    }

    private fun writeSignedVarint(buf: MutableList<Byte>, value: Int) {
        val zigzag = (value shl 1) xor (value shr 31)
        writeVarint(buf, zigzag)
    }

    private fun writeVarint(buf: MutableList<Byte>, value: Int) {
        var v = value
        while (true) {
            if ((v and 0x7F.inv()) == 0) {
                buf.add(v.toByte())
                return
            }
            buf.add(((v and 0x7F) or 0x80).toByte())
            v = v ushr 7
        }
    }
}
