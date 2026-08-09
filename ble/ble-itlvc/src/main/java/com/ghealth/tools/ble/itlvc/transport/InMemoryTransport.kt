package com.ghealth.tools.ble.itlvc.transport

import com.ghealth.tools.ble.itlvc.core.ItlvcError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 测试用内存传输：可注入入站块、记录出站字节、模拟发送失败。
 * 非线程安全，仅用于单线程测试。
 */
class InMemoryTransport(override val mtu: Int = 240) : ByteTransport {

    private val _receive = MutableSharedFlow<ByteArray>(extraBufferCapacity = 128)
    override val receive: Flow<ByteArray> = _receive.asSharedFlow()

    val sent = mutableListOf<ByteArray>()
    var failSends = false

    override fun send(data: ByteArray): Result<Unit> {
        if (failSends) return Result.failure(ItlvcError.TransportError("send failed (test)"))
        sent.add(data)
        return Result.success(Unit)
    }

    fun emit(bytes: ByteArray) {
        _receive.tryEmit(bytes)
    }

    fun emitBytes(vararg values: Int) {
        emit(ByteArray(values.size) { values[it].toByte() })
    }
}
