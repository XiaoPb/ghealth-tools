package com.ghealth.tools.ble.protocol.gh3036

enum class ParamType {
    U8, U16, U32, I8, I16, I32,
    U8_ARRAY, U16_ARRAY,
    TIMESTAMP,
    FUNC_MODE_BITS
}

data class CommandParamDef(
    val name: String,
    val label: String,
    val type: ParamType,
    val required: Boolean = true,
    val defaultValue: Any? = null,
    val options: List<OptionItem>? = null,
    val description: String? = null
) {
    data class OptionItem(
        val label: String,
        val value: Any
    )
}

data class CommandMeta(
    val key: String,
    val displayName: String,
    val description: String,
    val requestFormat: String,
    val params: List<CommandParamDef>,
    val hasResponse: Boolean,
    val responseFormat: String? = null,
    val group: CommandGroup = CommandGroup.OTHER
)

enum class CommandGroup(val displayName: String) {
    DEVICE_CONTROL("设备控制"),
    REGISTER("寄存器操作"),
    VERSION_STATUS("版本与状态"),
    TIME("时间设置"),
    FACTORY("工厂测试"),
    OTHER("其他")
}

object Gh3036CommandMeta {

    // ── Options from cmd.yaml ────────────────────────────────────────

    val CHIP_CTRL_OPTIONS = listOf(
        CommandParamDef.OptionItem("硬件复位 (0x5A)", 0x5A),
        CommandParamDef.OptionItem("软件复位 (0xC2)", 0xC2),
        CommandParamDef.OptionItem("退出休眠 (0xC3)", 0xC3),
        CommandParamDef.OptionItem("进入休眠 (0xC4)", 0xC4)
    )

    val VERSION_TYPE_OPTIONS = listOf(
        CommandParamDef.OptionItem("固件版本 (0x01)", 0x01),
        CommandParamDef.OptionItem("虚拟寄存器版本 (0x03)", 0x03),
        CommandParamDef.OptionItem("Bootloader版本 (0x04)", 0x04),
        CommandParamDef.OptionItem("协议版本 (0x05)", 0x05),
        CommandParamDef.OptionItem("驱动功能支持 (0x06)", 0x06),
        CommandParamDef.OptionItem("驱动版本 (0x07)", 0x07),
        CommandParamDef.OptionItem("芯片版本 (0x08)", 0x08),
        CommandParamDef.OptionItem("BLE版本 (0x09)", 0x09),
        CommandParamDef.OptionItem("算法调用Demo版本 (0x0A)", 0x0A),
        CommandParamDef.OptionItem("算法版本 (0x20)", 0x20)
    )

    val WORK_MODE_OPTIONS = listOf(
        CommandParamDef.OptionItem("EVK模式 (0)", 0),
        CommandParamDef.OptionItem("APP模式 (1,弃用)", 1),
        CommandParamDef.OptionItem("MCU在线模式 (2)", 2),
        CommandParamDef.OptionItem("MCU离线模式 (3)", 3),
        CommandParamDef.OptionItem("测试调谐模式 (4,弃用)", 4),
        CommandParamDef.OptionItem("透传模式 (5)", 5),
        CommandParamDef.OptionItem("获取工作模式 (6,弃用)", 6),
        CommandParamDef.OptionItem("工厂模式 (7)", 7)
    )

    val DOWNLOAD_STAGE_OPTIONS = listOf(
        CommandParamDef.OptionItem("开始下载 (0)", 0),
        CommandParamDef.OptionItem("结束下载 (1)", 1)
    )

    val LINK_TYPE_OPTIONS = listOf(
        CommandParamDef.OptionItem("主链路", 0),
        CommandParamDef.OptionItem("从链路", 1),
        CommandParamDef.OptionItem("全部链路", 2)
    )

    val TEST_MODE_OPTIONS = listOf(
        CommandParamDef.OptionItem("Chip Init Test (0x01)", 0x01),
        CommandParamDef.OptionItem("Chip UID Test (0x02)", 0x02),
        CommandParamDef.OptionItem("Base Noise Test (0x04)", 0x04),
        CommandParamDef.OptionItem("PPG Noise Test (0x08)", 0x08),
        CommandParamDef.OptionItem("LPCTR Test (0x10)", 0x10),
        CommandParamDef.OptionItem("LPL CTR Test (0x20)", 0x20)
    )

    val SW_FUNCTION_CTRL_OPTIONS = listOf(
        CommandParamDef.OptionItem("开启", 0),
        CommandParamDef.OptionItem("关闭", 1)
    )

    val LOW_POWER_CTRL_OPTIONS = listOf(
        CommandParamDef.OptionItem("开启低功耗", 0),
        CommandParamDef.OptionItem("退出低功耗", 1)
    )

