package com.ghealth.tools.feature.factory.engine

import com.ghealth.tools.ble.connection.BleConnectionManager
import com.ghealth.tools.ble.protocol.gh3036.KEY_DOWNLOAD_CONFIG
import com.ghealth.tools.ble.protocol.gh3036.KEY_F_GET_MODE
import com.ghealth.tools.ble.protocol.gh3036.KEY_F_SET_MODE
import com.ghealth.tools.ble.protocol.gh3036.KEY_GH3X_REGS_LIST_WRITE_CMD
import com.ghealth.tools.ble.protocol.gh3036.KEY_GH3X_REGS_READ_CMD
import com.ghealth.tools.ble.protocol.gh3036.KEY_GH3X_SW_FUNCTION_CMD
import com.ghealth.tools.ble.protocol.gh3036.RegisterCommandPayloadBuilder
import com.ghealth.tools.feature.factory.model.AppComputeConfig
import com.ghealth.tools.feature.factory.model.FactoryConfig
import com.ghealth.tools.feature.factory.model.TestDef
import com.ghealth.tools.feature.factory.model.TestResult
import com.ghealth.tools.feature.factory.model.TestType
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FactoryTestEngineTest {

    private val baseNoiseDef = TestDef(
        enabled = true,
        mode = TestType.BASE_NOISE.mode,
        channels = 1,
        unit = "dB"
    )

    private val baseNoiseConfig = FactoryConfig(
        project = "test",
        tests = mapOf("base_noise" to baseNoiseDef)
    )

    private val chipInitConfig = FactoryConfig(
        project = "test",
        tests = mapOf(
            "chip_init" to TestDef(
                enabled = true,
                mode = TestType.CHIP_INIT.mode,
                channels = 1,
                unit = "status"
            )
        )
    )

    /** F_GetMode 返回空（长度字段 0），其余命令成功返回空数组。 */
    private fun defaultManager(): BleConnectionManager {
        val manager = mockk<BleConnectionManager>()
        coEvery { manager.sendCommand(any(), any(), any()) } returns Result.success(ByteArray(0))
        coEvery { manager.sendCommand(any(), KEY_F_GET_MODE, any()) } returns Result.success(byteArrayOf(0, 0))
        return manager
    }

    private fun defaultCollector(): TestRawDataCollector {
        val collector = mockk<TestRawDataCollector>()
        every { collector.start(any()) } just Runs
        every { collector.stop() } returns CollectedRawData.EMPTY
        every { collector.isCollectionComplete(any()) } returns true
        return collector
    }

    /** eFuse 回退默认返回 32 字节非全 0，保证既有用例整体判定不变。 */
    private fun defaultEfuseReader(): EfuseReader {
        val reader = mockk<EfuseReader>()
        coEvery { reader.readAll(any()) } returns ByteArray(32) { (it + 1).toByte() }
        return reader
    }

    private fun newEngine(
        manager: BleConnectionManager,
        collector: TestRawDataCollector,
        evaluator: AppSideTestEvaluator
    ): FactoryTestEngine = FactoryTestEngine(manager, collector, evaluator, defaultEfuseReader())

    private suspend fun runSequence(
        engine: FactoryTestEngine,
        config: FactoryConfig = baseNoiseConfig,
        chip: String = "gh3036"
    ): List<TestEngineEvent> {
        val events = mutableListOf<TestEngineEvent>()
        engine.runTestSequence(
            deviceAddress = "AA:BB",
            chip = chip,
            factoryConfig = config,
            registerConfigs = emptyMap(),
            onEvent = { events.add(it) }
        )
        return events
    }

    @Test
    fun `F_GetMode 空数据时触发 App 端回退并上报评估器结果`() = runTest {
        val manager = defaultManager()
        val collector = defaultCollector()
        val evaluator = mockk<AppSideTestEvaluator>()
        val computed = listOf(
            TestResult(
                testType = TestType.BASE_NOISE,
                channelIndex = 0,
                value = 152,
                unit = "dB",
                threshold = "-",
                passed = true
            )
        )
        every { evaluator.evaluate(any(), any(), any(), any(), any()) } returns computed

        val events = runSequence(newEngine(manager, collector, evaluator))

        verify(exactly = 1) { evaluator.evaluate(any(), any(), any(), any(), any()) }
        // 非回退模式不轮询采集完成状态
        verify(exactly = 0) { collector.isCollectionComplete(any()) }
        val completed = events.filterIsInstance<TestEngineEvent.TestCompleted>()
            .single { it.type == TestType.BASE_NOISE }
        assertEquals(computed, completed.results)
        val sequence = events.filterIsInstance<TestEngineEvent.SequenceCompleted>().single()
        assertTrue(sequence.overallPassed)
        assertEquals(emptyList<Int>(), sequence.errorCodes)
    }

    @Test
    fun `评估器返回 null 时产出合成 FAIL 结果且整体不 PASS`() = runTest {
        val manager = defaultManager()
        val collector = defaultCollector()
        val evaluator = mockk<AppSideTestEvaluator>()
        every { evaluator.evaluate(any(), any(), any(), any(), any()) } returns null

        val events = runSequence(newEngine(manager, collector, evaluator))

        val completed = events.filterIsInstance<TestEngineEvent.TestCompleted>()
            .single { it.type == TestType.BASE_NOISE }
        assertEquals(1, completed.results.size)
        val failed = completed.results.single()
        assertEquals(TestType.BASE_NOISE, failed.testType)
        assertEquals(0, failed.channelIndex)
        assertEquals(0, failed.value)
        assertEquals("dB", failed.unit)
        assertEquals("-", failed.threshold)
        assertFalse(failed.passed)
        assertNull(failed.computedValue)
        assertTrue(
            events.filterIsInstance<TestEngineEvent.LogMessage>()
                .any { it.level == LogLevel.ERROR && it.message.contains("App 端计算失败") }
        )

        val sequence = events.filterIsInstance<TestEngineEvent.SequenceCompleted>().single()
        assertFalse(sequence.overallPassed)
        assertEquals(listOf(TestType.BASE_NOISE.errorBase), sequence.errorCodes)
    }

    @Test
    fun `F_GetMode 返回通道值时走原判定路径且不调用评估器`() = runTest {
        val manager = mockk<BleConnectionManager>()
        coEvery { manager.sendCommand(any(), any(), any()) } returns Result.success(ByteArray(0))
        coEvery { manager.sendCommand(any(), KEY_F_GET_MODE, any()) } returns Result.success(byteArrayOf(1, 0, 100, 0))
        val collector = defaultCollector()
        val evaluator = mockk<AppSideTestEvaluator>()

        val events = runSequence(newEngine(manager, collector, evaluator))

        verify(exactly = 0) { evaluator.evaluate(any(), any(), any(), any(), any()) }
        val completed = events.filterIsInstance<TestEngineEvent.TestCompleted>()
            .single { it.type == TestType.BASE_NOISE }
        val result = completed.results.single()
        assertTrue(result.passed)
        assertEquals(100, result.value)
        assertEquals(0, result.channelIndex)
        val sequence = events.filterIsInstance<TestEngineEvent.SequenceCompleted>().single()
        assertTrue(sequence.overallPassed)
    }

    @Test
    fun `CHIP_INIT 空数据时寄存器回读一致则 PASS`() = runTest {
        val manager = defaultManager()
        coEvery { manager.sendCommand(any(), KEY_GH3X_REGS_READ_CMD, any()) } returns
            Result.success(byteArrayOf(1, 0, 0x19, 0x29))
        val collector = defaultCollector()
        val evaluator = mockk<AppSideTestEvaluator>()

        val events = runSequence(
            newEngine(manager, collector, evaluator),
            config = chipInitConfig
        )

        coVerify(exactly = 1) {
            manager.sendCommand(
                any(),
                KEY_GH3X_REGS_LIST_WRITE_CMD,
                RegisterCommandPayloadBuilder.buildU16ArrayPayload(intArrayOf(0x0020, 0x2919))
            )
        }
        coVerify(exactly = 1) {
            manager.sendCommand(
                any(),
                KEY_GH3X_REGS_READ_CMD,
                byteArrayOf(0x20, 0x00, 0x01, 0x00, 0x00, 0x00)
            )
        }

        val completed = events.filterIsInstance<TestEngineEvent.TestCompleted>()
            .single { it.type == TestType.CHIP_INIT }
        assertEquals(1, completed.results.size)
        val result = completed.results.single()
        assertTrue(result.passed)
        assertEquals(0x2919, result.value)
        assertEquals("0x2919", result.displayValue)
        assertEquals("=0x2919", result.threshold)
        assertEquals(0, result.channelIndex)
        val sequence = events.filterIsInstance<TestEngineEvent.SequenceCompleted>().single()
        assertTrue(sequence.overallPassed)
    }

    @Test
    fun `CHIP_INIT 空数据时寄存器回读不一致则 FAIL`() = runTest {
        val manager = defaultManager()
        coEvery { manager.sendCommand(any(), KEY_GH3X_REGS_READ_CMD, any()) } returns
            Result.success(byteArrayOf(1, 0, 0x34, 0x12))
        val collector = defaultCollector()
        val evaluator = mockk<AppSideTestEvaluator>()

        val events = runSequence(
            newEngine(manager, collector, evaluator),
            config = chipInitConfig
        )

        val completed = events.filterIsInstance<TestEngineEvent.TestCompleted>()
            .single { it.type == TestType.CHIP_INIT }
        val result = completed.results.single()
        assertFalse(result.passed)
        assertEquals(0, result.value)
        assertEquals("0x1234", result.displayValue)
        assertEquals("=0x2919", result.threshold)
        val sequence = events.filterIsInstance<TestEngineEvent.SequenceCompleted>().single()
        assertFalse(sequence.overallPassed)
        assertEquals(listOf(TestType.CHIP_INIT.errorBase), sequence.errorCodes)
    }

    @Test
    fun `CHIP_INIT 空数据时寄存器写入失败则 FAIL`() = runTest {
        val manager = defaultManager()
        coEvery { manager.sendCommand(any(), KEY_GH3X_REGS_LIST_WRITE_CMD, any()) } returns
            Result.failure(IllegalStateException("write failed"))
        val collector = defaultCollector()
        val evaluator = mockk<AppSideTestEvaluator>()

        val events = runSequence(
            newEngine(manager, collector, evaluator),
            config = chipInitConfig
        )

        val completed = events.filterIsInstance<TestEngineEvent.TestCompleted>()
            .single { it.type == TestType.CHIP_INIT }
        val result = completed.results.single()
        assertFalse(result.passed)
        assertEquals(0, result.value)
        assertNull(result.displayValue)
        assertEquals("-", result.threshold)
        val sequence = events.filterIsInstance<TestEngineEvent.SequenceCompleted>().single()
        assertFalse(sequence.overallPassed)
        assertEquals(listOf(TestType.CHIP_INIT.errorBase), sequence.errorCodes)
    }

    @Test
    fun `CHIP_INIT 空数据时寄存器读取失败则 FAIL`() = runTest {
        val manager = defaultManager()
        coEvery { manager.sendCommand(any(), KEY_GH3X_REGS_READ_CMD, any()) } returns
            Result.failure(IllegalStateException("read failed"))
        val collector = defaultCollector()
        val evaluator = mockk<AppSideTestEvaluator>()

        val events = runSequence(
            newEngine(manager, collector, evaluator),
            config = chipInitConfig
        )

        val completed = events.filterIsInstance<TestEngineEvent.TestCompleted>()
            .single { it.type == TestType.CHIP_INIT }
        val result = completed.results.single()
        assertFalse(result.passed)
        assertEquals(0, result.value)
        assertNull(result.displayValue)
        assertEquals("-", result.threshold)
        val sequence = events.filterIsInstance<TestEngineEvent.SequenceCompleted>().single()
        assertFalse(sequence.overallPassed)
        assertEquals(listOf(TestType.CHIP_INIT.errorBase), sequence.errorCodes)
    }

    @Test
    fun `CHIP_INIT 空数据时寄存器读取成功但响应为空则 FAIL`() = runTest {
        val manager = defaultManager()
        val collector = defaultCollector()
        val evaluator = mockk<AppSideTestEvaluator>()

        val events = runSequence(
            newEngine(manager, collector, evaluator),
            config = chipInitConfig
        )

        val completed = events.filterIsInstance<TestEngineEvent.TestCompleted>()
            .single { it.type == TestType.CHIP_INIT }
        val result = completed.results.single()
        assertFalse(result.passed)
        assertEquals(0, result.value)
        assertNull(result.displayValue)
        assertEquals("-", result.threshold)
        val sequence = events.filterIsInstance<TestEngineEvent.SequenceCompleted>().single()
        assertFalse(sequence.overallPassed)
        assertEquals(listOf(TestType.CHIP_INIT.errorBase), sequence.errorCodes)
    }

    private val chipUidConfig = FactoryConfig(project = "test", tests = emptyMap())

    private val chipUidDisabledConfig = FactoryConfig(
        project = "test",
        tests = mapOf(
            "chip_uid" to TestDef(
                enabled = false,
                mode = TestType.CHIP_UID.mode,
                channels = 1,
                unit = "status"
            )
        )
    )

    @Test
    fun `CHIP_UID F_GetMode 无数据时回退 eFuse 读取成功则 PASS`() = runTest {
        val manager = defaultManager()
        val collector = defaultCollector()
        val evaluator = mockk<AppSideTestEvaluator>()
        val efuse = mockk<EfuseReader>()
        coEvery { efuse.readAll(any()) } returns ByteArray(32) { 0x30 }
        val engine = FactoryTestEngine(manager, collector, evaluator, efuse)

        val events = runSequence(engine, config = chipUidConfig)

        coVerify(exactly = 1) { efuse.readAll("AA:BB") }
        val completed = events.filterIsInstance<TestEngineEvent.TestCompleted>()
            .single { it.type == TestType.CHIP_UID }
        assertEquals(2, completed.results.size)
        assertTrue(completed.results.all { it.passed })
        assertEquals("30303030-3030-3030-3030-303030303030", completed.results[0].displayValue)
        val sequence = events.filterIsInstance<TestEngineEvent.SequenceCompleted>().single()
        assertTrue(sequence.overallPassed)
    }

    @Test
    fun `CHIP_UID F_GetMode 无数据且 eFuse 读取失败则 FAIL`() = runTest {
        val manager = defaultManager()
        val collector = defaultCollector()
        val evaluator = mockk<AppSideTestEvaluator>()
        val efuse = mockk<EfuseReader>()
        coEvery { efuse.readAll(any()) } returns null
        val engine = FactoryTestEngine(manager, collector, evaluator, efuse)

        val events = runSequence(engine, config = chipUidConfig)

        val completed = events.filterIsInstance<TestEngineEvent.TestCompleted>()
            .single { it.type == TestType.CHIP_UID }
        assertEquals(2, completed.results.size)
        assertTrue(completed.results.all { !it.passed })
        val sequence = events.filterIsInstance<TestEngineEvent.SequenceCompleted>().single()
        assertFalse(sequence.overallPassed)
        assertEquals(
            listOf(TestType.CHIP_UID.errorBase, TestType.CHIP_UID.errorBase + 1),
            sequence.errorCodes
        )
    }

    @Test
    fun `CHIP_UID 非 GH3036 系列芯片不读 eFuse 且判 FAIL`() = runTest {
        val manager = defaultManager()
        val collector = defaultCollector()
        val evaluator = mockk<AppSideTestEvaluator>()
        val efuse = mockk<EfuseReader>()
        coEvery { efuse.readAll(any()) } returns ByteArray(32) { 0x30 }
        val engine = FactoryTestEngine(manager, collector, evaluator, efuse)

        val events = runSequence(engine, config = chipUidConfig, chip = "gh3220")

        coVerify(exactly = 0) { efuse.readAll(any()) }
        val completed = events.filterIsInstance<TestEngineEvent.TestCompleted>()
            .single { it.type == TestType.CHIP_UID }
        assertTrue(completed.results.all { !it.passed })
        val sequence = events.filterIsInstance<TestEngineEvent.SequenceCompleted>().single()
        assertFalse(sequence.overallPassed)
    }

    @Test
    fun `CHIP_UID F_GetMode 失败时回退 eFuse 读取成功则 PASS`() = runTest {
        val manager = defaultManager()
        coEvery { manager.sendCommand(any(), KEY_F_GET_MODE, any()) } returns
            Result.failure(IllegalStateException("get mode failed"))
        val collector = defaultCollector()
        val evaluator = mockk<AppSideTestEvaluator>()
        val efuse = mockk<EfuseReader>()
        coEvery { efuse.readAll(any()) } returns ByteArray(32) { 0x30 }
        val engine = FactoryTestEngine(manager, collector, evaluator, efuse)

        val events = runSequence(engine, config = chipUidConfig)

        coVerify(exactly = 1) { efuse.readAll("AA:BB") }
        val completed = events.filterIsInstance<TestEngineEvent.TestCompleted>()
            .single { it.type == TestType.CHIP_UID }
        assertEquals(2, completed.results.size)
        assertTrue(completed.results.all { it.passed })
        val sequence = events.filterIsInstance<TestEngineEvent.SequenceCompleted>().single()
        assertTrue(sequence.overallPassed)
    }

    @Test
    fun `CHIP_UID F_GetMode 返回 32 字节时不读 eFuse 且 PASS`() = runTest {
        val manager = defaultManager()
        val uidResponse = ByteArray(34) { i ->
            when {
                i == 0 -> 16
                i == 1 -> 0
                i % 2 == 0 -> 0x34
                else -> 0x12
            }.toByte()
        }
        coEvery { manager.sendCommand(any(), KEY_F_GET_MODE, any()) } returns Result.success(uidResponse)
        val collector = defaultCollector()
        val evaluator = mockk<AppSideTestEvaluator>()
        val efuse = mockk<EfuseReader>()
        coEvery { efuse.readAll(any()) } returns ByteArray(32) { 0x30 }
        val engine = FactoryTestEngine(manager, collector, evaluator, efuse)

        val events = runSequence(engine, config = chipUidConfig)

        coVerify(exactly = 0) { efuse.readAll(any()) }
        val completed = events.filterIsInstance<TestEngineEvent.TestCompleted>()
            .single { it.type == TestType.CHIP_UID }
        assertEquals(2, completed.results.size)
        assertTrue(completed.results.all { it.passed })
        assertEquals("34123412-3412-3412-3412-341234123412", completed.results[0].displayValue)
        val sequence = events.filterIsInstance<TestEngineEvent.SequenceCompleted>().single()
        assertTrue(sequence.overallPassed)
    }

    @Test
    fun `CHIP_UID 配置禁用时跳过测试且序列正常完成`() = runTest {
        val manager = defaultManager()
        val collector = defaultCollector()
        val evaluator = mockk<AppSideTestEvaluator>()
        val efuse = mockk<EfuseReader>()
        coEvery { efuse.readAll(any()) } returns ByteArray(32) { 0x30 }
        val engine = FactoryTestEngine(manager, collector, evaluator, efuse)

        val events = runSequence(engine, config = chipUidDisabledConfig)

        coVerify(exactly = 0) { efuse.readAll(any()) }
        assertFalse(events.filterIsInstance<TestEngineEvent.TestCompleted>().any { it.type == TestType.CHIP_UID })
        val sequence = events.filterIsInstance<TestEngineEvent.SequenceCompleted>().single()
        assertEquals(emptyList<Int>(), sequence.errorCodes)
    }

    @Test
    fun `CHIP_UID F_SetMode 失败时回退 eFuse 读取成功则 PASS`() = runTest {
        val manager = defaultManager()
        coEvery { manager.sendCommand(any(), KEY_F_SET_MODE, any()) } returns
            Result.failure(IllegalStateException("set mode failed"))
        val collector = defaultCollector()
        val evaluator = mockk<AppSideTestEvaluator>()
        val efuse = mockk<EfuseReader>()
        coEvery { efuse.readAll(any()) } returns ByteArray(32) { 0x30 }
        val engine = FactoryTestEngine(manager, collector, evaluator, efuse)

        val events = runSequence(engine, config = chipUidConfig)

        coVerify(exactly = 1) { efuse.readAll("AA:BB") }
        val completed = events.filterIsInstance<TestEngineEvent.TestCompleted>()
            .single { it.type == TestType.CHIP_UID }
        assertEquals(2, completed.results.size)
        assertTrue(completed.results.all { it.passed })
        assertEquals("30303030-3030-3030-3030-303030303030", completed.results[0].displayValue)
        val sequence = events.filterIsInstance<TestEngineEvent.SequenceCompleted>().single()
        assertTrue(sequence.overallPassed)
    }

    private val chipInitPlusBaseNoiseConfig = FactoryConfig(
        project = "test",
        tests = mapOf(
            "chip_init" to TestDef(
                enabled = true,
                mode = TestType.CHIP_INIT.mode,
                channels = 1,
                unit = "status"
            ),
            "base_noise" to baseNoiseDef
        )
    )

    @Test
    fun `CHIP_INIT 空数据触发全局回退后硬件测试直接 App 端计算且不再尝试 F_GetMode`() = runTest {
        val manager = defaultManager()
        // 寄存器校验回读一致，CHIP_INIT 判定 PASS，保证 sequence.overallPassed
        coEvery { manager.sendCommand(any(), KEY_GH3X_REGS_READ_CMD, any()) } returns
            Result.success(byteArrayOf(1, 0, 0x19, 0x29))
        val collector = defaultCollector()
        val evaluator = mockk<AppSideTestEvaluator>()
        val computed = listOf(
            TestResult(
                testType = TestType.BASE_NOISE,
                channelIndex = 0,
                value = 152,
                unit = "dB",
                threshold = "-",
                passed = true
            )
        )
        every { evaluator.evaluate(any(), any(), any(), any(), any()) } returns computed

        val events = runSequence(newEngine(manager, collector, evaluator), config = chipInitPlusBaseNoiseConfig)

        // F_GetMode 只应被调用 1 次（CHIP_INIT 那次）；base_noise 不再尝试
        coVerify(exactly = 1) { manager.sendCommand(any(), KEY_F_GET_MODE, any()) }
        // F_SetMode 也只应被调用 1 次（CHIP_INIT 那次）
        coVerify(exactly = 1) { manager.sendCommand(any(), KEY_F_SET_MODE, any()) }
        // base_noise 仍执行 download_config(0)/(1)
        coVerify(exactly = 2) { manager.sendCommand(any(), KEY_DOWNLOAD_CONFIG, any()) }
        verify(exactly = 1) { evaluator.evaluate(any(), any(), any(), any(), any()) }
        val completed = events.filterIsInstance<TestEngineEvent.TestCompleted>()
            .single { it.type == TestType.BASE_NOISE }
        assertEquals(computed, completed.results)
        val sequence = events.filterIsInstance<TestEngineEvent.SequenceCompleted>().single()
        assertTrue(sequence.overallPassed)
    }

    @Test
    fun `全局回退后 CHIP_UID 跳过 F_SetMode 直接读 eFuse`() = runTest {
        val manager = defaultManager()
        // 寄存器校验回读一致，CHIP_INIT 判定 PASS，保证 sequence.overallPassed
        coEvery { manager.sendCommand(any(), KEY_GH3X_REGS_READ_CMD, any()) } returns
            Result.success(byteArrayOf(1, 0, 0x19, 0x29))
        val collector = defaultCollector()
        val evaluator = mockk<AppSideTestEvaluator>()
        val efuse = mockk<EfuseReader>()
        coEvery { efuse.readAll(any()) } returns ByteArray(32) { 0x30 }
        val engine = FactoryTestEngine(manager, collector, evaluator, efuse)

        val chipInitOnlyConfig = FactoryConfig(
            project = "test",
            tests = mapOf(
                "chip_init" to TestDef(
                    enabled = true,
                    mode = TestType.CHIP_INIT.mode,
                    channels = 1,
                    unit = "status"
                )
            )
        )
        val events = runSequence(engine, config = chipInitOnlyConfig)

        coVerify(exactly = 1) { efuse.readAll("AA:BB") }
        // 仅 CHIP_INIT 尝试过 F_SetMode/F_GetMode，CHIP_UID 直接读 eFuse
        coVerify(exactly = 1) { manager.sendCommand(any(), KEY_F_SET_MODE, any()) }
        coVerify(exactly = 1) { manager.sendCommand(any(), KEY_F_GET_MODE, any()) }
        val completed = events.filterIsInstance<TestEngineEvent.TestCompleted>()
            .single { it.type == TestType.CHIP_UID }
        assertEquals(2, completed.results.size)
        assertTrue(completed.results.all { it.passed })
        val sequence = events.filterIsInstance<TestEngineEvent.SequenceCompleted>().single()
        assertTrue(sequence.overallPassed)
    }

    @Test
    fun `CHIP_INIT F_GetMode 失败时触发回退并走寄存器校验判定`() = runTest {
        val manager = defaultManager()
        coEvery { manager.sendCommand(any(), KEY_F_GET_MODE, any()) } returns
            Result.failure(IllegalStateException("get failed"))
        coEvery { manager.sendCommand(any(), KEY_GH3X_REGS_READ_CMD, any()) } returns
            Result.success(byteArrayOf(1, 0, 0x19, 0x29))
        val collector = defaultCollector()
        val evaluator = mockk<AppSideTestEvaluator>()

        val events = runSequence(newEngine(manager, collector, evaluator), config = chipInitConfig)

        val completed = events.filterIsInstance<TestEngineEvent.TestCompleted>()
            .single { it.type == TestType.CHIP_INIT }
        assertTrue(completed.results.single().passed)
        assertTrue(
            events.filterIsInstance<TestEngineEvent.LogMessage>()
                .any { it.level == LogLevel.WARN && it.message.contains("切换为 App 端计算") }
        )
    }

    @Test
    fun `回退模式下硬件测试未采集到数据则合成 FAIL`() = runTest {
        val manager = defaultManager()
        // 寄存器校验回读一致，CHIP_INIT 判定 PASS，FAIL 归属仅来自 BASE_NOISE
        coEvery { manager.sendCommand(any(), KEY_GH3X_REGS_READ_CMD, any()) } returns
            Result.success(byteArrayOf(1, 0, 0x19, 0x29))
        val collector = defaultCollector() // stop() 返回 EMPTY
        val evaluator = mockk<AppSideTestEvaluator>()
        // strict mockk 对未 stub 的调用会抛 "no answer found"，显式返回 null 以触发合成 FAIL
        every { evaluator.evaluate(any(), any(), any(), any(), any()) } returns null

        val events = runSequence(newEngine(manager, collector, evaluator), config = chipInitPlusBaseNoiseConfig)

        coVerify(exactly = 1) { manager.sendCommand(any(), KEY_F_GET_MODE, any()) }
        val completed = events.filterIsInstance<TestEngineEvent.TestCompleted>()
            .single { it.type == TestType.BASE_NOISE }
        assertTrue(completed.results.all { !it.passed })
        val sequence = events.filterIsInstance<TestEngineEvent.SequenceCompleted>().single()
        assertEquals(listOf(TestType.BASE_NOISE.errorBase), sequence.errorCodes)
    }

    private val timeoutNoiseConfig = FactoryConfig(
        project = "test",
        tests = mapOf(
            "chip_init" to TestDef(
                enabled = true, mode = TestType.CHIP_INIT.mode, channels = 1, unit = "status"
            ),
            "base_noise" to TestDef(
                enabled = true, mode = TestType.BASE_NOISE.mode, channels = 1, unit = "dB",
                compute = AppComputeConfig(timeout = 300L)
            )
        )
    )

    private val twoTimeoutConfig = FactoryConfig(
        project = "test",
        tests = mapOf(
            "chip_init" to TestDef(
                enabled = true, mode = TestType.CHIP_INIT.mode, channels = 1, unit = "status"
            ),
            "base_noise" to TestDef(
                enabled = true, mode = TestType.BASE_NOISE.mode, channels = 1, unit = "dB",
                compute = AppComputeConfig(timeout = 300L)
            ),
            "ppg_noise" to TestDef(
                enabled = true, mode = TestType.PPG_NOISE.mode, channels = 1, unit = "dB",
                compute = AppComputeConfig(timeout = 300L)
            )
        )
    )

    @Test
    fun `回退模式采集满足条件时提前停止并 App 端计算`() = runTest {
        val manager = defaultManager()
        // 寄存器校验回读一致，CHIP_INIT 判定 PASS
        coEvery { manager.sendCommand(any(), KEY_GH3X_REGS_READ_CMD, any()) } returns
            Result.success(byteArrayOf(1, 0, 0x19, 0x29))
        val collector = mockk<TestRawDataCollector>()
        every { collector.start(any()) } just Runs
        every { collector.isCollectionComplete(any()) } returns true
        every { collector.stop() } returns CollectedRawData(
            rawdataByChannel = mapOf(0 to List(300) { 8_388_608 }),
            ipdPaByChannel = emptyMap(),
            ledCurrentSumMaByChannel = emptyMap(),
            frameCnts = (0 until 300).toList()
        )
        val evaluator = mockk<AppSideTestEvaluator>()
        every { evaluator.evaluate(any(), any(), any(), any(), any()) } returns listOf(
            TestResult(TestType.BASE_NOISE, 0, 152, "dB", "-", passed = true)
        )

        val events = runSequence(newEngine(manager, collector, evaluator), config = chipInitPlusBaseNoiseConfig)

        // BASE_NOISE：SwFunctionCmd(start) + SwFunctionCmd(stop) 各一次
        coVerify(exactly = 2) { manager.sendCommand(any(), KEY_GH3X_SW_FUNCTION_CMD, any()) }
        verify(exactly = 1) { collector.isCollectionComplete(any()) }
        verify(exactly = 1) { evaluator.evaluate(any(), any(), any(), any(), any()) }
        val completed = events.filterIsInstance<TestEngineEvent.TestCompleted>()
            .single { it.type == TestType.BASE_NOISE }
        assertTrue(completed.results.all { it.passed })
    }

    @Test
    fun `回退模式轮询未满足条件时重试直到完成`() = runTest {
        val manager = defaultManager()
        coEvery { manager.sendCommand(any(), KEY_GH3X_REGS_READ_CMD, any()) } returns
            Result.success(byteArrayOf(1, 0, 0x19, 0x29))
        val collector = mockk<TestRawDataCollector>()
        every { collector.start(any()) } just Runs
        var calls = 0
        every { collector.isCollectionComplete(any()) } answers {
            calls++
            calls >= 2
        }
        every { collector.stop() } returns CollectedRawData(
            rawdataByChannel = mapOf(0 to List(300) { 8_388_608 }),
            ipdPaByChannel = emptyMap(),
            ledCurrentSumMaByChannel = emptyMap(),
            frameCnts = (0 until 300).toList()
        )
        val evaluator = mockk<AppSideTestEvaluator>()
        every { evaluator.evaluate(any(), any(), any(), any(), any()) } returns listOf(
            TestResult(TestType.BASE_NOISE, 0, 152, "dB", "-", passed = true)
        )

        val events = runSequence(newEngine(manager, collector, evaluator), config = chipInitPlusBaseNoiseConfig)

        verify(exactly = 2) { collector.isCollectionComplete(any()) }
        val completed = events.filterIsInstance<TestEngineEvent.TestCompleted>()
            .single { it.type == TestType.BASE_NOISE }
        assertTrue(completed.results.all { it.passed })
    }

    @Test
    fun `回退模式采集超时则提示蓝牙不稳定并合成 FAIL`() = runTest {
        val manager = defaultManager()
        coEvery { manager.sendCommand(any(), KEY_GH3X_REGS_READ_CMD, any()) } returns
            Result.success(byteArrayOf(1, 0, 0x19, 0x29))
        val collector = mockk<TestRawDataCollector>()
        every { collector.start(any()) } just Runs
        every { collector.isCollectionComplete(any()) } returns false
        every { collector.stop() } returns CollectedRawData.EMPTY
        val evaluator = mockk<AppSideTestEvaluator>()
        every { evaluator.evaluate(any(), any(), any(), any(), any()) } returns null

        val events = runSequence(newEngine(manager, collector, evaluator), config = timeoutNoiseConfig)

        val completed = events.filterIsInstance<TestEngineEvent.TestCompleted>()
            .single { it.type == TestType.BASE_NOISE }
        assertTrue(completed.results.all { !it.passed })
        assertTrue(
            events.filterIsInstance<TestEngineEvent.LogMessage>()
                .any { it.level == LogLevel.ERROR && it.message.contains("蓝牙连接不稳定") }
        )
        assertEquals(1, events.filterIsInstance<TestEngineEvent.ShowBluetoothUnstableDialog>().size)
        // 超时不再调用评估器
        verify(exactly = 0) { evaluator.evaluate(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `多个测试项超时时仅首次弹出蓝牙不稳定对话框`() = runTest {
        val manager = defaultManager()
        coEvery { manager.sendCommand(any(), KEY_GH3X_REGS_READ_CMD, any()) } returns
            Result.success(byteArrayOf(1, 0, 0x19, 0x29))
        val collector = mockk<TestRawDataCollector>()
        every { collector.start(any()) } just Runs
        every { collector.isCollectionComplete(any()) } returns false
        every { collector.stop() } returns CollectedRawData.EMPTY
        val evaluator = mockk<AppSideTestEvaluator>()
        every { evaluator.evaluate(any(), any(), any(), any(), any()) } returns null

        val events = runSequence(newEngine(manager, collector, evaluator), config = twoTimeoutConfig)

        assertEquals(1, events.filterIsInstance<TestEngineEvent.ShowBluetoothUnstableDialog>().size)
        val timeouts = events.filterIsInstance<TestEngineEvent.LogMessage>()
            .count { it.level == LogLevel.ERROR && it.message.contains("蓝牙连接不稳定") }
        assertEquals(2, timeouts) // base_noise 与 ppg_noise 各超时一次
    }
}
