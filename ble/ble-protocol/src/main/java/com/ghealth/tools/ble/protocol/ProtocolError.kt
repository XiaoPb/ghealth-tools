package com.ghealth.tools.ble.protocol

sealed class ProtocolError(message: String) : Exception(message) {
    data object CrcMismatch : ProtocolError("CRC mismatch")
    data object KeyOverMaxSize : ProtocolError("Key exceeds max size")
    data object FormatError : ProtocolError("Frame format error")
    data object FrameTooLarge : ProtocolError("Frame exceeds max size")
}
