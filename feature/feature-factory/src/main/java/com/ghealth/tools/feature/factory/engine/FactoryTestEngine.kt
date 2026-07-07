package com.ghealth.tools.feature.factory.engine

import com.ghealth.tools.ble.connection.BleConnectionManager
import com.ghealth.tools.ble.protocol.gh3036.KEY_DOWNLOAD_CONFIG
import com.ghealth.tools.ble.protocol.gh3036.KEY_F_GET_MODE
import com.ghealth.tools.ble.protocol.gh3036.KEY_F_SET_MODE
import com.ghealth.tools.ble.protocol.gh3036.KEY_GH3X_REGS_LIST_WRITE_CMD
import com.ghealth.tools.ble.protocol.gh3036.KEY_GH3X_SW_FUNCTION_CMD
import com.ghealth.tools.ble.protocol.gh3036.KEY_GH_SET_WORK_MODE_CMD
import com.ghealth.tools.ble.protocol.gh3036.Gh3036CommandMeta
import com.ghealth.tools.ble.protocol.gh3036.RegisterCommandPayloadBuilder
import com.ghealth.tools.ble.protocol.rpccore.Package
import com.ghealth.tools.feature.factory.model.FactoryConfig
import com.ghealth.tools.feature.factory.model.RegisterConfig
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
    data class SequenceCompleted(val overallPassed: Boolean, val errorCodes: List<Int>) :
        TestEngineEvent()

    data class SequenceFailed(val error: String) : TestEngineEvent()
}

enum class LogLevel { INFO, WARN, ERROR }

