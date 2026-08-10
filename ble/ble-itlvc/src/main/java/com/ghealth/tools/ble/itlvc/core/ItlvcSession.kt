package com.ghealth.tools.ble.itlvc.core

import com.ghealth.tools.ble.itlvc.codec.FrameCodec
import com.ghealth.tools.ble.itlvc.codec.ItlvcFrame
import com.ghealth.tools.ble.itlvc.state.CommandStateMachine
import com.ghealth.tools.ble.itlvc.state.DropReason
import com.ghealth.tools.ble.itlvc.state.ReceiveStateMachine
import com.ghealth.tools.ble.itlvc.state.SessionState
import com.ghealth.tools.ble.itlvc.transport.ByteTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * ITLVC 会话门面：attach 后启动接收协程（collect transport.receive），
 * 提供单飞行请求队列（execute）、单向发送（send）、上报处理器注册与事件流。
 */
class ItlvcSession(
    private val frameCodec: FrameCodec,
    private val config: ItlvcConfig = ItlvcConfig(),
    private val clock: ProtocolClock = SystemClock,
) {
    private val rx = ReceiveStateMachine(frameCodec.layout)
    private val sendMutex = Mutex()
    private val flightMutex = Mutex()
    private val reportHandlers = HashMap<List<Byte>, suspend (ItlvcFrame) -> Unit>()
    private val _events = MutableSharedFlow<ItlvcEvent>(extraBufferCapacity = 256)
    val events: Flow<ItlvcEvent> = _events.asSharedFlow()

    @Volatile
    private var transport: ByteTransport? = null
    private var receiverJob: Job? = null
    @Volatile
    private var awaiting: PendingCommand? = null

    @Volatile
    var sessionState: SessionState = SessionState.DISCONNECTED
        private set

    val crcErrorCount: Int get() = rx.crcErrorCount
    val lengthErrorCount: Int get() = rx.lengthErrorCount
    val truncatedCount: Int get() = rx.truncatedCount

    fun attach(transport: ByteTransport, scope: CoroutineScope) {
        detach()
        this.transport = transport
        sessionState = SessionState.CONNECTED
        _events.tryEmit(ItlvcEvent.SessionChanged(SessionState.CONNECTED))
        receiverJob = scope.launch {
            transport.receive.collect { onReceive(it) }
        }
    }

    fun detach() {
        receiverJob?.cancel()
        receiverJob = null
        transport = null
        awaiting = null
        if (sessionState != SessionState.DISCONNECTED) {
            sessionState = SessionState.DISCONNECTED
            _events.tryEmit(ItlvcEvent.SessionChanged(SessionState.DISCONNECTED))
        }
    }

    fun reset() {
        detach()
        rx.reset()
        reportHandlers.clear()
    }

    /** 接收入口：由传输层 Notify 回调/接收协程驱动。 */
    suspend fun onReceive(data: ByteArray) {
        val now = clock.now()
        rx.checkTimeout(now, config.frameTimeoutMs)
        for (reason in rx.drainDropReasons()) {
            _events.emit(ItlvcEvent.FrameDropped(reason.toError()))
        }
        for (frame in rx.feed(data, now)) {
            handleFrame(frame)
        }
    }

    private suspend fun handleFrame(frame: ItlvcFrame) {
        val pending = awaiting
        if (pending != null && frame.type.toList() == pending.spec.type.toList()) {
            if (awaiting === pending) awaiting = null
            pending.complete(Result.success(frame.value))
            return
        }
        val handler = reportHandlers[frame.type.toList()]
        if (handler != null) {
            handler(frame)
        } else {
            _events.emit(ItlvcEvent.FrameReceived(frame))
        }
    }

    fun registerReportHandler(type: ByteArray, handler: suspend (ItlvcFrame) -> Unit) {
        reportHandlers[type.toList()] = handler
    }

    /**
     * 执行命令：入队 → 发送 → 等待响应（单飞行，按 T 匹配队首）→ 超时/重试。
     * 返回响应 V 字节。
     */
    suspend fun execute(spec: CommandSpec, payload: ByteArray): Result<ByteArray> = flightMutex.withLock {
        executeLocked(spec, payload)
    }

    private suspend fun executeLocked(spec: CommandSpec, payload: ByteArray): Result<ByteArray> {
        if (config.passThroughMode && !spec.allowedInPassThrough) {
            return Result.failure(ItlvcError.CommandError.Unsupported)
        }
        val cmd = PendingCommand(spec)
        cmd.stateMachine.onEnqueued()
        var retriesLeft = spec.retryCount
        while (true) {
            cmd.stateMachine.onSent()
            // 发送前挂载 awaiting：设备只在收到请求后才回响应，响应若在写入完成前到达也能被匹配；
            // 同类型上报帧在发送窗口内会被当作响应消费（类型匹配语义，已知限制）。
            awaiting = cmd
            val sendResult = try {
                sendFrame(spec.type, payload)
            } catch (e: CancellationException) {
                if (awaiting === cmd) awaiting = null
                throw e
            }
            if (sendResult.isFailure) {
                if (awaiting === cmd) awaiting = null
                cmd.stateMachine.onFailure()
                val error = sendResult.exceptionOrNull() ?: ItlvcError.TransportError("send failed")
                _events.emit(ItlvcEvent.CommandCompleted(spec.type, Result.failure(error)))
                return Result.failure(error)
            }
            val response = try {
                withTimeout(spec.timeoutMs) { cmd.completion.await() }
            } catch (e: TimeoutCancellationException) {
                if (awaiting === cmd) awaiting = null
                cmd.stateMachine.onTimeout()
                if (retriesLeft > 0) {
                    retriesLeft--
                    if (spec.retryDelayMs > 0) delay(spec.retryDelayMs)
                    continue
                }
                val error = ItlvcError.CommandError.Timeout(cmd.stateMachine.attempts)
                _events.emit(ItlvcEvent.CommandCompleted(spec.type, Result.failure(error)))
                return Result.failure(error)
            } catch (e: CancellationException) {
                if (awaiting === cmd) awaiting = null
                throw e
            }
            awaiting = null
            cmd.stateMachine.onResponse()
            _events.emit(ItlvcEvent.CommandCompleted(spec.type, response))
            return response
        }
    }

    /** 单向发送（无需响应），如 0x16 事件 ACK。 */
    suspend fun send(type: ByteArray, payload: ByteArray): Result<Unit> = sendFrame(type, payload)

    private suspend fun sendFrame(type: ByteArray, value: ByteArray): Result<Unit> {
        val t = transport ?: return Result.failure(ItlvcError.TransportError("not attached"))
        val encoded = try {
            frameCodec.encode(ItlvcFrame(type, value))
        } catch (e: IllegalArgumentException) {
            return Result.failure(ItlvcError.ParseError(e.message ?: "encode failed"))
        }
        return sendMutex.withLock { t.send(encoded) }
    }

    private class PendingCommand(val spec: CommandSpec) {
        val completion = CompletableDeferred<Result<ByteArray>>()
        val stateMachine = CommandStateMachine()
        fun complete(result: Result<ByteArray>) {
            completion.complete(result)
        }
    }

    private fun DropReason.toError(): ItlvcError.FrameError = when (this) {
        DropReason.LENGTH_OVERFLOW -> ItlvcError.FrameError.LengthOverflow
        DropReason.CRC_MISMATCH -> ItlvcError.FrameError.CrcMismatch
        DropReason.TRUNCATED -> ItlvcError.FrameError.TruncatedFrame
    }
}
