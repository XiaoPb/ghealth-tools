package com.ghealth.tools.feature.connection

import com.ghealth.tools.ble.gh3220.Gh3220Payload
import com.ghealth.tools.ble.gh3220.commands.BasicCommands
import com.ghealth.tools.ble.gh3220.commands.ConfigCommands
import com.ghealth.tools.ble.gh3220.commands.RegisterCommands

/**
 * GH3220 命令 payload 编码层（命令面板表单 → 字节）：
 * 按 [Gh3220CommandMeta.key] 分派到 ble-gh3220 编码器，与执行器（走 client 类型化 API）相互独立。
 * 参数缺失/类型不符/编码越界一律返回 failure，不抛异常。
 *
 * 数值参数兼容有符号 [Number] 与 Kotlin 无符号整型（UByte/UShort/UInt/ULong，来自面板十六进制输入），
 * 与 executor 共用 [toIntParam]/[toLongParam]/[validateGh3220Params]。
 */
object Gh3220CommandPayloadBuilder {

    fun build(meta: Gh3220CommandMeta, params: List<Any?>): Result<ByteArray> {
        val validationError = validateGh3220Params(meta.params, params)
        if (validationError != null) {
            return Result.failure(IllegalArgumentException("$validationError（key=${meta.key}）"))
        }
        return try {
            Result.success(encode(meta.key, params))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun encode(key: String, params: List<Any?>): ByteArray = when (key) {
        "GH3220_GET_VERSION" -> BasicCommands.getVersion(toIntParam(params[0])!!)
        "GH3220_CONN_STATUS" -> BasicCommands.getConnStatus()
        "GH3220_START_HBD" ->
            BasicCommands.startHbd(toIntParam(params[0])!! == 1, toIntParam(params[1])!!, toLongParam(params[2])!!)
        "GH3220_READ_REG" -> RegisterCommands.regRead(toIntParam(params[0])!!, toIntParam(params[1])!!)
        "GH3220_WORK_MODE" -> ConfigCommands.workMode(toIntParam(params[0])!!, toLongParam(params[1])!!)
        "GH3220_RAW_SEND" ->
            Gh3220Payload.u8(toIntParam(params[0])!!) + ((params[1] as? ByteArray) ?: ByteArray(0))
        "GH3220_PACKAGE_TEST" -> BasicCommands.packageTest(params[0] as ByteArray)
        "GH3220_GSENSOR_SET" ->
            ConfigCommands.gsensorSet(
                toIntParam(params[0])!!,
                toIntParam(params[1])!!,
                toIntParam(params[2])!!,
            )
        "GH3220_FIFO_THRESHOLD" -> ConfigCommands.fifoThreshold(toIntParam(params[0])!!)
        "GH3220_EVENT_SET" -> ConfigCommands.eventSet(toIntParam(params[0])!!)
        "GH3220_FUNC_MAP" -> ConfigCommands.funcMap(params[0] as ByteArray)
        "GH3220_CHIP_RESET" -> BasicCommands.chipCtrl(toIntParam(params[0])!!)
        "GH3220_CALIBRATE_CURRENT" -> BasicCommands.calibrateCurrent(toIntParam(params[0])!!)
        "GH3220_SAMPLE_RATES" ->
            ConfigCommands.sampleRates((params[0] as ByteArray).parseSampleRateEntries().getOrThrow())
        "GH3220_SLOT_EN" ->
            ConfigCommands.slotEn(
                toIntParam(params[0])!!,
                toIntParam(params[1])!!,
                toLongParam(params[2])!!,
                toIntParam(params[3])!! == 1,
            )
        "GH3220_ECG_CTRL" -> ConfigCommands.ecgCtrl(toIntParam(params[0])!!)
        "GH3220_WORK_MODE_SET" -> ConfigCommands.workModeSet(toIntParam(params[0])!!)
        "GH3220_APP_MODULE" -> BasicCommands.appModule(toIntParam(params[0])!!)
        "GH3220_SWITCH_CHIP" -> BasicCommands.switchChip(toIntParam(params[0])!!)
        "GH3220_REG_ARRAY_WRITE" ->
            RegisterCommands.regArrayWrite((params[0] as ByteArray).parseRegArrayBlocks().getOrThrow())
        else -> throw IllegalArgumentException("未知 GH3220 命令 key=$key")
    }
}
