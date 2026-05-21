package com.ghealth.tools.feature.factory.exporter

import com.ghealth.tools.feature.factory.model.TestResult
import com.ghealth.tools.feature.factory.model.TestSummary
import com.ghealth.tools.feature.factory.model.TestType
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
            val fileName = timeStr.replace(":", "-").replace(" ", "_") + ".csv"

            val resultDir = File(baseDir,
                "factory/result/${summary.chipType}/${summary.projectName}/$dateStr")
            resultDir.mkdirs()

            val file = File(resultDir, fileName)
            file.bufferedWriter().use { writer ->
                // Header
                writer.write(buildHeaderRow())
                writer.newLine()

                // Data row
                writer.write(buildDataRow(summary, now))
                writer.newLine()
            }
            file
        } catch (e: Exception) {
            null
        }
    }

    private fun buildHeaderRow(): String {
        val sb = StringBuilder()
        sb.append("timestamp,datetime,overall_result,error_code,device_info,")
        sb.append("chip_init_status,uuid")

        val maxChannels = 32
        for (i in 0 until maxChannels) sb.append(",base_noise_$i")
        for (i in 0 until maxChannels) sb.append(",ppg_noise_$i,lpctr_$i,lplctr_$i")

        return sb.toString()
    }

    private fun buildDataRow(summary: TestSummary, now: Date): String {
        val sb = StringBuilder()
        val timeStr = timeFormat.format(now)

        sb.append("${summary.timestamp},$timeStr,")
        sb.append(if (summary.overallPassed) "PASS" else "FAIL")
        sb.append(",")
        sb.append("\"${summary.errorCodeString}\"")
        sb.append(",")
        sb.append("\"${summary.deviceInfo}\"")
        sb.append(",")
        sb.append("\"${summary.chipInitStatus}\"")
        sb.append(",")
        sb.append("\"${summary.uuid}\"")

        val maxChannels = 32

        // base_noise channels
        val baseNoiseResults = summary.results[TestType.BASE_NOISE] ?: emptyList()
        for (i in 0 until maxChannels) {
            sb.append(",")
            sb.append(getChannelValue(baseNoiseResults, i))
        }

        // ppg_noise, lpctr, lplctr channels (interleaved per channel)
        val ppgResults = summary.results[TestType.PPG_NOISE] ?: emptyList()
        val lpctrResults = summary.results[TestType.LPCTR] ?: emptyList()
        val lplctrResults = summary.results[TestType.LPLCTR] ?: emptyList()
        for (i in 0 until maxChannels) {
            sb.append(",")
            sb.append(getChannelValue(ppgResults, i))
            sb.append(",")
            sb.append(getChannelValue(lpctrResults, i))
            sb.append(",")
            sb.append(getChannelValue(lplctrResults, i))
        }

        return sb.toString()
    }

    private fun getChannelValue(results: List<TestResult>, channel: Int): String {
        val r = results.find { it.channelIndex == channel }
        return if (r != null) r.value.toString() else "0"
    }
}
