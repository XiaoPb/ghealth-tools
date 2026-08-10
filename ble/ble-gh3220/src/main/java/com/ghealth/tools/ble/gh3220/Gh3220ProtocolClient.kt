package com.ghealth.tools.ble.gh3220

import com.ghealth.tools.ble.gh3220.commands.BasicCommands
import com.ghealth.tools.ble.gh3220.commands.ConfigCommands
import com.ghealth.tools.ble.gh3220.commands.Gh3220CommandSpecs
import com.ghealth.tools.ble.gh3220.commands.RegisterCommands
import com.ghealth.tools.ble.gh3220.event.EventAckHandler
import com.ghealth.tools.ble.gh3220.event.Gh3220CardiffEvent
import com.ghealth.tools.ble.gh3220.event.Gh3220CurrentBattery
import com.ghealth.tools.ble.gh3220.event.Gh3220DeviceEvent
import com.ghealth.tools.ble.gh3220.event.Gh3220SlaveLog
import com.ghealth.tools.ble.gh3220.event.ReportDecoder
import com.ghealth.tools.ble.gh3220.flow.DriverConfigFlow
import com.ghealth.tools.ble.gh3220.flow.FwUpgradeFlow
import com.ghealth.tools.ble.gh3220.rawdata.Gh3220FifoReport
import com.ghealth.tools.ble.gh3220.rawdata.Gh3220RawDataFrame
import com.ghealth.tools.ble.gh3220.rawdata.Gh3220RawDataPackage
import com.ghealth.tools.ble.gh3220.rawdata.RawDataDecoder
import com.ghealth.tools.ble.gh3220.rawdata.SamplingConfig
import com.ghealth.tools.ble.itlvc.core.CommandSpec
import com.ghealth.tools.ble.itlvc.core.ItlvcError
import com.ghealth.tools.ble.itlvc.core.ItlvcSession
import com.ghealth.tools.ble.itlvc.state.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * GH3220 应用层门面。
 *
 * 上报路由：0x08/09/0A → [rawdataFrames]，0x0B → [rawdataPackages]（同时逐帧转发到 rawdataFrames），
 * 0x2A → [fifoReports]，0x0D → [currentBattery]，0x14 → [deviceEvents]，0x21 → [slaveLogs]，
 * 0x16 → [cardiffEvents]（自动 ACK）。解码失败进 [decodeErrors]，不影响接收循环。
 *
 * 命令 API：类型化 payload 编解码 + [Gh3220CommandSpecs]；多包流程见 [fwUpgrade] / [driverConfig]。
 */
