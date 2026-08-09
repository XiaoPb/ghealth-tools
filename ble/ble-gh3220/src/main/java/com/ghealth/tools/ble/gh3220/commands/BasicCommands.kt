package com.ghealth.tools.ble.gh3220.commands

import com.ghealth.tools.ble.gh3220.Gh3220Payload
import com.ghealth.tools.ble.itlvc.core.ItlvcError

/** 基础命令（0x05/0x19/0x1A/0x0C/0x17/0x18/0x20/0x2E）payload 编解码。 */
object BasicCommands {

    /** 0x05 通讯包测试：[len][data]，设备回显同构。 */
    fun packageTest(data: ByteArray): ByteArray = byteArrayOf(data.size.toByte()) + data

    fun parsePackageTest(payload: ByteArray): Result<ByteArray> {
        if (payload.size < 1) return Result.failure(ItlvcError.ParseError("package test payload too short"))
        val len = Gh3220Payload.readU8(payload, 0)
        if (payload.size < 1 + len) return Result.failure(ItlvcError.ParseError("package test data truncated"))
        return Result.success(payload.copyOfRange(1, 1 + len))
    }

    /** 0x19 获取版本：[verType]；响应 [verType][dataLen][data...]。 */
    data class VersionInfo(val versionType: Int, val text: String)

    fun getVersion(versionType: Int): ByteArray = Gh3220Payload.u8(versionType)

    fun parseVersion(payload: ByteArray): Result<VersionInfo> {
        if (payload.size < 2) return Result.failure(ItlvcError.ParseError("version payload too short"))
        val type = Gh3220Payload.readU8(payload, 0)
        val len = Gh3220Payload.readU8(payload, 1)
        if (payload.size < 2 + len) return Result.failure(ItlvcError.ParseError("version data truncated"))
        return Result.success(VersionInfo(type, payload.copyOfRange(2, 2 + len).toString(Charsets.UTF_8)))
    }

    /** 0x1A 查询连接状态：无 payload；响应 1 字节（0=已连接 / 1=未连接）。 */
    fun getConnStatus(): ByteArray = ByteArray(0)

    /** 0x0C 启动 HBD：[onOff][mode][function u32le]。 */
    fun startHbd(on: Boolean, mode: Int, function: Long): ByteArray =
        Gh3220Payload.u8(if (on) 0 else 1) + Gh3220Payload.u8(mode) + Gh3220Payload.u32le(function)

    /** 0x17 Cardiff 复位：[resetType]。 */
    fun chipCtrl(resetType: Int): ByteArray = Gh3220Payload.u8(resetType)

    /** 0x18 电流校准：[mode]。 */
    fun calibrateCurrent(mode: Int): ByteArray = Gh3220Payload.u8(mode)

    /** 0x20 应用模块命令：[cmd]。 */
    fun appModule(cmd: Int): ByteArray = Gh3220Payload.u8(cmd)

    /** 0x2E 切换 Cardiff 芯片：[cmd]。 */
    fun switchChip(cmd: Int): ByteArray = Gh3220Payload.u8(cmd)

    /** 通用 1 字节状态响应解析（0=成功 / 1=失败）。 */
    fun parseStatus(payload: ByteArray, cmdName: String): Result<Int> {
        if (payload.size < 1) return Result.failure(ItlvcError.ParseError("$cmdName: status payload too short"))
        return Result.success(Gh3220Payload.readU8(payload, 0))
    }
}
