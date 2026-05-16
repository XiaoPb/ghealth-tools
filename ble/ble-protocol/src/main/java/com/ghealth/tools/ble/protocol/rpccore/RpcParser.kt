package com.ghealth.tools.ble.protocol.rpccore

interface RpcParser {
    fun encode(key: String, param: ByteArray = ByteArray(0), secure: Boolean = false): ByteArray
    fun decode(raw: ByteArray): List<Result<ParseResult>>
    fun reset()
}
