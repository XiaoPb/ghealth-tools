package com.ghealth.tools.feature.ota.engine

import android.content.Context
import com.ghealth.tools.ble.connection.BleConnectionManager
import com.ghealth.tools.feature.ota.model.OtaConfig
import com.ghealth.tools.feature.ota.model.UpgradeRegion
import com.goodix.ble.gr.lib.com.LogcatLogger
import com.goodix.ble.gr.lib.com.StringLogger
import com.goodix.ble.gr.lib.com.transport.BleConnection
import com.goodix.ble.gr.lib.com.transport.DfuReconnectHandler
import com.goodix.ble.gr.lib.dfu.v2.DfuProgressListener
import com.goodix.ble.gr.lib.dfu.v2.GR5xxxDfu2.CmdOpcode
import com.goodix.ble.gr.lib.dfu.v2.pojo.DfuFile
import com.goodix.ble.gr.lib.com.HexSerializer
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.InputStream

data class OtaProgress(
    val state: OtaState = OtaState.IDLE,
    val progressPercent: Float = 0f,
    val logLines: List<String> = emptyList(),
    val errorMessage: String? = null,
)

enum class OtaState {
    IDLE, PREPARING, CONNECTING, TRANSFERRING, VERIFYING, COMPLETED, CANCELLED, ERROR
}

data class DebugResult(
    val success: Boolean,
    val data: ByteArray? = null,
    val message: String = "",
)

class OtaEngine @Inject constructor(private val connectionManager: BleConnectionManager) {

    private val _progress = MutableStateFlow(OtaProgress())
    val progress: StateFlow<OtaProgress> = _progress.asStateFlow()

    private val _logEvents = MutableSharedFlow<String>(extraBufferCapacity = 256)
    val logEvents: Flow<String> = _logEvents.asSharedFlow()

    private val stringLogger = StringLogger()

    private val dfuReconnectHandler = object : DfuReconnectHandler {
        override fun getCurrentDeviceAddress(): String {
            val address = dfuProfile?.getCurrentMac() ?: throw Error("DFU Profile 未绑定")
            Timber.i("DFU reconnect: 获取当前 MAC=$address")
            return address
        }

        override fun scanAndConnect(targetMac: String, timeoutMs: Long): BleConnection {
            Timber.i("DFU reconnect: 开始扫描并连接 $targetMac")
            return runBlocking {
                val peripheral = connectionManager.scanForDeviceWithMac(targetMac, timeoutMs)
                if (peripheral == null) {
                    Timber.e("DFU reconnect: 扫描超时, MAC=$targetMac")
                    throw Error("未找到 AppBootloader 广播: $targetMac")
                }
                Timber.d("DFU reconnect: 扫描到设备，开始连接")
                peripheral.connect()
                Timber.i("DFU reconnect: 连接成功 ${peripheral.identifier}")
                val oldMac = dfuProfile?.getCurrentMac() ?: ""
                connectionManager.notifyDfuReconnect(oldMac, peripheral)
                KableBleConnection(peripheral)
            }
        }
    }

    private var dfuProfile: GR5xxxDfuKable? = null
    @Volatile
    private var isCancelled = false

    val isDfuReady: Boolean get() = dfuProfile != null

    suspend fun bindDfuProfile(context: Context, address: String) = withContext(Dispatchers.IO) {
        unbindDfuProfile()
        val peripheral = connectionManager.getPeripheral(address)
            ?: run {
                Timber.e("DFU bind: 未找到设备 $address")
                _logEvents.tryEmit("DFU Profile 绑定失败: 设备未连接")
                return@withContext
            }
        val bleConnection = KableBleConnection(peripheral)
        val profile = GR5xxxDfuKable(dfuReconnectHandler)
        profile.setLogger(null)
        profile.bind(bleConnection)
        dfuProfile = profile
        _logEvents.tryEmit("DFU Profile 已绑定")
    }

    fun unbindDfuProfile() {
        dfuProfile?.unbind()
        dfuProfile = null
    }

    private fun requireProfile(): GR5xxxDfuKable {
        return dfuProfile ?: throw IllegalStateException("DFU Profile 未绑定，请先选择设备")
    }

    suspend fun readFirmwareInfo(): FirmwareInfo = withContext(Dispatchers.IO) {
        val profile = requireProfile()
        _logEvents.tryEmit("读取固件信息...")
        val scaAddress = profile.getAddressOfSCA(null)
        val startupBootInfo = profile.getStartupBootInfo(scaAddress)
        val info = FirmwareInfo.fromBootInfo(startupBootInfo.bootInfo)
        _logEvents.tryEmit("固件信息读取完成")
        info
    }

