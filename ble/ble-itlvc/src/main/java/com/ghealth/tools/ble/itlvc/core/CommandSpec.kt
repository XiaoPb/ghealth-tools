package com.ghealth.tools.ble.itlvc.core

/**
 * 命令规格：请求 T 字段、超时、重试与透传模式权限。
 *
 * @param type 帧 T 字段（请求与响应的匹配键）
 * @param timeoutMs 响应超时
 * @param retryCount 超时后的重试次数
 * @param retryDelayMs 重试前等待
 * @param allowedInPassThrough 透传模式下是否允许（文档 §4.3.5 白名单）
 */
data class CommandSpec(
    val type: ByteArray,
    val timeoutMs: Long = 1000,
    val retryCount: Int = 0,
    val retryDelayMs: Long = 0,
    val allowedInPassThrough: Boolean = false,
)

