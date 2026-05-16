package com.ghealth.tools.ble.protocol.gh3036

enum class ParamType {
    U8, U16, U32, I8, I16, I32,
    U8_ARRAY, U16_ARRAY,
    TIMESTAMP
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

    val CHIP_CTRL_OPTIONS = listOf(
        CommandParamDef.OptionItem("复位", 0),
        CommandParamDef.OptionItem("使能", 1),
        CommandParamDef.OptionItem("禁能", 2)
    )

    val VERSION_TYPE_OPTIONS = listOf(
        CommandParamDef.OptionItem("固件版本", 0),
        CommandParamDef.OptionItem("硬件版本", 1),
        CommandParamDef.OptionItem("协议版本", 2)
    )

    val WORK_MODE_OPTIONS = listOf(
        CommandParamDef.OptionItem("空闲模式", 0),
        CommandParamDef.OptionItem("工作模式", 1),
        CommandParamDef.OptionItem("测试模式", 2),
        CommandParamDef.OptionItem("休眠模式", 3)
    )

    val DOWNLOAD_STAGE_OPTIONS = listOf(
        CommandParamDef.OptionItem("阶段0 - 初始化", 0),
        CommandParamDef.OptionItem("阶段1 - 数据传输", 1),
        CommandParamDef.OptionItem("阶段2 - 验证", 2),
        CommandParamDef.OptionItem("阶段3 - 完成", 3)
    )

    val LINK_TYPE_OPTIONS = listOf(
        CommandParamDef.OptionItem("主链路", 0),
        CommandParamDef.OptionItem("从链路", 1),
        CommandParamDef.OptionItem("全部链路", 2)
    )

    val TEST_MODE_OPTIONS = listOf(
        CommandParamDef.OptionItem("模式0", 0),
        CommandParamDef.OptionItem("模式1", 1),
        CommandParamDef.OptionItem("模式2", 2),
        CommandParamDef.OptionItem("模式3", 3)
    )

