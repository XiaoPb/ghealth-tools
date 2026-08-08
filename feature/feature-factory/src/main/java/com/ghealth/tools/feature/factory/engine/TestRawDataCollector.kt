package com.ghealth.tools.feature.factory.engine

import com.ghealth.tools.ble.connection.BleConnectionManager
import com.ghealth.tools.ble.protocol.gh3036.GhFuncFrame
import com.ghealth.tools.ble.protocol.gh3036.GhFuncId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 产测硬件测试期间采集指定设备的 TEST1 原始帧数据。
 * [start] 启动采集协程，[stop] 取消并返回快照；重复 [start] 会先停止旧采集。
 */
@Singleton
class TestRawDataCollector @Inject constructor(
    private val connectionManager: BleConnectionManager,
    private val scope: CoroutineScope
) {

    private val lock = Any()
    private var buffers = TestRawDataBuffers()
    private var job: Job? = null

    fun start(deviceAddress: String) {
        synchronized(lock) {
            stopLocked()
            val currentBuffers = TestRawDataBuffers()
            buffers = currentBuffers
            val currentDeduper = TestFrameDeduper()
            job = scope.launch {
                connectionManager.ghFrameFlow.collect { (address, frame) ->
                    if (address != deviceAddress) return@collect
                    if (frame.funcId != GhFuncId.TEST1) return@collect
                    synchronized(lock) {
                        if (currentDeduper.isDuplicate(frame.frameCnt, frame.timestamp)) return@collect
                        currentBuffers.addFrame(frame)
                    }
                }
            }
        }
    }

    fun stop(): CollectedRawData {
        synchronized(lock) {
            stopLocked()
            return buffers.snapshot()
        }
    }

    /** 采集是否满足完成条件：去重帧数 >= skip+min，且要求连续时末尾连续帧数 >= min。 */
    fun isCollectionComplete(spec: CollectionSpec): Boolean {
        synchronized(lock) {
            if (buffers.frameCount().toLong() < spec.skipNumber.toLong() + spec.minNumber.toLong()) return false
            if (!spec.isContinuous) return true
            return buffers.lastConsecutiveCount() >= spec.minNumber
        }
    }

    private fun stopLocked() {
        job?.cancel()
        job = null
    }
}

/**
 * 帧去重：最近 [recentSize] 帧内 (frameCnt, timestamp) 完全相同视为设备滚动窗口重发的重复帧。
 * 非线程安全，由 [TestRawDataCollector] 在锁内调用。
 */
class TestFrameDeduper(private val recentSize: Int = 16) {
    private val recent = ArrayDeque<Pair<Int, Long>>()

    fun isDuplicate(frameCnt: Int, timestamp: Long): Boolean {
        val stamp = frameCnt to timestamp
        val duplicate = stamp in recent
        if (!duplicate) {
            recent.addLast(stamp)
            if (recent.size > recentSize) recent.removeFirst()
        }
        return duplicate
    }
}
