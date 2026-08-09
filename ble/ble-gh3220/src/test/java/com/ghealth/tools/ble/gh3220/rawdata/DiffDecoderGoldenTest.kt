package com.ghealth.tools.ble.gh3220.rawdata

import com.ghealth.tools.ble.itlvc.core.ItlvcError
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DiffDecoderGoldenTest {

    /** 由 scripts/gh3220_golden_gen.py 生成；每组从 last=0 开始，可顺序解码。 */
    private val vectors: Map<Int, List<String>> = mapOf(
        1 to listOf(
            "00000000|2265b1f5|e2265b1f50",
            "2265b1f5|91b7584a|e6f51a6550",
            "91b7584a|d8f16adf|e473a12950",
        ),
        2 to listOf(
            "0000000000000000|f4bea973dcf4bb99|ef4bea973edcf4bb99",
            "f4bea973dcf4bb99|f2a4d27bd95bafc8|d219d6f8d3990bd1",
            "f2a4d27bd95bafc8|0e7a269f177219d3|fe42aabdcfc1e995f5",
        ),
        4 to listOf(
            "00000000000000000000000000000000|3c6da5d74da4f9fc1a6916c7b8a1abcd|e3c6da5d7e4da4f9fce1a6916c7eb8a1abcd",
            "3c6da5d74da4f9fc1a6916c7b8a1abcd|656412a97a97c64327ac435a1710cf53|e28f66cd2e2cf2cc47cd432c93fa190dc7a0",
            "656412a97a97c64327ac435a1710cf53|110722310512bd1366ceab368ca59966|f545cf078f75850930e3f2267dce7594ca13",
        ),
    )

    private fun hexBytes(s: String): ByteArray {
        require(s.length % 2 == 0)
        return ByteArray(s.length / 2) { i ->
            s.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    @Test
    fun `golden vectors decode to expected values`() {
        vectors.forEach { (channelCount, lines) ->
            val decoder = DiffDecoder(channelCount)
            lines.forEach { line ->
                val parts = line.split("|")
                val expected = hexBytes(parts[1])
                val encoded = hexBytes(parts[2])
                val result = decoder.decode(encoded)
                assertContentEquals(expected, result.getOrThrow().toByteArray())
            }
        }
    }

    @Test
    fun `decoder is stateful across frames and packets`() {
        // 奇数包（0x0A）相对前一帧差分：连续解码即可恢复
        val decoder = DiffDecoder(1)
        val r1 = decoder.decode(hexBytes("e2265b1f50")) // 0 → 0x2265B1F5
        assertContentEquals(intArrayOf(0x2265B1F5), r1.getOrThrow())
        val r2 = decoder.decode(hexBytes("e6f51a6550")) // → 0x91B7584A
        assertContentEquals(intArrayOf(0x91B7584A.toInt()), r2.getOrThrow())
    }

    @Test
    fun `rejects empty diff stream`() {
        val result = DiffDecoder(1).decode(ByteArray(0))
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<ItlvcError.ParseError>(error)
        assertContains(error.message, "type nibble missing")
    }

    @Test
    fun `rejects truncated value nibbles`() {
        // type=0xE 需要 8 个值 nibble，但只有 1 个可用
        val result = DiffDecoder(1).decode(hexBytes("e2"))
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<ItlvcError.ParseError>(error)
        assertContains(error.message, "value nibble missing")
    }

    @Test
    fun `rejects truncated second channel`() {
        // ch0（type=0）消费 2 个 nibble 后，ch1 的类型 nibble 缺失
        val result = DiffDecoder(2).decode(hexBytes("00"))
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<ItlvcError.ParseError>(error)
        assertContains(error.message, "type nibble missing")
    }

    @Test
    fun `failed decode does not mutate baseline`() {
        val decoder = DiffDecoder(1)
        assertTrue(decoder.decode(hexBytes("e2")).isFailure)
        val result = decoder.decode(hexBytes("e2265b1f50")) // 基线未变：0 → 0x2265B1F5
        assertContentEquals(intArrayOf(0x2265B1F5), result.getOrThrow())
    }

    @Test
    fun `returned array mutation does not affect decoder state`() {
        val decoder = DiffDecoder(1)
        val returned = decoder.decode(hexBytes("e2265b1f50")).getOrThrow() // 0 → 0x2265B1F5
        assertContentEquals(intArrayOf(0x2265B1F5), returned)
        returned[0] = 0 // 篡改返回数组不应影响内部基线
        val next = decoder.decode(hexBytes("e6f51a6550")) // → 0x91B7584A
        assertContentEquals(intArrayOf(0x91B7584A.toInt()), next.getOrThrow())
    }

    private fun IntArray.toByteArray(): ByteArray = ByteArray(size * 4) { i ->
        (this[i / 4] ushr ((3 - i % 4) * 8) and 0xFF).toByte()
    }
}
