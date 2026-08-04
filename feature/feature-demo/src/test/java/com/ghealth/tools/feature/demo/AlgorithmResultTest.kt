package com.ghealth.tools.feature.demo

import com.ghealth.tools.core.model.FunctionMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlgorithmResultTest {

    @Test
    fun `ADT display shows wear and det state separated by slash`() {
        val r = AlgorithmResult.ADT(wearEvent = AdtWearEvent.ON, detStatus = 0, ctr = 3)
        assertEquals("Wear / Det-On", r.display)
    }

    @Test
    fun `ADT display shows Idle and Unknown for zero values`() {
        val r = AlgorithmResult.ADT(wearEvent = 0, detStatus = 2, ctr = 0)
        assertEquals("Idle / Unknown", r.display)
    }

    @Test
    fun `ADT display shows combined wear bits`() {
        val r = AlgorithmResult.ADT(
            wearEvent = AdtWearEvent.ON or AdtWearEvent.MOVE,
            detStatus = 1,
            ctr = 0
        )
        assertEquals("Wear|Move / Det-Off", r.display)
    }

    @Test
    fun `ADT hasData is true once frame arrives even at DET_ON idle`() {
        // 修复点：DET_ON(0) + IDLE(0) 是合法状态，不应被判为无数据
        val r = AlgorithmResult.ADT(wearEvent = 0, detStatus = 0, ctr = 0)
        assertTrue(r.hasData)
    }

    @Test
    fun `ADT parse maps algoData indices correctly`() {
        val r = parseAlgorithmResult(FunctionMode.ADT, intArrayOf(1, 0, 7))
        assertTrue(r is AlgorithmResult.ADT)
        val a = r as AlgorithmResult.ADT
        assertEquals(1, a.wearEvent)
        assertEquals(0, a.detStatus)
        assertEquals(7, a.ctr)
    }

    @Test
    fun `ADT parse empty algoData returns None`() {
        val r = parseAlgorithmResult(FunctionMode.ADT, intArrayOf())
        assertTrue(r is AlgorithmResult.None)
        assertFalse(r.hasData)
    }
}