    val ALL_COMMANDS: List<CommandMeta> = listOf(
        CommandMeta(
            key = KEY_F_GET_MODE,
            displayName = "获取测试模式",
            description = "获取工厂测试模式的数据",
            params = listOf(
                CommandParamDef(
                    name = "testMode",
                    label = "测试模式",
                    type = ParamType.U8,
                    options = TEST_MODE_OPTIONS,
                    description = "选择要查询的测试模式"
                )
            ),
            hasResponse = true,
            responseFormat = RET_F_GET_MODE,
            group = CommandGroup.FACTORY
        ),
        CommandMeta(
            key = KEY_F_SET_MODE,
            displayName = "设置测试模式",
            description = "设置工厂测试模式",
            params = listOf(
                CommandParamDef(
                    name = "testMode",
                    label = "测试模式",
                    type = ParamType.U8,
                    options = TEST_MODE_OPTIONS,
                    description = "选择要设置的测试模式"
                )
            ),
            hasResponse = false,
            group = CommandGroup.FACTORY
        ),
        CommandMeta(
            key = KEY_GH3X_CHIP_CTRL,
            displayName = "芯片控制",
            description = "控制芯片的复位、使能、禁能等操作",
            params = listOf(
                CommandParamDef(
                    name = "ctrlType",
                    label = "控制类型",
                    type = ParamType.U8,
                    options = CHIP_CTRL_OPTIONS,
                    description = "选择控制操作类型"
                )
            ),
            hasResponse = false,
            group = CommandGroup.DEVICE_CONTROL
        ),
        CommandMeta(
            key = KEY_GH3X_GET_VERSION,
            displayName = "获取版本",
            description = "获取芯片的固件、硬件或协议版本信息",
            params = listOf(
                CommandParamDef(
                    name = "verType",
                    label = "版本类型",
                    type = ParamType.U8,
                    options = VERSION_TYPE_OPTIONS,
                    description = "选择要获取的版本类型"
                )
            ),
            hasResponse = true,
            responseFormat = RET_GH3X_GET_VERSION,
            group = CommandGroup.VERSION_STATUS
        ),
        CommandMeta(
            key = KEY_GH3X_REG_BIT_FIELD_WRITE_CMD,
            displayName = "寄存器位域写入",
            description = "写入寄存器的指定位域",
            params = listOf(
                CommandParamDef(
                    name = "regAddr",
                    label = "寄存器地址",
                    type = ParamType.U16,
                    description = "16位寄存器地址（十六进制）"
                ),
                CommandParamDef(
                    name = "lsb",
                    label = "起始位",
                    type = ParamType.U8,
                    description = "位域的最低位位置（0-15）"
                ),
                CommandParamDef(
                    name = "msb",
                    label = "结束位",
                    type = ParamType.U8,
                    description = "位域的最高位位置（0-15）"
                ),
                CommandParamDef(
                    name = "regVal",
                    label = "写入值",
                    type = ParamType.U16,
                    description = "要写入的值"
                )
            ),
            hasResponse = false,
            group = CommandGroup.REGISTER
        ),
        CommandMeta(
            key = KEY_GH3X_REGS_BIT_FIELD_WRITE_CMD,
            displayName = "多寄存器位域写入",
            description = "批量写入多个寄存器的位域",
            params = listOf(
                CommandParamDef(
                    name = "regBits",
                    label = "寄存器位域数据",
                    type = ParamType.U16_ARRAY,
                    description = "格式: 地址, LSB, MSB, 值, ...（十六进制，空格分隔）"
                )
            ),
            hasResponse = false,
            group = CommandGroup.REGISTER
        ),
        CommandMeta(
            key = KEY_GH3X_REGS_LIST_WRITE_CMD,
            displayName = "寄存器列表写入",
            description = "批量写入寄存器列表",
            params = listOf(
                CommandParamDef(
                    name = "regs",
                    label = "寄存器数据",
                    type = ParamType.U16_ARRAY,
                    description = "格式: 地址, 值, 地址, 值, ...（十六进制，空格分隔）"
                )
            ),
            hasResponse = false,
            group = CommandGroup.REGISTER
        ),
        CommandMeta(
            key = KEY_GH3X_REGS_READ_CMD,
            displayName = "寄存器读取",
            description = "从指定地址读取寄存器值",
            params = listOf(
                CommandParamDef(
                    name = "regAddr",
                    label = "寄存器地址",
                    type = ParamType.U16,
                    description = "16位寄存器起始地址（十六进制）"
                ),
                CommandParamDef(
                    name = "readLen",
                    label = "读取长度",
                    type = ParamType.I32,
                    defaultValue = 1,
                    description = "要读取的寄存器数量"
                )
            ),
            hasResponse = true,
            responseFormat = RET_GH3X_REGS_READ_CMD,
            group = CommandGroup.REGISTER
        ),
        CommandMeta(
            key = KEY_GH3X_REGS_WRITE_CMD,
            displayName = "寄存器写入",
            description = "批量写入寄存器值",
            params = listOf(
                CommandParamDef(
                    name = "regs",
                    label = "寄存器数据",
                    type = ParamType.U16_ARRAY,
                    description = "格式: 地址, 值, 地址, 值, ...（十六进制，空格分隔）"
                )
            ),
            hasResponse = false,
            group = CommandGroup.REGISTER
        ),
        CommandMeta(
            key = KEY_GH3X_SW_FUNCTION_CMD,
            displayName = "软件功能命令",
            description = "执行软件功能控制命令",
            params = listOf(
                CommandParamDef(
                    name = "targetFuncMode",
                    label = "目标功能模式",
                    type = ParamType.U32,
                    description = "目标功能模式值（十六进制）"
                ),
                CommandParamDef(
                    name = "ctrlType",
                    label = "控制类型",
                    type = ParamType.U8,
                    options = listOf(
                        CommandParamDef.OptionItem("启动", 0),
                        CommandParamDef.OptionItem("停止", 1)
                    ),
                    description = "控制操作类型"
                )
            ),
            hasResponse = false,
            group = CommandGroup.DEVICE_CONTROL
        ),
        CommandMeta(
            key = KEY_GH_SET_WORK_MODE_CMD,
            displayName = "设置工作模式",
            description = "设置芯片的工作模式",
            params = listOf(
                CommandParamDef(
                    name = "workMode",
                    label = "工作模式",
                    type = ParamType.U8,
                    options = WORK_MODE_OPTIONS,
                    description = "选择工作模式"
                )
            ),
            hasResponse = false,
            group = CommandGroup.DEVICE_CONTROL
        ),
        CommandMeta(
            key = KEY_DOWNLOAD_CONFIG,
            displayName = "下载配置",
            description = "执行配置下载流程",
            params = listOf(
                CommandParamDef(
                    name = "stage",
                    label = "下载阶段",
                    type = ParamType.U8,
                    options = DOWNLOAD_STAGE_OPTIONS,
                    description = "选择下载阶段"
                )
            ),
            hasResponse = false,
            group = CommandGroup.OTHER
        ),
        CommandMeta(
            key = KEY_GET_CHIP_LINK_STATUS,
            displayName = "获取芯片连接状态",
            description = "获取芯片的链路连接状态",
            params = listOf(
                CommandParamDef(
                    name = "linkType",
                    label = "链路类型",
                    type = ParamType.U8,
                    options = LINK_TYPE_OPTIONS,
                    description = "选择要查询的链路类型"
                )
            ),
            hasResponse = true,
            responseFormat = RET_GET_CHIP_LINK_STATUS,
            group = CommandGroup.VERSION_STATUS
        ),
        CommandMeta(
            key = KEY_GH_LOW_POWER_CMD,
            displayName = "低功耗命令",
            description = "控制芯片进入低功耗模式",
            params = listOf(
                CommandParamDef(
                    name = "targetFuncMode",
                    label = "目标功能模式",
                    type = ParamType.U32,
                    description = "目标功能模式值（十六进制）"
                ),
                CommandParamDef(
                    name = "ctrlType",
                    label = "控制类型",
                    type = ParamType.U8,
                    options = listOf(
                        CommandParamDef.OptionItem("进入低功耗", 0),
                        CommandParamDef.OptionItem("退出低功耗", 1)
                    ),
                    description = "低功耗控制类型"
                )
            ),
            hasResponse = false,
            group = CommandGroup.OTHER
        ),
        CommandMeta(
            key = KEY_GH_TIME_SET,
            displayName = "设置时间",
            description = "设置芯片时间（带时区偏移）",
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
                    description = "时区偏移小时数（东八区为8）"
                )
            ),
            hasResponse = false,
            group = CommandGroup.TIME
        ),
        CommandMeta(
            key = KEY_GH_TIMESTAMP_SET,
            displayName = "设置时间戳",
            description = "设置芯片时间戳",
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
        )
    )

    fun getCommandByKey(key: String): CommandMeta? {
        return ALL_COMMANDS.find { it.key == key }
    }

    fun getCommandsByGroup(group: CommandGroup): List<CommandMeta> {
        return ALL_COMMANDS.filter { it.group == group }
    }
}
