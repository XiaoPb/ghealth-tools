package com.ghealth.tools.ble.connection

/**
 * GH3220 配置下发薄桥接（internal 便于 JVM 单测）：
 * 守卫 nullable [Gh3220ItlvcBridge]，委托 [Gh3220ItlvcBridge.client] 的 0x1F 分包下发流程。
 */
internal suspend fun Gh3220ItlvcBridge?.sendDriverConfigOrFailure(
    address: String,
    data: ByteArray,
    save: Boolean,
    onProgress: suspend (sentBytes: Int, totalBytes: Int) -> Unit,
): Result<Unit> = when (this) {
    null -> Result.failure(Exception("GH3220 bridge not available for $address"))
    else -> client.driverConfig().sendDriverConfig(data, save = save, onProgress = onProgress)
}
