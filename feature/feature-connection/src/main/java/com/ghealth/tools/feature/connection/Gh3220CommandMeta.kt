package com.ghealth.tools.feature.connection

import com.ghealth.tools.ble.gh3220.Gh3220ProtocolClient
import com.ghealth.tools.ble.protocol.gh3036.CommandGroup
import com.ghealth.tools.ble.protocol.gh3036.CommandMeta
import com.ghealth.tools.ble.protocol.gh3036.CommandParamDef
import com.ghealth.tools.ble.protocol.gh3036.ParamType

/**
 * 表单数值参数 → Int。
 *
 * 兼容两种数值来源（与 ble-protocol 的 `CommandPayloadBuilder` 同一约定）：
 * - 有符号 [Number]（Byte/Short/Int/Long）：来自下拉选项、时间戳等；
 * - Kotlin 无符号类型（[UByte]/[UShort]/[UInt]/[ULong]）：来自十六进制文本输入
 *   （`CommandPanelScreen` 对 U8/U16/U32 产生无符号值，无符号类型不是 `java.lang.Number`）。
 *
 * 不支持的返回 null，由调用方统一转为 failure。
 */
internal fun toIntParam(value: Any?): Int? = when (value) {
    is Number -> value.toInt()
    is UByte -> value.toInt()
    is UShort -> value.toInt()
    is UInt -> value.toInt()
    is ULong -> value.toInt()
    else -> null
}

/** 表单数值参数 → Long：兼容有符号 [Number] 与 Kotlin 无符号类型，不支持的返回 null。 */
internal fun toLongParam(value: Any?): Long? = when (value) {
    is Number -> value.toLong()
    is UByte -> value.toLong()
    is UShort -> value.toLong()
    is UInt -> value.toLong()
    is ULong -> value.toLong()
    else -> null
}

/** 参数值类型校验：数值类参数接受有符号 [Number] 与 Kotlin 无符号类型；数组类按类型匹配。 */
internal fun isSupportedParamValue(type: ParamType, value: Any): Boolean = when (type) {
    ParamType.U8, ParamType.U16, ParamType.U32,
    ParamType.I8, ParamType.I16, ParamType.I32,
    ParamType.TIMESTAMP, ParamType.FUNC_MODE_BITS ->
        value is Number || value is UByte || value is UShort || value is UInt || value is ULong
    ParamType.U8_ARRAY -> value is ByteArray
    // 同时接受 ShortArray（命令面板 U16_ARRAY 文本输入与 GH3036 CommandPayloadBuilder 均消费 ShortArray）
    // 与 IntArray，供前瞻性 U16_ARRAY 命令使用。
    ParamType.U16_ARRAY -> value is IntArray || value is ShortArray
}

/** 校验参数列表与参数定义：数量/必填/类型，返回错误描述或 null。PayloadBuilder 与 executor 包装层共用。 */
internal fun validateGh3220Params(paramDefs: List<CommandParamDef>, params: List<Any?>): String? {
    if (params.size != paramDefs.size) {
        return "参数数量不匹配：期望 ${paramDefs.size} 个，实际 ${params.size} 个"
    }
    paramDefs.forEachIndexed { index, def ->
        val value = params[index]
        if (value == null) {
            if (def.required) return "参数 ${def.name} 缺失"
        } else if (!isSupportedParamValue(def.type, value)) {
            return "参数 ${def.name} 类型不符：期望 ${def.type}，实际 ${value::class.simpleName}"
        }
    }
    return null
}

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
 * [executor] 入口由 companion 的 `command` 工厂统一按 [CommandMeta.params] 校验
 * （数量/必填/类型，数值类兼容有符号 Number 与 Kotlin 无符号整型），非法参数返回
 * [Result.failure] 而非抛异常，与 [Gh3220CommandPayloadBuilder] 的 failure 语义一致。
 *
 * 执行决策：命令面板（CommandPanelScreen）经 `Gh3220CommandSource.buildPayload` 编码 payload 后走
 * `sendRaw` 原始字节通路（响应以 hex 展示，与 GH3036 面板体验一致）；[executor] 保留为程序化调用入口
 * （Task 4 测试覆盖），面板不直接使用。两条路径共用同一批 ble-gh3220 编码器（BasicCommands /
 * ConfigCommands / RegisterCommands），字节一致性由对拍测试锁定。
 *
 * [all] 清单顺序与计划一致：核心命令在前，其余命令按命令 ID 升序。
 */
