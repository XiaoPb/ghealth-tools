# Feature-Demo 模块流程文档

## 1. 模块概述

`feature-demo` 是实时数据演示模块。接收 BLE 设备 G 协议实时数据，展示波形图、算法结果、设备对比数据，并驱动 CSV 录制。

## 2. 核心组件

| 组件 | 文件 | 职责 |
|------|------|------|
| `DemoViewModel` | `DemoViewModel.kt` | 实时数据处理、算法解析、录制控制 |
| `DemoScreen` | `DemoScreen.kt` | 波形/算法/心率展示 UI |
| `DemoUiState` | DemoViewModel 内 | UI 状态数据类 |

## 3. DemoUiState

```kotlin
data class DemoUiState(
    val isRecording: Boolean,
    val chipType: DeviceType,
    val currentFunctionMode: String,
    val waveformData: List<Float>,
    val algoResults: Map<String, String>,
    val heartRateCompare: Map<Int, Int>,
    val connectedDeviceCount: Int,
    val testerName: String,
    val lastTestScenario: String,
    val testRound: Int,
    val showRestartConfigDialog: Boolean
)
```

## 4. 实时数据处理流程

### 4.1 G 协议帧接收

```
BLE Notify → Gh3036FrameDecoder.decode()
  │
  ▼
ghFrameFlow.emit(address, GhFuncFrame)
  │
  ▼
DemoViewModel 收集 ghFrameFlow
  │
  ▼
onFrameReceived(address, frame)
  │
  ├── 1. 按 funcId 路由
  │     ├── ADT → 波形更新
  │     ├── HR → 心率值更新
  │     ├── SPO2 → 血氧值更新
  │     └── NADT → 多通道数据更新
  │
  ├── 2. 提取算法结果 (algoData)
  │     ├── ALGO_RESULT0 → 主算法结果
  │     ├── ALGO_RESULT1 → 辅助算法结果
  │     └── ... → 更新 algoResults Map
  │
  ├── 3. 提取波形数据 (rawdata / phyValue)
  │     └── 更新 waveformData List → 触发图表重绘
  │
  ├── 4. toColumnMap() 构建 CSV 列映射
  │     ├── TimeStamp → frame.timestamp
  │     ├── FRAME_ID → frame.frameCnt
  │     ├── Ipd0..31 → frame.rawdata
  │     ├── ACCX/ACCY/ACCZ → frame.gsData
  │     └── ALGO_RESULT0..n → frame.algoData
  │
  └── 5. recordingManager.writeFrame(mode, address, columnMap, role)
        └── channel.trySend(WriteTask) — 非阻塞入队
```

### 4.2 funcId 路由表

| funcId | 中文名 | 通道数 | 用途 |
|--------|--------|--------|------|
| ADT | 光电容积描记 | 1 | PPG 原始波形 |
| HR | 心率 | 1 | 心率值输出 |
| SPO2 | 血氧 | 2 | 血氧饱和度 |
| NADT | 多通道 ADC | 1-32 | 多通道原始数据 |
| GYRO | 陀螺仪 | 3 | 三轴角速度 |
| ACC | 加速度计 | 3 | 三轴加速度 |
| PRESSURE | 气压 | 1 | 气压值 |
| TEMP | 温度 | 1 | 温度值 |

## 5. 波形展示

### 5.1 Vico 图表渲染

```
waveformData 更新
  │
  ▼
DemoScreen → Vico LineChart
  │
  ├── X轴: 时间 (自动滚动)
  ├── Y轴: 数据值 (自适应范围)
  │
  └── 使用 Vico Compose M3 API
        ├── CartesianChartHost
        ├── LineCartesianLayer
        └── rememberVicoScrollState
```

### 5.2 图表刷新策略

```
每 GhFuncFrame → waveformData 新增 N 个数据点
  │
  ├── rawdata 通道: 直接绘制
  ├── phyValue 通道: 物理值转换后绘制
  │
  └── 自动滚动: 始终保持最新 N 秒数据在视口内
```

### 5.3 显示宽度切换

- 折线图显示宽度以"数据点数"为单位(一帧 = 一个采样点),与底层采样率解耦。
- 右上角下拉框可选:50 / 100 / 125 / 250 / 500 点。
- 每个功能模式独立记忆当前宽度,默认值:
  - ADT:50 点(10s × 5Hz,唯一 10 秒默认)
  - HR / HRV / SPO2 / NADT_GREEN / NADT_IR:125 点(5s × 25Hz)
  - TEST1 / TEST2:500 点(5s × 100Hz)
  - 其余模式:125 点
