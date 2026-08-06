@file:OptIn(ExperimentalApi::class)

package com.ghealth.tools.ble.connection

import com.juul.kable.Characteristic
import com.juul.kable.Descriptor
import com.juul.kable.DiscoveredService
import com.juul.kable.ExperimentalApi
import com.juul.kable.Peripheral
import com.juul.kable.State
import com.juul.kable.WriteType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * 可驱动的 Kable [Peripheral] 测试替身。
 * - [disconnect] 默认把状态置为 [State.Disconnected]（模拟 Kable 正常断连）。
 * - [autoDisconnect] = false 时保持当前状态（模拟断连超时/失败）。
 * - [disconnectGate] 不为空时 [disconnect] 挂起直到 gate 完成（模拟慢速断连，用于单飞测试）。
 * - [disconnectThrows] = true 时 [disconnect] 抛异常。
 * - [closeTriggersDisconnected] = true 时 [close] 把状态置为 Disconnected（模拟 close 兜底生效）。
 */
internal class FakePeripheral(
    private val autoDisconnect: Boolean = true,
    private val disconnectGate: CompletableDeferred<Unit>? = null,
    private val disconnectThrows: Boolean = false,
    private val closeTriggersDisconnected: Boolean = false,
) : Peripheral {

    override val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private val _state = MutableStateFlow<State>(State.Disconnected())
    override val state: StateFlow<State> = _state.asStateFlow()

    private val _services = MutableStateFlow<List<DiscoveredService>?>(null)
    override val services: StateFlow<List<DiscoveredService>?> = _services

    override val identifier: String = "AA:BB:CC:DD:EE:FF"
    @ExperimentalApi
    override val name: String? = "FakePeripheral"

    var disconnectCount = 0
        private set
    var closeCount = 0
        private set

    /** 把状态置为 Connected，模拟已建立连接的外设。 */
    fun markConnected() {
        _state.value = State.Connected(scope)
    }

    override suspend fun connect(): CoroutineScope = scope

    override suspend fun disconnect() {
        disconnectCount++
        disconnectGate?.await()
        if (disconnectThrows) {
            throw RuntimeException("disconnect failed")
        }
        if (autoDisconnect) {
            _state.value = State.Disconnected()
        }
    }

    override fun close() {
        closeCount++
        if (closeTriggersDisconnected) {
            _state.value = State.Disconnected()
        }
        scope.cancel()
    }

    override suspend fun write(
        characteristic: Characteristic,
        data: ByteArray,
        writeType: WriteType,
    ) = Unit

    override suspend fun read(characteristic: Characteristic): ByteArray = byteArrayOf()

    override suspend fun write(descriptor: Descriptor, data: ByteArray) = Unit

    override suspend fun read(descriptor: Descriptor): ByteArray = byteArrayOf()

    override fun observe(
        characteristic: Characteristic,
        onSubscription: suspend () -> Unit,
    ): Flow<ByteArray> = emptyFlow()

    override suspend fun maximumWriteValueLengthForType(writeType: WriteType): Int = 20

    @ExperimentalApi
    override suspend fun rssi(): Int = 0
}
