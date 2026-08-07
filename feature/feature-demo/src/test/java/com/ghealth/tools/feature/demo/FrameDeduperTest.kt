package com.ghealth.tools.feature.demo

import com.ghealth.tools.core.model.FunctionMode
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FrameDeduperTest {

    @Test
    fun `same frameCnt and timestamp is duplicate`() {
        val deduper = FrameDeduper()
        assertFalse(deduper.isDuplicate("AA", FunctionMode.TEST1, frameCnt = 3236, timestamp = 1000L))
        assertTrue(deduper.isDuplicate("AA", FunctionMode.TEST1, frameCnt = 3236, timestamp = 1000L))
    }

    @Test
    fun `next frame with new cnt and timestamp passes`() {
        val deduper = FrameDeduper()
        assertFalse(deduper.isDuplicate("AA", FunctionMode.TEST1, frameCnt = 3236, timestamp = 1000L))
        assertFalse(deduper.isDuplicate("AA", FunctionMode.TEST1, frameCnt = 3237, timestamp = 1033L))
    }

    @Test
    fun `sliding window overlap from device is deduped`() {
        // 复现附件 CSV 的滚动窗口模式：msg1=[3236..3240]，msg2=[3239..3243]（重叠 2 帧），
        // msg3=[3241..3245]（重叠 3 帧）。重叠帧应被去重，新帧应保留。
        val deduper = FrameDeduper(recentSize = 16)
        // msg1
        assertFalse(deduper.isDuplicate("AA", FunctionMode.TEST1, 3236, 1000L))
        assertFalse(deduper.isDuplicate("AA", FunctionMode.TEST1, 3237, 1033L))
        assertFalse(deduper.isDuplicate("AA", FunctionMode.TEST1, 3238, 1066L))
        assertFalse(deduper.isDuplicate("AA", FunctionMode.TEST1, 3239, 1099L))
        assertFalse(deduper.isDuplicate("AA", FunctionMode.TEST1, 3240, 1132L))
        // msg2：3239、3240 是重发帧
        assertTrue(deduper.isDuplicate("AA", FunctionMode.TEST1, 3239, 1099L))
        assertTrue(deduper.isDuplicate("AA", FunctionMode.TEST1, 3240, 1132L))
        assertFalse(deduper.isDuplicate("AA", FunctionMode.TEST1, 3241, 1165L))
        assertFalse(deduper.isDuplicate("AA", FunctionMode.TEST1, 3242, 1198L))
        assertFalse(deduper.isDuplicate("AA", FunctionMode.TEST1, 3243, 1231L))
        // msg3：3241、3242、3243 是重发帧
        assertTrue(deduper.isDuplicate("AA", FunctionMode.TEST1, 3241, 1165L))
        assertTrue(deduper.isDuplicate("AA", FunctionMode.TEST1, 3242, 1198L))
        assertTrue(deduper.isDuplicate("AA", FunctionMode.TEST1, 3243, 1231L))
        assertFalse(deduper.isDuplicate("AA", FunctionMode.TEST1, 3244, 1264L))
        assertFalse(deduper.isDuplicate("AA", FunctionMode.TEST1, 3245, 1297L))
    }

    @Test
    fun `same frameCnt with different timestamp is not duplicate`() {
        // 帧号回绕/重置场景：帧号相同但时间戳不同，属于合法新帧
        val deduper = FrameDeduper()
        assertFalse(deduper.isDuplicate("AA", FunctionMode.TEST1, frameCnt = 0, timestamp = 1000L))
        assertFalse(deduper.isDuplicate("AA", FunctionMode.TEST1, frameCnt = 0, timestamp = 2000L))
    }

    @Test
    fun `dedup is scoped by device and mode`() {
        val deduper = FrameDeduper()
        assertFalse(deduper.isDuplicate("AA", FunctionMode.TEST1, frameCnt = 1, timestamp = 1000L))
        assertFalse(deduper.isDuplicate("BB", FunctionMode.TEST1, frameCnt = 1, timestamp = 1000L))
        assertFalse(deduper.isDuplicate("AA", FunctionMode.SPO2, frameCnt = 1, timestamp = 1000L))
    }

    @Test
    fun `clear resets dedup state`() {
        val deduper = FrameDeduper()
        assertFalse(deduper.isDuplicate("AA", FunctionMode.TEST1, frameCnt = 1, timestamp = 1000L))
        assertTrue(deduper.isDuplicate("AA", FunctionMode.TEST1, frameCnt = 1, timestamp = 1000L))
        deduper.clear()
        assertFalse(deduper.isDuplicate("AA", FunctionMode.TEST1, frameCnt = 1, timestamp = 1000L))
    }

    @Test
    fun `removeAddress prunes dedup state for that device only`() {
        val deduper = FrameDeduper()
        assertFalse(deduper.isDuplicate("AA", FunctionMode.TEST1, 1, 1000L))
        assertFalse(deduper.isDuplicate("BB", FunctionMode.TEST1, 1, 1000L))
        deduper.removeAddress("AA")
        // AA 的状态被清理：再次出现相同帧不再是重复
        assertFalse(deduper.isDuplicate("AA", FunctionMode.TEST1, 1, 1000L))
        // BB 不受影响
        assertTrue(deduper.isDuplicate("BB", FunctionMode.TEST1, 1, 1000L))
    }
}
