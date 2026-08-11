package com.ghealth.tools.core.storage

import com.ghealth.tools.core.model.FunctionMode

/** records CSV 列种类。 */
enum class RecordsColumnKind {
    /** 时间戳列（毫秒）。 */
    TIMESTAMP,

    /** 主设备算法字段，取值 ALGO_RESULT{algoIndex}。 */
    MASTER_ALGO,

    /** 从设备槽位算法字段，取值 slaveAlgos[slaveIndex][algoIndex]。 */
    SLAVE_ALGO,

    /** 标准设备HR值（主金标），取值 compareHrs[0]，无值时写 0。 */
    GOLD_HR,

    /** 标准设备RRI值，当前无可来源，恒写 0。 */
    GOLD_RRI,

    /** 其他对比设备心率（参考文档 Device{i}_HR），取值 compareHrs[compareIndex]。 */
    COMPARE_HR,

    /** 其他对比设备血氧（参考文档 Device{i}_SPO2），取值 compareSpo2s[compareIndex]。 */
    COMPARE_SPO2,

    /** 其他对比设备值但当前无可来源（参考文档 Device{i}_HRV），恒写 0。 */
    COMPARE_UNSOURCED
}

/** records CSV 单列定义。 */
data class RecordsColumn(
    val name: String,
    val kind: RecordsColumnKind,
    val algoIndex: Int? = null,
    val slaveIndex: Int? = null,
    val compareIndex: Int? = null
)

/**
 * records CSV 列规格（每秒一行），参考 record_app_output_data_format_20241105.csv。
 *
 * 列序：TimeStamp → 主设备算法字段 → 从设备算法字段（Slave1..3，与主设备同字段集）→ 金标/对比设备列。
 * 金标/对比/从设备列名带实际设备名；表头与行值共用同一份规格，保证值写入对应列。
 * - 表格功能（HR/ECG/SPO2/HRV）：含金标/对比设备列（金标名_HR/金标名_RRI/对比设备名_*）。
 * - 其他功能：仅算法结果（主设备 + Slave1..3），不保存金标。
 */
object RecordsFormat {

    /** 从设备槽位数（列名 Slave1..3）。 */
    const val MAX_SLAVE_DEVICES = 3

    /** 主设备算法字段：列名 → ALGO_RESULT 下标（与 parseAlgorithmResult 的下标一致）。 */
    private val MASTER_ALGO_FIELDS: Map<FunctionMode, List<Pair<String, Int>>> = mapOf(
        FunctionMode.HR to listOf("HR" to 0, "Confidence" to 1, "SNR" to 2),
        FunctionMode.ECG to listOf("ECG_Voltage" to 0, "HeartRate" to 1, "SNR" to 2),
        FunctionMode.SPO2 to listOf(
            "SPO2" to 0, "RValue" to 1, "Confidence" to 2, "ValidLevel" to 3, "HeartRate" to 4
        ),
        FunctionMode.HRV to listOf(
            "RRI1" to 0, "RRI2" to 1, "RRI3" to 2, "RRI4" to 3, "Confidence" to 4, "RRI_Count" to 5
        ),
        FunctionMode.ADT to listOf("WearEvent" to 0, "DetStatus" to 1, "Ctr" to 2),
        FunctionMode.NADT_GREEN to listOf("WearStatus" to 0, "SuspectOff" to 1, "LiveBodyConf" to 2),
        FunctionMode.NADT_IR to listOf("WearStatus" to 0, "SuspectOff" to 1, "LiveBodyConf" to 2),
        FunctionMode.BT to listOf("NTC0" to 0, "NTC1" to 1)
    )

    /** 表格功能（参考文档定义的表格式保存，含金标/对比设备列）。 */
    private val TABLE_MODES = setOf(
        FunctionMode.HR, FunctionMode.ECG, FunctionMode.SPO2, FunctionMode.HRV
    )

    /**
     * 指定 mode 的完整 records 列规格（以 TimeStamp 开头）。
     * 金标/对比/从设备列名由实际设备名生成；无设备名时回退 Gold/Device{i}/Slave{i+1}。
     */
    fun columnsFor(
        mode: FunctionMode,
        goldDeviceName: String? = null,
        compareDeviceNames: List<String> = emptyList(),
        slaveDeviceNames: List<String> = emptyList()
    ): List<RecordsColumn> {
        val columns = mutableListOf(RecordsColumn("TimeStamp", RecordsColumnKind.TIMESTAMP))
        val fields = masterFields(mode)
        for ((name, index) in fields) {
            columns.add(RecordsColumn(name, RecordsColumnKind.MASTER_ALGO, algoIndex = index))
        }
        // 从设备算法字段：Slave1..3 槽位，列名带从设备名
        for (slot in 0 until MAX_SLAVE_DEVICES) {
            val prefix = sanitizeColumnToken(
                slaveDeviceNames.getOrNull(slot)?.takeIf { it.isNotBlank() } ?: "Slave${slot + 1}"
            )
            for ((name, index) in fields) {
                columns.add(
                    RecordsColumn("${prefix}_$name", RecordsColumnKind.SLAVE_ALGO, algoIndex = index, slaveIndex = slot)
                )
            }
        }
        // 金标/对比设备列（仅表格功能）
        if (mode in TABLE_MODES) {
            columns.addAll(goldColumns(mode, goldDeviceName, compareDeviceNames))
        }
        return dedupeNames(columns)
    }