    suspend fun startFirmwareUpgrade(
        context: Context,
        firmwareStream: InputStream,
        config: OtaConfig,
    ) = withContext(Dispatchers.IO) {
        isCancelled = false
        _progress.value = OtaProgress(state = OtaState.PREPARING)

        stringLogger.clearBuffer()
        stringLogger.setNextLogger(LogcatLogger.INSTANCE)

        val profile = requireProfile()
        profile.setLogger(stringLogger)

        val listener = createListener()
        val dfuFile = loadDfuFile(firmwareStream, listener) ?: return@withContext

        val writeAddress = if (config.upgradeRegion == UpgradeRegion.DUAL) {
            if (config.copyAddress > 0) config.copyAddress.toInt()
            else dfuFile.imgInfo.bootInfo.loadAddr
        } else {
            dfuFile.imgInfo.bootInfo.loadAddr
        }

        try {
            profile.updateFirmware(config.fastMode, dfuFile, writeAddress, null, listener)
            Thread.sleep(200)
            listener.onDfuComplete()
        } catch (e: InterruptedException) {
            listener.onDfuError("DFU cancelled", Error("DFU cancelled"))
        } catch (e: Throwable) {
            Timber.e(e, "DFU firmware update failed")
            listener.onDfuError(e.message ?: "Unknown error", Error(e))
        }
    }

    suspend fun startResourceUpgrade(
        context: Context,
        resourceStream: InputStream,
        config: OtaConfig,
    ) = withContext(Dispatchers.IO) {
        isCancelled = false
        _progress.value = OtaProgress(state = OtaState.PREPARING)

        stringLogger.clearBuffer()
        stringLogger.setNextLogger(LogcatLogger.INSTANCE)

        val profile = requireProfile()
        profile.setLogger(stringLogger)

        val listener = createListener()
        val dfuFile = loadDfuFile(resourceStream, listener) ?: return@withContext

        val errMsg = when {
            dfuFile.data == null -> "Can't load resource file."
            dfuFile.data!!.isEmpty() -> "Empty resource file."
            else -> null
        }
        if (errMsg != null) {
            listener.onDfuError(errMsg, Error(errMsg))
            return@withContext
        }

        val extFlash = config.resourceStorageType ==
                com.ghealth.tools.feature.ota.model.StorageType.EXTERNAL

        try {
            profile.updateResource(extFlash, config.fastMode, dfuFile, config.resourceStartAddress.toInt(), null, listener)
            Thread.sleep(200)
            listener.onDfuComplete()
        } catch (e: InterruptedException) {
            listener.onDfuError("DFU cancelled", Error("DFU cancelled"))
        } catch (e: Throwable) {
            Timber.e(e, "DFU resource update failed")
            listener.onDfuError(e.message ?: "Unknown error", Error(e))
        }
    }

    suspend fun readBootInfo(): DebugResult = withContext(Dispatchers.IO) {
        _logEvents.tryEmit("读取BootInfo")
        try {
            val profile = requireProfile()
            val scaAddress = profile.getAddressOfSCA(null)
            val startupBootInfo = profile.getStartupBootInfo(scaAddress)
            val bi = startupBootInfo.bootInfo
            val msg = buildString {
                appendLine("BootInfo:")
                appendLine("  binSize=${bi.binSize}, checksum=0x${bi.checksum.toString(16)}")
                appendLine("  loadAddr=0x${bi.loadAddr.toString(16)}, runAddr=0x${bi.runAddr.toString(16)}")
                appendLine("  xqspiXipCmd=0x${bi.xqspiXipCmd.toString(16)}, xqspiSpeed=${bi.xqspiSpeed}")
                appendLine("  codeCopyMode=${bi.codeCopyMode}, systemClk=${bi.systemClk}")
                appendLine("  checkImage=${bi.checkImage}, bootDelay=${bi.bootDelay}")
                appendLine("  isDapBoot=${bi.isDapBoot}, isEncrypted=${startupBootInfo.isEncrypted}")
            }
            _logEvents.tryEmit("BootInfo读取完成")
            DebugResult(success = true, message = msg)
        } catch (e: Exception) {
            Timber.e(e, "读取BootInfo失败")
            DebugResult(success = false, message = "读取失败: ${e.message}")
        }
    }

