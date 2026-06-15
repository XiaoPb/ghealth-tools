package com.ghealth.tools.ble.connection

import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface BleRawChannel {
    val address: String
    val isConnected: Boolean
    val mtu: Int

    suspend fun connect()
    suspend fun connect(timeout: Long)
    suspend fun disconnect()
    suspend fun discoverServices(): List<BleRawService>
    suspend fun requestMtu(desiredMtu: Int): Int
    suspend fun write(serviceUuid: UUID, charUuid: UUID, data: ByteArray, withResponse: Boolean)
    fun observe(serviceUuid: UUID, charUuid: UUID): Flow<ByteArray>
}

data class BleRawService(
    val uuid: UUID,
    val characteristics: List<BleRawChar>,
)

data class BleRawChar(
    val uuid: UUID,
    val properties: Int,
)