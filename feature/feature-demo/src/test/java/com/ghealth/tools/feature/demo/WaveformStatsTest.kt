package com.ghealth.tools.feature.demo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WaveformStatsTest {

    @Test
    fun `空数据返回 null`() {
        assertNull(computeVisibleStats(emptyList(), 100))
    }

    @Test
    fun `数据量小于显示宽度时统计全部数据`() {
        val data = listOf(1f, 2f, 3f)
        val stats = computeVisibleStats(data, 100)!!
        assertEquals(3f, stats.max)
        assertEquals(1f, stats.min)
        assertEquals(2f, stats.avg)
        assertEquals(2f, stats.diff)
    }

    @Test
    fun `数据量超过显示宽度时只统计最后 displayWidth 个点`() {
        // 0..199,displayWidth=50 → 可见区域为 150..199
        val data = (0 until 200).map { it.toFloat() }
        val stats = computeVisibleStats(data, 50)!!
        assertEquals(199f, stats.max)
        assertEquals(150f, stats.min)
        assertEquals(174.5f, stats.avg, 0.0001f)
        assertEquals(49f, stats.diff)
    }

    @Test
    fun `displayWidth 等于数据长度时统计全部`() {
        val data = listOf(10f, 20f, 30f)
        val stats = computeVisibleStats(data, 3)!!
        assertEquals(30f, stats.max)
        assertEquals(10f, stats.min)
        assertEquals(20f, stats.avg, 0.0001f)
    }

    @Test
    fun `displayWidth 为非正数时返回 null`() {
        assertNull(computeVisibleStats(listOf(1f, 2f, 3f), 0))
        assertNull(computeVisibleStats(listOf(1f, 2f, 3f), -5))
    }
}
