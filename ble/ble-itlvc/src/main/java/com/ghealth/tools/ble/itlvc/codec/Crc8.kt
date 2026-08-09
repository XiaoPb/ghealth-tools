package com.ghealth.tools.ble.itlvc.codec

/**
 * CRC-8：poly 0x07（x^8 + x^2 + x + 1），初值 0xFF，不反射、无最终异或。
 *
 * 与设备端 `.claude/gh3220_protocol/c_to_mcu/demo_kernel_code/module/gh_protocol/gh_uprotocol.c`
 * 中 `g_uchCrc8TabArr` 逐项一致。
 */
object Crc8 : Checksum {
    override val size: Int = 1

    private val table = IntArray(256) { seed -> generate(seed) }

    private fun generate(seed: Int): Int {
        var crc = seed
        repeat(8) {
            crc = if (crc and 0x80 != 0) ((crc shl 1) xor 0x07) and 0xFF else (crc shl 1) and 0xFF
        }
        return crc
    }

    override fun compute(data: ByteArray): ByteArray {
        var crc = 0xFF
        for (b in data) {
            crc = table[(crc xor (b.toInt() and 0xFF)) and 0xFF]
        }
        return byteArrayOf(crc.toByte())
    }
}
