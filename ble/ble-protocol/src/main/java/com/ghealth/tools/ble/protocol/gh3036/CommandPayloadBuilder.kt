package com.ghealth.tools.ble.protocol.gh3036

/**
 * GH3x 命令字节载荷构建器。
 *
 * 从用户输入的参数值（[paramValues]）按 [CommandMeta] 定义拼装原始请求字节，
 * 是不依赖 Android/Compose 的纯逻辑，便于单元测试。
 *
 * 兼容两种数值来源：
 * - 有符号 [Number]（Byte/Short/Int/Long）：来自下拉选项、时间戳等；
 * - Kotlin 无符号类型（[UByte]/[UShort]/[UInt]/[ULong]）：来自十六进制文本输入。
 *
 * 无符号类型不是 java.lang.Number 的子类，故不能用 `as Number` 强转，
 * 否则触发 ClassCastException（曾导致命令面板寄存器读取闪退）。
 */
object CommandPayloadBuilder {

    /**
     * 按 [command] 的参数定义把 [paramValues] 序列化为小端字节流。
     *
     * @throws IllegalArgumentException 必填参数缺失或值类型不支持
     */
    fun buildCommandParams(command: CommandMeta, paramValues: Map<String, Any>): ByteArray {
        val bytes = mutableListOf<Byte>()
        command.params.forEach { param ->
            val value = paramValues[param.name] ?: param.defaultValue
                ?: throw IllegalArgumentException("参数 ${param.label} 未设置")
            when (param.type) {
                ParamType.U8, ParamType.I8 -> bytes.add(toByte(value))
                ParamType.U16, ParamType.I16 -> {
                    val v = toShort(value)
                    bytes.add((v.toInt() and 0xFF).toByte())
                    bytes.add((v.toInt() shr 8 and 0xFF).toByte())
                }
                ParamType.U32, ParamType.I32, ParamType.TIMESTAMP, ParamType.FUNC_MODE_BITS -> {
                    val v = toInt(value)
                    bytes.add((v and 0xFF).toByte())
                    bytes.add((v shr 8 and 0xFF).toByte())
                    bytes.add((v shr 16 and 0xFF).toByte())
                    bytes.add((v shr 24 and 0xFF).toByte())
                }
                ParamType.U16_ARRAY -> {
                    val arr = value as ShortArray
                    arr.forEach { v ->
                        bytes.add((v.toInt() and 0xFF).toByte())
                        bytes.add((v.toInt() shr 8 and 0xFF).toByte())
                    }
                }
                ParamType.U8_ARRAY -> {
                    val arr = value as ByteArray
                    bytes.addAll(arr.toList())
                }
            }
        }
        return bytes.toByteArray()
    }

    /** 多寄存器写入：从 (地址, 值) 十六进制字符串对拼装载荷。 */
    fun buildMultiRegWriteParams(pairs: List<Pair<String, String>>): ByteArray {
        val shorts = mutableListOf<Short>()
        pairs.forEach { (addr, value) ->
            shorts.add(addr.trim().toInt(16).toShort())
            shorts.add(value.trim().toInt(16).toShort())
        }
        return buildCommandParams(
            Gh3036CommandMeta.getCommandByKey(KEY_GH3X_REGS_WRITE_CMD)!!,
            mapOf("regs" to shorts.toShortArray())
        )
    }

    /** 多寄存器读取：从起始地址(十六进制字符串)与读取个数拼装载荷。 */
    fun buildMultiRegReadParams(addr: String, count: String): ByteArray {
        return buildCommandParams(
            Gh3036CommandMeta.getCommandByKey(KEY_GH3X_REGS_READ_CMD)!!,
            mapOf(
                "regAddr" to addr.trim().toInt(16).toShort().toUShort(),
                "readLen" to count.trim().toInt()
            )
        )
    }

    private fun toByte(value: Any): Byte = when (value) {
        is Number -> value.toByte()
        is UByte -> value.toByte()
        is UShort -> value.toByte()
        is UInt -> value.toByte()
        is ULong -> value.toByte()
        else -> throw IllegalArgumentException("不支持的参数值类型: ${value::class}")
    }

    private fun toShort(value: Any): Short = when (value) {
        is Number -> value.toShort()
        is UByte -> value.toShort()
        is UShort -> value.toShort()
        is UInt -> value.toShort()
        is ULong -> value.toShort()
        else -> throw IllegalArgumentException("不支持的参数值类型: ${value::class}")
    }

    private fun toInt(value: Any): Int = when (value) {
        is Number -> value.toInt()
        is UByte -> value.toInt()
        is UShort -> value.toInt()
        is UInt -> value.toInt()
        is ULong -> value.toInt()
        else -> throw IllegalArgumentException("不支持的参数值类型: ${value::class}")
    }
}
