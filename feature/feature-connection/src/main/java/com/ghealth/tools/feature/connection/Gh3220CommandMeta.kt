package com.ghealth.tools.feature.connection

import com.ghealth.tools.ble.gh3220.Gh3220ProtocolClient
import com.ghealth.tools.ble.protocol.gh3036.CommandGroup
import com.ghealth.tools.ble.protocol.gh3036.CommandMeta
import com.ghealth.tools.ble.protocol.gh3036.CommandParamDef
import com.ghealth.tools.ble.protocol.gh3036.ParamType

/** 解析采样率表原始字节：每 2 字节一项，高 4bit Function_ID + 低 12bit SR（大端），供执行器与 PayloadBuilder 共用。 */
internal fun ByteArray.parseSampleRateEntries(): Result<List<Pair<Int, Int>>> {
    if (isEmpty()) return Result.failure(IllegalArgumentException("采样率表不能为空"))
    if (size % 2 != 0) return Result.failure(IllegalArgumentException("采样率表字节数必须为偶数，实际 $size"))
    val entries = mutableListOf<Pair<Int, Int>>()
    var index = 0
    while (index < size) {
        val value = ((this[index].toInt() and 0xFF) shl 8) or (this[index + 1].toInt() and 0xFF)
        entries += ((value shr 12) and 0x0F) to (value and 0x0FFF)
        index += 2
    }
    return Result.success(entries)
}

/** 解析寄存器数组写入原始字节：每 4 字节一块 [addrHi, addrLo, valHi, valLo]，供执行器与 PayloadBuilder 共用。 */
internal fun ByteArray.parseRegArrayBlocks(): Result<List<IntArray>> {
    if (isEmpty()) return Result.failure(IllegalArgumentException("寄存器块数据不能为空"))
    if (size % 4 != 0) return Result.failure(IllegalArgumentException("寄存器块字节数必须为 4 的倍数，实际 $size"))
    val blocks = mutableListOf<IntArray>()
    var index = 0
    while (index < size) {
        blocks += intArrayOf(
            this[index].toInt() and 0xFF,
            this[index + 1].toInt() and 0xFF,
            this[index + 2].toInt() and 0xFF,
            this[index + 3].toInt() and 0xFF,
        )
        index += 4
    }
    return Result.success(blocks)
}

/**
 * GH3220 命令元数据（ITLVC 0xAA11 协议）：结构与 GH3036 命令页一致，执行经新协议栈。
 *
 * 每个命令除 [CommandMeta] 表单描述外，附带 [executor]：把表单参数转换为
 * [Gh3220ProtocolClient] 类型化 API 调用，并把类型化响应统一转为展示用 ByteArray：
 * - 状态类命令（client 返回 Int）：转 1 字节（0=成功 / 1=失败，其他值透传）；
 * - GET_VERSION：verType(1B) + 版本文本(UTF-8)；
 * - READ_REG：每寄存器 2 字节大端；
 * - RAW_SEND / PACKAGE_TEST：原始响应字节；
 * - REG_ARRAY_WRITE：状态已由 client 校验，成功时返回空数组。
 *
 * [all] 清单顺序与计划一致：核心命令在前，其余命令按命令 ID 升序。
 */
