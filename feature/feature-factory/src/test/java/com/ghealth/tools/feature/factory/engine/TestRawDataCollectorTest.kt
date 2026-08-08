package com.ghealth.tools.feature.factory.engine

import com.ghealth.tools.ble.connection.BleConnectionManager
import com.ghealth.tools.ble.protocol.gh3036.GhFuncFrame
import com.ghealth.tools.ble.protocol.gh3036.GhFuncId
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TestRawDataCollectorTest {

    private fun testFrame(rawdata: IntArray, frameCnt: Int = 1) = GhFuncFrame(
        funcId = GhFuncId.TEST1, frameCnt = frameCnt, timestamp = 0L, rawdata = rawdata
    )

    private fun newCollector(flow: MutableSharedFlow<Pair<String, GhFuncFrame>>): Pair<TestRawDataCollector, CoroutineScope> {
        val manager = mockk<BleConnectionManager>()
        every { manager.ghFrameFlow } returns flow
        val scope = CoroutineScope(UnconfinedTestDispatcher() + Job())
        return TestRawDataCollector(manager, scope) to scope
    }

    @Test
    fun `采集指定设备的 TEST1 帧并忽略其他设备与功能`() = runTest {
        val flow = MutableSharedFlow<Pair<String, GhFuncFrame>>(extraBufferCapacity = 64)
        val (collector, scope) = newCollector(flow)

        collector.start("AA:BB")
        flow.tryEmit("AA:BB" to testFrame(intArrayOf(100, 200), frameCnt = 1))
        flow.tryEmit("AA:BB" to testFrame(intArrayOf(101, 201), frameCnt = 2))
        flow.tryEmit("CC:DD" to testFrame(intArrayOf(999)))
        flow.tryEmit("AA:BB" to GhFuncFrame(funcId = GhFuncId.HR, rawdata = intArrayOf(1)))

        val data = collector.stop()
        assertEquals(listOf(100, 101), data.rawdataByChannel[0])
        assertEquals(listOf(200, 201), data.rawdataByChannel[1])
        assertEquals(2, data.channelCount)
        scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun `重复帧被去重`() = runTest {
        val flow = MutableSharedFlow<Pair<String, GhFuncFrame>>(extraBufferCapacity = 64)
        val (collector, scope) = newCollector(flow)

        collector.start("AA:BB")
        flow.tryEmit("AA:BB" to testFrame(intArrayOf(100)))
        flow.tryEmit("AA:BB" to testFrame(intArrayOf(100))) // frameCnt/timestamp 相同 → 重复
        val data = collector.stop()
        assertEquals(listOf(100), data.rawdataByChannel[0])
        scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun `重复 start 会清空上次采集`() = runTest {
        val flow = MutableSharedFlow<Pair<String, GhFuncFrame>>(extraBufferCapacity = 64)
        val (collector, scope) = newCollector(flow)

        collector.start("AA:BB")
        flow.tryEmit("AA:BB" to testFrame(intArrayOf(100)))
        collector.start("AA:BB") // 重新开始
        flow.tryEmit("AA:BB" to testFrame(intArrayOf(200)))
        val data = collector.stop()
        assertEquals(listOf(200), data.rawdataByChannel[0])
        scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun `未达 skip 加 min 总数时未完成`() = runTest {
        val flow = MutableSharedFlow<Pair<String, GhFuncFrame>>(extraBufferCapacity = 64)
        val (collector, scope) = newCollector(flow)
        collector.start("AA:BB")
        (0 until 3).forEach { flow.tryEmit("AA:BB" to testFrame(intArrayOf(it), frameCnt = it)) }
        val spec = CollectionSpec(minNumber = 2, skipNumber = 3, timeoutMs = 10_000L, isContinuous = true)
        assertFalse(collector.isCollectionComplete(spec))
        scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun `达到总数且不要求连续时完成`() = runTest {
        val flow = MutableSharedFlow<Pair<String, GhFuncFrame>>(extraBufferCapacity = 64)
        val (collector, scope) = newCollector(flow)
        collector.start("AA:BB")
        listOf(0, 1, 5, 6, 7).forEach { flow.tryEmit("AA:BB" to testFrame(intArrayOf(it), frameCnt = it)) }
        val spec = CollectionSpec(minNumber = 2, skipNumber = 3, timeoutMs = 10_000L, isContinuous = false)
        assertTrue(collector.isCollectionComplete(spec))
        scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun `达到总数要求连续但末尾断帧时未完成`() = runTest {
        val flow = MutableSharedFlow<Pair<String, GhFuncFrame>>(extraBufferCapacity = 64)
        val (collector, scope) = newCollector(flow)
        collector.start("AA:BB")
        listOf(0, 1, 2, 3, 5).forEach { flow.tryEmit("AA:BB" to testFrame(intArrayOf(it), frameCnt = it)) }
        val spec = CollectionSpec(minNumber = 2, skipNumber = 3, timeoutMs = 10_000L, isContinuous = true)
        assertFalse(collector.isCollectionComplete(spec))
        scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun `达到总数要求连续且末尾连续时完成`() = runTest {
        val flow = MutableSharedFlow<Pair<String, GhFuncFrame>>(extraBufferCapacity = 64)
        val (collector, scope) = newCollector(flow)
        collector.start("AA:BB")
        (0 until 5).forEach { flow.tryEmit("AA:BB" to testFrame(intArrayOf(it), frameCnt = it)) }
        val spec = CollectionSpec(minNumber = 2, skipNumber = 3, timeoutMs = 10_000L, isContinuous = true)
        assertTrue(collector.isCollectionComplete(spec))
        scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun `skip 为 0 时仅需 min 帧`() = runTest {
        val flow = MutableSharedFlow<Pair<String, GhFuncFrame>>(extraBufferCapacity = 64)
        val (collector, scope) = newCollector(flow)
        collector.start("AA:BB")
        (0 until 2).forEach { flow.tryEmit("AA:BB" to testFrame(intArrayOf(it), frameCnt = it)) }
        val spec = CollectionSpec(minNumber = 2, skipNumber = 0, timeoutMs = 10_000L, isContinuous = false)
        assertTrue(collector.isCollectionComplete(spec))
        scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun `末尾连续帧数恰好等于 min 时完成`() = runTest {
        val flow = MutableSharedFlow<Pair<String, GhFuncFrame>>(extraBufferCapacity = 64)
        val (collector, scope) = newCollector(flow)
        collector.start("AA:BB")
        listOf(0, 1, 2, 5, 6).forEach { flow.tryEmit("AA:BB" to testFrame(intArrayOf(it), frameCnt = it)) }
        val spec = CollectionSpec(minNumber = 2, skipNumber = 3, timeoutMs = 10_000L, isContinuous = true)
        assertTrue(collector.isCollectionComplete(spec))
        scope.coroutineContext[Job]!!.cancel()
    }
}
