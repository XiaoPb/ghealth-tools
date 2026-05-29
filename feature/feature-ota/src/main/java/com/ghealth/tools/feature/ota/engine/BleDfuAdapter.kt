package com.ghealth.tools.feature.ota.engine

import android.content.Context
import com.goodix.ble.gr.lib.com.ILogger
import com.goodix.ble.gr.lib.com.ble.BlockingBle
import com.goodix.ble.gr.lib.dfu.v2.DfuProgressListener
import com.goodix.ble.gr.lib.dfu.v2.pojo.DfuFile
import com.juul.kable.ExperimentalApi
import com.juul.kable.Peripheral
import timber.log.Timber
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalApi::class)
class BleDfuAdapter(
    private val context: Context,
    private val peripheral: Peripheral,
) {
    private var dfuProfile: GR5xxxDfuKable? = null
    private var taskThread: Thread? = null
    private val cancelled = AtomicBoolean(false)
    private var logger: ILogger? = null

    @Volatile
    private var listener: DfuProgressListener? = null

    fun setLogger(logger: ILogger?) {
        this.logger = logger
    }

    fun setListener(listener: DfuProgressListener?) {
        this.listener = listener
    }

    fun cancel() {
        cancelled.set(true)
        taskThread?.interrupt()
    }

    fun startFirmwareUpdate(
        fileStream: InputStream,
        fastMode: Boolean,
        copyMode: Boolean = false,
        copyAddress: Int = -1,
    ) {
        cancelled.set(false)
        val currentListener = listener
        currentListener?.onDfuStart()

        taskThread = Thread({
            try {
                BlockingBle.setup(context)
                val dfuFile = loadDfuFile(fileStream, currentListener) ?: return@Thread

                currentListener?.onDfuProgress(0, 0, "Binding DFU service...")
                val profile = GR5xxxDfuKable(peripheral)
                profile.setLogger(logger)
                profile.bind()
                this.dfuProfile = profile

                val writeAddress = if (copyMode) {
                    if (copyAddress >= 0) copyAddress
                    else dfuFile.imgInfo.bootInfo.loadAddr
                } else {
                    dfuFile.imgInfo.bootInfo.loadAddr
                }

                profile.updateFirmware(fastMode, dfuFile, writeAddress, null, currentListener)
                Thread.sleep(200)
                currentListener?.onDfuComplete()
            } catch (e: InterruptedException) {
                currentListener?.onDfuError("DFU cancelled", Error("DFU cancelled"))
            } catch (e: Throwable) {
                Timber.e(e, "DFU firmware update failed")
                currentListener?.onDfuError(e.message ?: "Unknown error", Error(e))
            } finally {
                dfuProfile?.unbind()
                dfuProfile = null
            }
        }, "BleDfuAdapter-fw").apply { start() }
    }

    fun startResourceUpdate(
        fileStream: InputStream,
        isExtFlash: Boolean,
        startAddress: Int,
        fastMode: Boolean,
    ) {
        cancelled.set(false)
        val currentListener = listener
        currentListener?.onDfuStart()

        taskThread = Thread({
            try {
                BlockingBle.setup(context)
                val dfuFile = loadDfuFile(fileStream, currentListener) ?: return@Thread

                val errMsg = when {
                    dfuFile.data == null -> "Can't load resource file."
                    dfuFile.data!!.isEmpty() -> "Empty resource file."
                    else -> null
                }
                if (errMsg != null) {
                    currentListener?.onDfuError(errMsg, Error(errMsg))
                    return@Thread
                }

                currentListener?.onDfuProgress(0, 0, "Binding DFU service...")
                val profile = GR5xxxDfuKable(peripheral)
                profile.setLogger(logger)
                profile.bind()
                this.dfuProfile = profile

                profile.updateResource(isExtFlash, fastMode, dfuFile, startAddress, null, currentListener)
                Thread.sleep(200)
                currentListener?.onDfuComplete()
            } catch (e: InterruptedException) {
                currentListener?.onDfuError("DFU cancelled", Error("DFU cancelled"))
            } catch (e: Throwable) {
                Timber.e(e, "DFU resource update failed")
                currentListener?.onDfuError(e.message ?: "Unknown error", Error(e))
            } finally {
                dfuProfile?.unbind()
                dfuProfile = null
            }
        }, "BleDfuAdapter-res").apply { start() }
    }

    internal fun getProfile(): GR5xxxDfuKable? = dfuProfile

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
}