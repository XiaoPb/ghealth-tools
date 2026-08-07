package com.ghealth.tools.feature.factory.model

import com.ghealth.tools.feature.factory.parser.ConfigJsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FactoryComputeConfigTest {

    private val parser = ConfigJsonParser()

    @Test
    fun `解析 compute 配置块`() {
        val json = """
        {
          "project": "P", "chip": "gh3036",
          "tests": {
            "lpctr": {
              "enabled": true, "mode": 16, "channels": 32,
              "compute": {"gain_k": 100, "led_current_ma": 20, "sample_rate_hz": 100}
            }
          }
        }
        """.trimIndent()
        val config = parser.parse(json)
        val compute = config.tests["lpctr"]!!.compute!!
        assertEquals(100.0, compute.gainK)
        assertEquals(20.0, compute.ledCurrentMa)
        assertEquals(100, compute.sampleRateHz)
    }

    @Test
    fun `未配置 compute 时为 null`() {
        val config = parser.parse("""{"project":"P","chip":"gh3036"}""")
        assertNull(config.tests["lpctr"])
    }

    @Test
    fun `double 阈值 le 判定`() {
        val threshold = ThresholdDef("le", 214.6)
        assertTrue(ThresholdOperator.LE.evaluate(214.5, threshold))
        assertFalse(ThresholdOperator.LE.evaluate(214.7, threshold))
    }

    @Test
    fun `double range 判定`() {
        val threshold = ThresholdDef("range", listOf(10.5, 20.5))
        assertTrue(ThresholdOperator.RANGE.evaluate(15.0, threshold))
        assertFalse(ThresholdOperator.RANGE.evaluate(10.4, threshold))
    }

    @Test
    fun `formatThresholdDouble 输出字符串`() {
        assertEquals("≤214.6", ThresholdOperator.LE.formatThresholdDouble(ThresholdDef("le", 214.6)))
        assertEquals("∈[10.5, 20.5]", ThresholdOperator.RANGE.formatThresholdDouble(ThresholdDef("range", listOf(10.5, 20.5))))
    }

    @Test
    fun `formatComputed 去掉多余小数零`() {
        assertEquals("214.58", TestResult.formatComputed(214.580))
        assertEquals("9000", TestResult.formatComputed(9000.0))
        assertEquals("N/A", TestResult.formatComputed(Double.NaN))
    }
}
