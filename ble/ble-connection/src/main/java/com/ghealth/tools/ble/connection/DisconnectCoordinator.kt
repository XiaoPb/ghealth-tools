package com.ghealth.tools.ble.connection

import com.juul.kable.Peripheral
import com.juul.kable.State
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.Collections

/**
 * 单地址断连协调器。
 *
 * 设计目标：
 * - 单飞：同一地址并发触发 disconnect 时，后续调用直接跳过，避免重复 disconnect/close 造成竞态。
 * - 确认制：只有确认 peripheral 状态为 [State.Disconnected] 才回调 [onConfirmedDisconnected]；
 *   否则先 [Peripheral.close] 触发 Kable 兜底清理，再确认一次；仍失败才回调 [onDisconnectFailed]。
 * - 协调器从不「假装」断开：是否从设备列表移除由回调方决定。
 *
 * 返回契约：返回 true 表示本次调用实际执行了断连流程，调用方负责收尾（兜底 close）；
 * 返回 false 表示被单飞拦截、跳过重复调用，由正在执行的那次调用负责收尾。
 */
internal class DisconnectCoordinator(
    private val disconnectTimeoutMs: Long = DEFAULT_DISCONNECT_TIMEOUT_MS,
) {

    private val disconnectingAddresses = Collections.synchronizedSet(mutableSetOf<String>())

    suspend fun disconnect(
        address: String,
        peripheral: Peripheral,
        markDisconnecting: (String) -> Unit,
        onConfirmedDisconnected: (String) -> Unit,
        onDisconnectFailed: (String) -> Unit,
    ): Boolean {
        if (!disconnectingAddresses.add(address)) {
            Timber.i("Disconnect already in progress for $address, skipping duplicate")
            return false
        }

        try {
            markDisconnecting(address)
            try {
                peripheral.disconnect()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Error disconnecting from $address")
            }

            if (awaitDisconnected(address, peripheral)) {
                onConfirmedDisconnected(address)
                return true
            }

            Timber.w("Disconnect timed out for $address, state=${peripheral.state.value}")
            try {
                peripheral.close()
            } catch (e: Exception) {
                Timber.w(e, "Error closing peripheral for $address")
            }
            if (awaitDisconnected(address, peripheral)) {
                Timber.i("Disconnect confirmed after close for $address")
                onConfirmedDisconnected(address)
            } else {
                Timber.e("Disconnect FAILED for $address, state=${peripheral.state.value}")
                onDisconnectFailed(address)
            }
            return true
        } finally {
            disconnectingAddresses.remove(address)
        }
    }

    private suspend fun awaitDisconnected(address: String, peripheral: Peripheral): Boolean =
        withTimeoutOrNull(disconnectTimeoutMs) {
            peripheral.state
                .filterIsInstance<State.Disconnected>()
                .first()
        }?.also { state ->
            Timber.i("State disconnected received for $address: status=${state.status}")
        } != null

    private companion object {
        const val DEFAULT_DISCONNECT_TIMEOUT_MS = 5_000L
    }
}
