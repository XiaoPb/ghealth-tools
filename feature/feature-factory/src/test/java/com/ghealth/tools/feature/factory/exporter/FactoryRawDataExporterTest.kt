package com.ghealth.tools.feature.factory.exporter

import com.ghealth.tools.ble.protocol.gh3036.AgcPhysicalCodec
import com.ghealth.tools.feature.factory.engine.CollectedRawData
import com.ghealth.tools.feature.factory.model.TestType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FactoryRawDataExporterTest {

    @TempDir
    lateinit var baseDir: File

    private val exporter = FactoryRawDataExporter()

    @Test
    fun `每个测试项导出一个包含通道元数据与 raw ipd 的 CSV`() = runTest {
        val timestamp = 1_700_000_000_000L
        val data = CollectedRawData(
            rawdataByChannel = mapOf(
                0 to listOf(10, 11),
                1 to listOf(20)
            ),
            ipdPaByChannel = mapOf(
                0 to listOf(100),
                2 to listOf(300, 301, 302)
            ),
            ledCurrentSumMaByChannel = mapOf(0 to 2.3, 1 to 0.7, 2 to 1.5),
            agcPhysicalByChannel = mapOf(
                0 to physical(gainCode = 4, ledCurrentTenthsMa = 23),
                1 to physical(gainCode = 0, ledCurrentTenthsMa = 7),
                2 to physical(gainCode = 12, ledCurrentTenthsMa = 15)
            )
        )

        val file = exporter.export(
            projectName = "ProjectA",
            chip = "gh3036",
            testType = TestType.LPCTR,
            data = data,
            ledCurrentTenthsMaByChannel = mapOf(0 to 23, 1 to 7, 2 to 15),
            gainKOhmByChannel = mapOf(0 to 100.0, 1 to 10.0, 2 to 2000.0),
            testStartedAt = timestamp,
            baseDir = baseDir
        )!!

        val timeDir = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(Date(timestamp))
        assertEquals(
            File(baseDir, "factory/gh3036/ProjectA/mp_raw/$timeDir/lpctr.csv").absolutePath,
            file.absolutePath
        )
        assertEquals(
            listOf(
                "raw_0,ipd_pa_0,raw_1,ipd_pa_1,raw_2,ipd_pa_2",
                "23,23,7,7,15,15",
                "100,100,10,10,2000,2000",
                "10,100,20,,,300",
                "11,,,,,301",
                ",,,,,302"
            ),
            file.readLines()
        )
    }

    @Test
    fun `非 GH3036 不把 phyValue 的环境光数据误标为 ipd`() = runTest {
        val file = exporter.export(
            projectName = "ProjectB",
            chip = "gh3220",
            testType = TestType.LPCTR,
            data = CollectedRawData(
                rawdataByChannel = mapOf(0 to listOf(10)),
                ipdPaByChannel = mapOf(0 to listOf(999)),
                ledCurrentSumMaByChannel = emptyMap()
            ),
            ledCurrentTenthsMaByChannel = mapOf(0 to 200),
            gainKOhmByChannel = mapOf(0 to 100.0),
            testStartedAt = 1_700_000_000_000L,
            baseDir = baseDir
        )!!

        assertEquals(listOf("raw_0", "200", "100", "10"), file.readLines())
    }

    private fun physical(gainCode: Int, ledCurrentTenthsMa: Int) =
        AgcPhysicalCodec.Physical(
            gain = gainCode,
            bgCancelLevel = 0,
            dcCancelLevel = 0,
            dcCancelCode = 0,
            ledCurrentSum = ledCurrentTenthsMa,
            ledCurrentDrv0 = ledCurrentTenthsMa,
            ledCurrentDrv1 = 0
        )
}
