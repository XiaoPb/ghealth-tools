package com.ghealth.tools.feature.ota.engine

import com.ghealth.tools.ble.connection.BleRawChannel
import com.goodix.ble.gr.lib.com.DataProgressListener
import com.goodix.ble.gr.lib.com.ILogger
import com.goodix.ble.gr.lib.com.transport.BleCharacteristic
import com.goodix.ble.gr.lib.com.transport.BleConnection
import com.goodix.ble.gr.lib.com.transport.BleProperty
import com.goodix.ble.gr.lib.com.transport.BleService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.UUID

class KableBleConnection(
    private val channel: BleRawChannel,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : BleConnection {

    private var logger: ILogger? = null
    private val notifyChannels = mutableMapOf<UUID, Channel<ByteArray>>()
    private var cachedServices: List<KableBleService>? = null

    override fun getTargetAddress(): String = channel.address

    override fun isConnected(): Boolean = channel.isConnected

    override fun connect() {
        runBlocking { channel.connect() }
    }

    override fun connect(timeout: Long) {
        runBlocking {
            withTimeoutOrNull(timeout) { channel.connect() }
                ?: throw java.util.concurrent.TimeoutException("Connection timeout after ${timeout}ms")
        }
    }

    override fun disconnect() {
        runBlocking { channel.disconnect() }
    }

    override fun discoverServices() {
        runBlocking {
            val services = channel.discoverServices()
            cachedServices = services.map { svc ->
                KableBleService(
                    serviceUuid = svc.uuid,
                    characteristics = svc.characteristics.map { char ->
                        KableBleCharacteristic(
                            serviceUuid = svc.uuid,
                            charUuid = char.uuid,
                            props = mapKableProperties(char.properties),
                        )
                    },
                )
            }
            Timber.d("KableBle: discovered ${cachedServices!!.size} services")
            cachedServices?.forEach { svc ->
                Timber.d("KableBle:   svcUUID=${svc.serviceUuid}")
                svc.characteristics.forEach { chr ->
                    Timber.d("KableBle:     charUUID=${chr.charUuid}, props=0x${chr.getProperties().toString(16)}")
                }
            }
        }
    }

    override fun setMtu(newMtu: Int) {
        Timber.d("KableBle: setMtu($newMtu) - no-op with Kable")
    }

    override fun queryServices(uuid: UUID): List<BleService> {
        return cachedServices?.filter { it.serviceUuid == uuid } ?: emptyList()
    }

    override fun queryCharacteristic(service: BleService, uuid: UUID): BleCharacteristic? {
        if (service is KableBleService) {
            return service.characteristics.find { it.charUuid == uuid }
        }
        return null
    }

    override fun enableNotification(chr: BleCharacteristic, enabled: Boolean) {
        if (!enabled) return
        val uuid = chr.uuid
        if (notifyChannels.containsKey(uuid)) return

        if (chr is KableBleCharacteristic) {
            val channel = Channel<ByteArray>(Channel.BUFFERED)
            notifyChannels[uuid] = channel

            this.channel.observe(chr.serviceUuid, chr.charUuid)
                .onEach { data ->
                    channel.trySend(data)
                }
                .launchIn(scope)
            Timber.d("KableBle: notification enabled svcUUID=${chr.serviceUuid}, charUUID=${chr.charUuid}")
        }
    }

    override fun writeChrWithResponse(
        chr: BleCharacteristic,
        timeout: Long,
        dat: ByteArray,
        offsetInDat: Int,
        writeSize: Int,
        listener: DataProgressListener?,
    ) {
        if (chr !is KableBleCharacteristic) return
        runBlocking {
            val data = dat.copyOfRange(offsetInDat, offsetInDat + writeSize.coerceAtMost(dat.size - offsetInDat))
            val startTime = System.currentTimeMillis()
            Timber.v("KableBle: writeWithResponse svcUUID=${chr.serviceUuid}, charUUID=${chr.charUuid}, size=$writeSize")
            channel.write(chr.serviceUuid, chr.charUuid, data, withResponse = true)
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
        if (chr !is KableBleCharacteristic) return
        runBlocking {
            val data = dat.copyOfRange(offsetInDat, offsetInDat + writeSize.coerceAtMost(dat.size - offsetInDat))
            val startTime = System.currentTimeMillis()
            Timber.v("KableBle: writeWithoutResponse svcUUID=${chr.serviceUuid}, charUUID=${chr.charUuid}, size=$writeSize")
            channel.write(chr.serviceUuid, chr.charUuid, data, withResponse = false)
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
        Timber.v("KableBle: readNtf waiting charUUID=${chr.uuid}, timeout=$timeout")
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

    private fun mapKableProperties(properties: Int): Int {
        return properties and (BleProperty.READ or BleProperty.WRITE or BleProperty.WRITE_NO_RESPONSE or BleProperty.NOTIFY or BleProperty.INDICATE)
    }

    internal class KableBleService(
        val serviceUuid: UUID,
        val characteristics: List<KableBleCharacteristic>,
    ) : BleService {
        override fun getUuid(): UUID = serviceUuid
    }

    internal class KableBleCharacteristic(
        val serviceUuid: UUID,
        val charUuid: UUID,
        private val props: Int,
    ) : BleCharacteristic {
        override fun getUuid(): UUID = charUuid
        override fun getProperties(): Int = props
    }
}