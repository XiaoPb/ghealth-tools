package com.ghealth.tools.feature.ota.engine

import android.bluetooth.BluetoothGattCharacteristic
import com.goodix.ble.gr.lib.com.DataProgressListener
import com.goodix.ble.gr.lib.com.HexSerializer
import com.goodix.ble.gr.lib.com.ble.BlockingBle
import com.goodix.ble.gr.lib.dfu.v2.GR5xxxDfu2
import com.juul.kable.ExperimentalApi
import com.juul.kable.Peripheral
import com.juul.kable.WriteType
import com.juul.kable.characteristicOf
import com.juul.kable.write
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal open class KableBleStub(mac: String) : BlockingBle(mac) {

    override fun connect(preferredPhyMask: Int, timeout: Long) {
    }

    override fun disconnect() {
    }

    override fun isConnected(): Boolean {
        return true
    }
}

@OptIn(ExperimentalApi::class, ExperimentalUuidApi::class)
class GR5xxxDfuKable(
    private var kablePeripheral: Peripheral,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : GR5xxxDfu2() {

    private val notifyChannel = Channel<ByteArray>(Channel.BUFFERED)
    private var channelInitialized = false
    private var currentMac: String = kablePeripheral.identifier.toString()

    fun onPeripheralReconnected(newPeripheral: Peripheral) {
        unbindInternal()
        this.kablePeripheral = newPeripheral
        this.currentMac = newPeripheral.identifier.toString()
        bindInternal()
    }

    fun bind() {
        if (BlockingBle.appCtx == null) {
            throw Error("Please call BlockingBle.setup(ctx) firstly.")
        }

        val mac = kablePeripheral.identifier.toString()
        val stubBle = KableBleStub(mac)
        stubBle.setLogger(null)
        this.ble = stubBle
        this.currentMac = mac
        bindInternal()
    }

    fun unbind() {
        unbindInternal()
        this.ble = null
    }

    override fun bindTo(ble: BlockingBle) {
        val newMac = ble.targetDevice.address

        if (newMac != currentMac) {
            val isStub = ble is KableBleStub
            if (!isStub) {
                this.ble = ble
                this.currentMac = newMac
                return
            }
        }

        this.ble = ble
        this.currentMac = newMac
    }

    @Throws(Throwable::class)
    override fun writeCtrlPoint(data: ByteArray?) {
        if (data == null) return
        val ctrlChar = characteristicOf(
            service = DfuServiceUuid,
            characteristic = DfuCtrlCharUuid,
        )
        scope.launch {
            runCatching {
                val properties = ctrlChr?.properties ?: 0
                if ((properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                    kablePeripheral.write(ctrlChar, data, WriteType.WithoutResponse)
                } else {
                    kablePeripheral.write(ctrlChar, data, WriteType.WithResponse)
                }
            }.onFailure {
                Timber.e(it, "writeCtrlPoint failed")
            }
        }
    }

    @Throws(Throwable::class)
    override fun sendCmdRaw(
        cmdFrame: ByteArray?,
        progressListener: DataProgressListener?,
    ) {
        if (cmdFrame == null) return
        val writeChar = characteristicOf(
            service = DfuServiceUuid,
            characteristic = DfuWriteCharUuid,
        )

        scope.launch {
            runCatching {
                val startTime = System.currentTimeMillis()
                val properties = writeChr?.properties ?: 0
                if ((properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                    kablePeripheral.write(writeChar, cmdFrame, WriteType.WithoutResponse)
                } else {
                    kablePeripheral.write(writeChar, cmdFrame, WriteType.WithResponse)
                }
                if (progressListener != null) {
                    val totalTime = System.currentTimeMillis() - startTime
                    progressListener.onDataProcessed(
                        cmdFrame,
                        cmdFrame.size,
                        cmdFrame.size,
                        totalTime,
                        totalTime,
                    )
                }
            }.onFailure {
                Timber.e(it, "sendCmdRaw failed")
            }
        }
    }

    @Throws(Throwable::class)
    override fun rcvCmd(opcode: Int): HexSerializer {
        val rcvCmdBuf = HexSerializer(2048)
        val timeout = defaultTimeout

        val header = readNotifyWithTimeout(timeout)
            ?: throw Error("rcvCmd(): Failed to get header of cmd.")

        rcvCmdBuf.setRangeAll()
        rcvCmdBuf.setPos(0)
        System.arraycopy(header, 0, rcvCmdBuf.buffer, 0, header.size)

        val magicNum = rcvCmdBuf.get(2)
        val rcvOpcode = rcvCmdBuf.get(2)
        val paramLen = rcvCmdBuf.get(2)
        if (magicNum != 0x4744) {
            throw Error("rcvCmd(): Error frame header: $magicNum")
        }
        if (rcvOpcode != opcode) {
            throw Error("rcvCmd(): Unexpected opcode: $rcvOpcode")
        }
        val extra = 2
        if (paramLen + extra > rcvCmdBuf.buffer.size - 6) {
            throw Error("rcvCmd(): Large length of param: $paramLen")
        }

        val remaining = readNotifyWithTimeout(timeout)
            ?: throw java.util.concurrent.TimeoutException(
                "rcvCmd(): Timeout reading param ($paramLen + $extra bytes)")

        System.arraycopy(remaining, 0, rcvCmdBuf.buffer, 6, remaining.size)
        rcvCmdBuf.setReadonly(true)
        rcvCmdBuf.setRange(6, paramLen)
        rcvCmdBuf.setPos(0)

        return rcvCmdBuf
    }

    override fun getBondBle(): BlockingBle? = this.ble

    private fun bindInternal() {
        if (channelInitialized) return

        val notifyChar = characteristicOf(
            service = DfuServiceUuid,
            characteristic = DfuNotifyCharUuid,
        )

        kablePeripheral.observe(notifyChar)
            .onEach { data ->
                notifyChannel.trySend(data)
            }
            .launchIn(scope)

        channelInitialized = true
    }

    private fun unbindInternal() {
        notifyChannel.cancel()
        channelInitialized = false
    }

    private fun readNotifyWithTimeout(timeoutMs: Long): ByteArray? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val result = runBlocking {
                withTimeoutOrNull(2000) {
                    notifyChannel.receiveCatching().getOrNull()
                }
            }
            if (result != null) return result
        }
        return null
    }

    companion object {
        private val DfuServiceUuid: Uuid = Uuid.parse("a6ed0401-d344-460a-8075-b9e8ec90d71b")
        private val DfuNotifyCharUuid: Uuid = Uuid.parse("a6ed0402-d344-460a-8075-b9e8ec90d71b")
        private val DfuWriteCharUuid: Uuid = Uuid.parse("a6ed0403-d344-460a-8075-b9e8ec90d71b")
        private val DfuCtrlCharUuid: Uuid = Uuid.parse("a6ed0404-d344-460a-8075-b9e8ec90d71b")
    }
}