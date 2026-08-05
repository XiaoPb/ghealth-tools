# Feature-Connection 模块流程文档

## 1. 模块概述

`feature-connection` 是设备连接与管理的核心界面。负责 BLE 设备扫描、连接管理、命令交互、设备状态卡（含电池/固件版本显示）、测试配置确认等。

## 2. 核心组件

| 组件 | 文件 | 职责 |
|------|------|------|
| `ConnectionViewModel` | `ConnectionViewModel.kt` | 连接状态管理、命令执行、订阅电池/版本状态 |
| `ConnectionScreen` | `ConnectionScreen.kt` | 连接主界面（设备列表、命令面板、设备状态卡） |
| `DeviceStatusCard` | `ConnectionScreen.kt` 内 | 设备连接状态卡（MAC、固件版本、电池指示器） |
| `BatteryIndicator` | `ConnectionScreen.kt` 内 | 自绘 Canvas 电池指示器（图标 + 百分比 + 充电闪电） |
| `CommandPanelScreen` | `CommandPanelScreen.kt` | 命令面板（命令列表、参数输入、响应展示） |
| `CommandResponseView` | `CommandResponseView.kt` | 命令响应数据展示（含寄存器读取特殊处理） |

## 3. ConnectionUiState

```kotlin
data class ConnectionUiState(
    val isScanning: Boolean = false,
    val scanResults: List<BleDevice> = emptyList(),
    val connectedDevices: Map<String, ConnectedDevice> = emptyMap(),
    val currentWorkMode: WorkMode? = WorkMode.AUTO_PASS,
    val selectedFunctions: Set<FunctionMode> = emptySet(),
    val scanForRole: DeviceRole? = null,
    val showWorkModeDialog: Boolean = false,
    val showFunctionDialog: Boolean = false,
    val showCommandSheet: Boolean = false,
    val showAppConfigDialog: Boolean = false,
    val minRssi: Int = -80,
    val scanError: String? = null,
    val connectionError: String? = null,
    val connectionErrorDevice: String? = null,
    val isBluetoothEnabled: Boolean = true,
    val hasPermissions: Boolean = true,
    val commandExecutionStates: Map<String, CommandExecutionState> = emptyMap(),
    val showTestConfigDialog: Boolean = false,
    val masterDeviceName: String? = null,
    val dataMonitorState: DataMonitorState = DataMonitorState(),
    val selectedChip: String = "gh3036",
    val registerConfigDownloadState: RegisterConfigDownloadState = RegisterConfigDownloadState(),
    val masterFirmwareVersion: String? = null,                  // 主设备固件版本（FirmwareVersionHolder 提供）
    val batteryStatusByAddress: Map<String, BatteryStatus> = emptyMap(), // 按地址索引的电池状态
)
```

## 4. 设备扫描与连接流程

```
ConnectionScreen 初始化
  │
  ▼
ConnectionViewModel.startScan(role)
  │
  ├── 1. 权限检查
  │     └── hasScanPermission && hasConnectPermission
  │
  ├── 2. 启动扫描
  │     ├── BleScanner.scan(minRssi=-80)
  │     │     └── 返回 Flow<BleDevice>
  │
  ├── 3. 收集扫描结果
  │     ├── 过滤已连接设备
  │     ├── 按 RSSI 降序排列
  │     ├── 去重 (按地址)
  │     └── 更新 scanResults StateList
  │
  └── 4. 停止扫描
        └── 用户停止 / 连接设备后自动停止
```

### 4.1 设备连接

```
用户点击扫描列表中的设备
  │
  ▼
ConnectionViewModel.connect(address, name, role)
  │
  ├── 1. 连接约束检查
  │     └── BleConnectionManager.checkConnectionConstraint(role)
  │
  ├── 2. 注册 Peripheral (扫描结果中的)
  │     └── BleConnectionManager.registerPeripheral(peripheral)
  │
  ├── 3. 发起连接
  │     └── BleConnectionManager.connect(address, name, role)
  │           └── CONNECTED 后异步触发 readBatteryService()（详见第 9 节）
  │
  └── 4. 观察连接状态变化
        └── devices StateFlow 收集
              ├── CONNECTED → 更新 UI
              ├── CONNECTING → 显示连接中
              └── DISCONNECTED → 从列表移除
```

### 4.2 断开连接

```
用户点击断开按钮 / 物理断开
  │
  ▼
ConnectionViewModel.disconnect(address)
  │
  ├── BleConnectionManager.disconnect(address)
  │     ├── executor.reset() + peripheral.disconnect() + peripheral.close()
  │     └── 清理该地址的 batteryStatus 缓存
  │
  └── 停止录音 (如果在录音)
        └── recordingManager.endSession()
```

## 5. 命令交互流程

### 5.1 命令面板

