package com.ghealth.tools.feature.factory.engine

import com.ghealth.tools.ble.connection.BleConnectionManager
import com.ghealth.tools.ble.protocol.gh3036.KEY_GH3X_REG_BIT_FIELD_WRITE_CMD
import com.ghealth.tools.ble.protocol.gh3036.KEY_GH3X_REGS_READ_CMD
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 上位机侧 GH3036 eFuse 读取：按 SDK `gh_efuse_read_single` 流程，用位域写 + 寄存器读命令
 * 驱动 eFuse 控制器，读取 4 段共 256bit（对应设备 UUID 的 32 字节）。
 */
@Singleton
class EfuseReader @Inject constructor(
    private val connectionManager: BleConnectionManager
) {

    /** 读取单个 64bit 段（seg 0~3）；任一步失败或超时返回 null。 */
    suspend fun readSegment(deviceAddress: String, seg: Int): Long? {
        if (seg !in 0..3) return null
        // 步骤1-4：读模式 / 选择段 / 读使能 / 启动
        if (!bitFieldWrite(deviceAddress, RG_EFUSE_MODE_ADDR, RG_EFUSE_MODE_LSB, RG_EFUSE_MODE_MSB, 0)) return null
        if (!bitFieldWrite(deviceAddress, RG_EFUSE_SEL_ADDR, RG_EFUSE_SEL_LSB, RG_EFUSE_SEL_MSB, seg)) return null
        if (!bitFieldWrite(deviceAddress, RG_EFUSE_RDEN_ADDR, RG_EFUSE_RDEN_LSB, RG_EFUSE_RDEN_MSB, 1)) return null
        if (!bitFieldWrite(deviceAddress, RG_EFUSE_START_ADDR, RG_EFUSE_START_LSB, RG_EFUSE_START_MSB, 1)) {
            bitFieldWrite(deviceAddress, RG_EFUSE_RDEN_ADDR, RG_EFUSE_RDEN_LSB, RG_EFUSE_RDEN_MSB, 0)
            return null
        }

        // 步骤5：轮询读完成标志
        var done = false
        var elapsed = 0
        while (elapsed < EFUSE_READ_TIMEOUT_MS) {
            delay(EFUSE_POLL_INTERVAL_MS.toLong())
            elapsed += EFUSE_POLL_INTERVAL_MS
            val reg = readReg(deviceAddress, RG_EFUSE_READ_DONE_MANUAL_ADDR)
            if (reg != null && (reg and 1) != 0) {
                done = true
                break
            }
        }
        if (!done) {
            bitFieldWrite(deviceAddress, RG_EFUSE_RDEN_ADDR, RG_EFUSE_RDEN_LSB, RG_EFUSE_RDEN_MSB, 0)
            return null
        }

        // 步骤6：读取 4 个 16bit 数据寄存器
        val values = readRegs(deviceAddress, RG_EFUSE_RDATA_0_ADDR, 4)
        bitFieldWrite(deviceAddress, RG_EFUSE_RDEN_ADDR, RG_EFUSE_RDEN_LSB, RG_EFUSE_RDEN_MSB, 0)
        if (values == null || values.size < 4) return null

        // 步骤7：拼接 64bit
        return (values[3].toLong() shl 48) or
            (values[2].toLong() shl 32) or
            (values[1].toLong() shl 16) or
            values[0].toLong()
    }

    /** 读取全部 4 段（256bit）为 32 字节（段内小端逐字节）；任一段失败返回 null。 */
    suspend fun readAll(deviceAddress: String): ByteArray? {
        val bytes = ByteArray(32)
        for (seg in 0..3) {
            val v = readSegment(deviceAddress, seg) ?: return null
            for (i in 0 until 8) {
                bytes[seg * 8 + i] = ((v shr (8 * i)) and 0xFF).toByte()
            }
        }
        return bytes
    }

    private suspend fun bitFieldWrite(
        deviceAddress: String, addr: Int, lsb: Int, msb: Int, value: Int
    ): Boolean {
        val param = byteArrayOf(
            (addr and 0xFF).toByte(), ((addr shr 8) and 0xFF).toByte(),
            lsb.toByte(), msb.toByte(),
            (value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte()
        )
        return connectionManager.sendCommand(deviceAddress, KEY_GH3X_REG_BIT_FIELD_WRITE_CMD, param).isSuccess
    }

    /** 读取单个寄存器，返回首个 U16 值；失败/空返回 null。 */
    private suspend fun readReg(deviceAddress: String, addr: Int): Int? =
        readRegs(deviceAddress, addr, 1)?.firstOrNull()

    /** 多寄存器读取，返回 U16 值数组（跳过响应首个计数字段）；失败/响应异常返回 null。 */
    private suspend fun readRegs(deviceAddress: String, addr: Int, count: Int): List<Int>? {
        val param = byteArrayOf(
            (addr and 0xFF).toByte(), ((addr shr 8) and 0xFF).toByte(),
            count.toByte(), 0, 0, 0
        )
        val result = connectionManager.sendCommand(deviceAddress, KEY_GH3X_REGS_READ_CMD, param)
        if (result.isFailure) return null
        val data = result.getOrThrow()
        if (data.size < 2) return null
        val len = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
        if (data.size < 2 + len * 2) return null
        return List(len) { i ->
            val offset = 2 + i * 2
            (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
        }
    }

    companion object {
        // 寄存器定义来自 SDK gh3036_reg.h
        const val RG_EFUSE_MODE_ADDR = 0x0580
        const val RG_EFUSE_MODE_LSB = 0
        const val RG_EFUSE_MODE_MSB = 0
        const val RG_EFUSE_SEL_ADDR = 0x0580
        const val RG_EFUSE_SEL_LSB = 2
        const val RG_EFUSE_SEL_MSB = 3
        const val RG_EFUSE_RDEN_ADDR = 0x0584
        const val RG_EFUSE_RDEN_LSB = 0
        const val RG_EFUSE_RDEN_MSB = 0
        const val RG_EFUSE_START_ADDR = 0x058A
        const val RG_EFUSE_START_LSB = 0
        const val RG_EFUSE_START_MSB = 0
        const val RG_EFUSE_READ_DONE_MANUAL_ADDR = 0x05A6
        const val RG_EFUSE_RDATA_0_ADDR = 0x059E
        const val EFUSE_POLL_INTERVAL_MS = 5
        const val EFUSE_READ_TIMEOUT_MS = 1000
    }
}
