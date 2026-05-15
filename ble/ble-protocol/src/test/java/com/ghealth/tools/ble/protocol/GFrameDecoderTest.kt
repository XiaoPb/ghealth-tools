package com.ghealth.tools.ble.protocol

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GFrameDecoderTest {
    private lateinit var decoder: GFrameDecoder

    @BeforeEach
    fun setup() {
        decoder = GFrameDecoder()
    }

    @Test
    fun `varint decode single byte`() {
        val buf = byteArrayOf(0x05)
        val (value, pos) = GFrameDecoder.readVarint(buf, 0)
        assertEquals(5, value)
        assertEquals(1, pos)
    }

    @Test
    fun `varint decode multi byte`() {
        val buf = byteArrayOf(0xAC.toByte(), 0x02)
        val (value, pos) = GFrameDecoder.readVarint(buf, 0)
        assertEquals(300, value)
        assertEquals(2, pos)
    }

    @Test
    fun `zigzag decode positive`() {
        assertEquals(1, GFrameDecoder.zigzagDecode(2))
        assertEquals(2, GFrameDecoder.zigzagDecode(4))
        assertEquals(150, GFrameDecoder.zigzagDecode(300))
    }

    @Test
    fun `zigzag decode negative`() {
        assertEquals(-1, GFrameDecoder.zigzagDecode(1))
        assertEquals(-2, GFrameDecoder.zigzagDecode(3))
        assertEquals(-150, GFrameDecoder.zigzagDecode(299))
    }

    @Test
    fun `zigzag decode zero`() {
        assertEquals(0, GFrameDecoder.zigzagDecode(0))
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
    fun `delta compression across frames`() {
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

        val frames1 = decoder.decode(data1)
        assertEquals(1000, frames1[0].rawdata[0])

        val frames2 = decoder.decode(data2)
        assertEquals(1005, frames2[0].rawdata[0])
    }

    private fun buildGFrameData(
        packHeaderBits: Int,
        rawdataSize: Int = 0,
        rawdata: IntArray = IntArray(0),
        frameId: Int = 0
    ): ByteArray {
        val buf = mutableListOf<Byte>()
        writeSignedVarint(buf, packHeaderBits)
        if ((packHeaderBits and 0x01) != 0) {
            writeSignedVarint(buf, rawdataSize)
            for (v in rawdata) writeSignedVarint(buf, v)
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
