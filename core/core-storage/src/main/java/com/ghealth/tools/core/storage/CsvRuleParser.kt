package com.ghealth.tools.core.storage

data class CsvRule(
    val chip: String,
    val columns: List<String>,
    val delimiter: String = ",",
    val encoding: String = "utf-8"
)

object CsvRuleParser {

    private val RANGE_PATTERN = Regex("""(.+?)\{(\d+)-(\d+)\}""")

    fun expandColumns(rawColumns: List<String>): List<String> {
        val result = mutableListOf<String>()
        for (col in rawColumns) {
            val match = RANGE_PATTERN.matchEntire(col)
            if (match != null) {
                val prefix = match.groupValues[1]
                val start = match.groupValues[2].toInt()
                val end = match.groupValues[3].toInt()
                for (i in start..end) {
                    result.add("$prefix$i")
                }
            } else {
                result.add(col)
            }
        }
        return result
    }

    private val gh3036Columns = listOf(
        "TimeStamp", "FRAME_ID", "ACCX", "ACCY", "ACCZ",
        "Ipd{0-31}", "FLAG{0-7}", "REF_RESULT{0-15}", "ALGO_RESULT{0-15}",
        "Rawdata{0-31}", "AGC_INFO_CH{0-31}", "LED_INFO_CH{0-31}",
        "GYRO_X", "GYRO_Y", "GYRO_Z"
    )

    private val gh3220Columns = listOf(
        "TimeStamp", "FRAME_ID", "ACCX", "ACCY", "ACCZ",
        "CH{0-15}", "FLAG{0-7}", "REF_RESULT{0-15}", "ALGO_RESULT{0-7}",
        "AGC_INFO_CH{0-15}", "AMB_CH{0-15}",
        "GYRO_X", "GYRO_Y", "GYRO_Z",
        "CH16-31", "ALGO_RESULT{8-15}", "AGC_INFO_CH{16-31}",
        "CAP_CH{0-3}", "TEMP_CH{0-3}"
    )

    val gh3036: CsvRule by lazy {
        CsvRule(
            chip = "gh3036",
            columns = expandColumns(gh3036Columns)
        )
    }

    val gh3220: CsvRule by lazy {
        CsvRule(
            chip = "gh3220",
            columns = expandColumns(gh3220Columns)
        )
    }

    fun forChip(chip: String): CsvRule = when (chip.lowercase()) {
        "gh3036" -> gh3036
        "gh3220" -> gh3220
        else -> throw IllegalArgumentException("Unknown chip: $chip")
    }

    fun forRecordsCsv(maxCompareDevices: Int): CsvRule {
        val columns = mutableListOf("TimeStamp", "MasterAlgo", "SlaveAlgo")
        for (i in 0 until maxCompareDevices.coerceIn(0, 5)) {
            columns.add("Compare${i}_HR")
        }
        return CsvRule(chip = "records", columns = columns)
    }

    fun forChipWithCompareDevices(chip: String, compareDeviceNames: List<String>): CsvRule {
        val baseRule = forChip(chip)
        if (compareDeviceNames.isEmpty()) return baseRule

        val newColumns = baseRule.columns.mapIndexed { index, column ->
            val refResultMatch = Regex("""REF_RESULT(\d+)""").find(column)
            if (refResultMatch != null) {
                val refIndex = refResultMatch.groupValues[1].toInt()
                if (refIndex < compareDeviceNames.size && compareDeviceNames[refIndex].isNotEmpty()) {
                    compareDeviceNames[refIndex]
                } else {
                    column
                }
            } else {
                column
            }
        }

        return CsvRule(
            chip = baseRule.chip,
            columns = newColumns,
            delimiter = baseRule.delimiter,
            encoding = baseRule.encoding
        )
    }
}
