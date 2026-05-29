package com.ghealth.tools.feature.ota.engine

import android.bluetooth.BluetoothDevice
import android.content.Context
import com.ghealth.tools.feature.ota.model.OtaConfig
import com.ghealth.tools.feature.ota.model.UpgradeRegion
import com.goodix.ble.gr.lib.com.LogcatLogger
import com.goodix.ble.gr.lib.com.StringLogger
import com.goodix.ble.gr.lib.dfu.v2.DfuProgressListener
import com.goodix.ble.gr.lib.dfu.v2.EasyDfu2
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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

class OtaEngine @Inject constructor() {

    private val _progress = MutableStateFlow(OtaProgress())
    val progress: StateFlow<OtaProgress> = _progress.asStateFlow()

    private val _logEvents = MutableSharedFlow<String>(extraBufferCapacity = 256)
    val logEvents: Flow<String> = _logEvents.asSharedFlow()

    private val stringLogger = StringLogger()

    private var currentDfu: EasyDfu2? = null
    @Volatile
    private var isCancelled = false

    suspend fun startFirmwareUpgrade(
        context: Context,
        device: BluetoothDevice,
        firmwareStream: InputStream,
        config: OtaConfig,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        isCancelled = false
        _progress.value = OtaProgress(state = OtaState.PREPARING)

        try {
            stringLogger.clearBuffer()
            stringLogger.setNextLogger(LogcatLogger.INSTANCE)

            val listener = createListener()
            val dfu = EasyDfu2().apply {
                setLogger(stringLogger)
                setListener(listener)
                setFastMode(config.fastMode)
            }
            currentDfu = dfu

            when (config.upgradeRegion) {
                UpgradeRegion.SINGLE -> {
                    dfu.startDfu(context, device, firmwareStream)
                }
                UpgradeRegion.DUAL -> {
                    dfu.startDfuInCopyMode(context, device, firmwareStream, config.copyAddress.toInt())
                }
            }

            if (isCancelled) {
                _progress.value = _progress.value.copy(state = OtaState.CANCELLED)
                return@withContext Result.failure(CancellationException("DFU cancelled by user"))
            }

            _progress.value = _progress.value.copy(state = OtaState.COMPLETED, progressPercent = 1f)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Firmware DFU failed")
            _progress.value = _progress.value.copy(
                state = OtaState.ERROR,
                errorMessage = e.message ?: "未知错误"
            )
            Result.failure(e)
        }
    }

    suspend fun startResourceUpgrade(
        context: Context,
        device: BluetoothDevice,
        resourceStream: InputStream,
        config: OtaConfig,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        isCancelled = false
        _progress.value = OtaProgress(state = OtaState.PREPARING)

        try {
            stringLogger.clearBuffer()
            stringLogger.setNextLogger(LogcatLogger.INSTANCE)

            val listener = createListener()
            val dfu = EasyDfu2().apply {
                setLogger(stringLogger)
                setListener(listener)
                setFastMode(config.fastMode)
            }
            currentDfu = dfu

            val extFlash = config.resourceStorageType ==
                    com.ghealth.tools.feature.ota.model.StorageType.EXTERNAL
            dfu.startUpdateResource(
                context, device, resourceStream,
                extFlash, config.resourceStartAddress.toInt()
            )

            if (isCancelled) {
                _progress.value = _progress.value.copy(state = OtaState.CANCELLED)
                return@withContext Result.failure(CancellationException("Resource upgrade cancelled"))
            }

            _progress.value = _progress.value.copy(state = OtaState.COMPLETED, progressPercent = 1f)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Resource upgrade failed")
            _progress.value = _progress.value.copy(
                state = OtaState.ERROR,
                errorMessage = e.message ?: "未知错误"
            )
            Result.failure(e)
        }
    }

    fun cancel() {
        isCancelled = true
        currentDfu?.cancel()
        currentDfu = null
    }

    fun reset() {
        _progress.value = OtaProgress()
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
            }

            override fun onDfuError(message: String?, error: Error?) {
                val errMsg = message ?: error?.message ?: "未知错误"
                Timber.e(error, "DFU error: $errMsg")
                _progress.value = _progress.value.copy(
                    state = OtaState.ERROR,
                    errorMessage = errMsg
                )
                _logEvents.tryEmit("DFU错误: $errMsg")
            }
        }
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