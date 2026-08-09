package com.ghealth.tools.ble.itlvc.core

/** ITLVC 会话配置。 */
data class ItlvcConfig(
    val frameTimeoutMs: Long = 100,
    val defaultResponseTimeoutMs: Long = 1000,
    val defaultRetryCount: Int = 0,
    val defaultRetryDelayMs: Long = 0,
    val passThroughMode: Boolean = false,
)
