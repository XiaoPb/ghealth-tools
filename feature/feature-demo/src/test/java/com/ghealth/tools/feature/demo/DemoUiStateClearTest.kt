package com.ghealth.tools.feature.demo

import com.ghealth.tools.core.model.DeviceType
import com.ghealth.tools.core.model.FunctionMode
import com.ghealth.tools.core.model.TestScenario
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DemoUiStateClearTest {

    /** 构造一个所有字段均为非默认值的完整状态,用于验证清空与保留边界。 */
    private fun fullState(): DemoUiState = DemoUiState(
        functionDataMap = mapOf(
            FunctionMode.HR to FunctionData(FunctionMode.HR, AlgorithmResult.HR(heartRate = 72), frameCount = 10)
        ),
        selectedFunction = FunctionMode.HR,
        chipType = DeviceType.GH3220,
        waveform1Data = listOf(1f, 2f, 3f),
        waveform2Data = listOf(4f, 5f, 6f),
        waveform1Column = "CH0",
        waveform2Column = "CH1",
        waveform1Stats = WaveformStats(max = 3f, min = 1f, avg = 2f, diff = 2f),
        waveform2Stats = WaveformStats(max = 6f, min = 4f, avg = 5f, diff = 2f),
        frameIds = listOf(1f, 2f, 3f),
        isRecording = true,
        compareHrResults = mapOf(2 to 80),
        masterAlgoResult = AlgorithmResult.HR(heartRate = 72),
        slaveAlgoResult = AlgorithmResult.SPO2(spo2 = 98),
        testerName = "Tester",
        scenario = "运动状态",
        testRound = 3,
        lastTestScenario = TestScenario.EXERCISE,
        manualCompareDevices = listOf(ManualCompareDevice(name = "DevA", spo2 = 99f)),
        showAddCompareDialog = true,
        editingCompareDeviceIndex = 1,
        showRestartConfigDialog = true,
        displayWidths = mapOf(FunctionMode.HR to 250),
        availableColumns = listOf("CH0", "CH1")
    )

    @Test
    fun `clearReceivedData 清空波形数据与统计值`() {
        val cleared = fullState().clearReceivedData()
        assertEquals(emptyList<Float>(), cleared.waveform1Data)
        assertEquals(emptyList<Float>(), cleared.waveform2Data)
        assertNull(cleared.waveform1Stats)
        assertNull(cleared.waveform2Stats)
    }

    @Test
    fun `clearReceivedData 清空 frameIds 与 functionDataMap`() {
        val cleared = fullState().clearReceivedData()
        assertEquals(emptyList<Float>(), cleared.frameIds)
        assertEquals(emptyMap<FunctionMode, FunctionData>(), cleared.functionDataMap)
    }

    @Test
    fun `clearReceivedData 清空算法结果与可选列`() {
        val cleared = fullState().clearReceivedData()
        assertEquals(AlgorithmResult.None, cleared.masterAlgoResult)
        assertNull(cleared.slaveAlgoResult)
        assertEquals(emptyList<String>(), cleared.availableColumns)
    }

    @Test
    fun `clearReceivedData 保留选中功能与芯片类型`() {
        val cleared = fullState().clearReceivedData()
        assertEquals(FunctionMode.HR, cleared.selectedFunction)
        assertEquals(DeviceType.GH3220, cleared.chipType)
    }

    @Test
    fun `clearReceivedData 保留列选择与显示宽度`() {
        val cleared = fullState().clearReceivedData()
        assertEquals("CH0", cleared.waveform1Column)
        assertEquals("CH1", cleared.waveform2Column)
        assertEquals(mapOf(FunctionMode.HR to 250), cleared.displayWidths)
    }

    @Test
    fun `clearReceivedData 保留录制状态与对比心率结果`() {
        val cleared = fullState().clearReceivedData()
        assertEquals(true, cleared.isRecording)
        assertEquals(mapOf(2 to 80), cleared.compareHrResults)
    }

    @Test
    fun `clearReceivedData 保留测试信息与手动对比设备`() {
        val cleared = fullState().clearReceivedData()
        assertEquals("Tester", cleared.testerName)
        assertEquals("运动状态", cleared.scenario)
        assertEquals(3, cleared.testRound)
        assertEquals(TestScenario.EXERCISE, cleared.lastTestScenario)
        assertEquals(listOf(ManualCompareDevice(name = "DevA", spo2 = 99f)), cleared.manualCompareDevices)
    }

    @Test
    fun `clearReceivedData 保留对话框可见性状态`() {
        val cleared = fullState().clearReceivedData()
        assertEquals(true, cleared.showAddCompareDialog)
        assertEquals(1, cleared.editingCompareDeviceIndex)
        assertEquals(true, cleared.showRestartConfigDialog)
    }
}
