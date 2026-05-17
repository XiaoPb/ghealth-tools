package com.ghealth.tools.ble.protocol.rpccore

@Deprecated(
    message = "Replaced by RpcCore. Use RpcCore.process() for decode and RpcCore.call/send/sall/publish for encode.",
    replaceWith = ReplaceWith("RpcCore", "com.ghealth.tools.ble.protocol.rpccore.RpcCore")
)
interface RpcParser {
    fun encode(key: String, param: ByteArray = ByteArray(0), secure: Boolean = false): ByteArray
    fun decode(raw: ByteArray): List<Result<ParseResult>>
    fun reset()
}