@Singleton
class FactoryTestEngine @Inject constructor(
    private val connectionManager: BleConnectionManager
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
            val chipInitResults = runSingleTest(
                deviceAddress, chip, factoryConfig, TestType.CHIP_INIT,
                registerConfigs, onEvent, currentStep, totalSteps
            )
            if (chipInitResults != null) {
                currentStep++
                allResults[TestType.CHIP_INIT] = chipInitResults
            } else if (failAction == "abort") return

            // Step 3: CHIP_UID
            val chipUidResults = runUidTest(
                deviceAddress, onEvent, currentStep, totalSteps
            )
            if (chipUidResults != null) {
                currentStep++
                allResults[TestType.CHIP_UID] = chipUidResults
            } else if (failAction == "abort") return

            // Step 4: Run each hardware test in order
            val hwTestOrder = listOf(TestType.BASE_NOISE, TestType.PPG_NOISE, TestType.LPCTR, TestType.LPLCTR)

            for (testType in hwTestOrder) {
                val testDef = factoryConfig.tests[testType.name.lowercase()]
                if (testDef == null || !testDef.enabled) continue

                currentStep++
                val results = runHardwareTest(
                    deviceAddress, chip, factoryConfig, testType,
                    registerConfigs[testType.name.lowercase()],
                    onEvent, currentStep, totalSteps
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
    ): List<TestResult>? {
        val testKey = testType.name.lowercase()
        val testDef = factoryConfig.tests[testKey] ?: return null
        if (!testDef.enabled) return null

        onEvent(TestEngineEvent.StepStarted(testType.displayName))
        onEvent(TestEngineEvent.Progress(stepIndex, totalSteps))
        onEvent(TestEngineEvent.LogMessage(LogLevel.INFO, "开始: ${testType.displayName}"))

        val results = mutableListOf<TestResult>()

        // F_SetMode
        val setResult = sendSimpleCommand(
            deviceAddress, KEY_F_SET_MODE,
            Package.packU8(testType.mode.toByte())
        )
        if (setResult.isFailure) {
            onEvent(TestEngineEvent.LogMessage(LogLevel.ERROR, "${testType.displayName}: F_SetMode 失败"))
            return null
        }

        delay(500)

        // F_GetMode
        val getResult = sendSimpleCommand(
            deviceAddress, KEY_F_GET_MODE,
            Package.packU8(testType.mode.toByte())
        )
        if (getResult.isFailure) {
            onEvent(TestEngineEvent.LogMessage(LogLevel.ERROR, "${testType.displayName}: F_GetMode 失败"))
            return null
        }

        val channelValues = parseU16ArrayResponse(getResult.getOrThrow())
        val actualChannels = channelValues.size
        val maxChannels = testDef.channels

        if (actualChannels == 0) {
            onEvent(TestEngineEvent.LogMessage(LogLevel.WARN, "${testType.displayName}: 设备未返回通道数据"))
            onEvent(TestEngineEvent.TestCompleted(testType, emptyList()))
            return emptyList()
        }

        if (actualChannels < maxChannels) {
            onEvent(TestEngineEvent.LogMessage(LogLevel.INFO,
                "${testType.displayName}: 配置最大${maxChannels}通道，设备返回${actualChannels}通道，以实际通道为准"))
        } else if (actualChannels > maxChannels) {
            onEvent(TestEngineEvent.LogMessage(LogLevel.WARN,
                "${testType.displayName}: 配置最大${maxChannels}通道，设备实际返回${actualChannels}通道"))
        }

        val thresholdDef = testDef.globalThreshold

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

    private suspend fun runUidTest(
        deviceAddress: String,
        onEvent: suspend (TestEngineEvent) -> Unit,
        stepIndex: Int,
        totalSteps: Int
    ): List<TestResult>? {
        val testType = TestType.CHIP_UID
        onEvent(TestEngineEvent.StepStarted(testType.displayName))
        onEvent(TestEngineEvent.Progress(stepIndex, totalSteps))
        onEvent(TestEngineEvent.LogMessage(LogLevel.INFO, "开始: ${testType.displayName}"))

        // F_SetMode
        val setResult = sendSimpleCommand(
            deviceAddress, KEY_F_SET_MODE,
            Package.packU8(testType.mode.toByte())
        )
        if (setResult.isFailure) {
            onEvent(TestEngineEvent.LogMessage(LogLevel.ERROR, "${testType.displayName}: F_SetMode 失败"))
            return null
        }

        delay(500)

        // F_GetMode — returns u16 array, each u16 is 2 bytes LE
        val getResult = sendSimpleCommand(
            deviceAddress, KEY_F_GET_MODE,
            Package.packU8(testType.mode.toByte())
        )
        if (getResult.isFailure) {
            onEvent(TestEngineEvent.LogMessage(LogLevel.ERROR, "${testType.displayName}: F_GetMode 失败"))
            return null
        }

        val u16Values = parseU16ArrayResponse(getResult.getOrThrow())
        if (u16Values.size < 16) {
            onEvent(TestEngineEvent.LogMessage(LogLevel.WARN,
                "${testType.displayName}: 预期32字节UUID数据，实际${u16Values.size * 2}字节"))
            return null
        }

        // Reconstruct 32 raw bytes from 16 u16 LE values
        val rawBytes = ByteArray(u16Values.size * 2)
        for (i in u16Values.indices) {
            rawBytes[i * 2] = (u16Values[i] and 0xFF).toByte()
            rawBytes[i * 2 + 1] = ((u16Values[i] shr 8) and 0xFF).toByte()
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
        if (passed1 && passed2) {
            onEvent(TestEngineEvent.LogMessage(LogLevel.INFO, "UUID1: $uuid1"))
            onEvent(TestEngineEvent.LogMessage(LogLevel.INFO, "UUID2: $uuid2"))
        }

        onEvent(TestEngineEvent.TestCompleted(testType, results))
        return results
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
        onEvent: suspend (TestEngineEvent) -> Unit,
        stepIndex: Int,
        totalSteps: Int
    ): List<TestResult>? {
        val testKey = testType.name.lowercase()
        val testDef = factoryConfig.tests[testKey] ?: return null
        if (!testDef.enabled) return null

        onEvent(TestEngineEvent.StepStarted(testType.displayName))
        onEvent(TestEngineEvent.Progress(stepIndex, totalSteps))
        onEvent(TestEngineEvent.LogMessage(LogLevel.INFO, "开始: ${testType.displayName}"))

        // registerConfig is optional but preferred; warn if missing
        if (registerConfig == null || registerConfig.registers.isEmpty()) {
            onEvent(TestEngineEvent.LogMessage(LogLevel.WARN,
                "${testType.displayName}: 无寄存器配置文件，F_GetMode 可能因未提前配置寄存器而返回异常值"))
        }

        // F_SetMode
        val setResult = sendSimpleCommand(
            deviceAddress, KEY_F_SET_MODE,
            Package.packU8(testType.mode.toByte())
        )
        if (setResult.isFailure) {
            onEvent(TestEngineEvent.LogMessage(LogLevel.ERROR, "${testType.displayName}: F_SetMode 失败"))
            return null
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

        // Start function TEST1
        val test1FuncMode = getTest1FuncMode(chip)
        val startFuncResult = sendSimpleCommand(
            deviceAddress, KEY_GH3X_SW_FUNCTION_CMD,
            Package.packU32(test1FuncMode) + Package.packU8(0)
        )
        if (startFuncResult.isFailure) {
            onEvent(TestEngineEvent.LogMessage(LogLevel.ERROR, "${testType.displayName}: SwFunctionCmd(start) 失败"))
            return null
        }

        // Wait 3 seconds
        delay(3000)

        // Stop function TEST1
        val stopFuncResult = sendSimpleCommand(
            deviceAddress, KEY_GH3X_SW_FUNCTION_CMD,
            Package.packU32(test1FuncMode) + Package.packU8(1)
        )
        if (stopFuncResult.isFailure) {
            onEvent(TestEngineEvent.LogMessage(LogLevel.WARN, "${testType.displayName}: SwFunctionCmd(stop) 失败"))
        }

        // F_GetMode
        val getResult = sendSimpleCommand(
            deviceAddress, KEY_F_GET_MODE,
            Package.packU8(testType.mode.toByte())
        )
        if (getResult.isFailure) {
            onEvent(TestEngineEvent.LogMessage(LogLevel.ERROR, "${testType.displayName}: F_GetMode 失败"))
            return null
        }

        // Evaluate results
        val channelValues = parseU16ArrayResponse(getResult.getOrThrow())
        val actualChannels = channelValues.size
        val maxChannels = testDef.channels

        if (actualChannels == 0) {
            onEvent(TestEngineEvent.LogMessage(LogLevel.WARN, "${testType.displayName}: 设备未返回通道数据"))
            onEvent(TestEngineEvent.TestCompleted(testType, emptyList()))
            return emptyList()
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

    private fun getTest1FuncMode(chip: String): Int {
        val bits = Gh3036CommandMeta.getFuncModeBits(chip)
        val test1Bit = bits.firstOrNull { it.name == "TEST1" } ?: return 0x40
        return 1 shl test1Bit.bit
    }

}
