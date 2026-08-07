package com.ghealth.tools.feature.connection

import com.ghealth.tools.ble.protocol.rpccore.ProtocolError

internal fun userFriendlyCommandError(error: Throwable, commandKey: String): String = when (error) {
    is ProtocolError.Timeout -> "命令 $commandKey 执行超时：设备未响应，可能不支持该命令或协议"
    is ProtocolError.CommandNotFound -> "命令 $commandKey 执行失败：设备不支持该命令"
    is ProtocolError.ChannelClosed -> "命令 $commandKey 执行失败：连接通道已关闭，请检查设备连接"
    is ProtocolError.CrcMismatch -> "命令 $commandKey 执行失败：数据校验失败（CRC）"
    is ProtocolError.LoseFrame -> "命令 $commandKey 执行失败：数据帧丢失"
    is ProtocolError.UnpackageError, is ProtocolError.FormatError -> "命令 $commandKey 执行失败：数据格式错误"
    else -> error.message?.let { "命令 $commandKey 执行失败：$it" } ?: "命令 $commandKey 执行失败"
}
