package com.ghealth.tools.feature.ota.engine

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import com.ghealth.tools.ble.connection.BleConnectionManager
import com.ghealth.tools.core.model.ConnectionState
import com.ghealth.tools.feature.ota.model.SmpUpgradeMode
import com.ghealth.tools.feature.ota.model.SmpUpgradeSettings
import no.nordicsemi.android.mcumgr.McuMgrTransport
import no.nordicsemi.android.mcumgr.ble.McuMgrBleTransport
import no.nordicsemi.android.mcumgr.dfu.FirmwareUpgradeCallback
import no.nordicsemi.android.mcumgr.dfu.FirmwareUpgradeController
import no.nordicsemi.android.mcumgr.dfu.mcuboot.FirmwareUpgradeManager
import no.nordicsemi.android.mcumgr.dfu.mcuboot.model.ImageSet
import no.nordicsemi.android.mcumgr.exception.McuMgrException
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

/**
 * Nordic MCUmgr SMP 升级引擎。
 *
 * 使用 Nordic 官方 `mcumgr-ble` 的 [McuMgrBleTransport] 独立建立 GATT 连接，
 * 因此升级前必须释放项目的 Kable 连接，升级结束后再交还给 [BleConnectionManager]。
 */
class NordicSmpEngine @Inject constructor(
    private val connectionManager: BleConnectionManager,
) {

    companion object {
        private const val DISCONNECT_TIMEOUT_MS = 5_000L
        private const val GATT_RELEASE_DELAY_MS = 300L
    }

    private val _progress = MutableStateFlow(OtaProgress())
    val progress: StateFlow<OtaProgress> = _progress.asStateFlow()

    private val _logEvents = MutableSharedFlow<String>(extraBufferCapacity = 256)
    val logEvents: Flow<String> = _logEvents.asSharedFlow()

    /** 仅上传阶段允许取消，TEST/CONFIRM 之后取消无法回滚。 */
    private val _isUploadPhase = MutableStateFlow(false)
    val isUploadPhase: StateFlow<Boolean> = _isUploadPhase.asStateFlow()

    private var manager: FirmwareUpgradeManager? = null
    private var transport: McuMgrBleTransport? = null
    private var completion: CompletableDeferred<Unit>? = null

    suspend fun upgrade(
        context: Context,
        address: String,
        deviceName: String?,
        images: ImageSet,
        mode: SmpUpgradeMode,
        settings: SmpUpgradeSettings,
    ) = withContext(Dispatchers.IO) {
        reset()
        _progress.value = OtaProgress(state = OtaState.PREPARING, statusText = "准备升级")
        emit("开始 Nordic SMP 升级（${mode.label}）")

        try {
            releaseKableConnection(address)

            val device = resolveDevice(context, address)
                ?: throw IllegalStateException("无法获取蓝牙设备 $address")

            val bleTransport = McuMgrBleTransport(context.applicationContext, device)
            transport = bleTransport

            _progress.value = OtaProgress(state = OtaState.CONNECTING, statusText = "连接 SMP 服务")
            emit("连接 SMP 服务…")
            connectTransport(bleTransport)

            val upgradeManager = FirmwareUpgradeManager(bleTransport, createCallback())
            upgradeManager.setMode(mode.toNordicMode())
            manager = upgradeManager
            completion = CompletableDeferred()

            emit("创建升级会话，模式：${mode.label}")
            upgradeManager.start(
                images,
                FirmwareUpgradeManager.Settings.Builder()
                    .setEstimatedSwapTime(settings.estimatedSwapTimeMs)
                    .setWindowCapacity(settings.windowCapacity)
                    .setMemoryAlignment(settings.memoryAlignment)
                    .setEraseAppSettings(false)
                    .build(),
            )

            // start() 为异步执行，等待升级回调给出最终结果。
            completion?.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Timber.e(e, "SMP 升级失败")
            fail(e.message ?: "未知错误")
        } finally {
            val running = manager
            if (running != null && running.isInProgress) {
                runCatching { running.cancel() }
            }
            manager = null
            completion = null
            _isUploadPhase.value = false
            transport?.release()
            transport = null
            emit("SMP 连接已释放，恢复项目连接")
            // 非挂起调用，取消场景下也能完成收尾。
            connectionManager.reconnectInBackground(address, deviceName)
        }
    }

    /** 仅上传阶段有效；其余阶段忽略，避免误以为可以回滚。 */
    fun cancel() {
        val running = manager ?: return
        if (!_isUploadPhase.value || !running.isInProgress) return
        emit("请求取消上传…")
        runCatching { running.cancel() }
    }

    /**
     * 页面销毁时调用。取消动作会触发升级协程进入 `finally`，
     * 由它统一完成 transport 释放与连接恢复，避免重复释放。
     */
    fun close() {
        val running = manager
        if (running != null && running.isInProgress) {
            runCatching { running.cancel() }
        }
        _isUploadPhase.value = false
    }

    fun reset() {
        _progress.value = OtaProgress()
        _isUploadPhase.value = false
    }

    private suspend fun releaseKableConnection(address: String) {
        _progress.value = OtaProgress(state = OtaState.CONNECTING, statusText = "释放当前连接")
        emit("断开当前 BLE 连接，交接到 SMP 传输层…")
        connectionManager.disconnect(address)
        val released = withTimeoutOrNull(DISCONNECT_TIMEOUT_MS) {
            connectionManager.devices.first { it[address]?.state != ConnectionState.CONNECTED }
        } != null
        if (!released) {
            throw IllegalStateException("设备断开失败，请重试")
        }
        // 部分机型 GATT 资源释放存在延迟，避免紧接着创建连接失败。
        delay(GATT_RELEASE_DELAY_MS)
    }

    private fun resolveDevice(context: Context, address: String): BluetoothDevice? {
        return runCatching {
            context.getSystemService(BluetoothManager::class.java)
                ?.adapter
                ?.getRemoteDevice(address)
        }.getOrNull()
    }

    private suspend fun connectTransport(bleTransport: McuMgrBleTransport) =
        suspendCancellableCoroutine { cont ->
            val resumed = AtomicBoolean(false)
            bleTransport.connect(object : McuMgrTransport.ConnectionCallback {
                override fun onConnected() {
                    if (resumed.compareAndSet(false, true)) {
                        emit("SMP 服务已连接")
                        cont.resume(Unit)
                    }
                }

                override fun onDeferred() {
                    emit("SMP 连接已延迟，等待设备就绪…")
                }

                override fun onError(error: Throwable) {
                    if (resumed.compareAndSet(false, true)) {
                        cont.resumeWithException(error)
                    }
                }
            })
        }

    private fun createCallback() = object : FirmwareUpgradeCallback<FirmwareUpgradeManager.State> {

        override fun onUpgradeStarted(controller: FirmwareUpgradeController) {
            emit("升级已开始")
        }

        override fun onStateChanged(
            previousState: FirmwareUpgradeManager.State,
            newState: FirmwareUpgradeManager.State,
        ) {
            if (newState == FirmwareUpgradeManager.State.NONE) return
            _isUploadPhase.value = newState == FirmwareUpgradeManager.State.UPLOAD
            _progress.value = _progress.value.copy(
                state = newState.toOtaState(),
                statusText = newState.stageText(),
            )
            emit("阶段：${newState.stageText()}")
        }

        override fun onUpgradeCompleted() {
            _isUploadPhase.value = false
            _progress.value = _progress.value.copy(
                state = OtaState.COMPLETED,
                progressPercent = 1f,
                statusText = "升级完成",
            )
            emit("SMP 升级完成")
            completion?.complete(Unit)
        }

        override fun onUpgradeFailed(
            state: FirmwareUpgradeManager.State,
            error: McuMgrException,
        ) {
            _isUploadPhase.value = false
            val message = when (state) {
                FirmwareUpgradeManager.State.VALIDATE -> "镜像校验失败：${error.message}"
                FirmwareUpgradeManager.State.UPLOAD -> "镜像上传中断：${error.message}"
                FirmwareUpgradeManager.State.TEST -> "新固件测试启动失败，设备可能已回滚：${error.message}"
                FirmwareUpgradeManager.State.RESET -> "设备重启或重连失败：${error.message}"
                FirmwareUpgradeManager.State.CONFIRM -> "固件已上传但确认失败：${error.message}"
                else -> "升级失败：${error.message}"
            }
            fail(message)
            completion?.complete(Unit)
        }

        override fun onUpgradeCanceled(state: FirmwareUpgradeManager.State) {
            _isUploadPhase.value = false
            _progress.value = _progress.value.copy(
                state = OtaState.CANCELLED,
                statusText = "升级已取消",
            )
            emit("SMP 升级已取消（${state.stageText()}）")
            completion?.complete(Unit)
        }

        override fun onUploadProgressChanged(bytesSent: Int, imageSize: Int, timestamp: Long) {
            val percent = if (imageSize > 0) bytesSent.toFloat() / imageSize else 0f
            _progress.value = _progress.value.copy(
                state = OtaState.TRANSFERRING,
                progressPercent = percent.coerceIn(0f, 1f),
                statusText = "上传镜像",
            )
        }
    }

    private fun fail(message: String) {
        Timber.e("SMP 升级失败: $message")
        _progress.value = _progress.value.copy(
            state = OtaState.ERROR,
            errorMessage = message,
            statusText = null,
        )
        _logEvents.tryEmit("SMP 升级失败: $message")
    }

    private fun emit(message: String) {
        Timber.i("SMP: $message")
        _logEvents.tryEmit(message)
    }
}

