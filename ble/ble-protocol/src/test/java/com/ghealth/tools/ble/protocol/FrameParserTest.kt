package com.ghealth.tools.ble.protocol

import com.ghealth.tools.ble.protocol.rpccore.FrameBuilder
import com.ghealth.tools.ble.protocol.rpccore.FrameParser
import com.ghealth.tools.ble.protocol.rpccore.ProtocolError
import com.ghealth.tools.ble.protocol.rpccore.TypeKey
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FrameParserTest {
    private lateinit var parser: FrameParser
    private lateinit var builder: FrameBuilder

    @BeforeEach
    fun setup() {
        parser = FrameParser()
        builder = FrameBuilder()
    }

    @Test
    fun `TypeKey from_byte and to_byte roundtrip`() {
        val tk = TypeKey.build(packType = 1, isArray = false, width = 1, secure = true, fin = true)
        val restored = TypeKey(tk.raw)
        assertEquals(1, restored.packType)
        assertFalse(restored.isArray)
        assertEquals(1, restored.width)
        assertTrue(restored.isSecure)
        assertTrue(restored.isFin)
    }

    @Test
    fun `simple frame encode and decode roundtrip`() {
        val frame = builder.build(key = "G", param = byteArrayOf(0x01, 0x02, 0x03))
        val results = parser.process(frame)

        assertEquals(1, results.size)
        assertTrue(results[0].isSuccess)
        val result = results[0].getOrNull()!!
        assertEquals("G", result.key)
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), result.param)
        assertTrue(result.isFin)
        assertFalse(result.isSecure)
    }

    @Test
    fun `multi-char key frame roundtrip`() {
        val frame = builder.build(key = "ver", param = byteArrayOf(0x10))
        val results = parser.process(frame)

        assertEquals(1, results.size)
        assertTrue(results[0].isSuccess)
        assertEquals("ver", results[0].getOrNull()!!.key)
    }

    @Test
    fun `secure frame with invoke index`() {
        val frame = builder.build(key = "R", param = byteArrayOf(0x55), secure = true, invokeIdx = 7)
        val results = parser.process(frame)

        assertEquals(1, results.size)
        assertTrue(results[0].isSuccess)
        val r = results[0].getOrNull()!!
        assertEquals("R", r.key)
        assertTrue(r.isSecure)
        assertTrue(r.isFin)
        assertEquals(7.toByte(), r.invokeIdx)
    }

    @Test
    fun `CRC mismatch returns error`() {
        val frame = builder.build(key = "G", param = byteArrayOf(0x01))
        frame[frame.size - 1] = (frame[frame.size - 1] + 1).toByte()
        val results = parser.process(frame)

        assertEquals(1, results.size)
        assertTrue(results[0].isFailure)
        assertTrue(results[0].exceptionOrNull() is ProtocolError.CrcMismatch)
    }

    @Test
    fun `fragmented data across multiple process calls`() {
        val frame = builder.build(key = "G", param = byteArrayOf(0xAB.toByte()))
        val part1 = frame.copyOfRange(0, 3)
        val part2 = frame.copyOfRange(3, frame.size)

        val r1 = parser.process(part1)
        assertTrue(r1.isEmpty())

        val r2 = parser.process(part2)
        assertEquals(1, r2.size)
        assertTrue(r2[0].isSuccess)
        assertEquals("G", r2[0].getOrNull()!!.key)
    }

    @Test
    fun `multiple frames in single buffer`() {
        val f1 = builder.build(key = "A", param = byteArrayOf(0x01))
        val f2 = builder.build(key = "B", param = byteArrayOf(0x02))
        val combined = f1 + f2

        val results = parser.process(combined)
        assertEquals(2, results.size)
        assertEquals("A", results[0].getOrNull()!!.key)
        assertEquals("B", results[1].getOrNull()!!.key)
    }
}
