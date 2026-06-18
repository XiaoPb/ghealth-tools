package com.ghealth.tools.ble.protocol.rpccore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

typealias SendFunction = (ByteArray) -> Result<Unit>

data class RpcConfig(
    val timeoutMs: Long = 3000,
    val retryCount: Byte = 3,
    val retryDelayMs: Long = 3000,
    val frameSize: Int = 240
)

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

class RpcCore(
    private val config: RpcConfig = RpcConfig()
) {
    private val frameParser = FrameParser()
    private val frameBuilder = FrameBuilder()
    private val processMutex = Mutex()

    private val staticHandlers = ConcurrentHashMap<String, (ByteArray, Int, InvokeContext) -> Unit>()
    private val pendingCalls = ConcurrentHashMap<String, PendingCall>()
    private var sendFunction: SendFunction? = null
    private var invokeIdx: Byte = 1

    private val multiFrameBuffer = MultiFrameBuffer()

    fun setSendFunction(func: SendFunction) {
        sendFunction = func
    }

    suspend fun process(data: ByteArray): List<Result<ParseResult>> {
        return processMutex.withLock {
            val results = frameParser.process(data)

            results.forEach { result ->
                if (result.isSuccess) {
                    handleParseResult(result.getOrThrow())
                }
            }

            results
        }
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

            try {
                handler(data, data.size, context)
            } catch (e: Exception) {
                Timber.e(e, "Exception in secure handler for key=$key")
                return
            }

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
                        @Suppress("UNUSED_VARIABLE")
                        val ackFrameIdx = data.getOrElse(1) { 0 }
                        pending.onResponse(Result.success(ByteArray(0)))
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
        val effectiveFrameIdx = if (frameIdx == LAST_FRAME_INDEX) 0 else frameIdx

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

            try {
                handler(allData, allData.size, context)
            } catch (e: Exception) {
                Timber.e(e, "Exception in handler for key=$key")
                return
            }

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
            val packed = Package.pack(format, params).getOrElse {
                return@withContext Result.failure(it)
            }

            val invokeIdx = nextInvokeIndex()

            val frames = frameBuilder.buildMultiFrame(
                key = key,
                param = packed,
                secure = false,
                invokeIdx = invokeIdx
            )

            val sendFn = sendFunction
            if (sendFn == null) {
                return@withContext Result.failure(ProtocolError.ChannelClosed)
            }

            val pending = PendingCall(invokeIdx, key)
            pendingCalls[key] = pending

            for (frame in frames) {
                val sendResult = sendFn.invoke(frame)
                if (sendResult.isFailure) {
                    pendingCalls.remove(key)
                    return@withContext Result.failure(ProtocolError.ChannelClosed)
                }
            }

            pending.waitForResponse(config.timeoutMs)
        }
    }

    suspend fun send(key: String, format: String, params: ByteArray): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val packed = Package.pack(format, params).getOrElse {
                return@withContext Result.failure(it)
            }

            val invokeIdx = nextInvokeIndex()

            val frames = frameBuilder.buildMultiFrame(
                key = key,
                param = packed,
                secure = true,
                invokeIdx = invokeIdx
            )

            val sendFn = sendFunction
            if (sendFn == null) {
                return@withContext Result.failure(ProtocolError.ChannelClosed)
            }

            val pending = PendingCall(invokeIdx, key)
            pendingCalls[key] = pending

            for (frame in frames) {
                val sendResult = sendFn.invoke(frame)
                if (sendResult.isFailure) {
                    pendingCalls.remove(key)
                    return@withContext Result.failure(ProtocolError.ChannelClosed)
                }
            }

            pending.waitForResponse(config.timeoutMs).map { }
        }
    }

    fun publish(key: String, params: ByteArray): Result<Unit> {
        val frames = frameBuilder.buildMultiFrame(
            key = key,
            param = params,
            secure = false
        )

        val sendFn = sendFunction ?: return Result.failure(ProtocolError.ChannelClosed)
        for (frame in frames) {
            val result = sendFn.invoke(frame)
            if (result.isFailure) return result
        }
        return Result.success(Unit)
    }

    suspend fun sall(key: String, format: String, params: ByteArray): Result<ByteArray> {
        return withContext(Dispatchers.IO) {
            val packed = Package.pack(format, params).getOrElse {
                return@withContext Result.failure(it)
            }

            val invokeIdx = nextInvokeIndex()

            val frames = frameBuilder.buildMultiFrame(
                key = key,
                param = packed,
                secure = true,
                invokeIdx = invokeIdx
            )

            val sendFn = sendFunction
            if (sendFn == null) {
                return@withContext Result.failure(ProtocolError.ChannelClosed)
            }

            val pending = PendingCall(invokeIdx, key)
            pendingCalls[key] = pending

            for (frame in frames) {
                val sendResult = sendFn.invoke(frame)
                if (sendResult.isFailure) {
                    pendingCalls.remove(key)
                    return@withContext Result.failure(ProtocolError.ChannelClosed)
                }
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