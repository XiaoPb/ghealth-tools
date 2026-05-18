package com.ghealth.tools.ble.protocol.gh3036

import com.ghealth.tools.ble.protocol.rpccore.InvokeContext
import com.ghealth.tools.ble.protocol.rpccore.ParseResult
import com.ghealth.tools.ble.protocol.rpccore.RpcConfig
import com.ghealth.tools.ble.protocol.rpccore.RpcCore
import com.ghealth.tools.ble.protocol.rpccore.unpackU8Array
import timber.log.Timber

typealias FrameCallback = (GhFuncFrame) -> Unit

class Gh3036Executor(
    config: RpcConfig = RpcConfig()
) {
    private val core = RpcCore(config)
    private val frameDecoder = Gh3036FrameDecoder()
    private var frameCallback: FrameCallback? = null

    fun setSendFunction(func: (ByteArray) -> Result<Unit>) {
        core.setSendFunction(func)
    }

    fun registerFrameCallback(callback: FrameCallback) {
        frameCallback = callback
    }

    suspend fun registerGHandler(): Result<Unit> {
        val handler: (ByteArray, Int, InvokeContext) -> Unit = { data, _, _ ->
            handleGData(data)
        }
        return core.register(KEY_G, handler)
    }

    private fun handleGData(data: ByteArray) {
        val unpacked = unpackU8Array(data).toByteArray()
        val frames = frameDecoder.decode(unpacked)

        frames.forEach { frame ->
            Timber.d("GhFuncFrame: funcId=${frame.funcId}, frameCnt=${frame.frameCnt}, timestamp=${frame.timestamp}")
            frameCallback?.invoke(frame)
        }
    }

    suspend fun process(data: ByteArray): List<Result<ParseResult>> {
        return core.process(data)
    }

    suspend fun call(key: String, format: String, params: ByteArray): Result<ByteArray> {
        return core.call(key, format, params)
    }

    suspend fun send(key: String, format: String, params: ByteArray): Result<Unit> {
        return core.send(key, format, params)
    }

    fun publish(key: String, params: ByteArray): Result<Unit> {
        return core.publish(key, params)
    }

    suspend fun sall(key: String, format: String, params: ByteArray): Result<ByteArray> {
        return core.sall(key, format, params)
    }

    fun register(key: String, handler: (ByteArray, Int, InvokeContext) -> Unit): Result<Unit> {
        return core.register(key, handler)
    }

    fun reset() {
        core.reset()
        frameDecoder.reset()
    }

    fun resetFrameDecoder() {
        frameDecoder.reset()
    }
}