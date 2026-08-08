package com.ghealth.tools.feature.factory.engine

import com.ghealth.tools.feature.factory.model.AppComputeConfig
import com.ghealth.tools.feature.factory.model.TestType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CollectionSpecTest {

    @Test
    fun `噪声测试默认 skip 200 min 100 要求连续 超时10s`() {
        val spec = CollectionSpec.resolve(null, TestType.BASE_NOISE)
        assertEquals(100, spec.minNumber)
        assertEquals(200, spec.skipNumber)
        assertTrue(spec.isContinuous)
        assertEquals(10_000L, spec.timeoutMs)
    }

    @Test
    fun `CTR 测试默认 skip 0 min 100 不要求连续`() {
        val spec = CollectionSpec.resolve(null, TestType.LPCTR)
        assertEquals(100, spec.minNumber)
        assertEquals(0, spec.skipNumber)
        assertFalse(spec.isContinuous)
        assertEquals(10_000L, spec.timeoutMs)
    }

    @Test
    fun `配置值覆盖默认`() {
        val compute = AppComputeConfig(minNumber = 50, skipNumber = 60, isContinuous = 0, timeout = 3000L)
        val spec = CollectionSpec.resolve(compute, TestType.PPG_NOISE)
        assertEquals(50, spec.minNumber)
        assertEquals(60, spec.skipNumber)
        assertFalse(spec.isContinuous)
        assertEquals(3000L, spec.timeoutMs)
    }

    @Test
    fun 部分覆盖时其余参数回退到类型默认() {
        val compute = AppComputeConfig(minNumber = 50)
        val spec = CollectionSpec.resolve(compute, TestType.BASE_NOISE)
        assertEquals(50, spec.minNumber)
        assertEquals(200, spec.skipNumber)
        assertTrue(spec.isContinuous)
        assertEquals(10_000L, spec.timeoutMs)
    }

    @Test
    fun `非法配置被钳制到合法范围`() {
        val compute = AppComputeConfig(minNumber = 0, skipNumber = -5)
        val spec = CollectionSpec.resolve(compute, TestType.BASE_NOISE)
        assertEquals(1, spec.minNumber)
        assertEquals(0, spec.skipNumber)
    }
}
