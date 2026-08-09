package com.ghealth.tools.ble.gh3220.event

import com.ghealth.tools.ble.gh3220.Gh3220Payload
import com.ghealth.tools.ble.itlvc.core.ItlvcError

/** 0x16 Cardiff 事件上报。 */
data class Gh3220CardiffEvent(val event: Int, val eventReportId: Int)

/** 0x14 下位机设备事件。 */
data class Gh3220DeviceEvent(val eventId: Int, val info: Long)

/** 0x0D 电流与电池信息。 */
data class Gh3220CurrentBattery(
    val cardiffCurrent: Int,
    val batteryPercent: Int,
    val txCurrent: Int,
    val bleSendPackageCount: Int,
)

/** 0x21 从机 Log。 */
data class Gh3220SlaveLog(val text: String)

/** 上报 payload 解码器。所有方法返回 Result，非法结构返回 [ItlvcError.ParseError]。 */
object ReportDecoder {

    /** 0x16：[eventH][eventL][eventReportId]。 */
    fun decodeCardiffEvent(payload: ByteArray): Result<Gh3220CardiffEvent> {
        if (payload.size < 3) return Result.failure(ItlvcError.ParseError("0x16: payload too short"))
        val event = (Gh3220Payload.readU8(payload, 0) shl 8) or Gh3220Payload.readU8(payload, 1)
        return Result.success(Gh3220CardiffEvent(event, Gh3220Payload.readU8(payload, 2)))
    }

    /** 0x14：[eventId][info u32le]。 */
    fun decodeDeviceEvent(payload: ByteArray): Result<Gh3220DeviceEvent> {
        if (payload.size < 5) return Result.failure(ItlvcError.ParseError("0x14: payload too short"))
        return Result.success(Gh3220DeviceEvent(Gh3220Payload.readU8(payload, 0), Gh3220Payload.readU32le(payload, 1)))
    }

    /** 0x0D：7 字节，多字节字段小端。 */
    fun decodeCurrentBattery(payload: ByteArray): Result<Gh3220CurrentBattery> {
        if (payload.size < 7) return Result.failure(ItlvcError.ParseError("0x0D: payload too short"))
        return Result.success(
            Gh3220CurrentBattery(
                cardiffCurrent = Gh3220Payload.readU16le(payload, 0),
                batteryPercent = Gh3220Payload.readU8(payload, 2),
                txCurrent = Gh3220Payload.readU16le(payload, 3),
                bleSendPackageCount = Gh3220Payload.readU16le(payload, 5),
            ),
        )
    }

    /** 0x21：Log 字符串（UTF-8）。 */
    fun decodeSlaveLog(payload: ByteArray): Result<Gh3220SlaveLog> =
        Result.success(Gh3220SlaveLog(payload.toString(Charsets.UTF_8)))
}