- 底层环形缓冲容量固定 500,显示宽度 ≤ 500;数据不足时自动显示全部。
- 配置逻辑见 `DisplayWidthConfig`,状态存于 `DemoUiState.displayWidths`。

## 6. 算法结果显示

```
algoResults 更新
  │
  ▼
DemoScreen 算法结果卡片
  │
  ├── 心率 (HR): frame.algoData[0]
  ├── 血氧 (SPO2): frame.algoData[1]
  ├── 呼吸率 (RR): frame.algoData[2]
  └── ... (根据芯片型号)
```

## 7. 设备对比功能

### 7.1 心率对比

```
COMPARE 设备: 标准心率服务 (BLE HR Service)
  │
  ▼
BleConnectionManager.onHeartRateReceived(address, data)
  │
  ├── 解析心率标志位
  │     ├── UINT8 模式: data[1]
  │     └── UINT16 模式: data[2]<<8 | data[1]
  │
  ├── 按 compareIndex 存入 heartRateResults
  │     └── _heartRateResults[index] = heartRate
  │
  ▼
DemoViewModel 收集 heartRateResults StateFlow
  │
  └── DemoScreen 展示对比表格
        ├── Compare0: Master 算法心率 (来自 algoData)
        ├── Compare1-5: 外部心率设备值
        └── 差异计算: |MasterHR - CompareHR|
```

### 7.2 主比较设备

```
ConnectionViewModel.setPrimaryCompareDevice(address)
  │
  ├── only one COMPARE device can be primary
  └── isPrimaryCompare = true (排序时排最前面)
```

## 8. 录制控制

### 8.1 手动录制

```
用户点击 TopAppBar 录制/停止按钮
  │
  ▼
DemoViewModel.toggleRecording()
  │
  ├── isRecording == false (开始录制):
  │     ├── 弹出 TestConfigDialog (如果无配置)
  │     │     └── 确认 → 启动录制会话
  │     └── 或直接开始 (如果已有配置)
  │
  └── isRecording == true (停止录制):
        ├── recordingManager.endSession()
        │     ├── Channel.close() → flush → join
        │     ├── CsvWriter.close()
        │     └── isSessionActive = false
        │
        └── connectionManager.notifyRecordingStopped()
```

### 8.2 重连恢复录制

```
设备重连 → TestConfigDialog 确认
  │
  ▼
DemoViewModel.confirmRestartRecording(config)
  │
  ├── connectionManager.resetFrameDecoders()
  ├── recordingManager.startSession(config, devices)
  │     └── 新的 CSV 文件 (新时间戳)
  │
  └── DemoUiState.isRecording = true
```

### 8.3 断联自动停止

```
BLE 断开 → devices StateFlow 变空
  │
  ▼
DemoViewModel 检测 devices.isEmpty
  │
  └── autoRecordingStopped = false
        └── 允许下次重连后自动恢复 (通过 TestConfigDialog)
```

## 9. CSV 文件输出

### 9.1 文件结构（每次会话）

```
{sessionDate}/
├── server/                       # Server CSV (每设备 × 每 mode)
│   └── ADT/
│       ├── extra_master_adt_scenario_tester_gh3036_adt_20260530_143000.csv
│       └── extra_slave_adt_scenario_tester_gh3036_adt_20260530_143000.csv
├── records/                      # Records CSV (每 mode 一份)
│   └── ADT/
│       └── extra_records_adt_20260530_143000.csv
│   └── HR/
│       └── extra_records_hr_20260530_143000.csv
└── logs/                         # BLE 原始日志
    └── ble_20260530_143000.log
```

### 9.2 Server CSV 格式

```
行1: {"MAC":"AA:BB:CC:DD:EE:FF","App-version":"1.0.0","name":"DeviceName",...}
行2: TimeStamp,FRAME_ID,Ipd0,Ipd1,...,ALGO_RESULT0,ALGO_RESULT1,...
行3+: 228530,1,0.1234,0.5678,...,72,98,...
```

### 9.3 Records CSV 格式

```
行1: TimeStamp,MasterAlgo,SlaveAlgo,Compare0_HR,...,Compare4_HR
行2+: 1720000.500,72,73,75,...,71
```
（每秒写入一行，聚合该秒内最新值）