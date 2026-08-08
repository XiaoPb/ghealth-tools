package com.ghealth.tools.feature.factory.engine

import com.ghealth.tools.ble.connection.BleConnectionManager
import com.ghealth.tools.ble.protocol.gh3036.KEY_DOWNLOAD_CONFIG
import com.ghealth.tools.ble.protocol.gh3036.KEY_F_GET_MODE
import com.ghealth.tools.ble.protocol.gh3036.KEY_F_SET_MODE
import com.ghealth.tools.ble.protocol.gh3036.KEY_GH3X_REGS_LIST_WRITE_CMD
import com.ghealth.tools.ble.protocol.gh3036.KEY_GH3X_REGS_READ_CMD
import com.ghealth.tools.ble.protocol.gh3036.KEY_GH3X_SW_FUNCTION_CMD
import com.ghealth.tools.ble.protocol.gh3036.KEY_GH_SET_WORK_MODE_CMD
import com.ghealth.tools.ble.protocol.gh3036.CommandPayloadBuilder
import com.ghealth.tools.ble.protocol.gh3036.Gh3036CommandMeta
import com.ghealth.tools.ble.protocol.gh3036.RegisterCommandPayloadBuilder
import com.ghealth.tools.ble.protocol.rpccore.Package
import com.ghealth.tools.feature.factory.model.ComputeMode
import com.ghealth.tools.feature.factory.model.FactoryConfig
import com.ghealth.tools.feature.factory.model.RegisterConfig
import com.ghealth.tools.feature.factory.model.TestDef
import com.ghealth.tools.feature.factory.model.RegEntry
import com.ghealth.tools.feature.factory.model.TestResult
import com.ghealth.tools.feature.factory.model.TestType
import com.ghealth.tools.feature.factory.model.ThresholdOperator
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

sealed class TestEngineEvent {
    data class StepStarted(val description: String) : TestEngineEvent()
    data class Progress(val currentStep: Int, val totalSteps: Int) : TestEngineEvent()
    data class TestCompleted(val type: TestType, val results: List<TestResult>) : TestEngineEvent()
    data class LogMessage(val level: LogLevel, val message: String) : TestEngineEvent()
    data object ShowEnvironmentSwitchDialog : TestEngineEvent()
    data object ShowBluetoothUnstableDialog : TestEngineEvent()
    data class ComputationMode(val mode: ComputeMode) : TestEngineEvent()
    data class SequenceCompleted(val overallPassed: Boolean, val errorCodes: List<Int>) :
        TestEngineEvent()

    data class SequenceFailed(val error: String) : TestEngineEvent()
}

/** CHIP_INIT 单测执行结果 + 是否触发 App 端全局回退。 */
private data class SingleTestOutcome(
    val results: List<TestResult>?,
    val fallbackTriggered: Boolean = false
)

enum class LogLevel { INFO, WARN, ERROR }