    suspend fun readRam(address: Int, length: Int): DebugResult = withContext(Dispatchers.IO) {
        _logEvents.tryEmit("读取RAM 地址=0x${address.toString(16)} 长度=$length")
        try {
            val profile = requireProfile()
            val param = HexSerializer(6)
            param.put(4, address)
            param.put(2, length)
            profile.sendCmd(CmdOpcode.READ_RAM, param.buffer)
            val rcv = profile.rcvCmd(CmdOpcode.READ_RAM)
            val data = ByteArray(rcv.rangeSize)
            System.arraycopy(rcv.buffer, rcv.offsetInBuffer, data, 0, rcv.rangeSize)
            _logEvents.tryEmit("RAM读取完成: ${data.size} bytes")
            DebugResult(success = true, data = data, message = "RAM读取成功: ${data.size} bytes")
        } catch (e: Exception) {
            Timber.e(e, "读取RAM失败")
            DebugResult(success = false, message = "读取失败: ${e.message}")
        }
    }

    suspend fun writeRam(address: Int, data: ByteArray): DebugResult = withContext(Dispatchers.IO) {
        _logEvents.tryEmit("写入RAM 地址=0x${address.toString(16)} 数据长度=${data.size}")
        try {
            val profile = requireProfile()
            val param = HexSerializer(6 + data.size)
            param.put(4, address)
            param.put(2, data.size)
            param.put(data)
            profile.sendCmd(CmdOpcode.WRITE_RAM, param.buffer)
            profile.rcvCmd(CmdOpcode.WRITE_RAM)
            _logEvents.tryEmit("RAM写入完成")
            DebugResult(success = true, message = "RAM写入成功: ${data.size} bytes")
        } catch (e: Exception) {
            Timber.e(e, "写入RAM失败")
            DebugResult(success = false, message = "写入失败: ${e.message}")
        }
    }

    suspend fun readFlash(address: Int, length: Int): DebugResult = withContext(Dispatchers.IO) {
        _logEvents.tryEmit("读取Flash 地址=0x${address.toString(16)} 长度=$length")
        try {
            val profile = requireProfile()
            val param = HexSerializer(6)
            param.put(4, address)
            param.put(2, length)
            profile.sendCmd(CmdOpcode.DUMP_FLASH, param.buffer)
            val rcv = profile.rcvCmd(CmdOpcode.DUMP_FLASH)
            val data = ByteArray(rcv.rangeSize)
            System.arraycopy(rcv.buffer, rcv.offsetInBuffer, data, 0, rcv.rangeSize)
            _logEvents.tryEmit("Flash读取完成: ${data.size} bytes")
            DebugResult(success = true, data = data, message = "Flash读取成功: ${data.size} bytes")
        } catch (e: Exception) {
            Timber.e(e, "读取Flash失败")
            DebugResult(success = false, message = "读取失败: ${e.message}")
        }
    }

    suspend fun writeFlash(address: Int, data: ByteArray): DebugResult = withContext(Dispatchers.IO) {
        _logEvents.tryEmit("写入Flash 地址=0x${address.toString(16)} 数据长度=${data.size}")
        try {
            val profile = requireProfile()
            val param = HexSerializer(6 + data.size)
            param.put(4, address)
            param.put(2, data.size)
            param.put(data)
            profile.sendCmd(CmdOpcode.UPDATE_FLASH, param.buffer)
            profile.rcvCmd(CmdOpcode.UPDATE_FLASH)
            _logEvents.tryEmit("Flash写入完成")
            DebugResult(success = true, message = "Flash写入成功: ${data.size} bytes")
        } catch (e: Exception) {
            Timber.e(e, "写入Flash失败")
            DebugResult(success = false, message = "写入失败: ${e.message}")
        }
    }

    suspend fun readRegister(address: Int): DebugResult = withContext(Dispatchers.IO) {
        _logEvents.tryEmit("读取寄存器 地址=0x${address.toString(16)}")
        try {
            val profile = requireProfile()
            val param = HexSerializer(4)
            param.put(4, address)
            profile.sendCmd(CmdOpcode.RW_REG, param.buffer)
            val rcv = profile.rcvCmd(CmdOpcode.RW_REG)
            val data = ByteArray(rcv.rangeSize)
            System.arraycopy(rcv.buffer, rcv.offsetInBuffer, data, 0, rcv.rangeSize)
            val hexStr = data.joinToString(" ") { "%02X".format(it) }
            _logEvents.tryEmit("寄存器读取完成: $hexStr")
            DebugResult(success = true, data = data, message = "寄存器读取成功: $hexStr")
        } catch (e: Exception) {
            Timber.e(e, "读取寄存器失败")
            DebugResult(success = false, message = "读取失败: ${e.message}")
        }
    }

