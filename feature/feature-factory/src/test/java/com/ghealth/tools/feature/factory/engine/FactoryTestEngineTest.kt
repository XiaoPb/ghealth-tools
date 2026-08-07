package com.ghealth.tools.feature.factory.engine

import com.ghealth.tools.ble.connection.BleConnectionManager
import com.ghealth.tools.ble.protocol.gh3036.KEY_F_GET_MODE
import com.ghealth.tools.ble.protocol.gh3036.KEY_GH3X_REGS_LIST_WRITE_CMD
import com.ghealth.tools.ble.protocol.gh3036.KEY_GH3X_REGS_READ_CMD
import com.ghealth.tools.ble.protocol.gh3036.RegisterCommandPayloadBuilder
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
        return collector
    }

    private suspend fun runSequence(
        engine: FactoryTestEngine,
        config: FactoryConfig = baseNoiseConfig
    ): List<TestEngineEvent> {
        val events = mutableListOf<TestEngineEvent>()
        engine.runTestSequence(
            deviceAddress = "AA:BB",
            chip = "gh3036",
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

        val events = runSequence(FactoryTestEngine(manager, collector, evaluator))

        verify(exactly = 1) { evaluator.evaluate(any(), any(), any(), any(), any()) }
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

        val events = runSequence(FactoryTestEngine(manager, collector, evaluator))

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

        val events = runSequence(FactoryTestEngine(manager, collector, evaluator))

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
            FactoryTestEngine(manager, collector, evaluator),
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
            FactoryTestEngine(manager, collector, evaluator),
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
            FactoryTestEngine(manager, collector, evaluator),
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
            FactoryTestEngine(manager, collector, evaluator),
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
            FactoryTestEngine(manager, collector, evaluator),
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
}