    // ── FuncModeBits for GH3036 SwFunctionCmd / LowPowerCmd ──────────
    // Each entry maps to a bit position in the 32-bit function mask.
    // GH3036 supported bits from cmd.yaml func_mode_bits section.

    data class FuncModeBit(
        val name: String,
        val label: String,
        val bit: Int
    )

    val FUNC_MODE_BITS_GH3036 = listOf(
        FuncModeBit("ADT", "ADT", 0),
        FuncModeBit("HR", "HR", 1),
        FuncModeBit("SpO2", "SpO2", 2),
        FuncModeBit("HRV", "HRV", 3),
        FuncModeBit("GNADT", "GNADT", 4),
        FuncModeBit("IRNADT", "IRNADT", 5),
        FuncModeBit("TEST1", "TEST1", 6),
        FuncModeBit("TEST2", "TEST2", 7),
        FuncModeBit("SLOT", "SLOT", 8)
    )

    val FUNC_MODE_BITS_GH3220 = listOf(
        FuncModeBit("ADT", "ADT", 0),
        FuncModeBit("HR", "HR", 1),
        FuncModeBit("HRV", "HRV", 2),
        FuncModeBit("HSM", "HSM", 3),
        FuncModeBit("FPBP", "FPBP", 4),
        FuncModeBit("PWA", "PWA", 5),
        FuncModeBit("SPO2", "SpO2", 6),
        FuncModeBit("ECG", "ECG", 7),
        FuncModeBit("PWTT", "PWTT", 8),
        FuncModeBit("SOFT_ADT_GREEN", "SOFT_ADT_GREEN", 9),
        FuncModeBit("BT", "BT", 10),
        FuncModeBit("RESP", "RESP", 11),
        FuncModeBit("AF", "AF", 12),
        FuncModeBit("TEST1", "TEST1", 13),
        FuncModeBit("TEST2", "TEST2", 14),
        FuncModeBit("SOFT_ADT_IR", "SOFT_ADT_IR", 15),
        FuncModeBit("RS0", "RS0", 16),
        FuncModeBit("RS1", "RS1", 17),
        FuncModeBit("RS2", "RS2", 18),
        FuncModeBit("LEAD_DET", "LEAD_DET", 19)
    )

    val FUNC_MODE_BITS_GH3300 = listOf(
        FuncModeBit("ADT", "ADT", 0),
        FuncModeBit("HR", "HR", 1),
        FuncModeBit("HRV", "HRV", 2),
        FuncModeBit("HSM", "HSM", 3),
        FuncModeBit("FPBP", "FPBP", 4),
        FuncModeBit("PWA", "PWA", 5),
        FuncModeBit("SPO2", "SpO2", 6),
        FuncModeBit("ECG", "ECG", 7),
        FuncModeBit("PWTT", "PWTT", 8),
        FuncModeBit("SOFT_ADT_GREEN", "SOFT_ADT_GREEN", 9),
        FuncModeBit("BT", "BT", 10),
        FuncModeBit("RESP", "RESP", 11),
        FuncModeBit("AF", "AF", 12),
        FuncModeBit("TEST1", "TEST1", 13),
        FuncModeBit("TEST2", "TEST2", 14),
        FuncModeBit("SOFT_ADT_IR", "SOFT_ADT_IR", 15),
        FuncModeBit("BIA", "BIA", 16),
        FuncModeBit("GSR", "GSR", 17),
        FuncModeBit("LEAD", "LEAD", 18)
    )

    fun getFuncModeBits(chipName: String): List<FuncModeBit> = when (chipName.lowercase()) {
        "gh3220" -> FUNC_MODE_BITS_GH3220
        "gh3300" -> FUNC_MODE_BITS_GH3300
        else -> FUNC_MODE_BITS_GH3036
    }

    // ── All Commands ─────────────────────────────────────────────────

