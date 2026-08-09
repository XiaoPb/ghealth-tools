package com.ghealth.tools.ble.gh3220.commands

import com.ghealth.tools.ble.gh3220.Gh3220Payload

/** 配置类命令（0x10/0x11/0x12/0x13/0x15/0x1B/0x1C/0x1D/0x1E）payload 编解码。 */
object ConfigCommands {

    /** 0x10 工作模式：[mode][function u32le]。 */
    fun workMode(mode: Int, function: Long): ByteArray =
        Gh3220Payload.u8(mode) + Gh3220Payload.u32le(function)

    /** 0x11 G-sensor 设置：[vendorId][res][采样率_H][采样率_L]。 */
    fun gsensorSet(vendorId: Int, resolution: Int, sampleRate: Int): ByteArray = byteArrayOf(
        vendorId.toByte(),
        resolution.toByte(),
        ((sampleRate shr 8) and 0xFF).toByte(),
        (sampleRate and 0xFF).toByte(),
    )

    /** 0x12 Cardiff FIFO 阈值：[阈值_H][阈值_L]（小端）。 */
    fun fifoThreshold(threshold: Int): ByteArray = Gh3220Payload.u16le(threshold)

    /** 0x13 Cardiff 事件设置：[eventHi][eventLo]（小端位掩码）。 */
    fun eventSet(events: Int): ByteArray = Gh3220Payload.u16le(events)

    /** 0x15 功能通道映射：固定 64 字节。 */
    fun funcMap(map: ByteArray): ByteArray {
        require(map.size == 64) { "function map must be 64 bytes" }
        return map
    }

    /** 0x1B 采样率：[funcNum][每项 2B：Function_ID(高4bit) + SR(低12bit)，大端]。 */
    fun sampleRates(entries: List<Pair<Int, Int>>): ByteArray {
        require(entries.size <= 255) { "too many entries: ${entries.size}" }
        val body = ByteArray(entries.size * 2)
        entries.forEachIndexed { i, (id, sr) ->
            val value = ((id and 0x0F) shl 12) or (sr and 0x0FFF)
            body[i * 2] = ((value shr 8) and 0xFF).toByte()
            body[i * 2 + 1] = (value and 0xFF).toByte()
        }
        return Gh3220Payload.u8(entries.size) + body
    }

    /** 0x1C 切换 SlotEn：[slotEn][附加命令][function u32le][onOff]。 */
    fun slotEn(slotEn: Int, addCmd: Int, function: Long, on: Boolean): ByteArray =
        Gh3220Payload.u8(slotEn) + Gh3220Payload.u8(addCmd) +
            Gh3220Payload.u32le(function) + Gh3220Payload.u8(if (on) 0 else 1)

    /** 0x1D ECG 控制：[ctrlFlag][res0][res1][res2]。 */
    fun ecgCtrl(ctrlFlag: Int, res0: Int = 0, res1: Int = 0, res2: Int = 0): ByteArray =
        byteArrayOf(ctrlFlag.toByte(), res0.toByte(), res1.toByte(), res2.toByte())

    /** 0x1E 工作模式设置：[workMode][res0][res1][res2]。 */
    fun workModeSet(workMode: Int, res0: Int = 0, res1: Int = 0, res2: Int = 0): ByteArray =
        byteArrayOf(workMode.toByte(), res0.toByte(), res1.toByte(), res2.toByte())
}
