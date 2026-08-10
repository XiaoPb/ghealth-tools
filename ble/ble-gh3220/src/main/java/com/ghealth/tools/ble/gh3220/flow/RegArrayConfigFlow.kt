package com.ghealth.tools.ble.gh3220.flow

import com.ghealth.tools.ble.gh3220.Gh3220Payload
import com.ghealth.tools.ble.gh3220.commands.Gh3220CommandSpecs
import com.ghealth.tools.ble.itlvc.core.CommandSpec
import com.ghealth.tools.ble.itlvc.core.ItlvcError
import com.ghealth.tools.ble.itlvc.core.ItlvcSession

/** 0xA1 写寄存器数组配置下发（文档 §3.38）：N×4 字节块 [addrHi,addrLo,valHi,valLo] 分帧写入。 */
class RegArrayConfigFlow(
    private val session: ItlvcSession,
    private val spec: CommandSpec = Gh3220CommandSpecs.REG_ARRAY_WRITE,
) {

    /**
     * 下发寄存器数组配置。数据按 [blocksPerFrame]（≤59 block = 236B value，整帧 241B = 2 ID + 1 T + 1 L + 236 value + 1 CRC，
     * 需 ATT payload ≥ 241 即 MTU ≥ 244、连接层协商 247（payload 244B，余量 3B）；若 MTU 回退到 240（payload 237B）则写失败，属已知假设。约束从 maxValueLen=238 变为 MTU（codec 上限仍存在））分帧：
     * 每帧 payload = N×4 块，响应 1B（0=成功 / 1=失败）；任一帧失败立即返回失败。
     * C 端 `GH3X2X_LoadNewRegConfigArr` 逐条写寄存器（非全量替换），多帧分批安全。
     * 注意：0xA1 无保存标志（0x1F handleFlag=2 才保存到 flash）。
     */
    suspend fun sendRegArrayConfig(
        data: ByteArray,
        blocksPerFrame: Int = 59,
        onProgress: suspend (sentBytes: Int, totalBytes: Int) -> Unit = { _, _ -> },
    ): Result<Unit> {
        require(data.isNotEmpty()) { "config empty" }
        require(data.size % 4 == 0) { "config length must be a multiple of 4, got ${data.size}" }
        require(blocksPerFrame in 1..59) { "blocksPerFrame must be 1..59, got $blocksPerFrame" }
        var sent = 0
        var offset = 0
        while (offset < data.size) {
            val len = minOf(blocksPerFrame * 4, data.size - offset)
            val chunk = data.copyOfRange(offset, offset + len)
            val result = session.execute(spec, chunk).mapCatching { resp ->
                if (resp.isEmpty()) throw ItlvcError.ParseError("reg array response empty")
                val status = Gh3220Payload.readU8(resp, 0)
                when (status) {
                    0 -> Unit
                    1 -> throw ItlvcError.CommandError.DeviceError(1)
                    else -> throw ItlvcError.ParseError("reg array unknown status $status")
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