```
ConnectionScreen 命令面板 (CommandPanelScreen)
  │
  ├── 显示所有可用命令 (CommandMeta)
  ├── 用户选择命令 + 填写参数
  │
  └── ConnectionViewModel.executeCommand(key, params)
        │
        ├── 参数验证 → 打包
        │
        └── BleConnectionManager.sendCommand(address, key, param)
              │
              ├── hasResponse == true:
              │     ├── executor.call(key, format, params)
              │     ├── 等待响应 (timeout=3000ms)
              │     ├── 解包响应数据
              │     └── 更新 commandExecutionStates
              │
              └── hasResponse == false:
                    └── executor.send(key, format, params)
```

### 5.2 常用命令

| 命令 | Key | 说明 |
|------|-----|------|
| 获取版本 | `GH3X_GetVersion` | 读取芯片固件版本（verType 0x09 优先，0x01 回退） |
| 芯片控制 | `GH3X_ChipCtrl` | 复位/唤醒/休眠 |
| 寄存器读 | `GH3X_RegsReadCmd` | 读取指定地址寄存器（响应隐藏首个 U16 计数字段） |
| 寄存器写 | `GH3X_RegsWriteCmd` | 写入寄存器值 |
| 设置工作模式 | `GHSetWorkModeCmd` | 切换工作模式 (ADT/HR/SPO2/...) |
| 切换功能模式 | `GH3X_SwFunctionCmd` | 切换功能模式 |
| 设置时间戳 | `GhTimeSet` / `GhTimestampSet` | 同步设备 RTC |
| 工厂模式读取 | `F_GetMode` | 获取工厂测试模式状态 |
| 工厂模式设置 | `F_SetMode` | 设置工厂测试模式 |

### 5.3 寄存器读取响应展示

`CommandResponseView` 对寄存器读取响应做特殊处理：响应数据的**首个 U16 是读取到的寄存器数量**（计数元信息），并非寄存器值本身，展示时需跳过。

```
ResponseDataView 渲染响应
  │
  ├── 判定是否为寄存器读取：meta?.key == KEY_GH3X_REGS_READ_CMD
  │     （注意：不能用 responseFormat 判定，因为 <u16*> 与 F_GetMode 共享）
  │
  ├── isRegRead == true:
  │     ├── registerReadCount(data) → 解析首个 U16 作为 N
  │     ├── 标签显示 "寄存器值 (N 个):"
  │     └── formatU16Array(data, skipFirstAsCount = true) → 跳过首个 U16 展示剩余值
  │
  └── isRegRead == false:
        └── formatU16Array(data, skipFirstAsCount = false) → 常规 U16 数组展示
```

辅助函数（`CommandResponseView.kt`，`internal` 可测试）：
- `formatU16Array(data, asHex, skipFirstAsCount)` — U16 数组格式化，`skipFirstAsCount` 跳过首项。
- `registerReadCount(data)` — 返回首个 U16 解析值（数据不足返回 null）。

## 6. 测试配置流程

```
ConnectionScreen 设备状态卡 / 录制指示器
  │
  ├── 主设备首次 CONNECTED 且未在监控 → 弹出 TestConfigDialog
  │
  └── TestConfigDialog
        ├── 测试人员名称 (TesterName)
        ├── 测试场景 (TestScenario)
        ├── 测试轮次 (TestRound)
        │
        └── 确认 → ConnectionViewModel.confirmTestConfig(config)
              │
              ├── connectionManager.resetFrameDecoders()
              ├── connectionManager.setTestConfig(config)
              │
              └── recordingManager.startSession(config, devices)
                    ├── endSession() (清理上次会话)
                    ├── 创建 12 个 FunctionMode 的 Channel + Consumer
                    ├── 创建 Records CSV Writer (立即打开)
                    └── isSessionActive = true
```

### 6.1 录制状态同步

```
RecordingManager.isSessionActive → StateFlow
  │
  ├── DemoViewModel 收集
  │     └── DemoUiState.isRecording
  │
  ├── ConnectionViewModel 收集
  │     └── dataMonitorState.isMonitoring
  │
  └── GHealthNavHost TopAppBar 录制指示器
        ├── isMonitoring==true → 红色 "录制中" + 停止按钮
        └── isMonitoring==false → 灰色 "未录制" + 录制按钮
```

## 7. 配置同步流程

### 7.1 寄存器配置下载

```
ConnectionViewModel (AppConfigDialog)
  │
  ├── ConfigDownloader.downloadConfig(projectId)
  │     ├── 调用 DownloadApi 获取配置文件列表
  │     ├── 下载并保存到本地
  │     └── 文件由 ConfigPathProvider 管理
  │
  └── ConfigSyncManager 同步到 BLE 设备
        └── 逐条发送寄存器写入命令
```

## 8. 进入产测 / OTA

