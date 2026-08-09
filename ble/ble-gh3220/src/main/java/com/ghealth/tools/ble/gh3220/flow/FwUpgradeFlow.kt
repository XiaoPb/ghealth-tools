package com.ghealth.tools.ble.gh3220.flow

import com.ghealth.tools.ble.gh3220.Gh3220Payload
import com.ghealth.tools.ble.gh3220.commands.Gh3220CommandSpecs
import com.ghealth.tools.ble.itlvc.core.CommandSpec
import com.ghealth.tools.ble.itlvc.core.ItlvcError
import com.ghealth.tools.ble.itlvc.core.ItlvcSession

/**
 * 0x0F 固件升级流程：版本查询 → 设置组包大小 → 分包传输。
 * 分包解释（假设，待真机验证）：固件按 blockSize 分块，块内按 ≤56 字节分包；
 * Total Len = 当前块字节数，Current Index = 包在块内字节偏移。
 * 多字节字段字节序按小端（文档 §3.11 未显式标注，与周边协议及计划约定一致）；C demo 无 0x0F 传输实现可对照。
 */
class FwUpgradeFlow(
    private val session: ItlvcSession,
    private val spec: CommandSpec = Gh3220CommandSpecs.FW_UPGRADE,
) {
    /** 0x01 获取固件版本：响应 [0x01][len][chars]。 */
    suspend fun getFirmwareVersion(): Result<String> =
        session.execute(spec, byteArrayOf(0x01)).mapCatching { resp ->
            if (resp.size < 2 || Gh3220Payload.readU8(resp, 0) != 0x01) {
                throw ItlvcError.ParseError("fw version response invalid")
            }
            val len = Gh3220Payload.readU8(resp, 1)
            if (resp.size < 2 + len) throw ItlvcError.ParseError("fw version data truncated")
            resp.copyOfRange(2, 2 + len).toString(Charsets.UTF_8)
        }

    /** 0x02 设置文件大小与组包大小：响应 [0x02][status]，1=成功 / 2=失败。 */
    suspend fun setTransferParams(fileSize: Long, blockSize: Int): Result<Unit> {
        require(blockSize in 1..0xFFFF) { "blockSize out of range: $blockSize" }
        val payload = byteArrayOf(0x02) + Gh3220Payload.u32le(fileSize) + Gh3220Payload.u16le(blockSize)
        return session.execute(spec, payload).mapCatching { resp ->
            checkStatus(resp, 0x02, "set transfer params")
        }
    }

    /**
     * 0x03 分包传输。分包规则：固件按 blockSize 分块，块内按 ≤56 字节分包；
     * `Total Len` = 当前块字节数，`Current Index` = 包在块内偏移。
     * [onProgress] 每包成功后回调已发送/总字节数；任一包失败立即返回失败。
     */
    suspend fun transferFirmware(
        firmware: ByteArray,
        blockSize: Int,
        onProgress: suspend (sentBytes: Int, totalBytes: Int) -> Unit = { _, _ -> },
    ): Result<Unit> {
        require(firmware.isNotEmpty()) { "firmware empty" }
        require(blockSize in 1..0xFFFF) { "blockSize out of range: $blockSize" }
        var sent = 0
        var blockStart = 0
        while (blockStart < firmware.size) {
            val blockEnd = minOf(firmware.size, blockStart + blockSize)
            val blockTotal = blockEnd - blockStart
            var indexInBlock = 0
            while (indexInBlock < blockTotal) {
                val chunkLen = minOf(56, blockTotal - indexInBlock)
                val data = firmware.copyOfRange(blockStart + indexInBlock, blockStart + indexInBlock + chunkLen)
                val payload = byteArrayOf(0x03) +
                    Gh3220Payload.u16le(blockTotal) +
                    Gh3220Payload.u16le(indexInBlock) +
                    byteArrayOf(chunkLen.toByte()) + data
                val result = session.execute(spec, payload).mapCatching { resp ->
                    checkTransferResponse(resp, blockTotal, indexInBlock, chunkLen)
                }
                if (result.isFailure) return result
                sent += chunkLen
                onProgress(sent, firmware.size)
                indexInBlock += chunkLen
            }
            blockStart += blockSize
        }
        return Result.success(Unit)
    }

    private fun checkStatus(resp: ByteArray, sub: Int, label: String) {
        if (resp.size < 2 || Gh3220Payload.readU8(resp, 0) != sub) {
            throw ItlvcError.ParseError("$label: response invalid")
        }
        when (Gh3220Payload.readU8(resp, 1)) {
            1 -> Unit
            2 -> throw ItlvcError.CommandError.DeviceError(2)
            else -> throw ItlvcError.ParseError("$label: unknown status ${Gh3220Payload.readU8(resp, 1)}")
        }
    }

    private fun checkTransferResponse(resp: ByteArray, total: Int, index: Int, len: Int) {
        if (resp.size < 7 || Gh3220Payload.readU8(resp, 0) != 0x03) {
            throw ItlvcError.ParseError("fw transfer response invalid")
        }
        when (Gh3220Payload.readU8(resp, 1)) {
            1 -> Unit
            2 -> throw ItlvcError.CommandError.DeviceError(2)
            else -> throw ItlvcError.ParseError("fw transfer: unknown status ${Gh3220Payload.readU8(resp, 1)}")
        }
        val echoTotal = Gh3220Payload.readU16le(resp, 2)
        val echoIndex = Gh3220Payload.readU16le(resp, 4)
        val echoLen = Gh3220Payload.readU8(resp, 6)
        if (echoTotal != total || echoIndex != index || echoLen != len) {
            throw ItlvcError.ParseError(
                "fw transfer echo mismatch: total=$echoTotal/$total index=$echoIndex/$index len=$echoLen/$len",
            )
        }
    }
}
