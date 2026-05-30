package com.ghealth.tools.ble.connection

import com.juul.kable.Peripheral
import com.juul.kable.State
import com.juul.kable.WriteType
import com.juul.kable.characteristicOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import java.util.UUID
import timber.log.Timber

@OptIn(ExperimentalUuidApi::class)
internal class KableRawChannel(
    private val peripheral: Peripheral,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : BleRawChannel {

    internal val kablePeripheral: Peripheral get() = peripheral

    override val address: String get() = peripheral.identifier
    override val isConnected: Boolean get() = peripheral.state.value is State.Connected

    override suspend fun connect() {
        peripheral.connect()
    }

    override suspend fun connect(timeout: Long) {
        withTimeoutOrNull(timeout) { peripheral.connect() }
            ?: throw java.util.concurrent.TimeoutException("Connection timeout after ${timeout}ms")
    }

    override suspend fun disconnect() {
        peripheral.disconnect()
    }

    override suspend fun discoverServices(): List<BleRawService> {
        val services = peripheral.services.first { it != null }
        return services!!.map { svc ->
            BleRawService(
                uuid = UUID.fromString(svc.serviceUuid.toString()),
                characteristics = svc.characteristics.map { char ->
                    BleRawChar(
                        uuid = UUID.fromString(char.characteristicUuid.toString()),
                        properties = char.properties.value,
                    )
                }
            )
        }.also {
            Timber.d("KableRaw: discovered ${it.size} services")
        }
    }

    override suspend fun write(
        serviceUuid: UUID,
        charUuid: UUID,
        data: ByteArray,
        withResponse: Boolean,
    ) {
        val kableChar = characteristicOf(
            service = Uuid.parse(serviceUuid.toString()),
            characteristic = Uuid.parse(charUuid.toString()),
        )
        val writeType = if (withResponse) WriteType.WithResponse else WriteType.WithoutResponse
        peripheral.write(kableChar, data, writeType)
    }

    override fun observe(serviceUuid: UUID, charUuid: UUID): Flow<ByteArray> {
        val kableChar = characteristicOf(
            service = Uuid.parse(serviceUuid.toString()),
            characteristic = Uuid.parse(charUuid.toString()),
        )
        return peripheral.observe(kableChar)
    }
}