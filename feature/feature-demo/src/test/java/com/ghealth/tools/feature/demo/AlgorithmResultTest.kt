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
    fun `HR parse maps GH3220 result indices correctly`() {
        val r = parseAlgorithmResult(FunctionMode.HR, intArrayOf(72, 90, 50, 999, 1))
        assertTrue(r is AlgorithmResult.HR)
        val h = r as AlgorithmResult.HR
        assertEquals(72, h.heartRate)
        assertEquals(90, h.confidence)
        assertEquals(50, h.snr)
    }

    @Test
    fun `SPO2 parse maps GH3220 result indices correctly`() {
        val r = parseAlgorithmResult(FunctionMode.SPO2, intArrayOf(98, 35000, 80, 3, 72, 0b100001))
        assertTrue(r is AlgorithmResult.SPO2)
        val s = r as AlgorithmResult.SPO2
        assertEquals(98, s.spo2)
        assertEquals(35000, s.rValue)
        assertEquals(80, s.confidence)
        assertEquals(3, s.confidenceLevel)
        assertEquals(72, s.heartRate)
        assertEquals(0b100001, s.invalidFlag)
    }

    @Test
    fun `HRV parse maps GH3220 result indices correctly`() {
        val r = parseAlgorithmResult(FunctionMode.HRV, intArrayOf(800, 790, 0, 0, 75, 2))
        assertTrue(r is AlgorithmResult.HRV)
        val h = r as AlgorithmResult.HRV
        assertEquals(listOf(800, 790, 0, 0), h.rri)
        assertEquals(75, h.confidence)
        assertEquals(2, h.validNum)
    }

    @Test
    fun `NADT parse decodes wear status and suspect off bits`() {
        val r = parseAlgorithmResult(FunctionMode.NADT_GREEN, intArrayOf(0b110, 85))
        assertTrue(r is AlgorithmResult.NADT)
        val n = r as AlgorithmResult.NADT
        assertEquals(2, n.wearStatus) // bit0-1 = 2 → 脱落
        assertEquals(1, n.suspectOff) // bit2 = 1 → 疑似脱落
        assertEquals(85, n.liveBodyConf)
        assertEquals("Off(~) / Live:85", n.display)
    }

    @Test
    fun `BT parse maps NTC temperatures with centi-degree unit`() {
        val r = parseAlgorithmResult(FunctionMode.BT, intArrayOf(3650, -125))
        assertTrue(r is AlgorithmResult.BT)
        val b = r as AlgorithmResult.BT
        assertEquals(3650, b.ntc0)
        assertEquals(-125, b.ntc1)
        assertEquals("NTC0:36.50℃ / NTC1:-1.25℃", b.display)
    }

    @Test
    fun `ECG parse maps voltage heart rate and snr`() {
        val r = parseAlgorithmResult(FunctionMode.ECG, intArrayOf(1200, 72, 40))
        assertTrue(r is AlgorithmResult.ECG)
        val e = r as AlgorithmResult.ECG
        assertEquals(1200, e.voltage)
        assertEquals(72, e.heartRate)
        assertEquals(40, e.snr)
        assertEquals("72 BPM", e.display)
    }

    @Test
    fun `ADT parse empty algoData returns None`() {
        val r = parseAlgorithmResult(FunctionMode.ADT, intArrayOf())
        assertTrue(r is AlgorithmResult.None)
        assertFalse(r.hasData)
    }
}
