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
    private val reconnectCallback: DfuReconnectCallback? = null,
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

    interface DfuReconnectCallback {
        suspend fun onDfuDisconnectCurrent(): String
        suspend fun onDfuScanAndConnect(newMac: String): Peripheral?
        suspend fun onDfuReconnected(peripheral: Peripheral)
    }

    override fun getBondBle(): BlockingBle {
        return object : BlockingBle(kablePeripheral.identifier) {
            override fun connect(preferredPhyMask: Int, timeout: Long) {}
            override fun disconnect() {}
            override fun isConnected(): Boolean = true
        }
    }

    fun getCurrentMac(): String = kablePeripheral.identifier.toString()

    suspend fun bind() {
        val mac = kablePeripheral.identifier
        Timber.i("DFU bind: 开始绑定, peripheral=$mac")

        val services = kablePeripheral.services.first { it != null }
        Timber.d("DFU bind: 发现 ${services!!.size} 个服务")
        services.forEach { service ->
            Timber.v("DFU bind:   Service: ${service.serviceUuid}, chars=${service.characteristics.size}")
            service.characteristics.forEach { char ->
                Timber.v("DFU bind:     Char: ${char.characteristicUuid} [props=${char.properties}]")
            }
        }

        val channel = Channel<ByteArray>(Channel.BUFFERED)
        notifyChannel = channel

        val notifyChar = characteristicOf(
            service = DfuServiceUuid,
            characteristic = DfuNotifyCharUuid,
        )
        kablePeripheral.observe(notifyChar)
            .onEach { data ->
                Timber.v("DFU notify: 收到 ${data.size} bytes, hex=${data.take(16).toByteArray().toHexString()}${if (data.size > 16) "..." else ""}")
                channel.trySend(data)
            }
            .launchIn(scope)

        Timber.i("DFU bind: 绑定成功, peripheral=$mac")
    }

    fun unbind() {
        notifyChannel?.close()
        notifyChannel = null
        Timber.i("DFU unbind: 已解绑")
    }

    suspend fun onPeripheralReconnected(newPeripheral: Peripheral) {
        Timber.i("DFU onPeripheralReconnected: ${kablePeripheral.identifier} -> ${newPeripheral.identifier}")
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
        Timber.d("DFU writeCtrlPoint: ${data.size} bytes, hex=${data.toHexString()}")
        runCatching {
            runBlocking {
                kablePeripheral.write(chara, data, WriteType.WithResponse)
            }
        }.onSuccess {
            Timber.v("DFU writeCtrlPoint: 写入成功")
        }.onFailure {
            Timber.e(it, "DFU writeCtrlPoint failed: ${data.size} bytes")
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
        Timber.v("DFU sendCmdRaw: ${cmdFrame.size} bytes, hex=${cmdFrame.take(32).toByteArray().toHexString()}${if (cmdFrame.size > 32) "..." else ""}")
        runBlocking {
            runCatching {
                val startTime = System.currentTimeMillis()
                kablePeripheral.write(writeChar, cmdFrame, WriteType.WithoutResponse)
                val elapsed = System.currentTimeMillis() - startTime
                Timber.v("DFU sendCmdRaw: 写入完成, 耗时=${elapsed}ms")
                progressListener?.onDataProcessed(
                    null, cmdFrame.size, cmdFrame.size, elapsed, elapsed
                )
            }.onFailure {
                Timber.e(it, "DFU sendCmdRaw failed: ${cmdFrame.size} bytes")
            }
        }
    }

    @Throws(Throwable::class)
    override fun rcvCmd(opcode: Int) = rcvCmdBuf.also {
        val channel = notifyChannel ?: throw Error("rcvCmd(): 通知通道未初始化")
        Timber.d("DFU rcvCmd: 等待通知, opcode=0x${opcode.toString(16)}")

        val data = runBlocking {
            withTimeoutOrNull(20000L) { channel.receive() }
        } ?: throw Error("rcvCmd(): 等待通知超时 (opcode=$opcode)")

        Timber.v("DFU rcvCmd: 收到 ${data.size} bytes, hex=${data.toHexString()}")
        it.setBuffer(data)
        it.setRangeAll()
        it.setPos(0)
        it.setReadonly(true)
        Timber.d("DFU rcvCmd: 完成, opcode=0x${opcode.toString(16)}, size=${data.size}")
    }

    override fun updateFirmware(
        withFastMode: Boolean,
        dfuFw: com.goodix.ble.gr.lib.dfu.v2.pojo.DfuFile,
        writeAddress: Int,
        ctrlCmd: ByteArray?,
        progressCallback: com.goodix.ble.gr.lib.dfu.v2.DfuProgressListener?,
    ) {
        Timber.i("DFU updateFirmware: fastMode=$withFastMode, writeAddr=0x${writeAddress.toString(16)}")
        if (isAppBootloaderSolution) {
            Timber.w("DFU updateFirmware: AppBootloader 路径需要重连, 当前通过 Kable stub BlockingBle 处理")
            if (reconnectCallback != null) {
                Timber.i("DFU updateFirmware: DfuReconnectCallback 已配置, 尝试 Kable 重连")
                handleAppBootloaderReconnection(progressCallback)
            }
        }
        super.updateFirmware(withFastMode, dfuFw, writeAddress, ctrlCmd, progressCallback)
        Timber.i("DFU updateFirmware: 完成")
    }

    private fun handleAppBootloaderReconnection(progressCallback: com.goodix.ble.gr.lib.dfu.v2.DfuProgressListener?) {
        val callback = reconnectCallback ?: return

        Timber.i("DFU handleAppBootloaderReconnection: 开始 AppBootloader 重连流程")

        runBlocking {
            val currentMac = callback.onDfuDisconnectCurrent()
            val newMac = changeMacAddress(currentMac, +1)
            Timber.i("DFU AppBootloader reconnection: $currentMac -> $newMac")

            Timber.d("DFU: 等待设备断开 (100ms + 200ms)")
            Thread.sleep(100)
            Thread.sleep(200)

            Timber.d("DFU: 开始扫描 AppBootloader 设备 $newMac")
            val scanStartMs = System.currentTimeMillis()
            val newPeripheral = callback.onDfuScanAndConnect(newMac)
            val scanElapsedMs = System.currentTimeMillis() - scanStartMs

            if (newPeripheral == null) {
                Timber.e("DFU: 扫描 AppBootloader 超时 (${scanElapsedMs}ms), MAC=$newMac")
                throw Error("updateFirmware(): 未找到 AppBootloader 广播: $newMac")
            }
            Timber.i("DFU: 找到 AppBootloader 设备 $newMac, 耗时 ${scanElapsedMs}ms")

            Timber.d("DFU: 通知 BleConnectionManager 重连完成")
            callback.onDfuReconnected(newPeripheral)
            onPeripheralReconnected(newPeripheral)
            Timber.i("DFU: AppBootloader 重连绑定完成, 新 Peripheral=${newPeripheral.identifier}")

            if (progressCallback != null) {
                progressCallback.onDfuProgress(0, 0, "Time for bootloader to take a deep breath...")
            }
            Timber.d("DFU: 等待 bootloader 就绪 (2000ms)")
            Thread.sleep(2_000)
        }
    }
}

private fun ByteArray.toHexString(): String = joinToString(" ") { "%02X".format(it) }
