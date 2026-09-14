package com.ghealth.tools.feature.factory.exporter

import com.ghealth.tools.feature.factory.engine.CollectedRawData
import com.ghealth.tools.feature.factory.model.TestType
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** 将一次 App 端产测计算使用的原始数据按测试项导出。 */
@Singleton
class FactoryRawDataExporter @Inject constructor() {

    private val directoryTimeFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)

    suspend fun export(
        projectName: String,
        chip: String,
        testType: TestType,
        data: CollectedRawData,
        ledCurrentTenthsMaByChannel: Map<Int, Int>,
        gainKOhmByChannel: Map<Int, Double>,
        testStartedAt: Long,
        baseDir: File
    ): File? = withContext(Dispatchers.IO) {
        try {
            val channels = (data.rawdataByChannel.keys + data.ipdPaByChannel.keys).sorted()
            if (channels.isEmpty()) return@withContext null

            val timeDirectory = directoryTimeFormat.format(Date(testStartedAt))
            val outputDirectory = File(
                baseDir,
                "factory/${sanitizePathSegment(projectName)}/$timeDirectory"
            )
            if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
                return@withContext null
            }

            val outputFile = File(outputDirectory, "${testType.name.lowercase(Locale.US)}.csv")
            val columns = channels.flatMap { channel ->
                buildList {
                    add(RawColumn(channel))
                    if (chip.equals("gh3036", ignoreCase = true)) add(IpdColumn(channel))
                }
            }
            val sampleCount = columns.maxOfOrNull { it.values(data).size } ?: 0

            csvWriter().open(outputFile, append = false) {
                writeRow(columns.map { it.header })
                writeRow(columns.map { ledCurrentTenthsMaByChannel[it.channel]?.toString().orEmpty() })
                writeRow(columns.map { gainKOhmByChannel[it.channel]?.let(::formatNumber).orEmpty() })
                repeat(sampleCount) { sampleIndex ->
                    writeRow(columns.map { it.values(data).getOrNull(sampleIndex)?.toString().orEmpty() })
                }
            }
            outputFile
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Factory raw data CSV export failed: project=%s test=%s", projectName, testType)
            null
        }
    }

    private fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    private fun sanitizePathSegment(value: String): String =
        value.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "unknown_project" }

    private sealed interface Column {
        val channel: Int
        val header: String
        fun values(data: CollectedRawData): List<Int>
    }

    private data class RawColumn(override val channel: Int) : Column {
        override val header: String = "raw_$channel"
        override fun values(data: CollectedRawData): List<Int> =
            data.rawdataByChannel[channel].orEmpty()
    }

    private data class IpdColumn(override val channel: Int) : Column {
        override val header: String = "ipd_pa_$channel"
        override fun values(data: CollectedRawData): List<Int> =
            data.ipdPaByChannel[channel].orEmpty()
    }

}
