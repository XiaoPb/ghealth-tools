package com.ghealth.tools.ble.gh3220.rawdata

import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals

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

    private fun IntArray.toByteArray(): ByteArray = ByteArray(size * 4) { i ->
        (this[i / 4] ushr ((3 - i % 4) * 8) and 0xFF).toByte()
    }
}
