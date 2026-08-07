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

### 4.2.1 App 端计算回退（F_GetMode 无数据）

当硬件测试中 `F_GetMode` 返回空通道数组（对端固件未实现产测计算逻辑）时，App 自动切换到 App 端计算：使用测试窗口内采集到的 TEST1 原始帧（`ghFrameFlow` 的 `rawdata` / `phyValue` / `agcInfo`）按公式计算指标并判定。

**触发条件**：`F_GetMode` 解析出的 U16 通道数组长度为 0。

**计算公式**（来自「PPG数据采集通用公式与配置说明」）：

- Noise（μV）= `σ_filter / full_scale × V_ref × 10^6`，`σ_filter` 为 7 阶 0.5Hz 高通滤波后数据的总体标准差
- Ipd（nA）= `(rawdata_avg - offset) / full_scale × V_ref × 10^6 / (tia_ratio × G_k)`，帧内存在 `Ipd_pa` 时优先用（GH3036 帧提供）
- CTR（nA/mA）= `Ipd / Iled`；`Iled`：GH3036 系优先取 AGC 帧 `led_current_sum`(0.1mA)/10（`compute.led_current_ma` 仅回退）；GH3220/GH3300 优先取 `compute.led_current_ma`（AGC 位域未文档化，仅回退并记录 WARN）
- SNR（dB）= `20·log10((rawdata_avg - offset) / σ_filter)`（仅日志展示）

**芯片参数**：`full_scale=2^23`、`vref=1.8V`、`tia_ratio=2` 为全系通用；`offset`：GH3036 为 0，GH3220/GH3300 为 2^23。

**配置字段**（`factory_config.json` 各测试项 `compute` 块，均选填）：

| 字段 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `gain_k` | number | 无 | 跨阻增益 kΩ；帧内无 Ipd pA 且需 rawdata 法计算时必需（不限芯片） |
| `led_current_ma` | number | 无 | LED 电流 mA，缺省从 AGC 帧读取 |
| `sample_rate_hz` | number | 100 | 采样率，用于 0.5Hz 高通滤波系数 |

计算值以 `TestResult.computedValue` 输出到界面与 CSV；单通道无原始数据时该通道标记 FAIL 并记录 WARN 日志；整个测试窗口未采集到任何原始数据时记录 ERROR 日志并产出 1 个合成 FAIL 结果（`value=0`），整次产测判定 FAIL，不再误判 PASS。

**CHIP_INIT（芯片初始化）**：`F_GetMode` 无数据时改为寄存器读写校验通信——写入 `{0x0020, 0x2919}`（FIFO_WATER_LINE:25, RG_FIFO_READ_INT_TIMER:0.4s，取自 GH3036 产测配置）并回读，一致则 PASS（通信正常），写入/读取失败或回读不一致则 FAIL；校验寄存器为引擎常量（`CHIP_COMM_CHECK_REG_ADDR` / `CHIP_COMM_CHECK_REG_VALUE`），其它芯片如需调整可修改常量。

**CHIP_UID（设备UUID）**：`F_GetMode` 无数据或不足 32 字节时（对端无产测逻辑），改用上位机寄存器指令读取 eFuse——按 SDK `gh_efuse_read_single` 流程依次配置 RG_EFUSE_MODE/SEL、打开 RDEN、启动 START、轮询 READ_DONE、读取 4 个 RDATA 寄存器并拼装 64bit，共读 4 段（256bit）拼装 32 字节导出两个 128bit UUID；仅 GH3036 系列芯片支持该回退，eFuse 读取失败或非 GH3036 系列时产出 2 个 FAIL 结果（错误码 0x2001/0x2002），整次产测判 FAIL。

**全局回退**：CHIP_INIT 流程出现 `F_SetMode`/`F_GetMode` 失败或 `F_GetMode` 无数据时（对端无产测逻辑），后续全部测试（CHIP_UID、BASE_NOISE、PPG_NOISE、LPCTR、LPLCTR）直接使用 App 端计算，不再尝试 `F_SetMode`/`F_GetMode`；硬件测试仍执行 `download_config` 与 TEST1 原始帧采集作为 App 端计算输入。非回退模式下各测试保持原行为（设备返回数据用设备数据，单项无数据单项回退）。

**SNR**：`PpgMetricsCalculator.snr` 已实现（`20·log10((rawdata_avg - offset)/σ_filter)`），App 端计算在 PPG_NOISE/BASE_NOISE 通道输出 `Noise=...μV SNR=...dB` 日志；SNR 暂不参与产测判定，后续有实际计划再接入接口。

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