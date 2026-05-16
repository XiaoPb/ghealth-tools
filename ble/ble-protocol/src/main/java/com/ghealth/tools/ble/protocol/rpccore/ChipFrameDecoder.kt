package com.ghealth.tools.ble.protocol.rpccore

interface ChipFrameDecoder<F> {
    fun decode(param: ByteArray): List<F>
    fun reset()
}
