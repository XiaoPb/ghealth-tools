package com.ghealth.tools.feature.ota.engine

import com.goodix.ble.gr.lib.com.DataProgressListener
import com.goodix.ble.gr.lib.com.ble.BlockingBle
import com.goodix.ble.gr.lib.dfu.v2.GR5xxxDfu2
import com.juul.kable.ExperimentalApi
import com.juul.kable.Peripheral
import com.juul.kable.WriteType
import com.juul.kable.characteristicOf
import com.juul.kable.write
import kotlinx.coroutines.CoroutineExceptionHandler
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
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalApi::class, ExperimentalUuidApi::class)
class GR5xxxDfuKable(
    private var kablePeripheral: Peripheral,
    private val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            Timber.e(e, "DFU scope 未捕获异常")
        }
    ),
) : GR5xxxDfu2() {

    private var notifyChannel: Channel<ByteArray>? = null

    companion object {
        val DfuServiceUuid = Uuid.parse("a6ed0401-d344-460a-8075-b9e8ec90d71b")
        val DfuWriteCharUuid = Uuid.parse("a6ed0403-d344-460a-8075-b9e8ec90d71b")
        val DfuNotifyCharUuid = Uuid.parse("a6ed0402-d344-460a-8075-b9e8ec90d71b")
        val DfuCtrlCharUuid = Uuid.parse("a6ed0404-d344-460a-8075-b9e8ec90d71b")
    }

    override fun getBondBle(): BlockingBle {
        return object : BlockingBle(kablePeripheral.identifier) {
            override fun connect(preferredPhyMask: Int, timeout: Long) {}
            override fun disconnect() {}
            override fun isConnected(): Boolean = true
        }
    }

    suspend fun bind() {
        val mac = kablePeripheral.identifier
        Timber.d("DFU bind: 使用共享Kable Peripheral $mac")

        val services = kablePeripheral.services.first { it != null }
        Timber.d("=== DFU Discovered services for $mac ===")
        services!!.forEach { service ->
            Timber.d("  Service: ${service.serviceUuid}")
            service.characteristics.forEach { char ->
                Timber.d("    Characteristic: ${char.characteristicUuid} [properties=${char.properties}]")
            }
        }
        Timber.d("=== End of DFU services ===")

        val channel = Channel<ByteArray>(Channel.BUFFERED)
        notifyChannel = channel

        val notifyChar = characteristicOf(
            service = DfuServiceUuid,
            characteristic = DfuNotifyCharUuid,
        )
        kablePeripheral.observe(notifyChar)
            .onEach { data -> channel.trySend(data) }
            .launchIn(scope)

        Timber.d("DFU bind: DFU服务绑定成功")
    }

    fun unbind() {
        notifyChannel?.close()
        notifyChannel = null
        Timber.d("DFU unbind: 已解绑")
    }

    suspend fun onPeripheralReconnected(newPeripheral: Peripheral) {
        unbind()
        kablePeripheral = newPeripheral
        bind()
    }

    @Throws(Throwable::class)
    override fun writeCtrlPoint(data: ByteArray) {
        if (data.isEmpty()) return
        val chara = characteristicOf(
            service = DfuServiceUuid,
            characteristic = DfuCtrlCharUuid,
        )
        runCatching {
            runBlocking {
                kablePeripheral.write(chara, data, WriteType.WithResponse)
            }
        }.onFailure {
            Timber.e(it, "writeCtrlPoint failed")
        }
    }

    @Throws(Throwable::class)
    override fun sendCmdRaw(
        cmdFrame: ByteArray,
        progressListener: DataProgressListener?,
    ) {
        val writeChar = characteristicOf(
            service = DfuServiceUuid,
            characteristic = DfuWriteCharUuid,
        )
        runBlocking {
            runCatching {
                val startTime = System.currentTimeMillis()
                kablePeripheral.write(writeChar, cmdFrame, WriteType.WithoutResponse)
                val elapsed = System.currentTimeMillis() - startTime
                progressListener?.onDataProcessed(
                    null, cmdFrame.size, cmdFrame.size, elapsed, elapsed
                )
            }.onFailure {
                Timber.e(it, "sendCmdRaw failed")
            }
        }
    }

    @Throws(Throwable::class)
    override fun rcvCmd(opcode: Int) = rcvCmdBuf.also {
        val channel = notifyChannel ?: throw Error("rcvCmd(): 通知通道未初始化")
        val data = runBlocking {
            withTimeoutOrNull(20000L) { channel.receive() }
        } ?: throw Error("rcvCmd(): 等待通知超时 (opcode=$opcode)")

        it.setBuffer(data)
        it.setRangeAll()
        it.setPos(0)
        it.setReadonly(true)
    }
}