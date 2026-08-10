package com.ghealth.tools.feature.connection

import com.ghealth.tools.ble.gh3220.Gh3220Payload
import com.ghealth.tools.ble.gh3220.commands.BasicCommands
import com.ghealth.tools.ble.gh3220.commands.ConfigCommands
import com.ghealth.tools.ble.gh3220.commands.RegisterCommands
import com.ghealth.tools.ble.protocol.gh3036.ParamType

/**
 * GH3220 命令 payload 编码层（命令面板表单 → 字节）：
 * 按 [Gh3220CommandMeta.key] 分派到 ble-gh3220 编码器，与执行器（走 client 类型化 API）相互独立。
 * 参数缺失/类型不符/编码越界一律返回 failure，不抛异常。
 */
object Gh3220CommandPayloadBuilder {

    fun build(meta: Gh3220CommandMeta, params: List<Any?>): Result<ByteArray> {
        val validationError = validateParams(meta, params)
        if (validationError != null) {
            return Result.failure(IllegalArgumentException(validationError))
        }
        return try {
            Result.success(encode(meta.key, params))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun validateParams(meta: Gh3220CommandMeta, params: List<Any?>): String? {
        if (params.size != meta.params.size) {
            return "参数数量不匹配：期望 ${meta.params.size} 个，实际 ${params.size} 个（key=${meta.key}）"
        }
        meta.params.forEachIndexed { index, def ->
            val value = params[index]
            if (value == null) {
                if (def.required) return "参数 ${def.name} 缺失（key=${meta.key}）"
            } else if (!typeMatches(def.type, value)) {
                return "参数 ${def.name} 类型不符：期望 ${def.type}，实际 ${value::class.simpleName}（key=${meta.key}）"
            }
        }
        return null
    }

    private fun typeMatches(type: ParamType, value: Any): Boolean = when (type) {
        ParamType.U8, ParamType.U16, ParamType.U32,
        ParamType.I8, ParamType.I16, ParamType.I32,
        ParamType.TIMESTAMP, ParamType.FUNC_MODE_BITS -> value is Number
        ParamType.U8_ARRAY -> value is ByteArray
        ParamType.U16_ARRAY -> value is IntArray
    }

    private fun encode(key: String, params: List<Any?>): ByteArray = when (key) {
        "GH3220_GET_VERSION" -> BasicCommands.getVersion(intParam(params[0]))
        "GH3220_CONN_STATUS" -> BasicCommands.getConnStatus()
        "GH3220_START_HBD" ->
            BasicCommands.startHbd(intParam(params[0]) == 1, intParam(params[1]), longParam(params[2]))
        "GH3220_READ_REG" -> RegisterCommands.regRead(intParam(params[0]), intParam(params[1]))
        "GH3220_WORK_MODE" -> ConfigCommands.workMode(intParam(params[0]), longParam(params[1]))
        "GH3220_RAW_SEND" ->
            Gh3220Payload.u8(intParam(params[0])) + ((params[1] as? ByteArray) ?: ByteArray(0))
        "GH3220_PACKAGE_TEST" -> BasicCommands.packageTest(params[0] as ByteArray)
        "GH3220_GSENSOR_SET" ->
            ConfigCommands.gsensorSet(intParam(params[0]), intParam(params[1]), intParam(params[2]))
        "GH3220_FIFO_THRESHOLD" -> ConfigCommands.fifoThreshold(intParam(params[0]))
        "GH3220_EVENT_SET" -> ConfigCommands.eventSet(intParam(params[0]))
        "GH3220_FUNC_MAP" -> ConfigCommands.funcMap(params[0] as ByteArray)
        "GH3220_CHIP_RESET" -> BasicCommands.chipCtrl(intParam(params[0]))
        "GH3220_CALIBRATE_CURRENT" -> BasicCommands.calibrateCurrent(intParam(params[0]))
        "GH3220_SAMPLE_RATES" ->
            ConfigCommands.sampleRates((params[0] as ByteArray).parseSampleRateEntries().getOrThrow())
        "GH3220_SLOT_EN" ->
            ConfigCommands.slotEn(
                intParam(params[0]),
                intParam(params[1]),
                longParam(params[2]),
                intParam(params[3]) == 1,
            )
        "GH3220_ECG_CTRL" -> ConfigCommands.ecgCtrl(intParam(params[0]))
        "GH3220_WORK_MODE_SET" -> ConfigCommands.workModeSet(intParam(params[0]))
        "GH3220_APP_MODULE" -> BasicCommands.appModule(intParam(params[0]))
        "GH3220_SWITCH_CHIP" -> BasicCommands.switchChip(intParam(params[0]))
        "GH3220_REG_ARRAY_WRITE" ->
            RegisterCommands.regArrayWrite((params[0] as ByteArray).parseRegArrayBlocks().getOrThrow())
        else -> throw IllegalArgumentException("未知 GH3220 命令 key=$key")
    }

    private fun intParam(value: Any?): Int = (value as Number).toInt()

    private fun longParam(value: Any?): Long = (value as Number).toLong()
}
