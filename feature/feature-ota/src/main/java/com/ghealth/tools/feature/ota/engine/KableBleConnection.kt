package com.ghealth.tools.feature.ota.engine

import com.goodix.ble.gr.lib.com.DataProgressListener
import com.goodix.ble.gr.lib.com.ILogger
import com.goodix.ble.gr.lib.com.transport.BleCharacteristic
import com.goodix.ble.gr.lib.com.transport.BleConnection
import com.goodix.ble.gr.lib.com.transport.BleProperty
import com.goodix.ble.gr.lib.com.transport.BleService
import com.juul.kable.Peripheral
import com.juul.kable.WriteType
import com.juul.kable.characteristicOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class KableBleConnection(
    private val peripheral: Peripheral,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : BleConnection {

    private var logger: ILogger? = null
    private val notifyChannels = mutableMapOf<UUID, Channel<ByteArray>>()
    private var cachedServices: List<KableBleService>? = null

    override fun getTargetAddress(): String = peripheral.identifier

    override fun isConnected(): Boolean = runBlocking {
        peripheral.state.value is com.juul.kable.State.Connected
    }

    override fun connect() {
        runBlocking { peripheral.connect() }
    }

    override fun connect(timeout: Long) {
        runBlocking {
            withTimeoutOrNull(timeout) { peripheral.connect() }
                ?: throw java.util.concurrent.TimeoutException("Connection timeout after ${timeout}ms")
        }
    }

    override fun disconnect() {
        runBlocking { peripheral.disconnect() }
    }

    override fun discoverServices() {
        runBlocking {
            val services = peripheral.services.first { it != null }
            cachedServices = services!!.map { svc ->
                KableBleService(
                    serviceUuid = svc.serviceUuid,
                    characteristics = svc.characteristics.map { char ->
                        KableBleCharacteristic(
                            serviceUuid = svc.serviceUuid,
                            charUuid = char.characteristicUuid,
                            props = mapKableProperties(char.properties),
                        )
                    }
                )
            }
            Timber.d("KableBle: discovered ${cachedServices!!.size} services")
        }
    }

    override fun setMtu(newMtu: Int) {
        Timber.d("KableBle: setMtu($newMtu) - no-op with Kable (negotiated automatically)")
    }

    override fun queryServices(uuid: UUID): List<BleService> {
        val kableUuid = Uuid.parse(uuid.toString())
        return cachedServices?.filter { it.serviceUuid == kableUuid } ?: emptyList()
    }

    override fun queryCharacteristic(service: BleService, uuid: UUID): BleCharacteristic? {
        if (service is KableBleService) {
            val kableUuid = Uuid.parse(uuid.toString())
            return service.characteristics.find { it.charUuid == kableUuid }
        }
        return null
    }

    override fun enableNotification(chr: BleCharacteristic, enabled: Boolean) {
        if (!enabled) return
        val uuid = chr.uuid
        if (notifyChannels.containsKey(uuid)) return

        val kableChar = chr.toKableCharacteristic()
        val channel = Channel<ByteArray>(Channel.BUFFERED)
        notifyChannels[uuid] = channel

        peripheral.observe(kableChar)
            .onEach { data ->
                channel.trySend(data)
            }
            .launchIn(scope)
        Timber.d("KableBle: notification enabled for $uuid")
    }

    override fun writeChrWithResponse(
        chr: BleCharacteristic,
        timeout: Long,
        dat: ByteArray,
        offsetInDat: Int,
        writeSize: Int,
        listener: DataProgressListener?,
    ) {
        runBlocking {
            val data = dat.copyOfRange(offsetInDat, offsetInDat + writeSize.coerceAtMost(dat.size - offsetInDat))
            val kableChar = chr.toKableCharacteristic()
            val startTime = System.currentTimeMillis()
            peripheral.write(kableChar, data, WriteType.WithResponse)
            val elapsed = System.currentTimeMillis() - startTime
            listener?.onDataProcessed(null, data.size, data.size, elapsed, elapsed)
        }
    }

    override fun writeChrWithoutResponse(
        chr: BleCharacteristic,
        timeout: Long,
        dat: ByteArray,
        offsetInDat: Int,
        writeSize: Int,
        listener: DataProgressListener?,
    ) {
        runBlocking {
            val data = dat.copyOfRange(offsetInDat, offsetInDat + writeSize.coerceAtMost(dat.size - offsetInDat))
            val kableChar = chr.toKableCharacteristic()
            val startTime = System.currentTimeMillis()
            peripheral.write(kableChar, data, WriteType.WithoutResponse)
            val elapsed = System.currentTimeMillis() - startTime
            listener?.onDataProcessed(null, data.size, data.size, elapsed, elapsed)
        }
    }

    override fun readNtf(chr: BleCharacteristic, timeout: Long, outBuf: ByteArray, offsetInBuf: Int, readSize: Int): Int {
        val data = readNtf(chr, timeout) ?: return 0
        val copySize = minOf(readSize, data.size, outBuf.size - offsetInBuf)
        System.arraycopy(data, 0, outBuf, offsetInBuf, copySize)
        return copySize
    }

    override fun readNtf(chr: BleCharacteristic, timeout: Long): ByteArray? = runBlocking {
        val channel = notifyChannels[chr.uuid]
            ?: throw Error("Notification channel not initialized for ${chr.uuid}")
        withTimeoutOrNull(timeout) { channel.receive() }
    }

    override fun setLogger(logger: ILogger?) {
        this.logger = logger
    }

    override fun getLogger(): ILogger? = logger

    fun close() {
        notifyChannels.values.forEach { it.close() }
        notifyChannels.clear()
    }

    private fun BleCharacteristic.toKableCharacteristic() = (this as KableBleCharacteristic).let { char ->
        characteristicOf(
            service = char.serviceUuid,
            characteristic = char.charUuid,
        )
    }

    private fun mapKableProperties(properties: com.juul.kable.Characteristic.Properties): Int {
        return properties.value and (BleProperty.READ or BleProperty.WRITE or BleProperty.WRITE_NO_RESPONSE or BleProperty.NOTIFY or BleProperty.INDICATE)
    }

    internal class KableBleService(
        val serviceUuid: Uuid,
        val characteristics: List<KableBleCharacteristic>,
    ) : BleService {
        override fun getUuid(): UUID = UUID.fromString(serviceUuid.toString())
    }

    internal class KableBleCharacteristic(
        val serviceUuid: Uuid,
        val charUuid: Uuid,
        private val props: Int,
    ) : BleCharacteristic {
        override fun getUuid(): UUID = UUID.fromString(charUuid.toString())
        override fun getProperties(): Int = props
    }
}
