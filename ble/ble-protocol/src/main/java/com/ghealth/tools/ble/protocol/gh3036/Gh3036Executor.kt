package com.ghealth.tools.ble.protocol.gh3036

import com.ghealth.tools.ble.protocol.rpccore.FrameBuilder
import com.ghealth.tools.ble.protocol.rpccore.FrameParser
import com.ghealth.tools.ble.protocol.rpccore.ParseResult
import com.ghealth.tools.ble.protocol.rpccore.ProtocolError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

typealias FrameCallback = (GhFuncFrame) -> Unit
typealias SendFunction = (ByteArray) -> Result<Unit>

data class RpcConfig(
    val timeoutMs: Long = 3000,
    val retryCount: Byte = 3,
    val retryDelayMs: Long = 3000,
    val frameSize: Int = 240
)

class Gh3036Executor(
    private val config: RpcConfig = RpcConfig()
) {
    private val frameParser = FrameParser()
    private val frameBuilder = FrameBuilder()
    private val frameDecoder = Gh3036FrameDecoder()

    private val staticHandlers = ConcurrentHashMap<String, (ByteArray, Int, InvokeContext) -> Unit>()
    private val pendingCalls = ConcurrentHashMap<String, PendingCall>()
    private var sendFunction: SendFunction? = null
    private var frameCallback: FrameCallback? = null
    private var invokeIndex: Byte = 1

    private val multiFrameBuffer = MultiFrameBuffer()

    fun setSendFunction(func: SendFunction) {
        sendFunction = func
    }

    fun registerFrameCallback(callback: FrameCallback) {
        frameCallback = callback
    }

    suspend fun registerGHandler(): Result<Unit> {
        val handler: (ByteArray, Int, InvokeContext) -> Unit = { data, _, _ ->
            handleGData(data)
        }
        staticHandlers[KEY_G] = handler
        return Result.success(Unit)
    }

    private fun handleGData(data: ByteArray) {
        val unpacked = unpackU8Array(data)
        val frames = frameDecoder.decodeFrames(unpacked)

        frames.forEach { frame ->
            frameCallback?.invoke(frame)
        }
    }

    suspend fun process(data: ByteArray): List<Result<ParseResult>> {
        val results = frameParser.process(data)

        results.forEach { result ->
            if (result.isSuccess) {
                handleParseResult(result.getOrThrow())
            }
        }

        return results
    }

    private fun handleParseResult(result: ParseResult) {
        val key = result.key
        val isSecure = result.isSecure
        val isFin = result.isFin
        val invokeIdx = result.invokeIdx
        val frameIdx = result.frameIdx
        val param = result.param

        if (isSecure) {
            handleSecureFrame(key, invokeIdx, frameIdx, isFin, param)
        } else {
            handleUnsecureFrame(key, frameIdx, isFin, param)
        }
    }

    private fun handleSecureFrame(
        key: String,
        invokeIdx: Byte,
        frameIdx: Byte,
        isFin: Boolean,
        data: ByteArray
    ) {
        val handler = staticHandlers[key]
        if (handler != null) {
            val context = InvokeContext(topic = key)
            context.isFin = isFin
            context.frameIdx = frameIdx
            context.invokeIdx = invokeIdx

            handler(data, data.size, context)

            val response = context.getResponse()
            if (response.isNotEmpty()) {
                val responseFrame = byteArrayOf(1) + response
                val frames = frameBuilder.build(
                    key = key,
                    param = responseFrame,
                    secure = true,
                    invokeIdx = invokeIdx
                )
                sendFunction?.invoke(frames)
            }
        } else {
            val pending = pendingCalls.remove(key)
            if (pending != null && data.size >= 2) {
                val msgType = data[0]
                when (msgType.toInt()) {
                    0 -> {
                        val ackFrameIdx = data.getOrElse(1) { 0 }
                    }
                    1 -> {
                        val responseData = if (data.size > 2) {
                            data.sliceArray(2 until data.size)
                        } else {
                            ByteArray(0)
                        }
                        pending.onResponse(Result.success(responseData))
                    }
                    2, 3 -> {
                        pending.onResponse(Result.failure(ProtocolError.CommandNotFound))
                    }
                }
            }
        }
    }

    private fun handleUnsecureFrame(
        key: String,
        frameIdx: Byte,
        isFin: Boolean,
        data: ByteArray
    ) {
        val effectiveFrameIdx = if (frameIdx == 255.toByte()) 0 else frameIdx

        try {
            multiFrameBuffer.addFrame(0, effectiveFrameIdx, data)

            if (!multiFrameBuffer.isComplete(isFin)) {
                return
            }
        } catch (e: Exception) {
            multiFrameBuffer.clear()
            return
        }

        val allData = multiFrameBuffer.getAllData()
        multiFrameBuffer.clear()

        val handler = staticHandlers[key]
        if (handler != null) {
            val context = InvokeContext(topic = key)
            context.isFin = isFin
            context.frameIdx = frameIdx

            handler(allData, allData.size, context)

            val response = context.getResponse()
            if (response.isNotEmpty()) {
                publish(key, response)
            }
        } else {
            val pending = pendingCalls.remove(key)
            pending?.onResponse(Result.success(allData))
        }
    }

    suspend fun call(key: String, format: String, params: ByteArray): Result<ByteArray> {
        return withContext(Dispatchers.IO) {
            val invokeIdx = nextInvokeIndex()

            val frames = frameBuilder.build(
                key = key,
                param = params,
                secure = false,
                invokeIdx = invokeIdx
            )

            val pending = PendingCall(invokeIdx, key)
            pendingCalls[key] = pending

            val sendFn = sendFunction
            if (sendFn == null) {
                pendingCalls.remove(key)
                return@withContext Result.failure(ProtocolError.ChannelClosed)
            }

            val sendResult = sendFn.invoke(frames)
            if (sendResult.isFailure) {
                pendingCalls.remove(key)
                return@withContext Result.failure(ProtocolError.ChannelClosed)
            }

            pending.waitForResponse(config.timeoutMs)
        }
    }

    suspend fun send(key: String, format: String, params: ByteArray): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val invokeIdx = nextInvokeIndex()

            val frames = frameBuilder.build(
                key = key,
                param = params,
                secure = true,
                invokeIdx = invokeIdx
            )

            val pending = PendingCall(invokeIdx, key)
            pendingCalls[key] = pending

            val sendFn = sendFunction
            if (sendFn == null) {
                pendingCalls.remove(key)
                return@withContext Result.failure(ProtocolError.ChannelClosed)
            }

            val sendResult = sendFn.invoke(frames)
            if (sendResult.isFailure) {
                pendingCalls.remove(key)
                return@withContext Result.failure(ProtocolError.ChannelClosed)
            }

            pending.waitForResponse(config.timeoutMs).map { }
        }
    }

    fun publish(key: String, params: ByteArray): Result<Unit> {
        val frames = frameBuilder.build(
            key = key,
            param = params,
            secure = false
        )

        val sendFn = sendFunction ?: return Result.failure(ProtocolError.ChannelClosed)
        return sendFn.invoke(frames)
    }

    suspend fun sall(key: String, format: String, params: ByteArray): Result<ByteArray> {
        return withContext(Dispatchers.IO) {
            val invokeIdx = nextInvokeIndex()

            val frames = frameBuilder.build(
                key = key,
                param = params,
                secure = true,
                invokeIdx = invokeIdx
            )

            val pending = PendingCall(invokeIdx, key)
            pendingCalls[key] = pending

            val sendFn = sendFunction
            if (sendFn == null) {
                pendingCalls.remove(key)
                return@withContext Result.failure(ProtocolError.ChannelClosed)
            }

            val sendResult = sendFn.invoke(frames)
            if (sendResult.isFailure) {
                pendingCalls.remove(key)
                return@withContext Result.failure(ProtocolError.ChannelClosed)
            }

            pending.waitForResponse(config.timeoutMs)
        }
    }

    fun register(key: String, handler: (ByteArray, Int, InvokeContext) -> Unit): Result<Unit> {
        staticHandlers[key] = handler
        return Result.success(Unit)
    }

    private fun nextInvokeIndex(): Byte {
        synchronized(this) {
            invokeIdx = (invokeIdx + 1).toByte()
            if (invokeIdx == 0.toByte()) {
                invokeIdx = 1
            }
            return invokeIdx
        }
    }

    fun reset() {
        frameParser.reset()
        staticHandlers.clear()
        pendingCalls.clear()
        multiFrameBuffer.clear()
    }
}