private fun SmpUpgradeMode.toNordicMode(): FirmwareUpgradeManager.Mode = when (this) {
    SmpUpgradeMode.TEST_AND_CONFIRM -> FirmwareUpgradeManager.Mode.TEST_AND_CONFIRM
    SmpUpgradeMode.CONFIRM_ONLY -> FirmwareUpgradeManager.Mode.CONFIRM_ONLY
    SmpUpgradeMode.TEST_ONLY -> FirmwareUpgradeManager.Mode.TEST_ONLY
    SmpUpgradeMode.NONE -> FirmwareUpgradeManager.Mode.NONE
}

private fun FirmwareUpgradeManager.State.toOtaState(): OtaState = when (this) {
    FirmwareUpgradeManager.State.VALIDATE -> OtaState.VERIFYING
    FirmwareUpgradeManager.State.UPLOAD -> OtaState.TRANSFERRING
    FirmwareUpgradeManager.State.TEST -> OtaState.VERIFYING
    FirmwareUpgradeManager.State.RESET -> OtaState.CONNECTING
    FirmwareUpgradeManager.State.CONFIRM -> OtaState.VERIFYING
    FirmwareUpgradeManager.State.NONE -> OtaState.IDLE
}

private fun FirmwareUpgradeManager.State.stageText(): String = when (this) {
    FirmwareUpgradeManager.State.VALIDATE -> "校验设备镜像状态"
    FirmwareUpgradeManager.State.UPLOAD -> "上传镜像"
    FirmwareUpgradeManager.State.TEST -> "测试启动新固件"
    FirmwareUpgradeManager.State.RESET -> "设备重启"
    FirmwareUpgradeManager.State.CONFIRM -> "确认新固件"
    FirmwareUpgradeManager.State.NONE -> "空闲"
}
