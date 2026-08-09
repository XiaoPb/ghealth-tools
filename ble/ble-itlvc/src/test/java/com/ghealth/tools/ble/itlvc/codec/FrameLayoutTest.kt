package com.ghealth.tools.ble.itlvc.codec

import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FrameLayoutTest {

    @Test
    fun `gh3220 layout defaults match protocol doc`() {
        val l = FrameLayout.GH3220
        assertContentEquals(byteArrayOf(0xAA.toByte(), 0x11.toByte()), l.idBytes)
        assertEquals(1, l.typeBytes)
        assertEquals(1, l.lenBytes)
        assertEquals(238, l.maxValueLen)
        assertEquals(1, l.checksumLen)
    }

    @Test
    fun `encodeLen big-endian single and multi byte`() {
        val single = FrameLayout.GH3220
        assertContentEquals(byteArrayOf(0x00), single.encodeLen(0))
        assertContentEquals(byteArrayOf(0xEE.toByte()), single.encodeLen(0xEE))

        val multi = FrameLayout(idBytes = byteArrayOf(0xAA.toByte(), 0x11.toByte()), lenBytes = 2)
        assertContentEquals(byteArrayOf(0x00, 0x05), multi.encodeLen(5))
        assertContentEquals(byteArrayOf(0x01, 0x00), multi.encodeLen(256))
    }

    @Test
    fun `encodeLen rejects out of range`() {
        val l = FrameLayout.GH3220
        assertFailsWith<IllegalArgumentException> { l.encodeLen(256) }
        assertFailsWith<IllegalArgumentException> { l.encodeLen(-1) }
    }

    @Test
    fun `checksumLen is zero when checksum null`() {
        val l = FrameLayout(idBytes = byteArrayOf(0xAA.toByte()), checksum = null)
        assertEquals(0, l.checksumLen)
    }
}
