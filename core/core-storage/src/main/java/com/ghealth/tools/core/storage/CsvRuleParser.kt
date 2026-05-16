package com.ghealth.tools.core.storage

data class CsvRule(
    val chip: String,
    val columns: List<String>,
    val delimiter: String = ",",
    val encoding: String = "utf-8",
    val hrRefColumn: Map<String, Int> = emptyMap(),
    val spoRefColumn: Map<String, Int> = emptyMap()
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

    fun parseGh3036(): CsvRule {
        val rawColumns = listOf(
            "TimeStamp", "FRAME_ID", "ACCX", "ACCY", "ACCZ",
            "Ipd{0-31}", "FLAG{0-7}", "REF_RESULT{0-15}", "ALGO_RESULT{0-15}",
            "Rawdata{0-31}", "AGC_INFO_CH{0-31}", "LED_INFO_CH{0-31}",
            "GYRO_X", "GYRO_Y", "GYRO_Z"
        )
        return CsvRule(
            chip = "gh3036",
            columns = expandColumns(rawColumns),
            hrRefColumn = mapOf("REF_RESULT0" to 46),
            spoRefColumn = mapOf("REF_RESULT5" to 51)
        )
    }

    fun parseGh3220(): CsvRule {
        val rawColumns = listOf(
            "TimeStamp", "FRAME_ID", "ACCX", "ACCY", "ACCZ",
            "CH{0-15}", "FLAG{0-7}", "REF_RESULT{0-15}", "ALGO_RESULT{0-7}",
            "AGC_INFO_CH{0-15}", "AMB_CH{0-15}",
            "GYRO_X", "GYRO_Y", "GYRO_Z",
            "CH16-31", "ALGO_RESULT{8-15}", "AGC_INFO_CH{16-31}",
            "CAP_CH{0-3}", "TEMP_CH{0-3}"
        )
        return CsvRule(
            chip = "gh3220",
            columns = expandColumns(rawColumns),
            hrRefColumn = mapOf("REF_RESULT0" to 30),
            spoRefColumn = mapOf("REF_RESULT5" to 35)
        )
    }

    fun forChip(chip: String): CsvRule = when (chip) {
        "gh3036" -> parseGh3036()
        "gh3220" -> parseGh3220()
        else -> throw IllegalArgumentException("Unknown chip: $chip")
    }
}
