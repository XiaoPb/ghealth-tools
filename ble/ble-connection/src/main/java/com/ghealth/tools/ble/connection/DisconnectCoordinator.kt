package com.ghealth.tools.ble.connection

import com.juul.kable.Peripheral
import com.juul.kable.State
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
    ) {
        if (!disconnectingAddresses.add(address)) {
            Timber.i("Disconnect already in progress for $address, skipping duplicate")
            return
        }
        markDisconnecting(address)

        try {
            try {
                peripheral.disconnect()
            } catch (e: Exception) {
                Timber.e(e, "Error disconnecting from $address")
            }

            if (awaitDisconnected(address, peripheral)) {
                onConfirmedDisconnected(address)
                return
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