    suspend fun writeRegister(address: Int, data: ByteArray): DebugResult = withContext(Dispatchers.IO) {
        _logEvents.tryEmit("写入寄存器 地址=0x${address.toString(16)} 数据=${data.joinToString(" ") { "%02X".format(it) }}")
        try {
            val profile = requireProfile()
            val param = HexSerializer(4 + data.size)
            param.put(4, address)
            param.put(data)
            profile.sendCmd(CmdOpcode.RW_REG, param.buffer)
            profile.rcvCmd(CmdOpcode.RW_REG)
            _logEvents.tryEmit("寄存器写入完成")
            DebugResult(success = true, message = "寄存器写入成功")
        } catch (e: Exception) {
            Timber.e(e, "写入寄存器失败")
            DebugResult(success = false, message = "写入失败: ${e.message}")
        }
    }

    suspend fun readEfuse(): DebugResult = withContext(Dispatchers.IO) {
        _logEvents.tryEmit("读取eFuse")
        try {
            val profile = requireProfile()
            val param = HexSerializer(1)
            param.put(1, 0)
            profile.sendCmd(CmdOpcode.RW_EFUSE, param.buffer)
            val rcv = profile.rcvCmd(CmdOpcode.RW_EFUSE)
            val data = ByteArray(rcv.rangeSize)
            System.arraycopy(rcv.buffer, rcv.offsetInBuffer, data, 0, rcv.rangeSize)
            val hexStr = data.joinToString(" ") { "%02X".format(it) }
            _logEvents.tryEmit("eFuse读取完成: $hexStr")
            DebugResult(success = true, data = data, message = "eFuse读取成功: $hexStr")
        } catch (e: Exception) {
            Timber.e(e, "读取eFuse失败")
            DebugResult(success = false, message = "读取失败: ${e.message}")
        }
    }

    suspend fun readNvds(): DebugResult = withContext(Dispatchers.IO) {
        _logEvents.tryEmit("读取NVDS")
        try {
            val profile = requireProfile()
            val param = HexSerializer(1)
            param.put(1, 0)
            profile.sendCmd(CmdOpcode.OPERATION_NVDS, param.buffer)
            val rcv = profile.rcvCmd(CmdOpcode.OPERATION_NVDS)
            val data = ByteArray(rcv.rangeSize)
            System.arraycopy(rcv.buffer, rcv.offsetInBuffer, data, 0, rcv.rangeSize)
            val hexStr = data.joinToString(" ") { "%02X".format(it) }
            _logEvents.tryEmit("NVDS读取完成: $hexStr")
            DebugResult(success = true, data = data, message = "NVDS读取成功: $hexStr")
        } catch (e: Exception) {
            Timber.e(e, "读取NVDS失败")
            DebugResult(success = false, message = "读取失败: ${e.message}")
        }
    }

    suspend fun writeNvds(data: ByteArray): DebugResult = withContext(Dispatchers.IO) {
        _logEvents.tryEmit("写入NVDS 数据长度=${data.size}")
        try {
            val profile = requireProfile()
            val param = HexSerializer(1 + data.size)
            param.put(1, 1)
            param.put(data)
            profile.sendCmd(CmdOpcode.OPERATION_NVDS, param.buffer)
            profile.rcvCmd(CmdOpcode.OPERATION_NVDS)
            _logEvents.tryEmit("NVDS写入完成")
            DebugResult(success = true, message = "NVDS写入成功")
        } catch (e: Exception) {
            Timber.e(e, "写入NVDS失败")
            DebugResult(success = false, message = "写入失败: ${e.message}")
        }
    }

    suspend fun writeControlPoint(hexData: String): DebugResult = withContext(Dispatchers.IO) {
        _logEvents.tryEmit("写控制点 数据=$hexData")
        try {
            val profile = requireProfile()
            val clean = hexData.replace(" ", "")
            val data = ByteArray(clean.length / 2) { i ->
                ((Character.digit(clean[i * 2], 16) shl 4) + Character.digit(clean[i * 2 + 1], 16)).toByte()
            }
            profile.writeCtrlPoint(data)
            _logEvents.tryEmit("控制点写入完成")
            DebugResult(success = true, message = "控制点写入成功")
        } catch (e: Exception) {
            Timber.e(e, "写控制点失败")
            DebugResult(success = false, message = "写入失败: ${e.message}")
        }
    }

    fun cancel() {
        isCancelled = true
    }

    fun reset() {
        _progress.value = OtaProgress()
    }

