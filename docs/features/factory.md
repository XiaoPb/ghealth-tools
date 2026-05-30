# Feature-Factory 模块流程文档

## 1. 模块概述

`feature-factory` 是产测（工厂测试）模块。加载设备配置文件，按序执行测试用例，收集测试结果并导出 CSV 报告。

## 2. 核心组件

| 组件 | 文件 | 职责 |
|------|------|------|
| `FactoryViewModel` | `FactoryViewModel.kt` | 测试流程控制、配置加载、结果汇总 |
| `FactoryScreen` | `FactoryScreen.kt` | 产测界面（项目选择、测试执行、结果展示） |
| `factory_config.json` | assets/ | 测试项目配置定义 |
| `.config` 文件 | assets/factory/ | 各测试项的寄存器配置 |

## 3. 配置结构

### 3.1 配置文件位置

```
feature/feature-factory/src/main/assets/factory/
├── factory_config_sample.json          # 配置模板
├── gh3036/
│   └── L-EVK-T2-GH3038Q/
│       ├── factory_config.json          # 测试项目定义
│       ├── Base_Noise_TEST1_100Hz_0327.config
│       ├── LPCTR_TEST1_100Hz_0327.config
│       ├── LPLCTR_TEST1_100Hz_0327.config
│       └── PPG_Noise_TEST1_100Hz_0327.config
└── gh3220/
    └── HR_SPO2_NADT_ADT_V4200/
        ├── HR_SPO2_NADT_ADT_V4200.ini   # 寄存器配置
        └── factory_config.json
```

### 3.2 factory_config.json 结构

```json
{
    "tests": [
        {
            "name": "BaseNoise",
            "displayName": "底噪测试",
            "configFile": "Base_Noise_TEST1_100Hz_0327.config",
            "params": { ... }
        },
        {
            "name": "PPGNoise",
            "displayName": "PPG噪声测试",
            "configFile": "PPG_Noise_TEST1_100Hz_0327.config",
            "params": { ... }
        }
    ]
}
```

### 3.3 .config 文件格式

寄存器地址-值对（每行一个寄存器）：
```
0x1000=0x01
0x1004=0x1234
0x1008=0x5678
```

## 4. 测试流程

### 4.1 初始化

```
FactoryScreen 加载
  │
  ▼
FactoryViewModel.init()
  │
  ├── 1. 根据芯片类型选择配置目录
  │     └── BlePreferences.effectiveChip → gh3036 / gh3220
  │
  ├── 2. 列出版本目录 (如 L-EVK-T2-GH3038Q/)
  │     └── 从 assets 或下载目录扫描
  │
  └── 3. 加载 factory_config.json
        ├── 解析测试项目列表
        └── 更新 FactoryUiState
```

### 4.2 测试执行

```
用户选择测试项目 + 点击执行
  │
  ▼
FactoryViewModel.startTest(testName)
  │
  ├── 1. 检查设备连接状态
  │     └── 主设备必须已连接
  │
  ├── 2. 加载配置文件
  │     └── 读取 .config 文件 → 解析寄存器地址-值对
  │
  ├── 3. 执行配置写入
  │     ├── 逐寄存器写入 (RegsWriteCmd)
  │     │     └── executor.sendCommand(address, "GH3X_RegsWriteCmd", params)
  │     │
  │     └── 写入完成确认
  │
  ├── 4. 启动测试模式
  │     ├── 特定控制命令
  │     └── 等待测试完成信号
  │
  ├── 5. 收集测试数据
  │     ├── 实时接收 G 协议帧 (通过 ghFrameFlow)
  │     ├── 提取测试指标 (噪声值、信号强度等)
  │     └── 更新 testResults
  │
  └── 6. 生成测试报告
        ├── 计算是否通过 (阈值比较)
        ├── 标记 Pass/Fail
        └── 更新 FactoryUiState
```

### 4.3 日志记录

```
FactoryViewModel 测试过程中
  │
  ├── 关键步骤 → testLogs 列表
  │     ├── "开始写入配置: 0x1000=0x01"
  │     ├── "写入完成，共 15 条"
  │     └── "测试结果: 底噪=0.023 (通过)"
  │
  └── LogManager 记录到文件
```

## 5. FactoryUiState

```kotlin
data class FactoryUiState(
    val selectedChip: DeviceType,
    val configVersions: List<String>,
    val selectedVersion: String?,
    val testItems: List<TestItem>,
    val currentTest: String?,
    val testProgress: Float,
    val testResults: Map<String, TestResult>,
    val testLogs: List<String>,
    val isRunning: Boolean,
    val isAllPassed: Boolean?
)

data class TestItem(
    val name: String,
    val displayName: String,
    val enabled: Boolean
)

data class TestResult(
    val name: String,
    val passed: Boolean,
    val value: Float?,
    val threshold: Float?,
    val unit: String
)
```

## 6. CSV 导出

```
FactoryScreen 导出按钮
  │
  ▼
FactoryViewModel.exportResults()
  │
  ├── 生成 CSV 文件
  │     ├── 列: TestName, Result, Value, Threshold, Unit, Timestamp
  │     └── 行: 每个测试项一行
  │
  ├── 保存路径
  │     └── factory/result_{chip}_{version}_{timestamp}.csv
  │
  └── 通过 StoragePath 管理
```

## 7. 错误处理

| 场景 | 处理方式 |
|------|----------|
| 设备未连接 | 提示"请先连接主设备" |
| 配置文件不存在 | 提示错误，跳过该测试 |
| 寄存器写入失败 | 记录日志，标记测试失败 |
| 测试超时 | 设置超时 (如 30s)，超时后标记失败 |
| 数值超出阈值 | 标记 FAIL + 高亮显示 |