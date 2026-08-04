package com.ghealth.tools.ble.protocol.gh3036

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class CommandPayloadBuilderTest {

    private val regReadCmd = Gh3036CommandMeta.getCommandByKey(KEY_GH3X_REGS_READ_CMD)!!
    private val regBitFieldWriteCmd = Gh3036CommandMeta.getCommandByKey(KEY_GH3X_REG_BIT_FIELD_WRITE_CMD)!!
    private val chipCtrlCmd = Gh3036CommandMeta.getCommandByKey(KEY_GH3X_CHIP_CTRL)!!

    // ── 闪退复现：无符号类型不得抛 ClassCastException ──

    @Test
    fun `U16 param accepts UShort value without ClassCastException`() {
        // 复现用户报告：寄存器读取 UI 把 regAddr 存为 UShort。
        val params = mapOf<String, Any>(
            "regAddr" to 0x8000.toUShort(),
            "readLen" to 1
        )
        val bytes = CommandPayloadBuilder.buildCommandParams(regReadCmd, params)
        // FMT_GH3X_REGS_READ_CMD = "<u16><d32>"，小端
        assertArrayEquals(
            byteArrayOf(
                0x00, 0x80.toByte(),             // regAddr 0x8000 LE
                0x01, 0x00, 0x00, 0x00          // readLen 1 LE (d32)
            ),
            bytes
        )
    }

    @Test
    fun `U8 param accepts UByte value without ClassCastException`() {
        // 复现潜在闪退：寄存器位域写入的 lsb/msb 为 U8 文本输入。
        val params = mapOf<String, Any>(
            "regAddr" to 0x1000.toUShort(),
            "lsb" to 0x02.toUByte(),
            "msb" to 0x05.toUByte(),
            "regVal" to 0x0001.toUShort()
        )
        val bytes = CommandPayloadBuilder.buildCommandParams(regBitFieldWriteCmd, params)
        // FMT_GH3X_REG_BIT_FIELD_WRITE_CMD = "<u16><u8><u8><u16>"
        assertArrayEquals(
            byteArrayOf(0x00, 0x10, 0x02, 0x05, 0x01, 0x00),
            bytes
        )
    }

    @Test
    fun `U32 param accepts UInt value without ClassCastException`() {
        // 用合成命令元数据覆盖 U32 标量（现网命令无 U32 文本输入项）。
        val synthetic = CommandMeta(
            key = "test_u32",
            displayName = "test",
            description = "",
            requestFormat = "<u32>",
            params = listOf(CommandParamDef(name = "v", label = "v", type = ParamType.U32)),
            hasResponse = false
        )
        val bytes = CommandPayloadBuilder.buildCommandParams(
            synthetic, mapOf("v" to 0xFFFFFFFFu)
        )
        assertArrayEquals(
            byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
            bytes
        )
    }

    // ── 回归：有符号与下拉取值路径不得被破坏 ──

    @Test
    fun `U16 param accepts signed Short value`() {
        val params = mapOf<String, Any>(
            "regAddr" to 0x1234.toShort(),
            "readLen" to 4
        )
        val bytes = CommandPayloadBuilder.buildCommandParams(regReadCmd, params)
        assertArrayEquals(
            byteArrayOf(0x34, 0x12, 0x04, 0x00, 0x00, 0x00),
            bytes
        )
    }

    @Test
    fun `U8 param accepts Int value from dropdown option`() {
        // 下拉选项 option.value 是 Int（如 0x5A），必须继续可用。
        val bytes = CommandPayloadBuilder.buildCommandParams(
            chipCtrlCmd, mapOf("ctrlType" to 0x5A)
        )
        assertArrayEquals(byteArrayOf(0x5A.toByte()), bytes)
    }
}
