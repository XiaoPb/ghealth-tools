package com.ghealth.tools.feature.connection

import com.ghealth.tools.ble.protocol.gh3036.CommandGroup
import com.ghealth.tools.ble.protocol.gh3036.CommandMeta
import com.ghealth.tools.ble.protocol.gh3036.CommandPayloadBuilder
import com.ghealth.tools.ble.protocol.gh3036.Gh3036CommandMeta

/**
 * 命令面板命令源：屏蔽 GH3036 / GH3220 命令元数据与 payload 编码差异，
 * 供 [CommandPanelScreen] 按当前芯片选择统一消费。
 */
interface CommandSource {
    /** 按分组返回命令列表（面板按 [CommandGroup.entries] 渲染分组）。 */
    fun getCommandsByGroup(group: CommandGroup): List<CommandMeta>

    /** 按 key 查找命令元数据，未知名返回 null（用于 GH3036 专属 UI 门控等）。 */
    fun getCommandByKey(key: String): CommandMeta?

    /**
     * 返回芯片功能位表（面板 FuncModeBits 多选消费 [Gh3036CommandMeta.FuncModeBit]）。
     * 未知名芯片返回 null，调用方按现状降级处理。
     */
    fun getFuncModeBits(chipName: String): List<Gh3036CommandMeta.FuncModeBit>?

    /**
     * 按命令参数定义把表单值编码为请求载荷。
     *
     * @throws IllegalArgumentException 必填参数缺失、类型不符或编码失败
     */
    fun buildPayload(command: CommandMeta, paramValues: Map<String, Any>): ByteArray
}

/** GH3036 命令源：委托既有 [Gh3036CommandMeta] 与 [CommandPayloadBuilder]，行为与现状一致。 */
object Gh3036CommandSource : CommandSource {
    override fun getCommandsByGroup(group: CommandGroup): List<CommandMeta> =
        Gh3036CommandMeta.getCommandsByGroup(group)

    override fun getCommandByKey(key: String): CommandMeta? =
        Gh3036CommandMeta.getCommandByKey(key)

    override fun getFuncModeBits(chipName: String): List<Gh3036CommandMeta.FuncModeBit>? =
        Gh3036CommandMeta.getFuncModeBits(chipName)

    override fun buildPayload(command: CommandMeta, paramValues: Map<String, Any>): ByteArray =
        CommandPayloadBuilder.buildCommandParams(command, paramValues)
}

/**
 * GH3220 命令源：委托 [Gh3220CommandMeta]，payload 经 [Gh3220CommandPayloadBuilder] 编码。
 *
 * 执行决策：命令面板经 [buildPayload] → `sendRaw` 通路发送（响应原始字节以 hex 展示，
 * 与 GH3036 面板体验一致）；[Gh3220CommandMeta.executor] 保留为程序化调用入口（Task 4 测试覆盖）。
 * 两条路径共用同一批 ble-gh3220 编码器，字节一致性由对拍测试锁定。
 */
object Gh3220CommandSource : CommandSource {
    override fun getCommandsByGroup(group: CommandGroup): List<CommandMeta> =
        Gh3220CommandMeta.getCommandsByGroup(group).map { it.meta }

    override fun getCommandByKey(key: String): CommandMeta? =
        Gh3220CommandMeta.getCommandByKey(key)?.meta

    override fun getFuncModeBits(chipName: String): List<Gh3036CommandMeta.FuncModeBit>? =
        Gh3036CommandMeta.getFuncModeBits(chipName)

    override fun buildPayload(command: CommandMeta, paramValues: Map<String, Any>): ByteArray {
        val meta = Gh3220CommandMeta.getCommandByKey(command.key)
            ?: throw IllegalArgumentException("未知 GH3220 命令 key=${command.key}")
        val values = meta.params.map { def -> paramValues[def.name] ?: def.defaultValue }
        return Gh3220CommandPayloadBuilder.build(meta, values).getOrThrow()
    }
}
