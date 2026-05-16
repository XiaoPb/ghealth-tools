package com.ghealth.tools.ble.protocol.rpccore

sealed class ProtocolError(message: String) : Exception(message) {
    data object CrcMismatch : ProtocolError("CRC mismatch")
    data object KeyOverMaxSize : ProtocolError("Key exceeds max size")
    data object FormatError : ProtocolError("Frame format error")
    data object FrameTooLarge : ProtocolError("Frame exceeds max size")
    data object ParamTooMuch : ProtocolError("Too many parameters")
    data object UnpackageError : ProtocolError("Unpackage error")
    data object Timeout : ProtocolError("Operation timeout")
    data object ChannelClosed : ProtocolError("Channel closed")
    data object CommandNotFound : ProtocolError("Command not found")
    data object LoseFrame : ProtocolError("Frame lost")
    data object NotUnderInvoke : ProtocolError("Not under invoke context")
}
