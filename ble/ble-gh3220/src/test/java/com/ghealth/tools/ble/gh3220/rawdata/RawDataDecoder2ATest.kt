package com.ghealth.tools.ble.gh3220.rawdata

import com.ghealth.tools.ble.itlvc.core.ItlvcError
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RawDataDecoder2ATest {

    private val decoder = RawDataDecoder(SamplingConfig())

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun `decode2A parses fifo report`() {
        val report = decoder.decode2A(
            bytes(0x03, 0x04, 0x00, 0x00, 0x00, 0xDE, 0xAD, 0xBE, 0xEF),
        ).getOrThrow()
        assertEquals(0x03, report.fifoId)
        assertContentEquals(bytes(0xDE, 0xAD, 0xBE, 0xEF), report.rawdata)
    }

    @Test
    fun `decode2A rejects truncated header`() {
        assertTrue(decoder.decode2A(bytes(0x03, 0x04, 0x00)).isFailure)
        assertTrue(decoder.decode2A(bytes(0x03)).isFailure)
    }

    @Test
    fun `decode2A rejects length overflow`() {
        // len=5 但只有 1 字节数据
        val result = decoder.decode2A(bytes(0x03, 0x05, 0x00, 0x00, 0x00, 0x01))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ItlvcError.ParseError)
    }

    @Test
    fun `decode2A rejects negative len from high bit`() {
        // len=0xFFFFFFFF（le32 高位 bit 置位 → Int -1）→ ParseError
        val result = decoder.decode2A(bytes(0x03, 0xFF, 0xFF, 0xFF, 0xFF, 0x01))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ItlvcError.ParseError)
    }
}