    private fun loadDfuFile(
        fileStream: InputStream,
        listener: DfuProgressListener?,
    ): DfuFile? {
        val dfuFile = DfuFile()
        if (!dfuFile.load(fileStream, true)) {
            listener?.onDfuError(dfuFile.lastError, Error(dfuFile.lastError))
            return null
        }
        return dfuFile
    }

    private fun createListener(): DfuProgressListener {
        return object : DfuProgressListener {
            override fun onDfuStart() {
                _progress.value = _progress.value.copy(state = OtaState.CONNECTING)
                _logEvents.tryEmit("DFU升级开始")
            }

            override fun onDfuProgress(percent: Int, speed: Int, message: String?) {
                _progress.value = _progress.value.copy(
                    progressPercent = percent / 100f,
                    state = mapDfuProgressState(percent)
                )
                val msg = if (message != null) {
                    "进度: $percent% | 速度: ${speed}KB/s | $message"
                } else {
                    "进度: $percent% | 速度: ${speed}KB/s"
                }
                _logEvents.tryEmit(msg)
            }

            override fun onDfuComplete() {
                _progress.value = _progress.value.copy(state = OtaState.COMPLETED, progressPercent = 1f)
                _logEvents.tryEmit("DFU升级完成")
                flushSdkLogs()
            }

            override fun onDfuError(message: String?, error: Error?) {
                val errMsg = message ?: error?.message ?: "未知错误"
                Timber.e(error, "DFU error: $errMsg")
                _progress.value = _progress.value.copy(
                    state = OtaState.ERROR,
                    errorMessage = errMsg
                )
                _logEvents.tryEmit("DFU错误: $errMsg")
                flushSdkLogs()
            }
        }
    }

    private fun flushSdkLogs() {
        val sdkLog = stringLogger.logBuffer.toString()
        if (sdkLog.isNotBlank()) {
            _logEvents.tryEmit("--- SDK日志 ---")
            sdkLog.lines().forEach { line ->
                _logEvents.tryEmit("  $line")
            }
            _logEvents.tryEmit("--- SDK日志结束 ---")
        }
        stringLogger.clearBuffer()
    }

    private fun mapDfuProgressState(percent: Int): OtaState = when {
        percent < 10 -> OtaState.CONNECTING
        percent < 90 -> OtaState.TRANSFERRING
        percent < 100 -> OtaState.VERIFYING
        else -> OtaState.COMPLETED
    }
}

data class FirmwareInfo(
    val version: Int = 0,
    val binSize: Int = 0,
    val checksum: Int = 0,
    val loadAddr: Int = 0,
    val runAddr: Int = 0,
    val xqspiXipCmd: Int = 0,
    val xqspiSpeed: Int = 0,
    val codeCopyMode: Int = 0,
    val systemClk: Int = 0,
    val checkImage: Int = 0,
    val bootDelay: Int = 0,
    val isDapBoot: Int = 0,
    val pattern: Int = 0,
    val comments: String = "",
) {
    companion object {
        fun fromBootInfo(bootInfo: com.goodix.ble.gr.lib.dfu.v2.pojo.BootInfo): FirmwareInfo {
            return FirmwareInfo(
                binSize = bootInfo.binSize,
                checksum = bootInfo.checksum,
                loadAddr = bootInfo.loadAddr,
                runAddr = bootInfo.runAddr,
                xqspiXipCmd = bootInfo.xqspiXipCmd,
                xqspiSpeed = bootInfo.xqspiSpeed,
                codeCopyMode = bootInfo.codeCopyMode,
                systemClk = bootInfo.systemClk,
                checkImage = bootInfo.checkImage,
                bootDelay = bootInfo.bootDelay,
                isDapBoot = bootInfo.isDapBoot,
            )
        }

        fun fromImgInfo(imgInfo: com.goodix.ble.gr.lib.dfu.v2.pojo.ImgInfo): FirmwareInfo {
            val bi = imgInfo.bootInfo
            return FirmwareInfo(
                pattern = imgInfo.pattern,
                version = imgInfo.version,
                comments = imgInfo.comments ?: "",
                binSize = bi.binSize,
                checksum = bi.checksum,
                loadAddr = bi.loadAddr,
                runAddr = bi.runAddr,
                xqspiXipCmd = bi.xqspiXipCmd,
                xqspiSpeed = bi.xqspiSpeed,
                codeCopyMode = bi.codeCopyMode,
                systemClk = bi.systemClk,
                checkImage = bi.checkImage,
                bootDelay = bi.bootDelay,
                isDapBoot = bi.isDapBoot,
            )
        }
    }
}