data class Gh3220CommandMeta(
    val meta: CommandMeta,
    /** 线命令 ID（协议文档 §2 命令 ID 列表，如 GET_VERSION=0x19、CONN_STATUS=0x1A、READ_REG=0x03）。 */
    val type: Int,
    /** 执行器：入参已按 [meta.params] 校验（含无符号整型转换），非法返回 failure；block 内可直接使用 `!!` 转换。 */
    val executor: suspend (Gh3220ProtocolClient, List<Any?>) -> Result<ByteArray>,
) {
    val key: String get() = meta.key
    val displayName: String get() = meta.displayName
    val params: List<CommandParamDef> get() = meta.params
    val group: CommandGroup get() = meta.group

    companion object {
        // ── 选项常量（值参考 .claude/gh3220_protocol/gh3220 protocol.md，真机待验证）──

        /** 0x19 获取版本类型（协议文档 §3.21；0x00 与 0x01 等价，此处取 0x01）。 */
        val VERSION_TYPE_OPTIONS: List<CommandParamDef.OptionItem> = listOf(
            CommandParamDef.OptionItem("EVK 版本 (0x00/0x01)", 0x01),
            CommandParamDef.OptionItem("虚拟寄存器版本 (0x0B)", 0x0B),
            CommandParamDef.OptionItem("Bootloader 版本 (0x0C)", 0x0C),
            CommandParamDef.OptionItem("BLE 版本 (0x0D)", 0x0D),
            CommandParamDef.OptionItem("协议版本 (0x0E)", 0x0E),
            CommandParamDef.OptionItem("支持功能 (0x0F)", 0x0F),
            CommandParamDef.OptionItem("驱动库版本 (0x10)", 0x10),
            CommandParamDef.OptionItem("芯片版本 (0x11)", 0x11),
            CommandParamDef.OptionItem("ADT (0x12)", 0x12),
            CommandParamDef.OptionItem("HR (0x13)", 0x13),
            CommandParamDef.OptionItem("HRV (0x14)", 0x14),
            CommandParamDef.OptionItem("HSM (0x15)", 0x15),
            CommandParamDef.OptionItem("FPBP (0x16)", 0x16),
            CommandParamDef.OptionItem("PWA (0x19)", 0x19),
            CommandParamDef.OptionItem("SPO2 (0x1A)", 0x1A),
            CommandParamDef.OptionItem("ECG (0x1B)", 0x1B),
            CommandParamDef.OptionItem("PWTT (0x1C)", 0x1C),
            CommandParamDef.OptionItem("SOFTADT (0x1D)", 0x1D),
            CommandParamDef.OptionItem("BT (0x1E)", 0x1E),
        )

        /** 0x10 下位机工作模式（协议文档 §3.12）。 */
        val WORK_MODE_OPTIONS: List<CommandParamDef.OptionItem> = listOf(
            CommandParamDef.OptionItem("EVK mode (0)", 0),
            CommandParamDef.OptionItem("APP mode (1)", 1),
            CommandParamDef.OptionItem("MCU online mode (2)", 2),
            CommandParamDef.OptionItem("MCU offline mode (3)", 3),
            CommandParamDef.OptionItem("验证工具工作模式 (4)", 4),
            CommandParamDef.OptionItem("Pass Through mode (5)", 5),
            CommandParamDef.OptionItem("获取下位机工作模式 (6)", 6),
        )

        /** 0x17 Cardiff 复位类型（协议文档 §3.19）。 */
        val CHIP_RESET_OPTIONS: List<CommandParamDef.OptionItem> = listOf(
            CommandParamDef.OptionItem("硬复位 (0x5A)", 0x5A),
            CommandParamDef.OptionItem("软复位 (0xC2)", 0xC2),
            CommandParamDef.OptionItem("wakeup (0xC3)", 0xC3),
            CommandParamDef.OptionItem("sleep 模式 (0xC4)", 0xC4),
        )

        /** 0x18 电流校准模式（协议文档 §3.20）。 */
        val CALIBRATE_MODE_OPTIONS: List<CommandParamDef.OptionItem> = listOf(
            CommandParamDef.OptionItem("自动校准 (0)", 0),
            CommandParamDef.OptionItem("手动校准 (1)", 1),
        )

        /** 0x2E 切换 Cardiff 芯片（协议文档 §3.37）。 */
        val SWITCH_CHIP_OPTIONS: List<CommandParamDef.OptionItem> = listOf(
            CommandParamDef.OptionItem("Cardiff 1 (1)", 1),
            CommandParamDef.OptionItem("Cardiff 2 (2)", 2),
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

        /**
         * 命令工厂：构造 [CommandMeta]（hasResponse 恒为 true、requestFormat 为空），
         * 并包装 [executor]：入口按参数定义校验（数量/必填/类型，含无符号整型），
         * 非法返回 [Result.failure]（不抛），与 [Gh3220CommandPayloadBuilder] 语义一致。
         */
        private fun command(
            key: String,
            type: Int,
            displayName: String,
            description: String,
            params: List<CommandParamDef>,
            responseFormat: String? = null,
            group: CommandGroup = CommandGroup.OTHER,
            executor: suspend (Gh3220ProtocolClient, List<Any?>) -> Result<ByteArray>,
        ): Gh3220CommandMeta {
            val meta = CommandMeta(
                key = key,
                displayName = displayName,
                description = description,
                requestFormat = "",
                params = params,
                hasResponse = true,
                responseFormat = responseFormat,
                group = group,
            )
            return Gh3220CommandMeta(meta = meta, type = type) { client, values ->
                val error = validateGh3220Params(meta.params, values)
                if (error != null) {
                    Result.failure(IllegalArgumentException("$error（key=${meta.key}）"))
                } else {
                    executor(client, values)
                }
            }
        }

        val all: List<Gh3220CommandMeta> = listOf(
            // ── 核心命令（计划顺序）────────────────────
            command(
                key = "GH3220_GET_VERSION",
                type = 0x19,
                displayName = "获取版本",
                description = "0x19 获取指定类型的版本信息（协议文档 §3.21）",
                params = listOf(
                    CommandParamDef(
                        name = "versionType",
                        label = "版本类型",
                        type = ParamType.U8,
                        options = VERSION_TYPE_OPTIONS,
                    ),
                ),
                responseFormat = "verType(1B) + 版本文本(UTF-8)",
                group = CommandGroup.VERSION_STATUS,
            ) { client, params ->
                client.getVersion(toIntParam(params[0])!!).map {
                    byteArrayOf(it.versionType.toByte()) + it.text.toByteArray()
                }
            },
            command(
                key = "GH3220_CONN_STATUS",
                type = 0x1A,
                displayName = "连接状态",
                description = "0x1A 查询连接状态（0=已连接 / 1=未连接）",
                params = emptyList(),
                responseFormat = "1 字节（0=已连接 / 1=未连接）",
                group = CommandGroup.VERSION_STATUS,
            ) { client, _ ->
                client.getConnectionStatus().map { byteArrayOf(it.toByte()) }
            },
            command(
                key = "GH3220_START_HBD",
                type = 0x0C,
                displayName = "启动 HBD",
                description = "0x0C 启动/停止 HBD 采集",
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
                responseFormat = STATUS_RESPONSE_FORMAT,
                group = CommandGroup.DEVICE_CONTROL,
            ) { client, params ->
                client.startHbd(
                    toIntParam(params[0])!! == 1,
                    toIntParam(params[1])!!,
                    toLongParam(params[2])!!,
                ).map { byteArrayOf(it.toByte()) }
            },
            command(
                key = "GH3220_READ_REG",
                type = 0x03,
                displayName = "读寄存器",
                description = "0x03 读取指定地址寄存器（响应每寄存器 2 字节大端）",
                params = listOf(
                    CommandParamDef(name = "addr", label = "地址", type = ParamType.U16, defaultValue = 0),
                    CommandParamDef(name = "count", label = "个数", type = ParamType.U8, defaultValue = 1),
                ),
                responseFormat = "每寄存器 2 字节大端",
                group = CommandGroup.REGISTER,
            ) { client, params ->
                client.readRegisters(toIntParam(params[0])!!, toIntParam(params[1])!!).map { it.toRegReadBytes() }
            },
            command(
                key = "GH3220_WORK_MODE",
                type = 0x10,
                displayName = "工作模式",
                description = "0x10 设置下位机工作模式",
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
                responseFormat = STATUS_RESPONSE_FORMAT,
                group = CommandGroup.DEVICE_CONTROL,
            ) { client, params ->
                client.setWorkMode(toIntParam(params[0])!!, toLongParam(params[1])!!)
                    .map { byteArrayOf(it.toByte()) }
            },
            command(
                key = "GH3220_RAW_SEND",
                type = 0x23,
                displayName = "原始透传",
                description = "任意命令 ID 原始字节收发（仅透传白名单 0x19/0x1A/0x1E/0x21/0x2A 放行）",
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
                responseFormat = "原始响应字节",
                group = CommandGroup.OTHER,
            ) { client, params ->
                client.sendRaw(toIntParam(params[0])!!, (params[1] as? ByteArray) ?: ByteArray(0))
            },

            // ── 其余命令（按命令 ID 升序）──────────────
            command(
                key = "GH3220_PACKAGE_TEST",
                type = 0x05,
                displayName = "通讯包测试",
                description = "0x05 通讯包测试，数据原样回显",
                params = listOf(
                    CommandParamDef(name = "data", label = "数据(hex)", type = ParamType.U8_ARRAY),
                ),
                responseFormat = "数据原样回显",
                group = CommandGroup.OTHER,
            ) { client, params ->
                client.packageTest(params[0] as ByteArray)
            },
            command(
                key = "GH3220_GSENSOR_SET",
                type = 0x11,
                displayName = "G-sensor 设置",
                description = "0x11 设置 G-sensor 厂商/分辨率/采样率",
                params = listOf(
                    CommandParamDef(name = "vendorId", label = "厂商 ID", type = ParamType.U8),
                    CommandParamDef(name = "resolution", label = "分辨率", type = ParamType.U8),
                    CommandParamDef(name = "sampleRate", label = "采样率", type = ParamType.U16, defaultValue = 0),
                ),
                responseFormat = STATUS_RESPONSE_FORMAT,
                group = CommandGroup.DEVICE_CONTROL,
            ) { client, params ->
                client.gsensorSet(
                    toIntParam(params[0])!!,
                    toIntParam(params[1])!!,
                    toIntParam(params[2])!!,
                ).map { byteArrayOf(it.toByte()) }
            },
            command(
                key = "GH3220_FIFO_THRESHOLD",
                type = 0x12,
                displayName = "FIFO 阈值",
                description = "0x12 设置 Cardiff FIFO 阈值",
                params = listOf(
                    CommandParamDef(name = "threshold", label = "阈值", type = ParamType.U16, defaultValue = 0),
                ),
                responseFormat = STATUS_RESPONSE_FORMAT,
                group = CommandGroup.OTHER,
            ) { client, params ->
                client.fifoThreshold(toIntParam(params[0])!!).map { byteArrayOf(it.toByte()) }
            },
            command(
                key = "GH3220_EVENT_SET",
                type = 0x13,
                displayName = "事件设置",
                description = "0x13 设置 Cardiff 事件掩码",
                params = listOf(
                    CommandParamDef(name = "events", label = "事件掩码", type = ParamType.U16, defaultValue = 0),
                ),
                responseFormat = STATUS_RESPONSE_FORMAT,
                group = CommandGroup.OTHER,
            ) { client, params ->
                client.eventSet(toIntParam(params[0])!!).map { byteArrayOf(it.toByte()) }
            },
            command(
                key = "GH3220_FUNC_MAP",
                type = 0x15,
                displayName = "功能通道映射",
                description = "0x15 下发功能通道映射（固定 64 字节）",
                params = listOf(
                    CommandParamDef(
                        name = "map",
                        label = "映射表(64字节hex)",
                        type = ParamType.U8_ARRAY,
                        description = "固定 64 字节，长度不符时编码层拒绝",
                    ),
                ),
                responseFormat = STATUS_RESPONSE_FORMAT,
                group = CommandGroup.OTHER,
            ) { client, params ->
                client.funcMap(params[0] as ByteArray).map { byteArrayOf(it.toByte()) }
            },
            command(
                key = "GH3220_CHIP_RESET",
                type = 0x17,
                displayName = "芯片复位",
                description = "0x17 Cardiff 芯片复位",
                params = listOf(
                    CommandParamDef(
                        name = "resetType",
                        label = "复位类型",
                        type = ParamType.U8,
                        options = CHIP_RESET_OPTIONS,
                    ),
                ),
                responseFormat = STATUS_RESPONSE_FORMAT,
                group = CommandGroup.DEVICE_CONTROL,
            ) { client, params ->
                client.chipReset(toIntParam(params[0])!!).map { byteArrayOf(it.toByte()) }
            },
            command(
                key = "GH3220_CALIBRATE_CURRENT",
                type = 0x18,
                displayName = "电流校准",
                description = "0x18 电流校准",
                params = listOf(
                    CommandParamDef(
                        name = "mode",
                        label = "校准模式",
                        type = ParamType.U8,
                        options = CALIBRATE_MODE_OPTIONS,
                    ),
                ),
                responseFormat = STATUS_RESPONSE_FORMAT,
                group = CommandGroup.FACTORY,
            ) { client, params ->
                client.calibrateCurrent(toIntParam(params[0])!!).map { byteArrayOf(it.toByte()) }
            },
            command(
                key = "GH3220_SAMPLE_RATES",
                type = 0x1B,
                displayName = "采样率设置",
                description = "0x1B 设置各 Function 采样率",
                params = listOf(
                    CommandParamDef(
                        name = "entries",
                        label = "采样率表(hex)",
                        type = ParamType.U8_ARRAY,
                        description = "每 2 字节一项：高 4bit Function_ID + 低 12bit SR（大端），按项依次解析",
                    ),
                ),
                responseFormat = STATUS_RESPONSE_FORMAT,
                group = CommandGroup.OTHER,
            ) { client, params ->
                val parsed = (params[0] as ByteArray).parseSampleRateEntries()
                if (parsed.isFailure) {
                    Result.failure(parsed.exceptionOrNull() ?: IllegalStateException("解析采样率表失败"))
                } else {
                    client.sampleRates(parsed.getOrThrow()).map { byteArrayOf(it.toByte()) }
                }
            },
            command(
                key = "GH3220_SLOT_EN",
                type = 0x1C,
                displayName = "Slot 使能",
                description = "0x1C 切换 Slot 使能",
                params = listOf(
                    CommandParamDef(name = "slotEn", label = "Slot", type = ParamType.U8),
                    CommandParamDef(name = "addCmd", label = "附加命令", type = ParamType.U8, defaultValue = 0),
                    CommandParamDef(name = "function", label = "功能位", type = ParamType.U32, defaultValue = 0L),
                    CommandParamDef(name = "on", label = "开关", type = ParamType.U8, options = ON_OFF_OPTIONS),
                ),
                responseFormat = STATUS_RESPONSE_FORMAT,
                group = CommandGroup.DEVICE_CONTROL,
            ) { client, params ->
                client.slotEn(
                    toIntParam(params[0])!!,
                    toIntParam(params[1])!!,
                    toLongParam(params[2])!!,
                    toIntParam(params[3])!! == 1,
                ).map { byteArrayOf(it.toByte()) }
            },
            command(
                key = "GH3220_ECG_CTRL",
                type = 0x1D,
                displayName = "ECG 控制",
                description = "0x1D ECG 控制",
                params = listOf(
                    CommandParamDef(name = "ctrlFlag", label = "控制标志", type = ParamType.U8),
                ),
                responseFormat = STATUS_RESPONSE_FORMAT,
                group = CommandGroup.DEVICE_CONTROL,
            ) { client, params ->
                client.ecgCtrl(toIntParam(params[0])!!).map { byteArrayOf(it.toByte()) }
            },
            command(
                key = "GH3220_WORK_MODE_SET",
                type = 0x1E,
                displayName = "工作模式设置",
                description = "0x1E 设置工作模式（透传白名单命令）",
                params = listOf(
                    CommandParamDef(name = "workMode", label = "工作模式", type = ParamType.U8),
                ),
                responseFormat = STATUS_RESPONSE_FORMAT,
                group = CommandGroup.DEVICE_CONTROL,
            ) { client, params ->
                client.workModeSet(toIntParam(params[0])!!).map { byteArrayOf(it.toByte()) }
            },
            command(
                key = "GH3220_APP_MODULE",
                type = 0x20,
                displayName = "应用模块命令",
                description = "0x20 应用模块命令",
                params = listOf(
                    CommandParamDef(name = "cmd", label = "模块命令", type = ParamType.U8),
                ),
                responseFormat = STATUS_RESPONSE_FORMAT,
                group = CommandGroup.OTHER,
            ) { client, params ->
                client.appModule(toIntParam(params[0])!!).map { byteArrayOf(it.toByte()) }
            },
            command(
                key = "GH3220_SWITCH_CHIP",
                type = 0x2E,
                displayName = "切换芯片",
                description = "0x2E 切换 Cardiff 芯片",
                params = listOf(
                    CommandParamDef(
                        name = "cmd",
                        label = "切换命令",
                        type = ParamType.U8,
                        options = SWITCH_CHIP_OPTIONS,
                    ),
                ),
                responseFormat = STATUS_RESPONSE_FORMAT,
                group = CommandGroup.OTHER,
            ) { client, params ->
                client.switchChip(toIntParam(params[0])!!).map { byteArrayOf(it.toByte()) }
            },
            command(
                key = "GH3220_REG_ARRAY_WRITE",
                type = 0xA1,
                displayName = "寄存器数组写入",
                description = "0xA1 批量写寄存器数组（响应状态已由 client 校验）",
                params = listOf(
                    CommandParamDef(
                        name = "blocks",
                        label = "寄存器块(hex)",
                        type = ParamType.U8_ARRAY,
                        description = "每 4 字节一块 [addrHi, addrLo, valHi, valLo]，按块依次解析",
                    ),
                ),
                responseFormat = "状态已由 client 校验（0=成功），展示为空",
                group = CommandGroup.REGISTER,
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
