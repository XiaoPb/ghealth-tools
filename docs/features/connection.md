# Feature-Connection 模块流程文档

## 1. 模块概述

`feature-connection` 是设备连接与管理的核心界面。负责 BLE 设备扫描、连接管理、命令交互、测试配置确认等。

## 2. 核心组件

| 组件 | 文件 | 职责 |
|------|------|------|
| `ConnectionViewModel` | `ConnectionViewModel.kt` | 连接状态管理、命令执行、测试配置 |
| `ConnectionScreen` | `ConnectionScreen.kt` | 连接主界面（设备列表、命令面板） |
| `TestConfigDialog` | `TestConfigDialog.kt` | 测试配置弹窗（人员、场景、轮次） |

## 3. ConnectionUiState

```kotlin
data class ConnectionUiState(
    val isScanning: Boolean,
    val scanResults: List<BleDevice>,
    val connectedDevices: Map<String, ConnectedDevice>,
    val selectedDevice: ConnectedDevice?,
    val commandResults: Map<String, String>,
    val commandHistory: List<String>,
    val error: ConnectionErrorState?,
    val isRecording: Boolean,
    val testConfig: TestConfig?,
    val showTestConfigDialog: Boolean,
    val chipType: DeviceType
)
```

## 4. 设备扫描与连接流程

```
ConnectionScreen 初始化
  │
  ▼
ConnectionViewModel.startScan()
  │
  ├── 1. 权限检查
  │     └── hasScanPermission && hasConnectPermission
  │
  ├── 2. 启动扫描
  │     ├── BleScanner.scan(minRssi=-80)
  │     │     └── 返回 Flow<BleDevice>
  │     └── 或 GHealthScanner.scanWithNameFilter(name)
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
  │     └── executor.reset() + peripheral.disconnect() + peripheral.close()
  │
  └── 停止录音 (如果在录音)
        └── recordingManager.endSession()
```

## 5. 命令交互流程

### 5.1 命令面板

```
ConnectionScreen 命令面板
  │
  ├── 显示所有可用命令 (Gh3036CommandMeta)
  ├── 用户选择命令 + 填写参数
  │
  └── ConnectionViewModel.executeCommand(commandMeta, params)
        │
        ├── 参数验证 → 打包
        │
        └── BleConnectionManager.sendCommand(address, key, param)
              │
              ├── hasResponse == true:
              │     ├── executor.call(key, format, params)
              │     ├── 等待响应 (timeout=3000ms)
              │     ├── 解包响应数据
              │     └── 更新 commandResults
              │
              └── hasResponse == false:
                    └── executor.send(key, format, params)
```

### 5.2 常用命令

| 命令 | Key | 说明 |
|------|-----|------|
| 获取版本 | `GH3X_GetVersion` | 读取芯片固件版本 |
| 芯片控制 | `GH3X_ChipCtrl` | 复位/唤醒/休眠 |
| 寄存器读 | `GH3X_RegsReadCmd` | 读取指定地址寄存器 |
| 寄存器写 | `GH3X_RegsWriteCmd` | 写入寄存器值 |
| 设置工作模式 | `GHSetWorkModeCmd` | 切换工作模式 (ADT/HR/SPO2/...) |
| 切换功能模式 | `GH3X_SwFunctionCmd` | 切换功能模式 |
| 设置时间戳 | `GhTimeSet` / `GhTimestampSet` | 同步设备 RTC |
| 工厂模式读取 | `F_GetMode` | 获取工厂测试模式状态 |
| 工厂模式设置 | `F_SetMode` | 设置工厂测试模式 |

## 6. 测试配置流程

```
ConnectionScreen TopAppBar 录制指示器
  │
  ├── 未录制状态: 点击录制按钮 → 弹出 TestConfigDialog
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
  │     └── ConnectionUiState.isRecording
  │
  └── GHealthNavHost TopAppBar 录制指示器
        ├── isRecording==true → 红色 "录制中" + 停止按钮
        └── isRecording==false → 灰色 "未录制" + 录制按钮
```

## 7. 配置同步流程

### 7.1 寄存器配置下载

```
ConnectionViewModel.downloadRegisterConfig()
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

## 9. 线程模型

| 操作 | 线程 | 说明 |
|------|------|------|
| BLE 扫描 | `Dispatchers.IO` | Kable scan Flow |
| 设备连接 | `suspend` 协程 | `peripheral.connect()` |
| 命令执行 | `suspend` 协程 | `executor.call()` 含超时等待 |
| UI 状态更新 | Main 线程 | StateFlow collect → UI recomposition |
| 录制启动 | `suspend` 协程 | `startSession()` 含文件创建 |