@Singleton
class FactoryTestEngine @Inject constructor(
    private val connectionManager: BleConnectionManager,
    private val rawDataCollector: TestRawDataCollector,
    private val appSideEvaluator: AppSideTestEvaluator,
    private val efuseReader: EfuseReader
) {

    private val environmentResumeMutex = Mutex(locked = true)

    fun resumeAfterEnvironmentSwitch() {
        if (environmentResumeMutex.isLocked) {
            environmentResumeMutex.unlock()
        }
    }

    suspend fun runTestSequence(
        deviceAddress: String,
        chip: String,
        factoryConfig: FactoryConfig,
        registerConfigs: Map<String, RegisterConfig>,
        onEvent: suspend (TestEngineEvent) -> Unit
    ) {
        val failAction = factoryConfig.global.failAction
        val totalSteps = computeTotalSteps(factoryConfig)
        var currentStep = 0
        val allResults = mutableMapOf<TestType, List<TestResult>>()
        var appSideFallback = false
        var bluetoothUnstableNoticeShown = false
        val notifyBluetoothUnstable: suspend () -> Unit = {
            if (!bluetoothUnstableNoticeShown) {
                bluetoothUnstableNoticeShown = true
                onEvent(TestEngineEvent.ShowBluetoothUnstableDialog)
            }
        }

        try {
            // Step 1: Enter factory mode
            currentStep++
            onEvent(TestEngineEvent.StepStarted("进入MCU在线模式"))
            onEvent(TestEngineEvent.Progress(currentStep, totalSteps))
            val enterResult = sendSimpleCommand(
                deviceAddress, KEY_GH_SET_WORK_MODE_CMD,
                Package.packU8(2)
            )
            if (enterResult.isFailure) {
                onEvent(TestEngineEvent.LogMessage(LogLevel.ERROR, "进入MCU在线模式失败: ${enterResult.exceptionOrNull()?.message}"))
                if (failAction == "abort") {
                    onEvent(TestEngineEvent.SequenceFailed("进入MCU在线模式失败"))
                    return
                }
            }
            onEvent(TestEngineEvent.LogMessage(LogLevel.INFO, "已进入MCU在线模式"))

            // Step 2: CHIP_INIT
            val chipInitOutcome = runSingleTest(
                deviceAddress, chip, factoryConfig, TestType.CHIP_INIT,
                registerConfigs, onEvent, currentStep, totalSteps
            )
            if (chipInitOutcome.fallbackTriggered) {
                appSideFallback = true
                onEvent(TestEngineEvent.LogMessage(LogLevel.WARN,
                    "检测到对端无产测逻辑，后续测试切换为 App 端计算"))
            }
            val chipInitResults = chipInitOutcome.results
            if (chipInitResults != null) {
                currentStep++
                allResults[TestType.CHIP_INIT] = chipInitResults
            } else if (failAction == "abort") return

            // 计算模式在 CHIP_INIT 一次性确定（MCU / App 端回退），供结果区顶部标签展示
            onEvent(TestEngineEvent.ComputationMode(
                if (appSideFallback) ComputeMode.APP else ComputeMode.MCU
            ))

            // Step 3: CHIP_UID
            if (factoryConfig.tests["chip_uid"]?.enabled != false) {
                val chipUidResults = runUidTest(
                    deviceAddress, chip, appSideFallback, onEvent, currentStep, totalSteps
                )
                if (chipUidResults != null) {
                    currentStep++
                    allResults[TestType.CHIP_UID] = chipUidResults
                } else if (failAction == "abort") return
            }

            // Step 4: Run each hardware test in order
            val hwTestOrder = listOf(TestType.BASE_NOISE, TestType.PPG_NOISE, TestType.LPCTR, TestType.LPLCTR)

            for (testType in hwTestOrder) {
                val testDef = factoryConfig.tests[testType.name.lowercase()]
                if (testDef == null || !testDef.enabled) continue

                currentStep++
                val results = runHardwareTest(
                    deviceAddress, chip, factoryConfig, testType,
                    registerConfigs[testType.name.lowercase()],
                    appSideFallback, notifyBluetoothUnstable, onEvent, currentStep, totalSteps
                )
                if (results != null) {
                    allResults[testType] = results
                } else if (failAction == "abort") return

                // Environment switch between LPCTR and LPLCTR
                if (testType == TestType.LPCTR && hwTestOrder.contains(TestType.LPLCTR)) {
                    val lplctrDef = factoryConfig.tests[TestType.LPLCTR.name.lowercase()]
                    if (lplctrDef != null && lplctrDef.enabled) {
                        onEvent(TestEngineEvent.LogMessage(LogLevel.INFO, "请切换测试环境"))
                        onEvent(TestEngineEvent.ShowEnvironmentSwitchDialog)
                        environmentResumeMutex.lock()
                        onEvent(TestEngineEvent.LogMessage(LogLevel.INFO, "环境切换完成，继续测试"))
                    }
                }
            }

            // Step 5: Exit factory mode
            currentStep++
            onEvent(TestEngineEvent.StepStarted("退出MCU在线模式"))
            onEvent(TestEngineEvent.Progress(currentStep, totalSteps))
            val exitResult = sendSimpleCommand(
                deviceAddress, KEY_GH_SET_WORK_MODE_CMD,
                Package.packU8(0)
            )
            if (exitResult.isSuccess) {
                onEvent(TestEngineEvent.LogMessage(LogLevel.INFO, "已退出MCU在线模式"))
            }

            // Collect all error codes
            val errorCodes = allResults.values.flatten()
                .filter { !it.passed }
                .map { it.errorCodeComputed }
            val overallPassed = errorCodes.isEmpty() && allResults.isNotEmpty()

            onEvent(TestEngineEvent.SequenceCompleted(overallPassed, errorCodes))

        } catch (e: Exception) {
            onEvent(TestEngineEvent.LogMessage(LogLevel.ERROR, "测试异常: ${e.message}"))
            onEvent(TestEngineEvent.SequenceFailed("测试异常: ${e.message}"))

            // Try to exit factory mode on error
            try {
                sendSimpleCommand(deviceAddress, KEY_GH_SET_WORK_MODE_CMD, Package.packU8(0))
            } catch (_: Exception) {}
        }
    }

    private fun computeTotalSteps(factoryConfig: FactoryConfig): Int {
        var steps = 2 // enter + exit
        if (factoryConfig.tests["chip_init"]?.enabled != false) steps++
        if (factoryConfig.tests["chip_uid"]?.enabled != false) steps++
        listOf("base_noise", "ppg_noise", "lpctr", "lplctr").forEach { key ->
            if (factoryConfig.tests[key]?.enabled == true) steps++
        }
        return steps
    }

    private suspend fun runSingleTest(
        deviceAddress: String,
        chip: String,
        factoryConfig: FactoryConfig,
        testType: TestType,
        registerConfigs: Map<String, RegisterConfig>,
        onEvent: suspend (TestEngineEvent) -> Unit,
        stepIndex: Int,
        totalSteps: Int
    ): SingleTestOutcome {
        val testKey = testType.name.lowercase()
        val testDef = factoryConfig.tests[testKey] ?: return SingleTestOutcome(null)
        if (!testDef.enabled) return SingleTestOutcome(null)

        onEvent(TestEngineEvent.StepStarted(testType.displayName))
        onEvent(TestEngineEvent.Progress(stepIndex, totalSteps))
        onEvent(TestEngineEvent.LogMessage(LogLevel.INFO, "开始: ${testType.displayName}"))

        var fallbackTriggered = false

        // F_SetMode
        val setResult = sendSimpleCommand(
            deviceAddress, KEY_F_SET_MODE,
            Package.packU8(testType.mode.toByte())
        )
        if (setResult.isFailure) {
            onEvent(TestEngineEvent.LogMessage(LogLevel.WARN,
                "${testType.displayName}: F_SetMode 失败，切换为 App 端计算模式"))
            fallbackTriggered = true
        } else {
            delay(500)

            // F_GetMode
            val getResult = sendSimpleCommand(
                deviceAddress, KEY_F_GET_MODE,
                Package.packU8(testType.mode.toByte())
            )
            if (getResult.isFailure) {
                onEvent(TestEngineEvent.LogMessage(LogLevel.WARN,
                    "${testType.displayName}: F_GetMode 失败，切换为 App 端计算模式"))
                fallbackTriggered = true
            } else {
                val channelValues = parseU16ArrayResponse(getResult.getOrThrow())
                val actualChannels = channelValues.size
                val maxChannels = testDef.channels

                if (actualChannels == 0) {
                    onEvent(TestEngineEvent.LogMessage(LogLevel.WARN,
                        "${testType.displayName}: 设备未返回通道数据，触发全局 App 端回退"))
                    fallbackTriggered = true
                } else {
                    if (actualChannels < maxChannels) {
                        onEvent(TestEngineEvent.LogMessage(LogLevel.INFO,
                            "${testType.displayName}: 配置最大${maxChannels}通道，设备返回${actualChannels}通道，以实际通道为准"))
                    } else if (actualChannels > maxChannels) {
                        onEvent(TestEngineEvent.LogMessage(LogLevel.WARN,
                            "${testType.displayName}: 配置最大${maxChannels}通道，设备实际返回${actualChannels}通道"))
                    }

                    val thresholdDef = testDef.globalThreshold
                    val results = mutableListOf<TestResult>()
                    for (ch in 0 until actualChannels) {
                        val value = channelValues[ch]
                        val passed = if (thresholdDef != null) {
                            ThresholdOperator.fromKey(thresholdDef.operator).evaluate(value, thresholdDef)
                        } else true
                        results.add(
                            TestResult(
                                testType = testType,
                                channelIndex = ch,
                                value = value,
                                unit = testDef.unit,
                                threshold = if (thresholdDef != null)
                                    ThresholdOperator.fromKey(thresholdDef.operator).formatThreshold(thresholdDef)
                                else "-",
                                passed = passed
                            )
                        )
                    }
                    val passCount = results.count { it.passed }
                    onEvent(TestEngineEvent.LogMessage(LogLevel.INFO,
                        "${testType.displayName}: $passCount/${results.size} 通道通过"))
                    onEvent(TestEngineEvent.TestCompleted(testType, results))
                    return SingleTestOutcome(results, fallbackTriggered = false)
                }
            }
        }

        // 回退触发：寄存器读写校验判定 CHIP_INIT，避免空窗口
        val readBack = verifyChipCommunication(deviceAddress, onEvent)
        val passed = readBack == CHIP_COMM_CHECK_REG_VALUE
        val result = TestResult(
            testType = testType,
            channelIndex = 0,
            value = if (passed) readBack ?: 0 else 0,
            unit = testDef.unit,
            threshold = if (readBack == null) "-" else "=0x%04X".format(CHIP_COMM_CHECK_REG_VALUE),
            passed = passed,
            displayValue = readBack?.let { "0x%04X".format(it) }
        )
        onEvent(TestEngineEvent.LogMessage(
            if (passed) LogLevel.INFO else LogLevel.ERROR,
            "${testType.displayName}: 寄存器读写校验${if (passed) "通过" else "失败"}" +
                (if (readBack == null) "（写入或回读失败）" else "（回读0x%04X，期望0x%04X）".format(readBack, CHIP_COMM_CHECK_REG_VALUE))
        ))
        onEvent(TestEngineEvent.TestCompleted(testType, listOf(result)))
        return SingleTestOutcome(listOf(result), fallbackTriggered = true)
    }

    private suspend fun runUidTest(
        deviceAddress: String,
        chip: String,
        appSideFallback: Boolean,
        onEvent: suspend (TestEngineEvent) -> Unit,
        stepIndex: Int,
        totalSteps: Int
    ): List<TestResult>? {
        val testType = TestType.CHIP_UID
        onEvent(TestEngineEvent.StepStarted(testType.displayName))
        onEvent(TestEngineEvent.Progress(stepIndex, totalSteps))
        onEvent(TestEngineEvent.LogMessage(LogLevel.INFO,
            "开始: ${testType.displayName}" + if (appSideFallback) "（App 端计算模式）" else ""))

        val rawBytes: ByteArray
        if (appSideFallback) {
            // 回退模式：跳过 F_SetMode/F_GetMode，直接读 eFuse
            onEvent(TestEngineEvent.LogMessage(LogLevel.INFO,
                "${testType.displayName}: App 端计算模式，直接读取 eFuse"))
            rawBytes = readUuidFromEfuse(deviceAddress, chip, onEvent) ?: return failedUidResults(testType, onEvent)
        } else {
            // F_SetMode
            val setResult = sendSimpleCommand(
                deviceAddress, KEY_F_SET_MODE,
                Package.packU8(testType.mode.toByte())
            )
            if (setResult.isFailure) {
                onEvent(TestEngineEvent.LogMessage(LogLevel.ERROR,
                    "${testType.displayName}: F_SetMode 失败，MCU 模式不允许中途回退，本项判定 FAIL"))
                return failedUidResults(testType, onEvent)
            }
            delay(500)

            // F_GetMode —— 非回退模式不允许中途回退：失败/不足 32 字节直接 FAIL
            val getResult = sendSimpleCommand(
                deviceAddress, KEY_F_GET_MODE,
                Package.packU8(testType.mode.toByte())
            )
            if (getResult.isFailure) {
                onEvent(TestEngineEvent.LogMessage(LogLevel.ERROR,
                    "${testType.displayName}: F_GetMode 失败，MCU 模式不允许中途回退，本项判定 FAIL"))
                return failedUidResults(testType, onEvent)
            }
            val u16Values = parseU16ArrayResponse(getResult.getOrThrow())
            if (u16Values.size < 16) {
                onEvent(TestEngineEvent.LogMessage(LogLevel.ERROR,
                    "${testType.displayName}: F_GetMode 返回 ${u16Values.size * 2} 字节，不足32字节，MCU 模式不允许中途回退，本项判定 FAIL"))
                return failedUidResults(testType, onEvent)
            }
            rawBytes = ByteArray(u16Values.size * 2)
            for (i in u16Values.indices) {
                rawBytes[i * 2] = (u16Values[i] and 0xFF).toByte()
                rawBytes[i * 2 + 1] = ((u16Values[i] shr 8) and 0xFF).toByte()
            }
        }

        // Two 128-bit UUIDs
        val uuid1 = formatUuid128(rawBytes.copyOfRange(0, 16))
        val uuid2 = formatUuid128(rawBytes.copyOfRange(16, 32))
        val passed1 = !rawBytes.copyOfRange(0, 16).all { it == 0.toByte() }
        val passed2 = !rawBytes.copyOfRange(16, 32).all { it == 0.toByte() }

        val results = listOf(
            TestResult(
                testType = testType,
                channelIndex = 0,
                value = if (passed1) 1 else 0,
                unit = "",
                threshold = "非全0",
                passed = passed1,
                displayValue = uuid1
            ),
            TestResult(
                testType = testType,
                channelIndex = 1,
                value = if (passed2) 1 else 0,
                unit = "",
                threshold = "非全0",
                passed = passed2,
                displayValue = uuid2
            )
        )
        val passCount = results.count { it.passed }
        onEvent(TestEngineEvent.LogMessage(LogLevel.INFO,
            "${testType.displayName}: $passCount/${results.size} UUID通过"))
        onEvent(TestEngineEvent.LogMessage(
            if (passed1) LogLevel.INFO else LogLevel.WARN,
            "UUID1: $uuid1 ${if (passed1) "PASS" else "FAIL（全 0）"}"))
        onEvent(TestEngineEvent.LogMessage(
            if (passed2) LogLevel.INFO else LogLevel.WARN,
            "UUID2: $uuid2 ${if (passed2) "PASS" else "FAIL（全 0）"}"))

        onEvent(TestEngineEvent.TestCompleted(testType, results))
        return results
    }

    /** 通过上位机寄存器指令读取 eFuse 256bit 作为 UUID 载荷；非 GH3036 系列或读取失败返回 null。 */
    private suspend fun readUuidFromEfuse(
        deviceAddress: String,
        chip: String,
        onEvent: suspend (TestEngineEvent) -> Unit
    ): ByteArray? {
        val normalized = chip.lowercase()
        if (!normalized.startsWith("gh3036") && !normalized.startsWith("gh3038")) {
            onEvent(TestEngineEvent.LogMessage(LogLevel.WARN,
                "${TestType.CHIP_UID.displayName}: 非 GH3036 系列芯片（$chip）暂不支持 eFuse 回退"))
            return null
        }
        val bytes = efuseReader.readAll(deviceAddress)
        if (bytes == null) {
            onEvent(TestEngineEvent.LogMessage(LogLevel.ERROR,
                "${TestType.CHIP_UID.displayName}: eFuse 读取失败"))
        } else {
            onEvent(TestEngineEvent.LogMessage(LogLevel.INFO,
                "${TestType.CHIP_UID.displayName}: eFuse 读取成功: ${bytes.joinToString("") { "%02X".format(it) }}"))
        }
        return bytes
    }

    /** eFuse 回退失败时产出 2 个合成 FAIL 结果，避免空窗口静默通过。 */
    private suspend fun failedUidResults(
        testType: TestType,
        onEvent: suspend (TestEngineEvent) -> Unit
    ): List<TestResult> {
        val failed = listOf(
            TestResult(testType = testType, channelIndex = 0, value = 0, unit = "", threshold = "-", passed = false),
            TestResult(testType = testType, channelIndex = 1, value = 0, unit = "", threshold = "-", passed = false)
        )
        onEvent(TestEngineEvent.TestCompleted(testType, failed))
        return failed
    }

    private fun formatUuid128(bytes: ByteArray): String {
        val hex = bytes.joinToString("") { "%02X".format(it) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20, 32)}"
    }

    private suspend fun runHardwareTest(
        deviceAddress: String,
        chip: String,
        factoryConfig: FactoryConfig,
        testType: TestType,
        registerConfig: RegisterConfig?,
        appSideFallback: Boolean,
        notifyBluetoothUnstable: suspend () -> Unit,
        onEvent: suspend (TestEngineEvent) -> Unit,
        stepIndex: Int,
        totalSteps: Int
    ): List<TestResult>? {
        val testKey = testType.name.lowercase()
        val testDef = factoryConfig.tests[testKey] ?: return null
        if (!testDef.enabled) return null

        onEvent(TestEngineEvent.StepStarted(testType.displayName))
        onEvent(TestEngineEvent.Progress(stepIndex, totalSteps))
        onEvent(TestEngineEvent.LogMessage(LogLevel.INFO,
            "开始: ${testType.displayName}" + if (appSideFallback) "（App 端计算模式）" else ""))

        // registerConfig is optional but preferred; warn if missing
        if (registerConfig == null || registerConfig.registers.isEmpty()) {
            onEvent(TestEngineEvent.LogMessage(LogLevel.WARN,
                "${testType.displayName}: 无寄存器配置文件，F_GetMode 可能因未提前配置寄存器而返回异常值"))
        }

        // F_SetMode —— App 端计算模式下跳过（对端无产测逻辑）
        if (!appSideFallback) {
            val setResult = sendSimpleCommand(
                deviceAddress, KEY_F_SET_MODE,
                Package.packU8(testType.mode.toByte())
            )
            if (setResult.isFailure) {
                onEvent(TestEngineEvent.LogMessage(LogLevel.ERROR, "${testType.displayName}: F_SetMode 失败"))
                return null
            }
        }

        // download_config stage 0
        val dc0Result = sendSimpleCommand(
            deviceAddress, KEY_DOWNLOAD_CONFIG,
            Package.packU8(0)
        )
        if (dc0Result.isFailure) {
            onEvent(TestEngineEvent.LogMessage(LogLevel.ERROR, "${testType.displayName}: download_config(0) 失败"))
            return null
        }

        // Write register list if available
        if (registerConfig != null && registerConfig.registers.isNotEmpty()) {
            val interleaved = RegEntry.toInterleavedArray(registerConfig.registers)
            val regsParam = RegisterCommandPayloadBuilder.buildU16ArrayPayload(interleaved)
            val regResult = sendSimpleCommand(
                deviceAddress, KEY_GH3X_REGS_LIST_WRITE_CMD, regsParam
            )
            if (regResult.isFailure) {
                onEvent(TestEngineEvent.LogMessage(LogLevel.ERROR,
                    "${testType.displayName}: RegsListWrite 失败"))
                return null
            }
        }

        // download_config stage 1
        val dc1Result = sendSimpleCommand(
            deviceAddress, KEY_DOWNLOAD_CONFIG,
            Package.packU8(1)
        )
        if (dc1Result.isFailure) {
            onEvent(TestEngineEvent.LogMessage(LogLevel.ERROR, "${testType.displayName}: download_config(1) 失败"))
            return null
        }

        // Start function TEST1，同时采集 TEST1 原始帧（F_GetMode 无数据时 App 端计算回退用）
        val test1FuncMode = getTest1FuncMode(chip)
        rawDataCollector.start(deviceAddress)
        var getResult: Result<ByteArray>? = null
        var collected = CollectedRawData.EMPTY
        try {
            val startFuncResult = sendSimpleCommand(
                deviceAddress, KEY_GH3X_SW_FUNCTION_CMD,
                Package.packU32(test1FuncMode) + Package.packU8(0)
            )
            if (startFuncResult.isFailure) {
                onEvent(TestEngineEvent.LogMessage(LogLevel.ERROR, "${testType.displayName}: SwFunctionCmd(start) 失败"))
                return null
            }

            if (appSideFallback) {
                // App 端计算模式：按采集参数轮询，满足条件立即停止；超时 → 蓝牙不稳定 FAIL
                val spec = CollectionSpec.resolve(testDef.compute, testType)
                onEvent(TestEngineEvent.LogMessage(LogLevel.INFO,
                    "${testType.displayName}: 采集参数 skip=${spec.skipNumber} min=${spec.minNumber} timeout=${spec.timeoutMs}ms"))
                var waited = 0L
                var complete = false
                while (waited < spec.timeoutMs) {
                    if (rawDataCollector.isCollectionComplete(spec)) {
                        complete = true
                        break
                    }
                    delay(COLLECT_POLL_INTERVAL_MS)
                    waited += COLLECT_POLL_INTERVAL_MS
                }
                if (!complete) {
                    onEvent(TestEngineEvent.LogMessage(LogLevel.ERROR,
                        "${testType.displayName}: 蓝牙连接不稳定，数据采集超时（${spec.timeoutMs}ms），本项判定 FAIL"))
                    notifyBluetoothUnstable()
                    sendSimpleCommand(deviceAddress, KEY_GH3X_SW_FUNCTION_CMD,
                        Package.packU32(test1FuncMode) + Package.packU8(1)) // 尽力停止
                    val failed = syntheticFail(testType, testDef)
                    onEvent(TestEngineEvent.TestCompleted(testType, failed))
                    return failed
                }
            } else {
                // 非回退模式：固定等待 3 秒
                delay(3000)
            }

            // Stop function TEST1
            val stopFuncResult = sendSimpleCommand(
                deviceAddress, KEY_GH3X_SW_FUNCTION_CMD,
                Package.packU32(test1FuncMode) + Package.packU8(1)
            )
            if (stopFuncResult.isFailure) {
                onEvent(TestEngineEvent.LogMessage(LogLevel.WARN, "${testType.displayName}: SwFunctionCmd(stop) 失败"))
            }

            // F_GetMode —— App 端计算模式下跳过
            if (!appSideFallback) {
                getResult = sendSimpleCommand(
                    deviceAddress, KEY_F_GET_MODE,
                    Package.packU8(testType.mode.toByte())
                )
                if (getResult.isFailure) {
                    onEvent(TestEngineEvent.LogMessage(LogLevel.ERROR,
                        "${testType.displayName}: F_GetMode 失败，MCU 模式不允许中途回退，本项判定 FAIL"))
                    val failed = failedResults(testType, testDef)
                    onEvent(TestEngineEvent.TestCompleted(testType, failed))
                    return failed
                }
            }
        } finally {
            collected = rawDataCollector.stop()
        }
        onEvent(TestEngineEvent.LogMessage(LogLevel.INFO,
            "${testType.displayName}: 采集结束，帧数=${collected.frameCnts.size}，通道数=${collected.channelCount}" +
                "（rawdata=${collected.rawdataByChannel.size}，ipdPa=${collected.ipdPaByChannel.size}，led=${collected.ledCurrentSumMaByChannel.size}）"))

        // Evaluate results
        if (appSideFallback) {
            return evaluateAppSide(testType, testDef, collected, chip,
                "App 端计算模式，跳过 F_GetMode 直接计算", onEvent)
        }

        val channelValues = parseU16ArrayResponse(getResult!!.getOrThrow())
        val actualChannels = channelValues.size
        val maxChannels = testDef.channels

        if (actualChannels == 0) {
            onEvent(TestEngineEvent.LogMessage(LogLevel.ERROR,
                "${testType.displayName}: 设备未返回产测数据，MCU 模式不允许中途回退，本项判定 FAIL"))
            val failed = failedResults(testType, testDef)
            onEvent(TestEngineEvent.TestCompleted(testType, failed))
            return failed
        }

        if (actualChannels < maxChannels) {
            onEvent(TestEngineEvent.LogMessage(LogLevel.INFO,
                "${testType.displayName}: 配置最大${maxChannels}通道，设备返回${actualChannels}通道，以实际通道为准"))
        } else if (actualChannels > maxChannels) {
            onEvent(TestEngineEvent.LogMessage(LogLevel.WARN,
                "${testType.displayName}: 配置最大${maxChannels}通道，设备实际返回${actualChannels}通道"))
        }

        val thresholdDef = testDef.globalThreshold
        val results = mutableListOf<TestResult>()

        for (ch in 0 until actualChannels) {
            val value = channelValues[ch]
            val passed = if (thresholdDef != null) {
                ThresholdOperator.fromKey(thresholdDef.operator).evaluate(value, thresholdDef)
            } else true

            results.add(
                TestResult(
                    testType = testType,
                    channelIndex = ch,
                    value = value,
                    unit = testDef.unit,
                    threshold = if (thresholdDef != null)
                        ThresholdOperator.fromKey(thresholdDef.operator).formatThreshold(thresholdDef)
                    else "-",
                    passed = passed
                )
            )
        }

        val passCount = results.count { it.passed }
        onEvent(TestEngineEvent.LogMessage(LogLevel.INFO,
            "${testType.displayName}: $passCount/${results.size} 通道通过"))

        onEvent(TestEngineEvent.TestCompleted(testType, results))
        return results
    }

    /** App 端计算并上报；未采集到数据时产出合成 FAIL。reason 用于 LOG 前缀。 */
    private suspend fun evaluateAppSide(
        testType: TestType,
        testDef: TestDef,
        data: CollectedRawData,
        chip: String,
        reason: String,
        onEvent: suspend (TestEngineEvent) -> Unit
    ): List<TestResult> {
        onEvent(TestEngineEvent.LogMessage(LogLevel.WARN, "${testType.displayName}: $reason"))
        val logBuffer = mutableListOf<Pair<LogLevel, String>>()
        val computed = appSideEvaluator.evaluate(
            testType = testType,
            testDef = testDef,
            data = data,
            chip = chip,
            log = { level, message -> logBuffer.add(level to message) }
        )
        logBuffer.forEach { (level, message) -> onEvent(TestEngineEvent.LogMessage(level, message)) }
        if (computed == null) {
            onEvent(TestEngineEvent.LogMessage(LogLevel.ERROR,
                "${testType.displayName}: 未采集到 TEST1 原始数据，App 端计算失败"))
            val failed = syntheticFail(testType, testDef)
            onEvent(TestEngineEvent.TestCompleted(testType, failed))
            return failed
        }
        onEvent(TestEngineEvent.TestCompleted(testType, computed))
        return computed
    }

    /** 非回退模式中途异常时按配置通道数产出全通道 FAIL，避免空窗口静默跳过。 */
    private fun failedResults(testType: TestType, testDef: TestDef): List<TestResult> =
        (0 until testDef.channels.coerceAtLeast(1)).map { ch ->
            TestResult(
                testType = testType,
                channelIndex = ch,
                value = 0,
                unit = testDef.unit,
                threshold = "-",
                passed = false
            )
        }

    /** App 端计算不可用或采集超时时产出合成 FAIL（单通道），避免空窗口静默通过。 */
    private fun syntheticFail(testType: TestType, testDef: TestDef): List<TestResult> =
        listOf(
            TestResult(
                testType = testType,
                channelIndex = 0,
                value = 0,
                unit = testDef.unit,
                threshold = "-",
                passed = false
            )
        )

    private suspend fun sendSimpleCommand(
        deviceAddress: String,
        key: String,
        param: ByteArray
    ): Result<ByteArray> {
        return connectionManager.sendCommand(deviceAddress, key, param)
    }

    private fun parseU16ArrayResponse(data: ByteArray): IntArray {
        if (data.size < 2) return IntArray(0)
        val len = ((data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8))
        if (data.size < 2 + len * 2) return IntArray(0)
        return IntArray(len) { i ->
            val offset = 2 + i * 2
            (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
        }
    }

    /** 写入通信校验寄存器并回读，返回回读值；写入/读取任一步失败返回 null。 */
    private suspend fun verifyChipCommunication(
        deviceAddress: String,
        onEvent: suspend (TestEngineEvent) -> Unit
    ): Int? {
        val writeParam = RegisterCommandPayloadBuilder.buildU16ArrayPayload(
            intArrayOf(CHIP_COMM_CHECK_REG_ADDR, CHIP_COMM_CHECK_REG_VALUE)
        )
        val writeResult = sendSimpleCommand(deviceAddress, KEY_GH3X_REGS_LIST_WRITE_CMD, writeParam)
        if (writeResult.isFailure) {
            onEvent(TestEngineEvent.LogMessage(LogLevel.ERROR,
                "${TestType.CHIP_INIT.displayName}: 通信校验寄存器写入失败"))
            return null
        }
        val readParam = CommandPayloadBuilder.buildMultiRegReadParams(
            CHIP_COMM_CHECK_REG_ADDR.toString(16).padStart(4, '0'), "1"
        )
        val readResult = sendSimpleCommand(deviceAddress, KEY_GH3X_REGS_READ_CMD, readParam)
        if (readResult.isFailure) {
            onEvent(TestEngineEvent.LogMessage(LogLevel.ERROR,
                "${TestType.CHIP_INIT.displayName}: 通信校验寄存器读取失败"))
            return null
        }
        return parseU16ArrayResponse(readResult.getOrThrow()).firstOrNull()
    }

    private fun getTest1FuncMode(chip: String): Int {
        val bits = Gh3036CommandMeta.getFuncModeBits(chip)
        val test1Bit = bits.firstOrNull { it.name == "TEST1" } ?: return 0x40
        return 1 shl test1Bit.bit
    }

    companion object {
        /** CHIP_INIT 通信校验寄存器（来自 GH3036 产测配置）：FIFO_WATER_LINE:25, RG_FIFO_READ_INT_TIMER:0.4s */
        const val CHIP_COMM_CHECK_REG_ADDR = 0x0020
        const val CHIP_COMM_CHECK_REG_VALUE = 0x2919
        /** App 端计算采集轮询间隔 ms。 */
        const val COLLECT_POLL_INTERVAL_MS = 100L
    }

}
