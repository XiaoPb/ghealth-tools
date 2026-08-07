package com.ghealth.tools.feature.factory.compute

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class HighPassFilterTest {

    private val fs = 100.0
    private val filter = HighPassFilter(sampleRateHz = 100)

    private fun sine(amp: Double, freqHz: Double, n: Int): DoubleArray =
        DoubleArray(n) { i -> amp * sin(2 * PI * freqHz * i / fs) }

    private fun stats(values: DoubleArray): Pair<Double, Double> {
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return mean to sqrt(variance)
    }

    @Test
    fun `纯直流输入以均值为初态时输出恒为 0`() {
        val input = DoubleArray(2000) { 8_388_608.0 + 1234.0 }
        val output = filter.filter(input, initialX = input.average())
        val (mean, stddev) = stats(output)
        assertEquals(0.0, mean, 0.001)
        assertEquals(0.0, stddev, 0.001)
    }

    @Test
    fun `5Hz 正弦处于通带，幅度基本保留`() {
        val input = sine(amp = 1000.0, freqHz = 5.0, n = 2000)
        val output = filter.filter(input, initialX = input.average())
        val (_, stddev) = stats(output)
        // 正弦 stddev = A/√2 ≈ 707.1
        assertEquals(707.1, stddev, 707.1 * 0.02)
    }

    @Test
    fun `0_1Hz 正弦被强烈衰减`() {
        val input = sine(amp = 1000.0, freqHz = 0.1, n = 8000)
        val output = filter.filter(input, initialX = input.average())
        val tail = output.copyOfRange(4000, output.size)
        val (_, stddev) = stats(tail)
        assertTrue(stddev < 1.0, "0.1Hz 衰减后 stddev=$stddev 应远小于输入")
    }

    @Test
    fun `直流叠加 5Hz 信号时滤除直流并保留交流`() {
        val dc = 8_388_608.0
        val input = DoubleArray(2000) { i -> dc + 1000.0 * sin(2 * PI * 5.0 * i / fs) }
        val output = filter.filter(input, initialX = input.average())
        val tail = output.copyOfRange(1000, output.size)
        val (mean, stddev) = stats(tail)
        assertEquals(0.0, mean, 1.0)
        assertEquals(707.1, stddev, 707.1 * 0.02)
    }

    @Test
    fun `截止频率超过奈奎斯特频率时抛异常`() {
        assertThrows(IllegalArgumentException::class.java) {
            HighPassFilter(sampleRateHz = 100, cutoffHz = 60.0)
        }
    }

    @Test
    fun `采样率为 0 时抛异常`() {
        assertThrows(IllegalArgumentException::class.java) {
            HighPassFilter(sampleRateHz = 0)
        }
    }
}