    /** 主设备算法字段；未定义的模式退化为通用 Algo0（ALGO_RESULT0）。 */
    fun masterFields(mode: FunctionMode): List<Pair<String, Int>> =
        MASTER_ALGO_FIELDS[mode] ?: listOf("Algo" to 0)

    /** 金标/对比设备列（仅表格功能；参考文档 Device1..3 为其他对比设备值）。 */
    private fun goldColumns(
        mode: FunctionMode,
        goldDeviceName: String?,
        compareDeviceNames: List<String>
    ): List<RecordsColumn> {
        val goldPrefix = sanitizeColumnToken(goldDeviceName?.takeIf { it.isNotBlank() } ?: "Gold")
        val columns = mutableListOf(RecordsColumn("${goldPrefix}_HR", RecordsColumnKind.GOLD_HR))
        if (mode == FunctionMode.SPO2 || mode == FunctionMode.HRV) {
            for (i in 1..4) {
                columns.add(RecordsColumn("${goldPrefix}_RRI$i", RecordsColumnKind.GOLD_RRI))
            }
        }
        when (mode) {
            FunctionMode.HR -> for (i in 1..3) {
                val prefix = sanitizeColumnToken(
                    compareDeviceNames.getOrNull(i - 1)?.takeIf { it.isNotBlank() } ?: "Device$i"
                )
                columns.add(RecordsColumn("${prefix}_HR", RecordsColumnKind.COMPARE_HR, compareIndex = i))
            }
            FunctionMode.SPO2 -> for (i in 1..3) {
                val prefix = sanitizeColumnToken(
                    compareDeviceNames.getOrNull(i - 1)?.takeIf { it.isNotBlank() } ?: "Device$i"
                )
                columns.add(RecordsColumn("${prefix}_SPO2", RecordsColumnKind.COMPARE_SPO2, compareIndex = i))
            }
            FunctionMode.HRV -> for (i in 1..3) {
                val prefix = sanitizeColumnToken(
                    compareDeviceNames.getOrNull(i - 1)?.takeIf { it.isNotBlank() } ?: "Device$i"
                )
                columns.add(RecordsColumn("${prefix}_HRV", RecordsColumnKind.COMPARE_UNSOURCED, compareIndex = i))
            }
            else -> {} // ECG 按参考文档无 Device 列
        }
        return columns
    }
}

/** 从设备地址 → 槽位（0..MAX_SLAVE_DEVICES-1）；未知地址或超上限返回 -1。 */
internal fun slaveSlotFor(slaveAddresses: List<String>, address: String): Int {
    val index = slaveAddresses.indexOf(address)
    return if (index in 0 until RecordsFormat.MAX_SLAVE_DEVICES) index else -1
}

/** CSV 表头列名安全化：逗号/引号/换行替换为下划线并去除首尾空白。 */
internal fun sanitizeColumnToken(name: String): String =
    name.replace(Regex("""[,\"\r\n]"""), "_").trim()

/** 列名去重：重名追加 _2/_3 后缀，保证每个值都有唯一列可写。 */
internal fun dedupeNames(columns: List<RecordsColumn>): List<RecordsColumn> {
    val seen = mutableSetOf<String>()
    return columns.map { col ->
        var name = col.name
        var suffix = 2
        while (!seen.add(name)) {
            name = "${col.name}_$suffix"
            suffix++
        }
        col.copy(name = name)
    }
}

/** 从帧 columnMap 拷贝 ALGO_RESULT0..15 到缓冲区，缺失写 0。 */
internal fun copyAlgoValues(columnMap: Map<String, Any?>, dest: IntArray) {
    for (i in dest.indices) {
        dest[i] = (columnMap["ALGO_RESULT$i"] as? Number)?.toInt() ?: 0
    }
}

/** 取数组下标值，越界或缺省写 0。 */
private fun algoAt(array: IntArray, index: Int?): Int =
    if (index != null && index >= 0 && index < array.size) array[index] else 0

/** 由 records 缓冲区构建一行值；金标/对比设备无值写 0，从设备未连接写 0，绝不写空。 */
internal fun buildRecordsValues(
    columns: List<RecordsColumn>,
    masterAlgo: IntArray,
    slaveAlgos: Array<IntArray>,
    compareHrs: Map<Int, Int>,
    compareSpo2s: Map<Int, Float>,
    timestampMs: Long
): Map<String, Any?> {
    val values = mutableMapOf<String, Any?>()
    for (col in columns) {
        values[col.name] = when (col.kind) {
            RecordsColumnKind.TIMESTAMP -> timestampMs
            RecordsColumnKind.MASTER_ALGO -> algoAt(masterAlgo, col.algoIndex)
            RecordsColumnKind.SLAVE_ALGO -> {
                val slot = col.slaveIndex ?: 0
                val index = col.algoIndex ?: 0
                if (slot in slaveAlgos.indices) algoAt(slaveAlgos[slot], index) else 0
            }
            RecordsColumnKind.GOLD_HR -> compareHrs[0] ?: 0
            RecordsColumnKind.GOLD_RRI -> 0
            RecordsColumnKind.COMPARE_HR -> compareHrs[col.compareIndex ?: 0] ?: 0
            RecordsColumnKind.COMPARE_SPO2 -> compareSpo2s[col.compareIndex ?: 0] ?: 0
            RecordsColumnKind.COMPARE_UNSOURCED -> 0
        }
    }
    return values
}
