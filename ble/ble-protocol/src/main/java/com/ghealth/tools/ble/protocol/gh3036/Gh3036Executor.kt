package com.ghealth.tools.ble.protocol.gh3036

import com.ghealth.tools.ble.protocol.rpccore.GHealthExecutor
import com.ghealth.tools.ble.protocol.rpccore.InvokeContext
import com.ghealth.tools.ble.protocol.rpccore.ParseResult
import com.ghealth.tools.ble.protocol.rpccore.RpcConfig
import com.ghealth.tools.ble.protocol.rpccore.RpcCore
import com.ghealth.tools.ble.protocol.rpccore.unpackU8Array
import timber.log.Timber

class Gh3036Executor(
    config: RpcConfig = RpcConfig()
) : GHealthExecutor {
    private val core = RpcCore(config)
    private val frameDecoder = Gh3036FrameDecoder()
    private var frameCallback: ((GhFuncFrame) -> Unit)? = null

    override fun setSendFunction(func: (ByteArray) -> Result<Unit>) {
        core.setSendFunction(func)
    }

    override fun registerFrameCallback(callback: (GhFuncFrame) -> Unit) {
        frameCallback = callback
    }

    override suspend fun registerGHandler(): Result<Unit> {
        val handler: (ByteArray, Int, InvokeContext) -> Unit = { data, _, _ ->
            handleGData(data)
        }
        return core.register(KEY_G, handler)
    }

    private fun handleGData(data: ByteArray) {
        val unpacked = unpackU8Array(data).toByteArray()
        val frames = frameDecoder.decode(unpacked)

        frames.forEach { frame ->
            if (debugLogEnabled) Timber.v("GhFuncFrame: funcId=${frame.funcId}, frameCnt=${frame.frameCnt}, timestamp=${frame.timestamp}")
            frameCallback?.invoke(frame)
        }
    }

    override suspend fun process(data: ByteArray): List<Result<ParseResult>> {
        return core.process(data)
    }

    override suspend fun call(key: String, format: String, params: ByteArray): Result<ByteArray> {
        return core.call(key, format, params)
    }

    override suspend fun send(key: String, format: String, params: ByteArray): Result<Unit> {
        return core.send(key, format, params)
    }

    override fun publish(key: String, params: ByteArray): Result<Unit> {
        return core.publish(key, params)
    }

    override suspend fun sall(key: String, format: String, params: ByteArray): Result<ByteArray> {
        return core.sall(key, format, params)
    }

    override fun register(key: String, handler: (ByteArray, Int, InvokeContext) -> Unit): Result<Unit> {
        return core.register(key, handler)
    }

    override fun reset() {
        core.reset()
        frameDecoder.reset()
    }

    override fun resetFrameDecoder() {
        frameDecoder.reset()
    }

    companion object {
        @Volatile var debugLogEnabled = false
    }
}