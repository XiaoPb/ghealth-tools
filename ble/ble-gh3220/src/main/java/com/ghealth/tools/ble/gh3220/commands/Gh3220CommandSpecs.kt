package com.ghealth.tools.ble.gh3220.commands

import com.ghealth.tools.ble.gh3220.Gh3220Cmd
import com.ghealth.tools.ble.itlvc.core.CommandSpec

/** GH3220 命令规格注册表。响应类命令均有 spec；上报类命令（0x08/09/0A/0B/0D/0E/14/16/21/2A）走 report handler。 */
object Gh3220CommandSpecs {

    val NOP = CommandSpec(byteArrayOf(Gh3220Cmd.NOP.toByte()))
    val PACKAGE_TEST = CommandSpec(byteArrayOf(Gh3220Cmd.PACKAGE_TEST.toByte()))
    val REG_RW = CommandSpec(byteArrayOf(Gh3220Cmd.REG_RW.toByte()))
    val START_CTRL = CommandSpec(byteArrayOf(Gh3220Cmd.START_CTRL.toByte()))
    val FW_UPGRADE = CommandSpec(byteArrayOf(Gh3220Cmd.FW_UPGRADE.toByte()), timeoutMs = 3000)
    val WORK_MODE = CommandSpec(byteArrayOf(Gh3220Cmd.WORK_MODE.toByte()))
    val GSENSOR_SET = CommandSpec(byteArrayOf(Gh3220Cmd.GSENSOR_SET.toByte()))
    val FIFO_THR = CommandSpec(byteArrayOf(Gh3220Cmd.FIFO_THR.toByte()))
    val EVENT_SET = CommandSpec(byteArrayOf(Gh3220Cmd.EVENT_SET.toByte()))
    val FUNC_MAP = CommandSpec(byteArrayOf(Gh3220Cmd.FUNC_MAP.toByte()))
    val CHIP_CTRL = CommandSpec(byteArrayOf(Gh3220Cmd.CHIP_CTRL.toByte()))
    val CURRENT_CALIBRATE = CommandSpec(byteArrayOf(Gh3220Cmd.CURRENT_CALIBRATE.toByte()))
    val GET_VER = CommandSpec(byteArrayOf(Gh3220Cmd.GET_VER.toByte()), allowedInPassThrough = true)
    val CONN_STATUS = CommandSpec(byteArrayOf(Gh3220Cmd.CONN_STATUS.toByte()), allowedInPassThrough = true)
    val SAMPLE_RATE = CommandSpec(byteArrayOf(Gh3220Cmd.SAMPLE_RATE.toByte()))
    val SLOT_EN = CommandSpec(byteArrayOf(Gh3220Cmd.SLOT_EN.toByte()))
    val ECG_CTRL = CommandSpec(byteArrayOf(Gh3220Cmd.ECG_CTRL.toByte()))
    val WORK_MODE_SET = CommandSpec(byteArrayOf(Gh3220Cmd.WORK_MODE_SET.toByte()), allowedInPassThrough = true)
    val DRV_CFG = CommandSpec(byteArrayOf(Gh3220Cmd.DRV_CFG.toByte()), timeoutMs = 3000)
    val APP_MODULE = CommandSpec(byteArrayOf(Gh3220Cmd.APP_MODULE.toByte()))
    val REG_ARRAY_WRITE = CommandSpec(byteArrayOf(Gh3220Cmd.REG_ARRAY_WRITE.toByte()))
    val SWITCH_CHIP = CommandSpec(byteArrayOf(Gh3220Cmd.SWITCH_CHIP.toByte()))

    /** 文档 §4.3.5 透传模式白名单：0x19/0x1A/0x1E/0x21/0x2A。 */
    val passThroughWhitelist: Set<Byte> = setOf(
        Gh3220Cmd.GET_VER, Gh3220Cmd.CONN_STATUS, Gh3220Cmd.WORK_MODE_SET,
        Gh3220Cmd.SLAVE_LOG, Gh3220Cmd.RAWDATA_FIFO,
    ).map { it.toByte() }.toSet()

    /** 全部响应类命令规格（供测试遍历与文档生成）。 */
    val all: List<CommandSpec> = listOf(
        NOP, PACKAGE_TEST, REG_RW, START_CTRL, FW_UPGRADE, WORK_MODE, GSENSOR_SET,
        FIFO_THR, EVENT_SET, FUNC_MAP, CHIP_CTRL, CURRENT_CALIBRATE, GET_VER,
        CONN_STATUS, SAMPLE_RATE, SLOT_EN, ECG_CTRL, WORK_MODE_SET, DRV_CFG,
        APP_MODULE, REG_ARRAY_WRITE, SWITCH_CHIP,
    )
}
