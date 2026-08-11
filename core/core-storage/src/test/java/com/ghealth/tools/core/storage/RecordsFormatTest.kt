package com.ghealth.tools.core.storage

import com.ghealth.tools.core.model.FunctionMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecordsFormatTest {

    @Test
    fun `HR 表格功能列序为主算法 从算法 金标与对比设备`() {
        assertEquals(
            listOf(
                "TimeStamp", "HR", "Confidence", "SNR",
                "Slave1_HR", "Slave1_Confidence", "Slave1_SNR",
                "Slave2_HR", "Slave2_Confidence", "Slave2_SNR",
                "Slave3_HR", "Slave3_Confidence", "Slave3_SNR",
                "Gold_HR", "Device1_HR", "Device2_HR", "Device3_HR"
            ),
            RecordsFormat.columnsFor(FunctionMode.HR).map { it.name }
        )
    }

    @Test
    fun `ECG 表格功能含从设备字段但无 Device 对比列`() {
        assertEquals(
            listOf(
                "TimeStamp", "ECG_Voltage", "HeartRate", "SNR",
                "Slave1_ECG_Voltage", "Slave1_HeartRate", "Slave1_SNR",
                "Slave2_ECG_Voltage", "Slave2_HeartRate", "Slave2_SNR",
                "Slave3_ECG_Voltage", "Slave3_HeartRate", "Slave3_SNR",
                "Gold_HR"
            ),
            RecordsFormat.columnsFor(FunctionMode.ECG).map { it.name }
        )
    }

    @Test
    fun `SPO2 表格功能含从设备字段 金标 RRI 与对比 SPO2 列`() {
        assertEquals(
            listOf(
                "TimeStamp", "SPO2", "RValue", "Confidence", "ValidLevel", "HeartRate",
                "Slave1_SPO2", "Slave1_RValue", "Slave1_Confidence", "Slave1_ValidLevel", "Slave1_HeartRate",
                "Slave2_SPO2", "Slave2_RValue", "Slave2_Confidence", "Slave2_ValidLevel", "Slave2_HeartRate",
                "Slave3_SPO2", "Slave3_RValue", "Slave3_Confidence", "Slave3_ValidLevel", "Slave3_HeartRate",
                "Gold_HR", "Gold_RRI1", "Gold_RRI2", "Gold_RRI3", "Gold_RRI4",
                "Device1_SPO2", "Device2_SPO2", "Device3_SPO2"
            ),
            RecordsFormat.columnsFor(FunctionMode.SPO2).map { it.name }
        )
    }

    @Test
    fun `HRV 表格功能含从设备字段 金标 RRI 与对比 HRV 列`() {
        assertEquals(
            listOf(
                "TimeStamp", "RRI1", "RRI2", "RRI3", "RRI4", "Confidence", "RRI_Count",
                "Slave1_RRI1", "Slave1_RRI2", "Slave1_RRI3", "Slave1_RRI4", "Slave1_Confidence", "Slave1_RRI_Count",
                "Slave2_RRI1", "Slave2_RRI2", "Slave2_RRI3", "Slave2_RRI4", "Slave2_Confidence", "Slave2_RRI_Count",
                "Slave3_RRI1", "Slave3_RRI2", "Slave3_RRI3", "Slave3_RRI4", "Slave3_Confidence", "Slave3_RRI_Count",
                "Gold_HR", "Gold_RRI1", "Gold_RRI2", "Gold_RRI3", "Gold_RRI4",
                "Device1_HRV", "Device2_HRV", "Device3_HRV"
            ),
            RecordsFormat.columnsFor(FunctionMode.HRV).map { it.name }
        )
    }

    @Test
    fun `ADT 非表格功能仅算法结果无金标列`() {
        assertEquals(
            listOf(
                "TimeStamp", "WearEvent", "DetStatus", "Ctr",
                "Slave1_WearEvent", "Slave1_DetStatus", "Slave1_Ctr",
                "Slave2_WearEvent", "Slave2_DetStatus", "Slave2_Ctr",
                "Slave3_WearEvent", "Slave3_DetStatus", "Slave3_Ctr"
            ),
            RecordsFormat.columnsFor(FunctionMode.ADT).map { it.name }
        )
    }

    @Test
    fun `NADT 非表格功能仅算法结果无金标列`() {
        assertEquals(
            listOf(
                "TimeStamp", "WearStatus", "SuspectOff", "LiveBodyConf",
                "Slave1_WearStatus", "Slave1_SuspectOff", "Slave1_LiveBodyConf",
                "Slave2_WearStatus", "Slave2_SuspectOff", "Slave2_LiveBodyConf",
                "Slave3_WearStatus", "Slave3_SuspectOff", "Slave3_LiveBodyConf"
            ),
            RecordsFormat.columnsFor(FunctionMode.NADT_GREEN).map { it.name }
        )
    }

    @Test
    fun `NADT 位打包字段按 parseAlgorithmResult 语义取值`() {
        val master = IntArray(16).also { it[0] = 0b110; it[1] = 85 }
        val slaves = Array(RecordsFormat.MAX_SLAVE_DEVICES) { IntArray(16) }.also { it[0][0] = 0b110; it[0][1] = 85 }
        val values = buildRecordsValues(
            RecordsFormat.columnsFor(FunctionMode.NADT_GREEN),
            master,
            slaves,
            emptyMap(),
            emptyMap(),
            1L
        )
        assertEquals(2, values["WearStatus"])
        assertEquals(1, values["SuspectOff"])
        assertEquals(85, values["LiveBodyConf"])
        assertEquals(2, values["Slave1_WearStatus"])
        assertEquals(1, values["Slave1_SuspectOff"])
        assertEquals(85, values["Slave1_LiveBodyConf"])
        assertEquals(0, values["Slave2_WearStatus"])
        assertEquals(0, values["Slave2_SuspectOff"])
        assertEquals(0, values["Slave2_LiveBodyConf"])
    }

    @Test
    fun `BT 非表格功能仅算法结果无金标列`() {
        assertEquals(
            listOf(
                "TimeStamp", "NTC0", "NTC1",
                "Slave1_NTC0", "Slave1_NTC1",
                "Slave2_NTC0", "Slave2_NTC1",
                "Slave3_NTC0", "Slave3_NTC1"
            ),
            RecordsFormat.columnsFor(FunctionMode.BT).map { it.name }
        )
    }

    @Test
    fun `未解析功能使用通用 Algo 字段且无金标`() {
        assertEquals(
            listOf("TimeStamp", "Algo", "Slave1_Algo", "Slave2_Algo", "Slave3_Algo"),
            RecordsFormat.columnsFor(FunctionMode.TEST1).map { it.name }
        )
    }

    @Test
    fun `buildRecordsValues 写入主算法 从算法 金标与对比设备值`() {
        val master = IntArray(16)
        master[0] = 72
        master[1] = 90
        master[2] = 25
        val slaves = Array(RecordsFormat.MAX_SLAVE_DEVICES) { IntArray(16) }
        slaves[0][0] = 71
        slaves[0][1] = 88
        val values = buildRecordsValues(
            RecordsFormat.columnsFor(FunctionMode.HR),
            master,
            slaves,
            mapOf(0 to 70, 1 to 69),
            emptyMap(),
            1_700_000_000L
        )
        assertEquals(72, values["HR"])
        assertEquals(90, values["Confidence"])
        assertEquals(25, values["SNR"])
        assertEquals(71, values["Slave1_HR"])
        assertEquals(88, values["Slave1_Confidence"])
        assertEquals(0, values["Slave2_HR"])
        assertEquals(70, values["Gold_HR"])
        assertEquals(69, values["Device1_HR"])
        assertEquals(0, values["Device2_HR"])
    }

    @Test
    fun `SPO2 对比设备列取 compareSpo2s 值`() {
        val values = buildRecordsValues(
            RecordsFormat.columnsFor(FunctionMode.SPO2),
            IntArray(16),
            Array(RecordsFormat.MAX_SLAVE_DEVICES) { IntArray(16) },
            mapOf(0 to 72),
            mapOf(1 to 98.5f, 3 to 97.0f),
            1L
        )
        assertEquals(98.5f, values["Device1_SPO2"])
        assertEquals(0, values["Device2_SPO2"])
        assertEquals(97.0f, values["Device3_SPO2"])
    }

    @Test
    fun `金标无值时写 0 不写空`() {
        val values = buildRecordsValues(
            RecordsFormat.columnsFor(FunctionMode.SPO2),
            IntArray(16),
            Array(RecordsFormat.MAX_SLAVE_DEVICES) { IntArray(16) },
            emptyMap(),
            emptyMap(),
            1L
        )
        assertEquals(0, values["Gold_HR"])
        assertEquals(0, values["Gold_RRI1"])
        assertEquals(0, values["Gold_RRI4"])
        assertEquals(0, values["Device1_SPO2"])
    }

    @Test
    fun `金标列名跟随金标设备名且对比与从设备列带设备名`() {
        val columns = RecordsFormat.columnsFor(
            mode = FunctionMode.HR,
            goldDeviceName = "HUAWEI Band HR-AD1",
            compareDeviceNames = listOf("Watch2"),
            slaveDeviceNames = listOf("Watch3")
        ).map { it.name }
        assertEquals(
            listOf(
                "TimeStamp", "HR", "Confidence", "SNR",
                "Watch3_HR", "Watch3_Confidence", "Watch3_SNR",
                "Slave2_HR", "Slave2_Confidence", "Slave2_SNR",
                "Slave3_HR", "Slave3_Confidence", "Slave3_SNR",
                "HUAWEI Band HR-AD1_HR", "Watch2_HR", "Device2_HR", "Device3_HR"
            ),
            columns
        )
    }

    @Test
    fun `设备名含逗号或引号时列名安全化`() {
        assertEquals("A_B", sanitizeColumnToken("A,B"))
        assertEquals("A_B", sanitizeColumnToken("A\"B"))
        assertEquals("", sanitizeColumnToken(""))
        assertEquals("Name", sanitizeColumnToken("  Name  "))
    }

    @Test
    fun `带设备名列名时值写入对应列`() {
        val master = IntArray(16).also { it[0] = 72 }
        val slaves = Array(RecordsFormat.MAX_SLAVE_DEVICES) { IntArray(16) }.also { it[0][0] = 71 }
        val columns = RecordsFormat.columnsFor(
            FunctionMode.HR, "HUAWEI Band HR-AD1", listOf("Watch2"), listOf("Watch3")
        )
        val values = buildRecordsValues(columns, master, slaves, mapOf(0 to 70, 1 to 69), emptyMap(), 1L)
        assertEquals(70, values["HUAWEI Band HR-AD1_HR"])
        assertEquals(69, values["Watch2_HR"])
        assertEquals(71, values["Watch3_HR"])
    }

    @Test
    fun `行值键与声明的列名一一对应且非空`() {
        for (mode in FunctionMode.entries) {
            val columns = RecordsFormat.columnsFor(
                mode,
                goldDeviceName = "Gold-1",
                compareDeviceNames = listOf("C2", "C3", "C4"),
                slaveDeviceNames = listOf("S1", "S2", "S3")
            )
            val values = buildRecordsValues(
                columns,
                IntArray(16),
                Array(RecordsFormat.MAX_SLAVE_DEVICES) { IntArray(16) },
                mapOf(0 to 70, 1 to 69, 2 to 68, 3 to 67),
                mapOf(1 to 98.5f, 2 to 97.0f, 3 to 96.0f),
                1L
            )
            assertEquals(columns.map { it.name }.toSet(), values.keys)
            assertTrue(values.values.none { it == null })
        }
    }

    @Test
    fun `copyAlgoValues 拷贝 ALGO_RESULT 值缺失写 0`() {
        val dest = IntArray(16)
        copyAlgoValues(mapOf("ALGO_RESULT0" to 72, "ALGO_RESULT1" to 90), dest)
        assertEquals(72, dest[0])
        assertEquals(90, dest[1])
        assertEquals(0, dest[2])
    }

    @Test
    fun `slaveSlotFor 按连接顺序分配从设备槽位超上限为负`() {
        val addresses = listOf("AA:1", "BB:2", "CC:3", "DD:4")
        assertEquals(0, slaveSlotFor(addresses, "AA:1"))
        assertEquals(1, slaveSlotFor(addresses, "BB:2"))
        assertEquals(2, slaveSlotFor(addresses, "CC:3"))
        assertEquals(-1, slaveSlotFor(addresses, "DD:4"))
        assertEquals(-1, slaveSlotFor(addresses, "unknown"))
    }

    @Test
    fun `重名设备列名去重后值写入唯一列`() {
        val columns = RecordsFormat.columnsFor(
            FunctionMode.HR,
            goldDeviceName = "Watch",
            compareDeviceNames = listOf("Watch"),
            slaveDeviceNames = listOf("Watch")
        ).map { it.name }
        assertEquals(columns.size, columns.toSet().size)
        assertTrue("Watch_HR" in columns)
        assertTrue("Watch_HR_2" in columns)
        assertTrue("Watch_HR_3" in columns)
    }

    @Test
    fun `负向槽位或负向算法下标写 0 不崩溃`() {
        val columns = listOf(
            RecordsColumn("BadSlot", RecordsColumnKind.SLAVE_ALGO, algoIndex = 0, slaveIndex = -1),
            RecordsColumn("BadAlgo", RecordsColumnKind.MASTER_ALGO, algoIndex = -1)
        )
        val values = buildRecordsValues(
            columns,
            IntArray(16),
            Array(1) { IntArray(16) },
            emptyMap(),
            emptyMap(),
            1L
        )
        assertEquals(0, values["BadSlot"])
        assertEquals(0, values["BadAlgo"])
    }

    @Test
    fun `全空白设备名回退默认前缀`() {
        val columns = RecordsFormat.columnsFor(
            FunctionMode.HR,
            goldDeviceName = "   ",
            compareDeviceNames = listOf("  "),
            slaveDeviceNames = listOf("  ")
        ).map { it.name }
        assertTrue("Gold_HR" in columns)
        assertTrue("Device1_HR" in columns)
        assertTrue("Slave1_HR" in columns)
    }

    @Test
    fun `records 规则按 mode 与设备名声明列数`() {
        assertEquals(
            listOf(
                "TimeStamp", "HR", "Confidence", "SNR",
                "Slave1_HR", "Slave1_Confidence", "Slave1_SNR",
                "Slave2_HR", "Slave2_Confidence", "Slave2_SNR",
                "Slave3_HR", "Slave3_Confidence", "Slave3_SNR",
                "Gold_HR", "Device1_HR", "Device2_HR", "Device3_HR"
            ),
            CsvRuleParser.forRecordsCsv(FunctionMode.HR).columns
        )
        assertEquals(
            listOf(
                "TimeStamp", "Algo", "Slave1_Algo", "Slave2_Algo", "Slave3_Algo"
            ),
            CsvRuleParser.forRecordsCsv(FunctionMode.TEST2).columns
        )
        assertEquals(
            listOf(
                "TimeStamp", "HR", "Confidence", "SNR",
                "Watch3_HR", "Watch3_Confidence", "Watch3_SNR",
                "Slave2_HR", "Slave2_Confidence", "Slave2_SNR",
                "Slave3_HR", "Slave3_Confidence", "Slave3_SNR",
                "HUAWEI Band HR-AD1_HR", "Watch2_HR", "Device2_HR", "Device3_HR"
            ),
            CsvRuleParser.forRecordsCsv(
                FunctionMode.HR,
                goldDeviceName = "HUAWEI Band HR-AD1",
                compareDeviceNames = listOf("Watch2"),
                slaveDeviceNames = listOf("Watch3")
            ).columns
        )
    }
}
