package com.ghealth.tools.feature.factory.engine

import com.ghealth.tools.ble.connection.BleConnectionManager
import com.ghealth.tools.ble.protocol.gh3036.KEY_GH3X_REGS_READ_CMD
import com.ghealth.tools.ble.protocol.gh3036.KEY_GH3X_REGS_WRITE_CMD
import com.ghealth.tools.ble.protocol.gh3036.RegisterCommandPayloadBuilder
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 上位机侧 GH3036 eFuse 读取：按 SDK `gh_efuse_read_single` 流程，用寄存器整值写
 * （GH3X_RegsWriteCmd）+ 寄存器读命令驱动 eFuse 控制器，读取 4 段共 256bit
 * （对应设备 UUID 的 32 字节）。
 *
 * 说明：实测该固件对 eFuse 寄存器（0x0580-0x05A6）的写入仅 GH3X_RegsWriteCmd 生效；
 * GH3X_RegsListWriteCmd / GH3X_RegBitFieldWriteCmd 写后回读仍为原值，故统一采用
 * 读-改-写整值方式（GH3X_RegsReadCmd + GH3X_RegsWriteCmd）。
 */
@Singleton
class EfuseReader @Inject constructor(
    private val connectionManager: BleConnectionManager
) {

    /** 读取单个 64bit 段（seg 0~3）；任一步失败或超时返回 null。 */
    suspend fun readSegment(deviceAddress: String, seg: Int): Long? {
        if (seg !in 0..3) return null

        // 步骤1-2：读模式 + 选择段（0x0580：mode bit0=0，sel bit2-3=seg），整值读-改-写
        val modeSel = readReg(deviceAddress, RG_EFUSE_MODE_ADDR) ?: 0
        val newModeSel = (modeSel and MODE_SEL_KEEP_MASK) or (seg shl RG_EFUSE_SEL_LSB)
        Timber.d("EFUSE seg=$seg: 写 mode/sel(0x0580)=0x%04X（原值 0x%04X）", newModeSel, modeSel)
        if (!regWrite(deviceAddress, RG_EFUSE_MODE_ADDR, newModeSel)) {
            Timber.w("EFUSE seg=$seg: mode/sel 写入失败")
            return null
        }

        // 步骤3：打开读使能 RDEN=1（0x0584 bit0）
        val rden = readReg(deviceAddress, RG_EFUSE_RDEN_ADDR) ?: 0
        val newRden = rden or 1
        Timber.d("EFUSE seg=$seg: 写 rden(0x0584)=0x%04X（原值 0x%04X）", newRden, rden)
        if (!regWrite(deviceAddress, RG_EFUSE_RDEN_ADDR, newRden)) {
            Timber.w("EFUSE seg=$seg: rden 写入失败")
            return null
        }

        // 诊断：回读 clk_en/mode/sel 与 rden，确认时钟使能与写入是否生效（best-effort，不影响流程）
        logRegisterReadBack(deviceAddress, seg, "启动前", intArrayOf(
            RG_EFUSE_CLK_EN_ADDR, RG_EFUSE_MODE_ADDR, RG_EFUSE_RDEN_ADDR
        ))

        // 步骤4：启动读取 START=1（0x058A bit0）
        val start = readReg(deviceAddress, RG_EFUSE_START_ADDR) ?: 0
        val newStart = start or 1
        Timber.d("EFUSE seg=$seg: 写 start(0x058A)=0x%04X（原值 0x%04X）", newStart, start)
        if (!regWrite(deviceAddress, RG_EFUSE_START_ADDR, newStart)) {
            regWrite(deviceAddress, RG_EFUSE_RDEN_ADDR, rden)
            Timber.w("EFUSE seg=$seg: start 写入失败，已复位 rden")
            return null
        }
        logRegisterReadBack(deviceAddress, seg, "启动后", intArrayOf(RG_EFUSE_START_ADDR))

        // 步骤5：轮询读完成标志
        var done = false
        var elapsed = 0
        var lastDoneReg: Int? = null
        while (elapsed < EFUSE_READ_TIMEOUT_MS) {
            delay(EFUSE_POLL_INTERVAL_MS.toLong())
            elapsed += EFUSE_POLL_INTERVAL_MS
            val reg = readReg(deviceAddress, RG_EFUSE_READ_DONE_MANUAL_ADDR)
            lastDoneReg = reg
            Timber.d("EFUSE seg=$seg: 轮询 done(0x05A6)=0x%04X", reg ?: -1)
            if (reg != null && (reg and 1) != 0) {
                done = true
                break
            }
        }
        if (!done) {
            regWrite(deviceAddress, RG_EFUSE_RDEN_ADDR, rden)
            Timber.w("EFUSE seg=$seg: 读完成标志未置位（最后值 0x%04X），已复位 rden", lastDoneReg ?: -1)
            return null
        }

        // 步骤6：读取 4 个 16bit 数据寄存器
        val values = readRegs(deviceAddress, RG_EFUSE_RDATA_0_ADDR, 4)
        regWrite(deviceAddress, RG_EFUSE_RDEN_ADDR, rden)
        if (values == null || values.size < 4) {
            Timber.w("EFUSE seg=$seg: 数据寄存器读取失败/不足4个: %s",
                values?.joinToString(", ") { "0x%04X".format(it) } ?: "null")
            return null
        }
        Timber.d("EFUSE seg=$seg: 数据寄存器(0x059E..)=%s",
            values.joinToString(", ") { "0x%04X".format(it) })

        // 步骤7：拼接 64bit
        val value = (values[3].toLong() shl 48) or
            (values[2].toLong() shl 32) or
            (values[1].toLong() shl 16) or
            values[0].toLong()
        Timber.d("EFUSE seg=$seg: 读取成功 64bit=0x%016X", value)
        return value
    }

    /** 读取全部 4 段（256bit）为 32 字节（段内小端逐字节）；任一段失败返回 null。 */
    suspend fun readAll(deviceAddress: String): ByteArray? {
        val bytes = ByteArray(32)
        for (seg in 0..3) {
            val v = readSegment(deviceAddress, seg) ?: run {
                Timber.w("EFUSE readAll: 段 $seg 读取失败，中止")
                return null
            }
            for (i in 0 until 8) {
                bytes[seg * 8 + i] = ((v shr (8 * i)) and 0xFF).toByte()
            }
        }
        Timber.d("EFUSE readAll: 32字节 = %s", bytes.joinToString("") { "%02X".format(it) })
        return bytes
    }

    /** 整值写寄存器：使用 GH3X_RegsWriteCmd（实测该固件仅此命令对 eFuse 寄存器生效）。 */
    private suspend fun regWrite(deviceAddress: String, addr: Int, value: Int): Boolean {
        val param = RegisterCommandPayloadBuilder.buildU16ArrayPayload(intArrayOf(addr, value))
        return connectionManager.sendCommand(deviceAddress, KEY_GH3X_REGS_WRITE_CMD, param).isSuccess
    }

    /** 诊断用：回读指定寄存器并记录值（用于确认写入是否生效）。 */
    private suspend fun logRegisterReadBack(
        deviceAddress: String,
        seg: Int,
        label: String,
        addrs: IntArray
    ) {
        val parts = mutableListOf<String>()
        for (addr in addrs) {
            val v = readReg(deviceAddress, addr)
            parts += "0x%04X=0x%04X".format(addr, v ?: -1)
        }
        Timber.d("EFUSE seg=$seg: 回读（$label） ${parts.joinToString(", ")}")
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
        const val RG_EFUSE_CLK_EN_ADDR = 0x000E
        const val RG_EFUSE_MODE_ADDR = 0x0580
        const val RG_EFUSE_SEL_LSB = 2
        const val RG_EFUSE_RDEN_ADDR = 0x0584
        const val RG_EFUSE_START_ADDR = 0x058A
        const val RG_EFUSE_READ_DONE_MANUAL_ADDR = 0x05A6
        const val RG_EFUSE_RDATA_0_ADDR = 0x059E
        /** 0x0580 保留位：bit1（REG_MODE）与 bit4-15；仅清 mode(bit0)/sel(bit2-3) 后写入。 */
        const val MODE_SEL_KEEP_MASK = 0xFFF2
        const val EFUSE_POLL_INTERVAL_MS = 50
        const val EFUSE_READ_TIMEOUT_MS = 200
    }
}
