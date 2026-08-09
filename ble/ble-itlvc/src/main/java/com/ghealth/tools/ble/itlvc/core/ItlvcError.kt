package com.ghealth.tools.ble.itlvc.core

/** ITLVC 分层错误类型。 */
sealed class ItlvcError : Exception() {

    sealed class FrameError : ItlvcError() {
        object CrcMismatch : FrameError()
        object InvalidHeader : FrameError()
        /** 保留：供绑定层区分版本不匹配；通用状态机以前缀失配报告 InvalidHeader。 */
        object InvalidVersion : FrameError()
        object LengthOverflow : FrameError()
        object TruncatedFrame : FrameError()
    }

    sealed class CommandError : ItlvcError() {
        data class Timeout(val attempts: Int) : CommandError()
        data class DeviceError(val code: Int) : CommandError()
        object Unsupported : CommandError()
        object Busy : CommandError()
        object InvalidParam : CommandError()
        object CrcFail : CommandError()
        object Unknown : CommandError()
    }

    data class ParseError(override val message: String) : ItlvcError()

    data class TransportError(override val message: String) : ItlvcError()
}