    val ALL_COMMANDS: List<CommandMeta> = listOf(
        // ── 设备控制 ──
        CommandMeta(
            key = KEY_GH3X_CHIP_CTRL,
            displayName = "芯片控制",
            description = "硬件复位、软件复位、休眠控制",
            requestFormat = FMT_GH3X_CHIP_CTRL,
            params = listOf(
                CommandParamDef(
                    name = "ctrlType",
                    label = "控制类型",
                    type = ParamType.U8,
                    options = CHIP_CTRL_OPTIONS
                )
            ),
            hasResponse = false,
            group = CommandGroup.DEVICE_CONTROL
        ),
        CommandMeta(
            key = KEY_GH3X_SW_FUNCTION_CMD,
            displayName = "功能切换",
            description = "开启/关闭芯片功能模块，支持多选",
            requestFormat = FMT_GH3X_SW_FUNCTION_CMD,
            params = listOf(
                CommandParamDef(
                    name = "funcModeBits",
                    label = "功能选择",
                    type = ParamType.FUNC_MODE_BITS,
                    description = "选择要控制的功能（多选）"
                ),
                CommandParamDef(
                    name = "ctrlType",
                    label = "控制类型",
                    type = ParamType.U8,
                    options = SW_FUNCTION_CTRL_OPTIONS
                )
            ),
            hasResponse = false,
            group = CommandGroup.DEVICE_CONTROL
        ),
        CommandMeta(
            key = KEY_GH_SET_WORK_MODE_CMD,
            displayName = "设置工作模式",
            description = "设置芯片工作模式",
            requestFormat = FMT_GH_SET_WORK_MODE_CMD,
            params = listOf(
                CommandParamDef(
                    name = "workMode",
                    label = "工作模式",
                    type = ParamType.U8,
                    options = WORK_MODE_OPTIONS
                )
            ),
            hasResponse = false,
            group = CommandGroup.DEVICE_CONTROL
        ),
        CommandMeta(
            key = KEY_GH_LOW_POWER_CMD,
            displayName = "低功耗命令",
            description = "控制芯片进入/退出低功耗模式，支持按功能模块控制",
            requestFormat = FMT_GH_LOW_POWER_CMD,
            params = listOf(
                CommandParamDef(
                    name = "funcModeBits",
                    label = "功能选择",
                    type = ParamType.FUNC_MODE_BITS,
                    description = "选择要控制的功能（多选）"
                ),
                CommandParamDef(
                    name = "ctrlType",
                    label = "控制类型",
                    type = ParamType.U8,
                    options = LOW_POWER_CTRL_OPTIONS
                )
            ),
            hasResponse = false,
            group = CommandGroup.DEVICE_CONTROL
        ),
        CommandMeta(
            key = KEY_DOWNLOAD_CONFIG,
            displayName = "下载配置",
            description = "控制配置下载的开始与结束",
            requestFormat = FMT_DOWNLOAD_CONFIG,
            params = listOf(
                CommandParamDef(
                    name = "stage",
                    label = "下载阶段",
                    type = ParamType.U8,
                    options = DOWNLOAD_STAGE_OPTIONS
                )
            ),
            hasResponse = false,
            group = CommandGroup.DEVICE_CONTROL
        ),

        // ── 寄存器操作 ──
        CommandMeta(
            key = KEY_GH3X_REGS_WRITE_CMD,
            displayName = "寄存器写入",
            description = "连续写寄存器：格式 [地址1, 值1, 地址2, 值2, ...]（十六进制，空格分隔）",
            requestFormat = FMT_GH3X_REGS_WRITE_CMD,
            params = listOf(
                CommandParamDef(
                    name = "regs",
                    label = "寄存器数据",
                    type = ParamType.U16_ARRAY,
                    description = "地址和值交替排列"
                )
            ),
            hasResponse = false,
            group = CommandGroup.REGISTER
        ),
        CommandMeta(
            key = KEY_GH3X_REGS_READ_CMD,
            displayName = "寄存器读取",
            description = "从指定地址连续读取寄存器值",
            requestFormat = FMT_GH3X_REGS_READ_CMD,
            params = listOf(
                CommandParamDef(
                    name = "regAddr",
                    label = "起始地址",
                    type = ParamType.U16,
                    description = "16位寄存器地址（十六进制）"
                ),
                CommandParamDef(
                    name = "readLen",
                    label = "读取个数",
                    type = ParamType.I32,
                    defaultValue = 1,
                    description = "读取的寄存器数量 (1-200)"
                )
            ),
            hasResponse = true,
            responseFormat = RET_GH3X_REGS_READ_CMD,
            group = CommandGroup.REGISTER
        ),
        CommandMeta(
            key = KEY_GH3X_REG_BIT_FIELD_WRITE_CMD,
            displayName = "寄存器位域写入",
            description = "修改单个寄存器的位域",
            requestFormat = FMT_GH3X_REG_BIT_FIELD_WRITE_CMD,
            params = listOf(
                CommandParamDef(
                    name = "regAddr",
                    label = "寄存器地址",
                    type = ParamType.U16,
                    description = "16位寄存器地址（十六进制）"
                ),
                CommandParamDef(
                    name = "lsb",
                    label = "起始位 (LSB)",
                    type = ParamType.U8,
                    description = "位域最低位 (0-15)"
                ),
                CommandParamDef(
                    name = "msb",
                    label = "结束位 (MSB)",
                    type = ParamType.U8,
                    description = "位域最高位 (0-15)"
                ),
                CommandParamDef(
                    name = "regVal",
                    label = "写入值",
                    type = ParamType.U16,
                    description = "要写入的值（自动移位到对应位置）"
                )
            ),
            hasResponse = false,
            group = CommandGroup.REGISTER
        ),
        CommandMeta(
            key = KEY_GH3X_REGS_BIT_FIELD_WRITE_CMD,
            displayName = "批量位域写入",
            description = "批量修改多个寄存器的位域：格式 [地址, LSB, MSB, 值, ...]（十六进制）",
            requestFormat = FMT_GH3X_REGS_BIT_FIELD_WRITE_CMD,
            params = listOf(
                CommandParamDef(
                    name = "regBits",
                    label = "位域数据",
                    type = ParamType.U16_ARRAY,
                    description = "每组4个值：地址 LSB MSB 值"
                )
            ),
            hasResponse = false,
            group = CommandGroup.REGISTER
        ),
        CommandMeta(
            key = KEY_GH3X_REGS_LIST_WRITE_CMD,
            displayName = "寄存器列表写入",
            description = "批量写寄存器地址列表（十六进制，空格分隔）",
            requestFormat = FMT_GH3X_REGS_LIST_WRITE_CMD,
            params = listOf(
                CommandParamDef(
                    name = "regs",
                    label = "地址列表",
                    type = ParamType.U16_ARRAY,
                    description = "要写入的寄存器地址列表"
                )
            ),
            hasResponse = false,
            group = CommandGroup.REGISTER
        ),

        // ── 版本与状态 ──
        CommandMeta(
            key = KEY_GH3X_GET_VERSION,
            displayName = "获取版本",
            description = "获取芯片各类型版本信息",
            requestFormat = FMT_GH3X_GET_VERSION,
            params = listOf(
                CommandParamDef(
                    name = "verType",
                    label = "版本类型",
                    type = ParamType.U8,
                    options = VERSION_TYPE_OPTIONS
                )
            ),
            hasResponse = true,
            responseFormat = RET_GH3X_GET_VERSION,
            group = CommandGroup.VERSION_STATUS
        ),
        CommandMeta(
            key = KEY_GET_CHIP_LINK_STATUS,
            displayName = "获取连接状态",
            description = "获取芯片链路连接状态",
            requestFormat = FMT_GET_CHIP_LINK_STATUS,
            params = listOf(
                CommandParamDef(
                    name = "linkType",
                    label = "链路类型",
                    type = ParamType.U8,
                    options = LINK_TYPE_OPTIONS
                )
            ),
            hasResponse = true,
            responseFormat = RET_GET_CHIP_LINK_STATUS,
            group = CommandGroup.VERSION_STATUS
        ),

        // ── 时间设置 ──
        CommandMeta(
            key = KEY_GH_TIMESTAMP_SET,
            displayName = "设置时间戳",
            description = "设置芯片 Unix 时间戳（秒）",
            requestFormat = FMT_GH_TIMESTAMP_SET,
            params = listOf(
                CommandParamDef(
                    name = "ts",
                    label = "时间戳",
                    type = ParamType.TIMESTAMP,
                    description = "Unix时间戳（秒）"
                )
            ),
            hasResponse = false,
            group = CommandGroup.TIME
        ),
        CommandMeta(
            key = KEY_GH_TIME_SET,
            displayName = "设置时间",
            description = "设置芯片时间与时区偏移",
            requestFormat = FMT_GH_TIME_SET,
            params = listOf(
                CommandParamDef(
                    name = "ts",
                    label = "时间戳",
                    type = ParamType.TIMESTAMP,
                    description = "Unix时间戳（秒）"
                ),
                CommandParamDef(
                    name = "hourOffset",
                    label = "时区偏移",
                    type = ParamType.I8,
                    defaultValue = 8,
                    description = "时区偏移小时数 (-12 ~ +14)"
                )
            ),
            hasResponse = false,
            group = CommandGroup.TIME
        ),

        // ── 工厂测试 ──
        CommandMeta(
            key = KEY_F_SET_MODE,
            displayName = "设置测试模式",
            description = "进入指定的工厂测试模式",
            requestFormat = FMT_F_SET_MODE,
            params = listOf(
                CommandParamDef(
                    name = "testMode",
                    label = "测试模式",
                    type = ParamType.U8,
                    options = TEST_MODE_OPTIONS
                )
            ),
            hasResponse = false,
            group = CommandGroup.FACTORY
        ),
        CommandMeta(
            key = KEY_F_GET_MODE,
            displayName = "获取测试模式",
            description = "读取工厂测试模式的测试数据",
            requestFormat = FMT_F_GET_MODE,
            params = listOf(
                CommandParamDef(
                    name = "testMode",
                    label = "测试模式",
                    type = ParamType.U8,
                    options = TEST_MODE_OPTIONS
                )
            ),
            hasResponse = true,
            responseFormat = RET_F_GET_MODE,
            group = CommandGroup.FACTORY
        )
    )

    fun getCommandByKey(key: String): CommandMeta? = ALL_COMMANDS.find { it.key == key }

    fun getCommandsByGroup(group: CommandGroup): List<CommandMeta> = ALL_COMMANDS.filter { it.group == group }
}
