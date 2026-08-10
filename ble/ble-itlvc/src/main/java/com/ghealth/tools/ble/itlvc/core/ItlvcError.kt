package com.ghealth.tools.ble.itlvc.core

/** ITLVC 分层错误类型。 */
sealed class ItlvcError : Exception() {

    sealed class FrameError : ItlvcError() {
        object CrcMismatch : FrameError()
        object InvalidHeader : FrameError()
        /** 保留：供绑定层区分版本不匹配；当前接收状态机对头部失配静默重同步，不产出该错误。 */
        object InvalidVersion : FrameError()
        object LengthOverflow : FrameError()
        object TruncatedFrame : FrameError()
    }

    sealed class CommandError : ItlvcError() {
        /** 响应超时（attempts 为总发送次数，即重试次数 + 1）。 */
        data class Timeout(val attempts: Int) : CommandError() {
            override val message: String get() = "响应超时(attempts=$attempts)"
        }

        data class DeviceError(val code: Int) : CommandError() {
            override val message: String get() = "设备返回错误(code=$code)"
        }

        object Unsupported : CommandError()
        object Busy : CommandError()
        object InvalidParam : CommandError()
        object CrcFail : CommandError()
        object Unknown : CommandError()
    }

    data class ParseError(override val message: String) : ItlvcError()

    data class TransportError(override val message: String) : ItlvcError()
}
