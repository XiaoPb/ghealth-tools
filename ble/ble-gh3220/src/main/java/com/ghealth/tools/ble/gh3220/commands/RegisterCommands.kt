package com.ghealth.tools.ble.gh3220.commands

import com.ghealth.tools.ble.gh3220.Gh3220Payload
import com.ghealth.tools.ble.itlvc.core.ItlvcError

/** 寄存器类命令（0x03 读写寄存器 / 0xA1 写寄存器数组）payload 编解码。 */
object RegisterCommands {

    /** 0x03 读寄存器：[0x00][count][addrHi][addrLo]。 */
    fun regRead(addr: Int, count: Int): ByteArray = byteArrayOf(
        0x00,
        count.toByte(),
        ((addr shr 8) and 0xFF).toByte(),
        (addr and 0xFF).toByte(),
    )

    /** 0x03 写寄存器：[0x01][count][addrHi][addrLo][data 每寄存器 2B 大端]。 */
    fun regWrite(addr: Int, values: IntArray): ByteArray {
        require(values.size <= 255) { "too many registers: ${values.size}" }
        val head = byteArrayOf(
            0x01,
            values.size.toByte(),
            ((addr shr 8) and 0xFF).toByte(),
            (addr and 0xFF).toByte(),
        )
        val body = ByteArray(values.size * 2)
        values.forEachIndexed { i, v ->
            body[i * 2] = ((v shr 8) and 0xFF).toByte()
            body[i * 2 + 1] = (v and 0xFF).toByte()
        }
        return head + body
    }

    /** 0x03 读寄存器响应：[0x00][count][addrHi][addrLo][data 每寄存器 2B 大端]。 */
    fun parseRegRead(payload: ByteArray): Result<IntArray> {
        if (payload.size < 4) return Result.failure(ItlvcError.ParseError("reg read payload too short"))
        if (Gh3220Payload.readU8(payload, 0) != 0x00) {
            return Result.failure(ItlvcError.ParseError("reg read mode mismatch"))
        }
        val count = Gh3220Payload.readU8(payload, 1)
        val dataLen = count * 2
        if (payload.size < 4 + dataLen) return Result.failure(ItlvcError.ParseError("reg read data truncated"))
        val values = IntArray(count) { i ->
            val off = 4 + i * 2
            ((payload[off].toInt() and 0xFF) shl 8) or (payload[off + 1].toInt() and 0xFF)
        }
        return Result.success(values)
    }

    /** 0xA1 写寄存器数组：N × [addrHi][addrLo][valHi][valLo]。 */
    fun regArrayWrite(blocks: List<IntArray>): ByteArray {
        require(blocks.all { it.size == 4 }) { "each block must be [addrHi, addrLo, valHi, valLo]" }
        val out = ByteArray(blocks.size * 4)
        blocks.forEachIndexed { i, b ->
            out[i * 4] = b[0].toByte()
            out[i * 4 + 1] = b[1].toByte()
            out[i * 4 + 2] = b[2].toByte()
            out[i * 4 + 3] = b[3].toByte()
        }
        return out
    }
}