class Gh3220ProtocolClient(
    private val session: ItlvcSession,
    private val decoder: RawDataDecoder = RawDataDecoder(SamplingConfig()),
) {

    private val _rawdataFrames = MutableSharedFlow<Gh3220RawDataFrame>(extraBufferCapacity = 128)
    private val _rawdataPackages = MutableSharedFlow<Gh3220RawDataPackage>(extraBufferCapacity = 128)
    private val _fifoReports = MutableSharedFlow<Gh3220FifoReport>(extraBufferCapacity = 128)
    private val _currentBattery = MutableSharedFlow<Gh3220CurrentBattery>(extraBufferCapacity = 64)
    private val _deviceEvents = MutableSharedFlow<Gh3220DeviceEvent>(extraBufferCapacity = 64)
    private val _slaveLogs = MutableSharedFlow<Gh3220SlaveLog>(extraBufferCapacity = 64)
    private val _decodeErrors = MutableSharedFlow<ItlvcError>(extraBufferCapacity = 64)

    val rawdataFrames: Flow<Gh3220RawDataFrame> = _rawdataFrames.asSharedFlow()
    val rawdataPackages: Flow<Gh3220RawDataPackage> = _rawdataPackages.asSharedFlow()
    val fifoReports: Flow<Gh3220FifoReport> = _fifoReports.asSharedFlow()
    val currentBattery: Flow<Gh3220CurrentBattery> = _currentBattery.asSharedFlow()
    val deviceEvents: Flow<Gh3220DeviceEvent> = _deviceEvents.asSharedFlow()
    val slaveLogs: Flow<Gh3220SlaveLog> = _slaveLogs.asSharedFlow()
    val decodeErrors: Flow<ItlvcError> = _decodeErrors.asSharedFlow()

    private val eventAckHandler = EventAckHandler(session)
    val cardiffEvents: Flow<Gh3220CardiffEvent> = eventAckHandler.events

    val sessionState: SessionState
        get() = session.sessionState

    /**
     * 注册全部上报处理器；需在 `session.attach(...)` 之后调用。
     * 断线重连后必须重新调用本方法（内部执行 decoder.reset() 恢复差分解压基准）。
     */
    fun attach() {
        decoder.reset()
        session.registerReportHandler(byteArrayOf(Gh3220Cmd.RAWDATA.toByte())) { frame ->
            try {
                decoder.decode08(frame.value).getOrThrow().forEach { _rawdataFrames.tryEmit(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _decodeErrors.tryEmit(e as? ItlvcError ?: ItlvcError.ParseError(e.message ?: "decode08 failed"))
            }
        }
        session.registerReportHandler(byteArrayOf(Gh3220Cmd.RAWDATA_ZIP_EVEN.toByte())) { frame ->
            try {
                decoder.decode09(frame.value).getOrThrow().forEach { _rawdataFrames.tryEmit(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _decodeErrors.tryEmit(e as? ItlvcError ?: ItlvcError.ParseError(e.message ?: "decode09 failed"))
            }
        }
        session.registerReportHandler(byteArrayOf(Gh3220Cmd.RAWDATA_ZIP_ODD.toByte())) { frame ->
            try {
                decoder.decode0A(frame.value).getOrThrow().forEach { _rawdataFrames.tryEmit(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _decodeErrors.tryEmit(e as? ItlvcError ?: ItlvcError.ParseError(e.message ?: "decode0A failed"))
            }
        }
        session.registerReportHandler(byteArrayOf(Gh3220Cmd.RAWDATA_NEW.toByte())) { frame ->
            try {
                val pkg = decoder.decode0B(frame.value).getOrThrow()
                _rawdataPackages.tryEmit(pkg)
                pkg.frames.forEach { _rawdataFrames.tryEmit(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _decodeErrors.tryEmit(e as? ItlvcError ?: ItlvcError.ParseError(e.message ?: "decode0B failed"))
            }
        }
        session.registerReportHandler(byteArrayOf(Gh3220Cmd.RAWDATA_FIFO.toByte())) { frame ->
            try {
                _fifoReports.tryEmit(decoder.decode2A(frame.value).getOrThrow())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _decodeErrors.tryEmit(e as? ItlvcError ?: ItlvcError.ParseError(e.message ?: "decode2A failed"))
            }
        }
        session.registerReportHandler(byteArrayOf(Gh3220Cmd.CURRENT_BATTERY.toByte())) { frame ->
            try {
                _currentBattery.tryEmit(ReportDecoder.decodeCurrentBattery(frame.value).getOrThrow())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _decodeErrors.tryEmit(e as? ItlvcError ?: ItlvcError.ParseError(e.message ?: "decode0D failed"))
            }
        }
        session.registerReportHandler(byteArrayOf(Gh3220Cmd.DEVICE_EVENT.toByte())) { frame ->
            try {
                _deviceEvents.tryEmit(ReportDecoder.decodeDeviceEvent(frame.value).getOrThrow())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _decodeErrors.tryEmit(e as? ItlvcError ?: ItlvcError.ParseError(e.message ?: "decode14 failed"))
            }
        }
        session.registerReportHandler(byteArrayOf(Gh3220Cmd.SLAVE_LOG.toByte())) { frame ->
            try {
                _slaveLogs.tryEmit(ReportDecoder.decodeSlaveLog(frame.value).getOrThrow())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _decodeErrors.tryEmit(e as? ItlvcError ?: ItlvcError.ParseError(e.message ?: "decode21 failed"))
            }
        }
        eventAckHandler.attach()
    }

    // —— 命令 API ——

    /**
     * 原始透传（"略"/无格式命令）：按 [type] 原样发送 [payload] 并返回响应 V 字节，不做结构解析。
     * 透传模式下仅白名单命令（文档 §4.3.5，见 [Gh3220CommandSpecs.passThroughWhitelist]）放行，
     * 其余以 [ItlvcError.CommandError.Unsupported] 拒绝且不写入传输。
     */
    suspend fun sendRaw(
        type: Int,
        payload: ByteArray = ByteArray(0),
        timeoutMs: Long = 1000,
    ): Result<ByteArray> = session.execute(
        CommandSpec(
            byteArrayOf(type.toByte()),
            timeoutMs = timeoutMs,
            allowedInPassThrough = type.toByte() in Gh3220CommandSpecs.passThroughWhitelist,
        ),
        payload,
    )

    suspend fun getConnectionStatus(): Result<Int> =
        session.execute(Gh3220CommandSpecs.CONN_STATUS, BasicCommands.getConnStatus()).mapCatching { resp ->
            if (resp.isEmpty()) throw ItlvcError.ParseError("conn status empty")
            Gh3220Payload.readU8(resp, 0)
        }

    suspend fun getVersion(versionType: Int): Result<BasicCommands.VersionInfo> =
        session.execute(Gh3220CommandSpecs.GET_VER, BasicCommands.getVersion(versionType))
            .mapCatching { BasicCommands.parseVersion(it).getOrThrow() }

    suspend fun packageTest(data: ByteArray): Result<ByteArray> =
        session.execute(Gh3220CommandSpecs.PACKAGE_TEST, BasicCommands.packageTest(data))
            .mapCatching { BasicCommands.parsePackageTest(it).getOrThrow() }

    suspend fun startHbd(on: Boolean, mode: Int, function: Long): Result<Int> =
        session.execute(Gh3220CommandSpecs.START_CTRL, BasicCommands.startHbd(on, mode, function))
            .mapCatching { BasicCommands.parseStatus(it, "start hbd").getOrThrow() }

    suspend fun chipReset(resetType: Int): Result<Int> =
        session.execute(Gh3220CommandSpecs.CHIP_CTRL, BasicCommands.chipCtrl(resetType))
            .mapCatching { BasicCommands.parseStatus(it, "chip ctrl").getOrThrow() }

    suspend fun calibrateCurrent(mode: Int): Result<Int> =
        session.execute(Gh3220CommandSpecs.CURRENT_CALIBRATE, BasicCommands.calibrateCurrent(mode))
            .mapCatching { BasicCommands.parseStatus(it, "calibrate current").getOrThrow() }

    suspend fun appModule(cmd: Int): Result<Int> =
        session.execute(Gh3220CommandSpecs.APP_MODULE, BasicCommands.appModule(cmd))
            .mapCatching { BasicCommands.parseStatus(it, "app module").getOrThrow() }

    suspend fun switchChip(cmd: Int): Result<Int> =
        session.execute(Gh3220CommandSpecs.SWITCH_CHIP, BasicCommands.switchChip(cmd))
            .mapCatching { BasicCommands.parseStatus(it, "switch chip").getOrThrow() }

    suspend fun readRegisters(addr: Int, count: Int): Result<IntArray> =
        session.execute(Gh3220CommandSpecs.REG_RW, RegisterCommands.regRead(addr, count))
            .mapCatching { RegisterCommands.parseRegRead(it).getOrThrow() }

    suspend fun writeRegisters(addr: Int, values: IntArray): Result<Unit> =
        session.execute(Gh3220CommandSpecs.REG_RW, RegisterCommands.regWrite(addr, values)).mapCatching { resp ->
            if (resp.isEmpty() || Gh3220Payload.readU8(resp, 0) != 0x01) {
                throw ItlvcError.ParseError("reg write response invalid")
            }
        }

    suspend fun regArrayWrite(blocks: List<IntArray>): Result<Unit> =
        session.execute(Gh3220CommandSpecs.REG_ARRAY_WRITE, RegisterCommands.regArrayWrite(blocks)).mapCatching { resp ->
            if (resp.isEmpty()) throw ItlvcError.ParseError("reg array write response empty")
            when (Gh3220Payload.readU8(resp, 0)) {
                0 -> Unit
                1 -> throw ItlvcError.CommandError.DeviceError(1)
                else -> throw ItlvcError.ParseError("reg array write unknown status ${Gh3220Payload.readU8(resp, 0)}")
            }
        }

    suspend fun setWorkMode(mode: Int, function: Long): Result<Int> =
        session.execute(Gh3220CommandSpecs.WORK_MODE, ConfigCommands.workMode(mode, function))
            .mapCatching { BasicCommands.parseStatus(it, "work mode").getOrThrow() }

    suspend fun gsensorSet(vendorId: Int, resolution: Int, sampleRate: Int): Result<Int> =
        session.execute(Gh3220CommandSpecs.GSENSOR_SET, ConfigCommands.gsensorSet(vendorId, resolution, sampleRate))
            .mapCatching { BasicCommands.parseStatus(it, "gsensor set").getOrThrow() }

    suspend fun fifoThreshold(threshold: Int): Result<Int> =
        session.execute(Gh3220CommandSpecs.FIFO_THR, ConfigCommands.fifoThreshold(threshold))
            .mapCatching { BasicCommands.parseStatus(it, "fifo threshold").getOrThrow() }

    suspend fun eventSet(events: Int): Result<Int> =
        session.execute(Gh3220CommandSpecs.EVENT_SET, ConfigCommands.eventSet(events))
            .mapCatching { BasicCommands.parseStatus(it, "event set").getOrThrow() }

    suspend fun funcMap(map: ByteArray): Result<Int> =
        session.execute(Gh3220CommandSpecs.FUNC_MAP, ConfigCommands.funcMap(map))
            .mapCatching { BasicCommands.parseStatus(it, "func map").getOrThrow() }

    suspend fun sampleRates(entries: List<Pair<Int, Int>>): Result<Int> =
        session.execute(Gh3220CommandSpecs.SAMPLE_RATE, ConfigCommands.sampleRates(entries))
            .mapCatching { BasicCommands.parseStatus(it, "sample rates").getOrThrow() }

    suspend fun slotEn(slotEn: Int, addCmd: Int, function: Long, on: Boolean): Result<Int> =
        session.execute(Gh3220CommandSpecs.SLOT_EN, ConfigCommands.slotEn(slotEn, addCmd, function, on))
            .mapCatching { BasicCommands.parseStatus(it, "slot en").getOrThrow() }

    suspend fun ecgCtrl(ctrlFlag: Int): Result<Int> =
        session.execute(Gh3220CommandSpecs.ECG_CTRL, ConfigCommands.ecgCtrl(ctrlFlag))
            .mapCatching { BasicCommands.parseStatus(it, "ecg ctrl").getOrThrow() }

    suspend fun workModeSet(workMode: Int): Result<Int> =
        session.execute(Gh3220CommandSpecs.WORK_MODE_SET, ConfigCommands.workModeSet(workMode))
            .mapCatching { BasicCommands.parseStatus(it, "work mode set").getOrThrow() }

    /** 0x0F 固件升级流程。 */
    fun fwUpgrade(): FwUpgradeFlow = FwUpgradeFlow(session)

    /** 0x1F 驱动配置下发流程。 */
    fun driverConfig(): DriverConfigFlow = DriverConfigFlow(session)
}