class InvokeContext(val topic: String) {
    var isSecure: Boolean = false
    var isFin: Boolean = true
    var invokeIdx: Byte = 0
    var frameIdx: Byte = 0

    private val responseData = mutableListOf<Byte>()

    fun setResponse(data: ByteArray) {
        responseData.clear()
        responseData.addAll(data.toList())
    }

    fun getResponse(): ByteArray {
        return responseData.toByteArray()
    }
}

private class PendingCall(
    val invokeIdx: Byte,
    val key: String
) {
    private var response: Result<ByteArray>? = null
    private val lock = Object()

    fun onResponse(result: Result<ByteArray>) {
        synchronized(lock) {
            response = result
            lock.notifyAll()
        }
    }

    fun waitForResponse(timeoutMs: Long): Result<ByteArray> {
        synchronized(lock) {
            if (response != null) {
                return response!!
            }

            lock.wait(timeoutMs)

            return response ?: Result.failure(ProtocolError.Timeout)
        }
    }
}

private class MultiFrameBuffer {
    private val frames = mutableListOf<FrameBuffer>()
    private var expectedFrameIdx: Byte = 0

    fun addFrame(invokeIdx: Byte, frameIdx: Byte, data: ByteArray) {
        if (frames.isNotEmpty() && frames[0].invokeIdx != invokeIdx) {
            frames.clear()
            expectedFrameIdx = 0
        }

        if (frameIdx == expectedFrameIdx) {
            frames.add(FrameBuffer(invokeIdx, frameIdx, data))
            expectedFrameIdx = (expectedFrameIdx + 1).toByte()
        } else if (frameIdx < expectedFrameIdx) {
            return
        } else {
            throw ProtocolError.LoseFrame
        }
    }

    fun isComplete(isFin: Boolean): Boolean {
        return isFin && frames.isNotEmpty()
    }

    fun getAllData(): ByteArray {
        val result = mutableListOf<Byte>()
        frames.forEach { frame ->
            result.addAll(frame.data.toList())
        }
        return result.toByteArray()
    }

    fun clear() {
        frames.clear()
        expectedFrameIdx = 0
    }
}

private data class FrameBuffer(
    val invokeIdx: Byte,
    val frameIdx: Byte,
    val data: ByteArray
)
