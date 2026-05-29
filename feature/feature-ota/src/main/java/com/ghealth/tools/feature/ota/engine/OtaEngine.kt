package com.ghealth.tools.feature.ota.engine

import android.content.Context
import com.ghealth.tools.feature.ota.model.OtaConfig
import com.ghealth.tools.feature.ota.model.UpgradeRegion
import com.goodix.ble.gr.lib.com.LogcatLogger
import com.goodix.ble.gr.lib.com.StringLogger
import com.goodix.ble.gr.lib.dfu.v2.DfuProgressListener
import com.juul.kable.ExperimentalApi
import com.juul.kable.Peripheral
import javax.inject.Inject
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

    private var currentDfu: BleDfuAdapter? = null
    @Volatile
    private var isCancelled = false

    @OptIn(ExperimentalApi::class)
    suspend fun startFirmwareUpgrade(
        context: Context,
        peripheral: Peripheral,
        firmwareStream: InputStream,
        config: OtaConfig,
    ) = withContext(Dispatchers.IO) {
        isCancelled = false
        _progress.value = OtaProgress(state = OtaState.PREPARING)

        stringLogger.clearBuffer()
        stringLogger.setNextLogger(LogcatLogger.INSTANCE)

        val listener = createListener()
        val adapter = BleDfuAdapter(
            context = context.applicationContext,
            peripheral = peripheral,
        ).apply {
            setLogger(stringLogger)
            setListener(listener)
        }
        currentDfu = adapter

        val fastMode = config.fastMode
        val copyMode = config.upgradeRegion == UpgradeRegion.DUAL
        val copyAddress = config.copyAddress.toInt()

        adapter.startFirmwareUpdate(firmwareStream, fastMode, copyMode, copyAddress)
    }

    @OptIn(ExperimentalApi::class)
    suspend fun startResourceUpgrade(
        context: Context,
        peripheral: Peripheral,
        resourceStream: InputStream,
        config: OtaConfig,
    ) = withContext(Dispatchers.IO) {
        isCancelled = false
        _progress.value = OtaProgress(state = OtaState.PREPARING)

        stringLogger.clearBuffer()
        stringLogger.setNextLogger(LogcatLogger.INSTANCE)

        val listener = createListener()
        val adapter = BleDfuAdapter(
            context = context.applicationContext,
            peripheral = peripheral,
        ).apply {
            setLogger(stringLogger)
            setListener(listener)
        }
        currentDfu = adapter

        val extFlash = config.resourceStorageType ==
                com.ghealth.tools.feature.ota.model.StorageType.EXTERNAL

        adapter.startResourceUpdate(
            fileStream = resourceStream,
            isExtFlash = extFlash,
            startAddress = config.resourceStartAddress.toInt(),
            fastMode = config.fastMode,
        )
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