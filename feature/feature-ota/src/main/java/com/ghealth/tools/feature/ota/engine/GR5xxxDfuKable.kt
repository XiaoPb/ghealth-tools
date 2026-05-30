package com.ghealth.tools.feature.ota.engine

import com.goodix.ble.gr.lib.com.DataProgressListener
import com.goodix.ble.gr.lib.com.transport.BleConnection
import com.goodix.ble.gr.lib.com.transport.DfuReconnectHandler
import com.goodix.ble.gr.lib.dfu.v2.GR5xxxDfu2
import timber.log.Timber

class GR5xxxDfuKable(
    reconnectHandler: DfuReconnectHandler? = null,
) : GR5xxxDfu2() {

    init {
        reconnectHandler?.let { setReconnectHandler(it) }
    }

    fun getCurrentMac(): String = ble?.getTargetAddress() ?: ""

    suspend fun bind(bleConnection: BleConnection) {
        val mac = bleConnection.getTargetAddress()
        Timber.i("DFU bind: 开始绑定, address=$mac")
        bleConnection.discoverServices()
        bindTo(bleConnection)
        Timber.i("DFU bind: 绑定成功, address=$mac, serviceUUID=$DFU_SERVICE_UUID, notifyUUID=$DFU_NOTIFY_CHARACTERISTIC_UUID, writeUUID=$DFU_WRITE_CHARACTERISTIC_UUID, ctrlUUID=$DFU_CONTROL_CHARACTERISTIC_UUID")
    }

    fun unbind() {
        (ble as? KableBleConnection)?.close()
        ble = null
        Timber.i("DFU unbind: 已解绑")
    }

    @Throws(Throwable::class)
    override fun writeCtrlPoint(data: ByteArray) {
        if (data.isEmpty()) return
        Timber.d("DFU writeCtrlPoint: ${data.size} bytes, ctrlUUID=$DFU_CONTROL_CHARACTERISTIC_UUID, hex=${data.toHexString()}")
        try {
            super.writeCtrlPoint(data)
            Timber.v("DFU writeCtrlPoint: 写入成功")
        } catch (e: Throwable) {
            Timber.e(e, "DFU writeCtrlPoint failed: ${data.size} bytes")
            throw e
        }
    }

    @Throws(Throwable::class)
    override fun sendCmdRaw(
        cmdFrame: ByteArray,
        progressListener: DataProgressListener?,
    ) {
        Timber.v("DFU sendCmdRaw: ${cmdFrame.size} bytes, writeUUID=$DFU_WRITE_CHARACTERISTIC_UUID, hex=${cmdFrame.take(32).toByteArray().toHexString()}${if (cmdFrame.size > 32) "..." else ""}")
        try {
            super.sendCmdRaw(cmdFrame, progressListener)
        } catch (e: Throwable) {
            Timber.e(e, "DFU sendCmdRaw failed: ${cmdFrame.size} bytes")
            throw e
        }
    }

    @Throws(Throwable::class)
    override fun rcvCmd(opcode: Int) = rcvCmdBuf.also {
        val conn = ble
            ?: throw Error("rcvCmd(): BLE未连接")
        Timber.d("DFU rcvCmd: 等待通知, notifyUUID=$DFU_NOTIFY_CHARACTERISTIC_UUID, opcode=0x${opcode.toString(16)}")

        var data = conn.readNtf(notifyChr, 20000L)
            ?: throw Error("rcvCmd(): 等待通知超时 (opcode=$opcode)")

        if (data.size < 6) {
            throw Error("rcvCmd(): 头部数据不足, 需要至少6字节, 收到${data.size}字节")
        }

        val paramLen = ((data[4].toInt() and 0xFF) or ((data[5].toInt() and 0xFF) shl 8))
        val totalExpected = 6 + paramLen + 2

        while (data.size < totalExpected) {
            val remaining = totalExpected - data.size
            Timber.v("DFU rcvCmd: 等待分帧数据, 还需 $remaining bytes, opcode=0x${opcode.toString(16)}")
            val nextData = conn.readNtf(notifyChr, 20000L)
                ?: throw Error("rcvCmd(): 等待分帧数据超时 (opcode=$opcode), 已收到${data.size}/${totalExpected}字节")
            Timber.v("DFU rcvCmd: 收到分帧 ${nextData.size} bytes, hex=${nextData.toHexString()}")
            data = data + nextData
        }

        Timber.v("DFU rcvCmd: 收到 ${data.size} bytes, notifyUUID=$DFU_NOTIFY_CHARACTERISTIC_UUID, hex=${data.toHexString()}")

        it.setBuffer(data)
        it.setRangeAll()
        it.setPos(0)
        it.setReadonly(false)

        val magicNum = it.get(2)
        val rcvOpcode = it.get(2)
        val parsedParamLen = it.get(2)
        if (magicNum != 0x4744) {
            throw Error("rcvCmd(): 帧头错误, magic=0x${magicNum.toString(16)}")
        }
        if (rcvOpcode != opcode) {
            throw Error("rcvCmd(): opcode不匹配, expected=0x${opcode.toString(16)}, actual=0x${rcvOpcode.toString(16)}")
        }

        it.setRange(6, parsedParamLen)
        it.setPos(0)
        it.setReadonly(true)
        Timber.d("DFU rcvCmd: 完成, opcode=0x${opcode.toString(16)}, paramLen=$parsedParamLen")
    }
}

private fun ByteArray.toHexString(): String = joinToString(" ") { "%02X".format(it) }