data class Gh3220CommandMeta(
    val meta: CommandMeta,
    /** 执行器：把表单参数编码后经 [Gh3220ProtocolClient] 执行，返回原始响应字节。 */
    val executor: suspend (Gh3220ProtocolClient, List<Any?>) -> Result<ByteArray>,
) {
    val key: String get() = meta.key
    val displayName: String get() = meta.displayName
    val params: List<CommandParamDef> get() = meta.params
    val group: CommandGroup get() = meta.group

    companion object {
        // ── 选项常量（值参考 .claude/gh_protocol/cmd.yaml，真机待验证）──

        val VERSION_TYPE_OPTIONS: List<CommandParamDef.OptionItem> = listOf(
            CommandParamDef.OptionItem("固件版本 (0x01)", 0x01),
            CommandParamDef.OptionItem("虚拟寄存器版本 (0x03)", 0x03),
            CommandParamDef.OptionItem("Bootloader版本 (0x04)", 0x04),
            CommandParamDef.OptionItem("协议版本 (0x05)", 0x05),
            CommandParamDef.OptionItem("驱动功能支持 (0x06)", 0x06),
            CommandParamDef.OptionItem("驱动版本 (0x07)", 0x07),
            CommandParamDef.OptionItem("芯片版本 (0x08)", 0x08),
            CommandParamDef.OptionItem("BLE版本 (0x09)", 0x09),
            CommandParamDef.OptionItem("算法调用Demo版本 (0x0A)", 0x0A),
            CommandParamDef.OptionItem("算法版本 (0x20)", 0x20),
        )

        val WORK_MODE_OPTIONS: List<CommandParamDef.OptionItem> = listOf(
            CommandParamDef.OptionItem("EVK模式 (0)", 0),
            CommandParamDef.OptionItem("APP模式 (1,弃用)", 1),
            CommandParamDef.OptionItem("MCU在线模式 (2)", 2),
            CommandParamDef.OptionItem("MCU离线模式 (3)", 3),
            CommandParamDef.OptionItem("测试调谐模式 (4,弃用)", 4),
            CommandParamDef.OptionItem("透传模式 (5)", 5),
            CommandParamDef.OptionItem("获取工作模式 (6,弃用)", 6),
            CommandParamDef.OptionItem("工厂模式 (7)", 7),
        )

        /** 启动/停止（0x0C 启动 HBD）：表单值 1=启动 0=停止，编码层映射为 0x00=启动 0x01=停止。 */
        val START_STOP_OPTIONS: List<CommandParamDef.OptionItem> = listOf(
            CommandParamDef.OptionItem("启动 (1)", 1),
            CommandParamDef.OptionItem("停止 (0)", 0),
        )

        /** 开启/关闭（0x1C SlotEn）：表单值 1=开启 0=关闭，编码层映射为 0x00=开启 0x01=关闭。 */
        val ON_OFF_OPTIONS: List<CommandParamDef.OptionItem> = listOf(
            CommandParamDef.OptionItem("开启 (1)", 1),
            CommandParamDef.OptionItem("关闭 (0)", 0),
        )

        private val STATUS_RESPONSE_FORMAT = "1 字节状态（0=成功 / 1=失败，其他值透传）"

        val all: List<Gh3220CommandMeta> = listOf(
            // ── 核心命令（计划顺序）────────────────────
            Gh3220CommandMeta(
                CommandMeta(
                    key = "GH3220_GET_VERSION",
                    displayName = "获取版本",
                    description = "0x19 获取指定类型的版本信息（固件/协议/驱动等）",
                    requestFormat = "",
                    params = listOf(
                        CommandParamDef(
                            name = "versionType",
                            label = "版本类型",
                            type = ParamType.U8,
                            options = VERSION_TYPE_OPTIONS,
                        ),
                    ),
                    hasResponse = true,
                    responseFormat = "verType(1B) + 版本文本(UTF-8)",
                    group = CommandGroup.VERSION_STATUS,
                ),
            ) { client, params ->
                client.getVersion(intParam(params[0])).map {
                    byteArrayOf(it.versionType.toByte()) + it.text.toByteArray()
                }
            },
            Gh3220CommandMeta(
                CommandMeta(
                    key = "GH3220_CONN_STATUS",
                    displayName = "连接状态",
                    description = "0x1A 查询连接状态（0=已连接 / 1=未连接）",
                    requestFormat = "",
                    params = emptyList(),
                    hasResponse = true,
                    responseFormat = "1 字节（0=已连接 / 1=未连接）",
                    group = CommandGroup.VERSION_STATUS,
                ),
            ) { client, _ ->
                client.getConnectionStatus().map { byteArrayOf(it.toByte()) }
            },
            Gh3220CommandMeta(
                CommandMeta(
                    key = "GH3220_START_HBD",
                    displayName = "启动 HBD",
                    description = "0x0C 启动/停止 HBD 采集",
                    requestFormat = "",
                    params = listOf(
                        CommandParamDef(
                            name = "on",
                            label = "开关",
                            type = ParamType.U8,
                            options = START_STOP_OPTIONS,
                        ),
                        CommandParamDef(
                            name = "mode",
                            label = "模式",
                            type = ParamType.U8,
                            defaultValue = 0,
                        ),
                        CommandParamDef(
                            name = "function",
                            label = "功能位",
                            type = ParamType.U32,
                            defaultValue = 0L,
                        ),
                    ),
                    hasResponse = true,
                    responseFormat = STATUS_RESPONSE_FORMAT,
                    group = CommandGroup.DEVICE_CONTROL,
                ),
            ) { client, params ->
                client.startHbd(intParam(params[0]) == 1, intParam(params[1]), longParam(params[2]))
                    .map { byteArrayOf(it.toByte()) }
            },
            Gh3220CommandMeta(
                CommandMeta(
                    key = "GH3220_READ_REG",
                    displayName = "读寄存器",
                    description = "0x03 读取指定地址寄存器（响应每寄存器 2 字节大端）",
                    requestFormat = "",
                    params = listOf(
                        CommandParamDef(name = "addr", label = "地址", type = ParamType.U16, defaultValue = 0),
                        CommandParamDef(name = "count", label = "个数", type = ParamType.U8, defaultValue = 1),
                    ),
                    hasResponse = true,
                    responseFormat = "每寄存器 2 字节大端",
                    group = CommandGroup.REGISTER,
                ),
            ) { client, params ->
                client.readRegisters(intParam(params[0]), intParam(params[1])).map { it.toRegReadBytes() }
            },
            Gh3220CommandMeta(
                CommandMeta(
                    key = "GH3220_WORK_MODE",
                    displayName = "工作模式",
                    description = "0x10 设置下位机工作模式",
                    requestFormat = "",
                    params = listOf(
                        CommandParamDef(
                            name = "mode",
                            label = "模式",
                            type = ParamType.U8,
                            options = WORK_MODE_OPTIONS,
                        ),
                        CommandParamDef(
                            name = "function",
                            label = "功能位",
                            type = ParamType.U32,
                            defaultValue = 0L,
                        ),
                    ),
                    hasResponse = true,
                    responseFormat = STATUS_RESPONSE_FORMAT,
                    group = CommandGroup.DEVICE_CONTROL,
                ),
            ) { client, params ->
                client.setWorkMode(intParam(params[0]), longParam(params[1]))
                    .map { byteArrayOf(it.toByte()) }
            },
            Gh3220CommandMeta(
                CommandMeta(
                    key = "GH3220_RAW_SEND",
                    displayName = "原始透传",
                    description = "任意命令 ID 原始字节收发（仅透传白名单 0x19/0x1A/0x1E/0x21/0x2A 放行）",
                    requestFormat = "",
                    params = listOf(
                        CommandParamDef(
                            name = "type",
                            label = "命令 ID(hex)",
                            type = ParamType.U8,
                            defaultValue = 0x23,
                        ),
                        CommandParamDef(
                            name = "payload",
                            label = "载荷(hex)",
                            type = ParamType.U8_ARRAY,
                            required = false,
                            defaultValue = byteArrayOf(),
                        ),
                    ),
                    hasResponse = true,
                    responseFormat = "原始响应字节",
                    group = CommandGroup.OTHER,
                ),
            ) { client, params ->
                client.sendRaw(intParam(params[0]), (params[1] as? ByteArray) ?: ByteArray(0))
            },

            // ── 其余命令（按命令 ID 升序）──────────────
            Gh3220CommandMeta(
                CommandMeta(
                    key = "GH3220_PACKAGE_TEST",
                    displayName = "通讯包测试",
                    description = "0x05 通讯包测试，数据原样回显",
                    requestFormat = "",
                    params = listOf(
                        CommandParamDef(name = "data", label = "数据(hex)", type = ParamType.U8_ARRAY),
                    ),
                    hasResponse = true,
                    responseFormat = "数据原样回显",
                    group = CommandGroup.OTHER,
                ),
            ) { client, params ->
                client.packageTest(params[0] as ByteArray)
            },
            Gh3220CommandMeta(
                CommandMeta(
                    key = "GH3220_GSENSOR_SET",
                    displayName = "G-sensor 设置",
                    description = "0x11 设置 G-sensor 厂商/分辨率/采样率",
                    requestFormat = "",
                    params = listOf(
                        CommandParamDef(name = "vendorId", label = "厂商 ID", type = ParamType.U8),
                        CommandParamDef(name = "resolution", label = "分辨率", type = ParamType.U8),
                        CommandParamDef(name = "sampleRate", label = "采样率", type = ParamType.U16, defaultValue = 0),
                    ),
                    hasResponse = true,
                    responseFormat = STATUS_RESPONSE_FORMAT,
                    group = CommandGroup.DEVICE_CONTROL,
                ),
            ) { client, params ->
                client.gsensorSet(intParam(params[0]), intParam(params[1]), intParam(params[2]))
                    .map { byteArrayOf(it.toByte()) }
            },
            Gh3220CommandMeta(
                CommandMeta(
                    key = "GH3220_FIFO_THRESHOLD",
                    displayName = "FIFO 阈值",
                    description = "0x12 设置 Cardiff FIFO 阈值",
                    requestFormat = "",
                    params = listOf(
                        CommandParamDef(name = "threshold", label = "阈值", type = ParamType.U16, defaultValue = 0),
                    ),
                    hasResponse = true,
                    responseFormat = STATUS_RESPONSE_FORMAT,
                    group = CommandGroup.OTHER,
                ),
            ) { client, params ->
                client.fifoThreshold(intParam(params[0])).map { byteArrayOf(it.toByte()) }
            },
            Gh3220CommandMeta(
                CommandMeta(
                    key = "GH3220_EVENT_SET",
                    displayName = "事件设置",
                    description = "0x13 设置 Cardiff 事件掩码",
                    requestFormat = "",
                    params = listOf(
                        CommandParamDef(name = "events", label = "事件掩码", type = ParamType.U16, defaultValue = 0),
                    ),
                    hasResponse = true,
                    responseFormat = STATUS_RESPONSE_FORMAT,
                    group = CommandGroup.OTHER,
                ),
            ) { client, params ->
                client.eventSet(intParam(params[0])).map { byteArrayOf(it.toByte()) }
            },
            Gh3220CommandMeta(
                CommandMeta(
                    key = "GH3220_FUNC_MAP",
                    displayName = "功能通道映射",
                    description = "0x15 下发功能通道映射（固定 64 字节）",
                    requestFormat = "",
                    params = listOf(
                        CommandParamDef(
                            name = "map",
                            label = "映射表(64字节hex)",
                            type = ParamType.U8_ARRAY,
                            description = "固定 64 字节，长度不符时编码层拒绝",
                        ),
                    ),
                    hasResponse = true,
                    responseFormat = STATUS_RESPONSE_FORMAT,
                    group = CommandGroup.OTHER,
                ),
            ) { client, params ->
                client.funcMap(params[0] as ByteArray).map { byteArrayOf(it.toByte()) }
            },
            Gh3220CommandMeta(
                CommandMeta(
                    key = "GH3220_CHIP_RESET",
                    displayName = "芯片复位",
                    description = "0x17 Cardiff 芯片复位",
                    requestFormat = "",
                    params = listOf(
                        CommandParamDef(name = "resetType", label = "复位类型", type = ParamType.U8),
                    ),
                    hasResponse = true,
                    responseFormat = STATUS_RESPONSE_FORMAT,
                    group = CommandGroup.DEVICE_CONTROL,
                ),
            ) { client, params ->
                client.chipReset(intParam(params[0])).map { byteArrayOf(it.toByte()) }
            },
            Gh3220CommandMeta(
                CommandMeta(
                    key = "GH3220_CALIBRATE_CURRENT",
                    displayName = "电流校准",
                    description = "0x18 电流校准",
                    requestFormat = "",
                    params = listOf(
                        CommandParamDef(name = "mode", label = "校准模式", type = ParamType.U8),
                    ),
                    hasResponse = true,
                    responseFormat = STATUS_RESPONSE_FORMAT,
                    group = CommandGroup.FACTORY,
                ),
            ) { client, params ->
                client.calibrateCurrent(intParam(params[0])).map { byteArrayOf(it.toByte()) }
            },
            Gh3220CommandMeta(
                CommandMeta(
                    key = "GH3220_SAMPLE_RATES",
                    displayName = "采样率设置",
                    description = "0x1B 设置各 Function 采样率",
                    requestFormat = "",
                    params = listOf(
                        CommandParamDef(
                            name = "entries",
                            label = "采样率表(hex)",
                            type = ParamType.U8_ARRAY,
                            description = "每 2 字节一项：高 4bit Function_ID + 低 12bit SR（大端），按项依次解析",
                        ),
                    ),
                    hasResponse = true,
                    responseFormat = STATUS_RESPONSE_FORMAT,
                    group = CommandGroup.OTHER,
                ),
            ) { client, params ->
                val parsed = (params[0] as ByteArray).parseSampleRateEntries()
                if (parsed.isFailure) {
                    Result.failure(parsed.exceptionOrNull() ?: IllegalStateException("解析采样率表失败"))
                } else {
                    client.sampleRates(parsed.getOrThrow()).map { byteArrayOf(it.toByte()) }
                }
            },
            Gh3220CommandMeta(
                CommandMeta(
                    key = "GH3220_SLOT_EN",
                    displayName = "Slot 使能",
                    description = "0x1C 切换 Slot 使能",
                    requestFormat = "",
                    params = listOf(
                        CommandParamDef(name = "slotEn", label = "Slot", type = ParamType.U8),
                        CommandParamDef(name = "addCmd", label = "附加命令", type = ParamType.U8, defaultValue = 0),
                        CommandParamDef(name = "function", label = "功能位", type = ParamType.U32, defaultValue = 0L),
                        CommandParamDef(name = "on", label = "开关", type = ParamType.U8, options = ON_OFF_OPTIONS),
                    ),
                    hasResponse = true,
                    responseFormat = STATUS_RESPONSE_FORMAT,
                    group = CommandGroup.DEVICE_CONTROL,
                ),
            ) { client, params ->
                client.slotEn(intParam(params[0]), intParam(params[1]), longParam(params[2]), intParam(params[3]) == 1)
                    .map { byteArrayOf(it.toByte()) }
            },
            Gh3220CommandMeta(
                CommandMeta(
                    key = "GH3220_ECG_CTRL",
                    displayName = "ECG 控制",
                    description = "0x1D ECG 控制",
                    requestFormat = "",
                    params = listOf(
                        CommandParamDef(name = "ctrlFlag", label = "控制标志", type = ParamType.U8),
                    ),
                    hasResponse = true,
                    responseFormat = STATUS_RESPONSE_FORMAT,
                    group = CommandGroup.DEVICE_CONTROL,
                ),
            ) { client, params ->
                client.ecgCtrl(intParam(params[0])).map { byteArrayOf(it.toByte()) }
            },
            Gh3220CommandMeta(
                CommandMeta(
                    key = "GH3220_WORK_MODE_SET",
                    displayName = "工作模式设置",
                    description = "0x1E 设置工作模式（透传白名单命令）",
                    requestFormat = "",
                    params = listOf(
                        CommandParamDef(name = "workMode", label = "工作模式", type = ParamType.U8),
                    ),
                    hasResponse = true,
                    responseFormat = STATUS_RESPONSE_FORMAT,
                    group = CommandGroup.DEVICE_CONTROL,
                ),
            ) { client, params ->
                client.workModeSet(intParam(params[0])).map { byteArrayOf(it.toByte()) }
            },
            Gh3220CommandMeta(
                CommandMeta(
                    key = "GH3220_APP_MODULE",
                    displayName = "应用模块命令",
                    description = "0x20 应用模块命令",
                    requestFormat = "",
                    params = listOf(
                        CommandParamDef(name = "cmd", label = "模块命令", type = ParamType.U8),
                    ),
                    hasResponse = true,
                    responseFormat = STATUS_RESPONSE_FORMAT,
                    group = CommandGroup.OTHER,
                ),
            ) { client, params ->
                client.appModule(intParam(params[0])).map { byteArrayOf(it.toByte()) }
            },
            Gh3220CommandMeta(
                CommandMeta(
                    key = "GH3220_SWITCH_CHIP",
                    displayName = "切换芯片",
                    description = "0x2E 切换 Cardiff 芯片",
                    requestFormat = "",
                    params = listOf(
                        CommandParamDef(name = "cmd", label = "切换命令", type = ParamType.U8),
                    ),
                    hasResponse = true,
                    responseFormat = STATUS_RESPONSE_FORMAT,
                    group = CommandGroup.OTHER,
                ),
            ) { client, params ->
                client.switchChip(intParam(params[0])).map { byteArrayOf(it.toByte()) }
            },
            Gh3220CommandMeta(
                CommandMeta(
                    key = "GH3220_REG_ARRAY_WRITE",
                    displayName = "寄存器数组写入",
                    description = "0xA1 批量写寄存器数组（响应状态已由 client 校验）",
                    requestFormat = "",
                    params = listOf(
                        CommandParamDef(
                            name = "blocks",
                            label = "寄存器块(hex)",
                            type = ParamType.U8_ARRAY,
                            description = "每 4 字节一块 [addrHi, addrLo, valHi, valLo]，按块依次解析",
                        ),
                    ),
                    hasResponse = true,
                    responseFormat = "状态已由 client 校验（0=成功），展示为空",
                    group = CommandGroup.REGISTER,
                ),
            ) { client, params ->
                val parsed = (params[0] as ByteArray).parseRegArrayBlocks()
                if (parsed.isFailure) {
                    Result.failure(parsed.exceptionOrNull() ?: IllegalStateException("解析寄存器块失败"))
                } else {
                    client.regArrayWrite(parsed.getOrThrow()).map { ByteArray(0) }
                }
            },
        )

        private val byKey: Map<String, Gh3220CommandMeta> = all.associateBy { it.key }

        fun getCommandByKey(key: String): Gh3220CommandMeta? = byKey[key]

        fun getCommandsByGroup(group: CommandGroup): List<Gh3220CommandMeta> =
            all.filter { it.group == group }

        private fun intParam(value: Any?): Int = (value as Number).toInt()

        private fun longParam(value: Any?): Long = (value as Number).toLong()

        private fun IntArray.toRegReadBytes(): ByteArray {
            val out = ByteArray(size * 2)
            forEachIndexed { i, v ->
                out[i * 2] = ((v shr 8) and 0xFF).toByte()
                out[i * 2 + 1] = (v and 0xFF).toByte()
            }
            return out
        }
    }
}
