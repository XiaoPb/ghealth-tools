package com.ghealth.tools.ble.itlvc.state

import com.ghealth.tools.ble.itlvc.codec.FrameLayout
import com.ghealth.tools.ble.itlvc.codec.ItlvcFrameCodec
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReceiveStateMachineTest {

    private val layout = FrameLayout.GH3220
    private val codec = ItlvcFrameCodec(layout)
    private lateinit var rx: ReceiveStateMachine

    @BeforeEach
    fun setup() {
        rx = ReceiveStateMachine(layout)
    }

    private fun frame(type: Int, value: ByteArray): ByteArray =
        codec.encode(com.ghealth.tools.ble.itlvc.codec.ItlvcFrame(byteArrayOf(type.toByte()), value))

    @Test
    fun `single frame in one chunk`() {
        val frames = rx.feed(frame(0x1A, ByteArray(0)), now = 0)
        assertEquals(1, frames.size)
        assertContentEquals(byteArrayOf(0x1A), frames[0].type)
        assertContentEquals(ByteArray(0), frames[0].value)
    }

    @Test
    fun `two frames in one chunk (sticky)`() {
        val chunk = frame(0x1A, ByteArray(0)) + frame(0x19, byteArrayOf(0x01))
        val frames = rx.feed(chunk, now = 0)
        assertEquals(2, frames.size)
    }

    @Test
    fun `one frame split across chunks (fragmentation)`() {
        val full = frame(0x03, byteArrayOf(0x00, 0x01, 0x00, 0x00))
        val part1 = full.copyOfRange(0, 3)
        val part2 = full.copyOfRange(3, full.size)
        assertEquals(0, rx.feed(part1, now = 0).size)
        val frames = rx.feed(part2, now = 1)
        assertEquals(1, frames.size)
        assertContentEquals(byteArrayOf(0x03), frames[0].type)
        assertContentEquals(byteArrayOf(0x00, 0x01, 0x00, 0x00), frames[0].value)
    }

    @Test
    fun `crc mismatch drops frame and resyncs`() {
        val bad = frame(0x1A, ByteArray(0)).copyOf()
        bad[bad.lastIndex] = (bad.last().toInt() xor 0xFF).toByte()
        assertEquals(0, rx.feed(bad, now = 0).size)
        assertEquals(1, rx.crcErrorCount)
        // 后续合法帧仍能解析
        assertEquals(1, rx.feed(frame(0x1A, ByteArray(0)), now = 1).size)
    }

    @Test
    fun `garbage before header resyncs`() {
        val chunk = byteArrayOf(0x00, 0x01, 0xAA.toByte(), 0x12) + frame(0x1A, ByteArray(0))
        val frames = rx.feed(chunk, now = 0)
        assertEquals(1, frames.size)
        assertContentEquals(byteArrayOf(0x1A), frames[0].type)
    }

    @Test
    fun `length overflow drops and counts`() {
        // AA 11 01 FF ... L=255 > 238
        val chunk = byteArrayOf(0xAA.toByte(), 0x11, 0x01, 0xFF.toByte(), 0x00, 0x00)
        assertEquals(0, rx.feed(chunk, now = 0).size)
        assertEquals(1, rx.lengthErrorCount)
    }

    @Test
    fun `frame timeout resets partial frame`() {
        val full = frame(0x19, byteArrayOf(0x01))
        val part1 = full.copyOfRange(0, 4) // 已收到头+type+len
        assertEquals(0, rx.feed(part1, now = 0).size)
        assertTrue(rx.checkTimeout(nowMs = 200, timeoutMs = 100))
        assertEquals(1, rx.truncatedCount)
        // 半帧被丢弃，新帧正常
        assertEquals(1, rx.feed(full, now = 300).size)
    }

    @Test
    fun `no checksum layout emits frame right after value`() {
        val noCrc = ReceiveStateMachine(
            FrameLayout(idBytes = byteArrayOf(0xAA.toByte(), 0x11.toByte()), checksum = null),
        )
        val bytes = byteArrayOf(0xAA.toByte(), 0x11, 0x01, 0x02, 0x10, 0x20)
        val frames = noCrc.feed(bytes, now = 0)
        assertEquals(1, frames.size)
        assertContentEquals(byteArrayOf(0x10, 0x20), frames[0].value)
    }
}
