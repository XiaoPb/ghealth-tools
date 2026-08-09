package com.ghealth.tools.ble.gh3220.flow

import com.ghealth.tools.ble.gh3220.Gh3220Payload
import com.ghealth.tools.ble.gh3220.commands.Gh3220CommandSpecs
import com.ghealth.tools.ble.itlvc.core.CommandSpec
import com.ghealth.tools.ble.itlvc.core.ItlvcError
import com.ghealth.tools.ble.itlvc.core.ItlvcSession

/** 0x1F 驱动配置下发流程（文档 §3.27）。 */
class DriverConfigFlow(
    private val session: ItlvcSession,
    private val spec: CommandSpec = Gh3220CommandSpecs.DRV_CFG,
) {

    /**
     * 下发驱动配置。数据按 [chunkSize]（≤230）分包：
     * 每包 `[pos u16le][handleFlag][data]`，handleFlag：0=非最后 / 1=最后不保存 / 2=最后保存。
     * 响应 `[0x00]` 为成功；任一包失败立即返回失败。
     */
    suspend fun sendDriverConfig(
        data: ByteArray,
        save: Boolean = true,
        chunkSize: Int = 230,
        onProgress: suspend (sentBytes: Int, totalBytes: Int) -> Unit = { _, _ -> },
    ): Result<Unit> {
        require(data.isNotEmpty()) { "config empty" }
        require(chunkSize in 1..230) { "chunkSize must be 1..230, got $chunkSize" }
        var sent = 0
        var offset = 0
        while (offset < data.size) {
            val len = minOf(chunkSize, data.size - offset)
            val isLast = offset + len >= data.size
            val handleFlag = when {
                !isLast -> 0
                save -> 2
                else -> 1
            }
            val payload = Gh3220Payload.u16le(offset) +
                byteArrayOf(handleFlag.toByte()) +
                data.copyOfRange(offset, offset + len)
            val result = session.execute(spec, payload).mapCatching { resp ->
                if (resp.isEmpty()) throw ItlvcError.ParseError("drv cfg response empty")
                when (Gh3220Payload.readU8(resp, 0)) {
                    0 -> Unit
                    1 -> throw ItlvcError.CommandError.DeviceError(1)
                    else -> throw ItlvcError.ParseError("drv cfg unknown status ${Gh3220Payload.readU8(resp, 0)}")
                }
            }
            if (result.isFailure) return result
            sent += len
            onProgress(sent, data.size)
            offset += len
        }
        return Result.success(Unit)
    }
}
