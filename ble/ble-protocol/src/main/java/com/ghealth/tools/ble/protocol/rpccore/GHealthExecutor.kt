package com.ghealth.tools.ble.protocol.rpccore

import com.ghealth.tools.ble.protocol.gh3036.GhFuncFrame

interface GHealthExecutor {
    fun setSendFunction(func: (ByteArray) -> Result<Unit>)
    fun registerFrameCallback(callback: (GhFuncFrame) -> Unit)
    suspend fun registerGHandler(): Result<Unit>
    suspend fun process(data: ByteArray): List<Result<ParseResult>>
    suspend fun call(key: String, format: String, params: ByteArray): Result<ByteArray>
    suspend fun send(key: String, format: String, params: ByteArray): Result<Unit>
    fun publish(key: String, params: ByteArray): Result<Unit>
    suspend fun sall(key: String, format: String, params: ByteArray): Result<ByteArray>
    fun register(key: String, handler: (ByteArray, Int, InvokeContext) -> Unit): Result<Unit>
    fun reset()
    fun resetFrameDecoder()
}
