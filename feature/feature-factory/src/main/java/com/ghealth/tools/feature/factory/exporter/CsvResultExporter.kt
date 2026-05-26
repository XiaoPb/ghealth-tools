package com.ghealth.tools.feature.factory.exporter

import com.ghealth.tools.feature.factory.model.TestResult
import com.ghealth.tools.feature.factory.model.TestSummary
import com.ghealth.tools.feature.factory.model.TestType
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CsvResultExporter @Inject constructor() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    suspend fun export(summary: TestSummary, baseDir: File): File? = withContext(Dispatchers.IO) {
        try {
            val now = Date(summary.timestamp)
            val dateStr = dateFormat.format(now)
            val timeStr = timeFormat.format(now)
            val fileName = "$dateStr.csv"

            val resultDir = File(baseDir,
                "factory/result/${summary.chipType}/${summary.projectName}/$dateStr")
            resultDir.mkdirs()

            val file = File(resultDir, fileName)
            val isNew = !file.exists() || file.length() == 0L
            val headerRow = buildHeaderRow()
            val dataRow = buildDataRow(summary, timeStr)

            csvWriter().open(file, append = !isNew) {
                if (isNew) writeRow(headerRow)
                writeRow(dataRow)
            }
            file
        } catch (e: Exception) {
            null
        }
    }

    private fun buildHeaderRow(): List<String> {
        val columns = mutableListOf<String>()
        columns.addAll(listOf(
            "timestamp", "datetime", "overall_result", "error_code",
            "device_info", "device_addr", "chip_init", "uuid"
        ))
        for (i in 0 until 8) columns.add("base_noise_$i")
        for (i in 0 until 32) {
            columns.add("ppg_noise_$i")
            columns.add("lpctr_$i")
            columns.add("lplctr_$i")
        }
        return columns
    }

    private fun buildDataRow(summary: TestSummary, timeStr: String): List<String> {
        val row = mutableListOf<String>()
        row.add(summary.timestamp.toString())
        row.add(timeStr)
        row.add(if (summary.overallPassed) "PASS" else "FAIL")
        row.add(summary.errorCodeString)
        row.add(summary.deviceInfo)
        row.add(summary.deviceAddress)
        row.add(summary.chipInitStatus)
        row.add(summary.uuid)

        val baseNoiseResults = summary.results[TestType.BASE_NOISE] ?: emptyList()
        for (i in 0 until 8) {
            row.add(getChannelValue(baseNoiseResults, i))
        }

        val ppgResults = summary.results[TestType.PPG_NOISE] ?: emptyList()
        val lpctrResults = summary.results[TestType.LPCTR] ?: emptyList()
        val lplctrResults = summary.results[TestType.LPLCTR] ?: emptyList()
        for (i in 0 until 32) {
            row.add(getChannelValue(ppgResults, i))
            row.add(getChannelValue(lpctrResults, i))
            row.add(getChannelValue(lplctrResults, i))
        }

        return row
    }

    private fun getChannelValue(results: List<TestResult>, channel: Int): String {
        val r = results.find { it.channelIndex == channel }
        return if (r != null) r.value.toString() else "0"
    }
}
