package com.ghealth.tools.feature.factory.exporter

import com.ghealth.tools.feature.factory.model.TestResult
import com.ghealth.tools.feature.factory.model.TestSummary
import com.ghealth.tools.feature.factory.model.TestType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class CsvResultExporterTest {

    @TempDir
    lateinit var baseDir: File

    private val exporter = CsvResultExporter()

    @Test
    fun `导出时计算值写入 CSV 而非整数 value`() = runTest {
        val summary = TestSummary(
            projectName = "ProjectA",
            chipType = "gh3036",
            timestamp = 1_700_000_000_000L,
            results = mapOf(
                TestType.BASE_NOISE to listOf(
                    TestResult(TestType.BASE_NOISE, 0, value = 152, unit = "uV", threshold = "≤300", passed = true, computedValue = 151.758)
                )
            )
        )
        val file = exporter.export(summary, baseDir)!!
        val lines = file.readLines()
        val header = lines[0].split(",")
        val row = lines[1].split(",")
        assertEquals("151.758", row[header.indexOf("base_noise_0")])
    }

    @Test
    fun `缺失通道输出 0`() = runTest {
        val summary = TestSummary(
            projectName = "ProjectA",
            chipType = "gh3036",
            timestamp = 1_700_000_000_000L,
            results = mapOf(
                TestType.BASE_NOISE to listOf(
                    TestResult(TestType.BASE_NOISE, 1, value = 1, unit = "uV", threshold = "-", passed = true)
                )
            )
        )
        val file = exporter.export(summary, baseDir)!!
        val lines = file.readLines()
        val header = lines[0].split(",")
        val row = lines[1].split(",")
        assertEquals("0", row[header.indexOf("base_noise_0")])
        assertEquals("1", row[header.indexOf("base_noise_1")])
    }

    @Test
    fun `阈值 FAIL 通道仍导出计算值而非 0`() = runTest {
        val summary = TestSummary(
            projectName = "ProjectA",
            chipType = "gh3036",
            timestamp = 1_700_000_000_000L,
            results = mapOf(
                TestType.BASE_NOISE to listOf(
                    TestResult(TestType.BASE_NOISE, 0, value = 152, unit = "uV", threshold = "≤50", passed = false, computedValue = 151.758)
                )
            )
        )
        val file = exporter.export(summary, baseDir)!!
        val lines = file.readLines()
        val header = lines[0].split(",")
        val row = lines[1].split(",")
        assertEquals("151.758", row[header.indexOf("base_noise_0")])
    }

    @Test
    fun `操作失败无计算值通道导出 0`() = runTest {
        val summary = TestSummary(
            projectName = "ProjectA",
            chipType = "gh3036",
            timestamp = 1_700_000_000_000L,
            results = mapOf(
                TestType.BASE_NOISE to listOf(
                    TestResult(TestType.BASE_NOISE, 0, value = 0, unit = "uV", threshold = "-", passed = false)
                )
            )
        )
        val file = exporter.export(summary, baseDir)!!
        val lines = file.readLines()
        val header = lines[0].split(",")
        val row = lines[1].split(",")
        assertEquals("0", row[header.indexOf("base_noise_0")])
    }
}