```
ConnectionScreen 按钮
  │
  ├── 产测按钮 → onFactoryTest()
  │     └── navController.navigate(Routes.Main.FACTORY)
  │
  └── OTA按钮 → onOtaUpgrade()
        └── navController.navigate(Routes.Main.OTA)
```

## 9. 设备状态卡与电池显示

### 9.1 DeviceStatusCard

设备状态卡展示已连接设备列表，每台设备分两行显示：

```
DeviceStatusCard
  ├── 标题: "设备连接状态" + 蓝牙图标
  │
  └── 每台设备一行:
        ├── 左侧 Column:
        │     ├── 第一行: "{ROLE} - {device.name}"        (bodyMedium)
        │     └── 第二行: "{device.address}"              (bodySmall, MAC 地址)
        │
        └── 右侧:
              ├── 主设备(MASTER): 固件版本 + BatteryIndicator
              └── 其他设备: BatteryIndicator（若有电池数据）
```

主设备行额外显示固件版本（来自 `masterFirmwareVersion`），与 MAC 地址分两行展示，避免拥挤。

### 9.2 BatteryIndicator（自绘 Canvas）

```
BatteryIndicator(status: BatteryStatus)
  │
  ├── 电量图标: Canvas 自绘电池外形
  │     ├── 外框: outline 色
  │     ├── 填充: primaryContainer 色，宽度按 level(0-100) 比例
  │     └── 满电(level==100)填满
  │
  ├── 百分比文字: "$level" 或 "--"（level==null）
  │     └── 颜色: 随容器色自适应的 onPrimaryContainer
  │
  └── 充电闪电槽位: 固定宽度预占，保证多行电池右缘对齐
        ├── chargeState == Charging 且非 Full → 显示 Bolt 图标 (primary 色)
        └── 其他 → 留空
```

`BatteryStatus` 数据来自 `ble-connection` 模块：
```kotlin
data class BatteryStatus(
    val level: Int? = null,                  // 0–100；null 表示尚未读到
    val chargeState: ChargeState = Unknown,  // Unknown/Charging/Discharging/NotCharging/Full
)
```

### 9.3 电池状态数据流

```
BleConnectionManager.readBatteryService(address)   [CONNECTED 后异步触发]
  │
  ├── 读 0x2A19 Battery Level（一次）+ 订阅 notify（若支持）
  ├── 订阅 0x2A1E Battery Level Status（若存在且支持 notify）
  │
  └── batteryStatus: StateFlow<Map<String, BatteryStatus>>  [按地址索引]
        │
        ▼
ConnectionViewModel.collect { batteryByAddress }
  └── ConnectionUiState.batteryStatusByAddress
        │
        ▼
DeviceStatusCard(batteryStatusByAddress = ...)
  └── 每台设备取 batteryStatusByAddress[device.address] 渲染 BatteryIndicator
```

### 9.4 DEBUG 模拟预览

`BatteryPreviewScope` 仅在 `Debug` Build 中生效，向 `DeviceStatusCard` 注入 mock 电池数据（85%/10%/60% 充电/100% 满电），便于在模拟器无真机时验证 UI。Release 构建自动透传真实状态。

> 电池服务的 GATT 解析细节（UUID、flags 位解析、GSS 规范版本）见 [BLE 端到端流程](../ble/ble-end-to-end-flow.md) 第 4.5 节。

## 10. 固件版本显示

主屏设备卡的固件版本由共享单例 `FirmwareVersionHolder` 统一获取，避免连接页与设置页重复下发读取命令。

```
FirmwareVersionHolder (@Singleton)
  │  订阅 BleConnectionManager.devices
  │  主设备 CONNECTED → 延迟 5s → 0x09 优先 / 0x01 回退
  │
  └── state: StateFlow<FirmwareVersionState(version, isReading)>
        │
        ├── ConnectionViewModel.collect → masterFirmwareVersion → DeviceStatusCard
        └── SettingsViewModel.collect   → bleVersion            → SettingsScreen
```

- 主设备断开时，`FirmwareVersionHolder` 取消在途读取并清空状态，UI 版本立即消失，无 stale 回填。
- 版本获取策略与断连清理细节见 [Settings 文档](settings.md) 第 5 节。

## 11. 线程模型

| 操作 | 线程 | 说明 |
|------|------|------|
| BLE 扫描 | `Dispatchers.IO` | Kable scan Flow |
| 设备连接 | `suspend` 协程 | `peripheral.connect()` |
| 电池服务读取/订阅 | `Dispatchers.IO` | `readBatteryService` fire-and-forget |
| 版本读取 | `FirmwareVersionHolder` scope | 延迟 5s + 3000ms 超时 |
| 命令执行 | `suspend` 协程 | `executor.call()` 含超时等待 |
| UI 状态更新 | Main 线程 | StateFlow collect → UI recomposition |
| 录制启动 | `suspend` 协程 | `startSession()` 含文件创建 |
