# Feature-OTA 模块流程文档

## 1. 模块概述

`feature-ota` 负责 OTA（Over-The-Air）固件升级。通过 Nordic DFU 协议对 GHealth 设备进行固件更新，支持主设备 DFU 升级和资源文件升级。

## 2. 核心组件

| 组件 | 文件 | 职责 |
|------|------|------|
| `OtaViewModel` | `OtaViewModel.kt` | OTA 升级流程控制、DFU 引擎管理 |
| `OtaScreen` | `OtaScreen.kt` | OTA 升级界面（设备选择、进度、日志） |
| `OtaTopBarMenu` | `OtaScreen.kt` | 顶部菜单（调试选项） |
| `libdfu2` | `external/libdfu2/` | Nordic DFU 库封装 |

## 3. OtaUiState

```kotlin
data class OtaUiState(
    val availableDevices: List<ConnectedDeviceInfo>,
    val selectedDevice: ConnectedDeviceInfo?,
    val firmwareFile: File?,
    val firmwareFileName: String?,
    val upgradeState: UpgradeState,
    val progress: Float,
    val speed: Float,
    val logLines: List<String>,
    val isDebugMode: Boolean,
    val resourceFile: File?
)

sealed class UpgradeState {
    object Idle : UpgradeState()
    object Preparing : UpgradeState()
    object Uploading : UpgradeState()
    object Enabling : UpgradeState()
    object Completed : UpgradeState()
    data class Failed(val error: String) : UpgradeState()
}
```

## 4. 升级流程

### 4.1 设备选择

```
OtaScreen 进入
  │
  ▼
LaunchedEffect(connectionState.connectedDevices)
  │
  ├── 过滤已连接的主设备 (role=MASTER, state=CONNECTED)
  │     └── ConnectedDeviceInfo(address, name, role)
  │
  └── viewModel.loadAvailableDevices(deviceInfos)
        └── OtaUiState.availableDevices 更新
```

### 4.2 固件文件选择

```
用户点击"选择固件文件"
  │
  ▼
系统文件选择器 (Intent / SAF)
  │
  ├── 支持格式: .zip (DFU package), .bin (raw firmware)
  ├── 文件大小校验
  └── 更新 OtaUiState.firmwareFile
```

### 4.3 DFU 升级流程

```
用户点击"开始升级"
  │
  ▼
OtaViewModel.startDfu(device, firmwareFile)
  │
  ├── 1. 准备阶段 (Preparing)
  │     ├── 解析 DFU 包 (init packet + firmware)
  │     ├── 校验固件版本兼容性
  │     └── 通知设备进入 DFU 模式
  │           └── 芯片复位 → 设备断开 → 切换 DFU bootloader
  │
  ├── 2. 扫描阶段 (设备 MAC 地址可能变化)
  │     ├── BleConnectionManager.scanForDeviceWithMac(targetMac, timeoutMs=31000)
  │     │     ├── 创建独立 Scanner
  │     │     ├── withTimeoutOrNull 保护
  │     │     ├── 找到 → Peripheral(advertisement) { requestMtu(247) }
  │     │     └── 未找到 → UpgradeState.Failed("设备未找到")
  │     │
  │     └── notifyDfuReconnect(oldAddress, newPeripheral)
  │           ├── 迁移 device 状态
  │           └── dfuState = Reconnected(newAddress, peripheral)
  │
  ├── 3. 上传阶段 (Uploading)
  │     ├── Nordic DFU Service 通信
  │     ├── 分包发送 (MTU 247)
  │     ├── 进度回调 → progress 更新 (0.0 ~ 1.0)
  │     └── 速度计算 → speed (bytes/s)
  │
  ├── 4. 激活阶段 (Enabling)
  │     ├── 发送激活命令
  │     ├── 设备复位 → 切换到新固件
  │     └── 等待设备重新连接 (可选)
  │
  └── 5. 完成/失败
        ├── 成功 → UpgradeState.Completed + 日志 "升级成功"
        └── 失败 → UpgradeState.Failed(error)
              ├── 超时
              ├── 校验失败
              └── 通信中断
```

### 4.4 资源升级

```
用户选择资源文件 + 点击"资源升级"
  │
  ▼
OtaViewModel.startResourceUpgrade()
  │
  ├── 资源文件格式: .bin / .res
  ├── 通过自定义协议写入 Flash
  └── 进度跟踪同 DFU
```

## 5. 调试功能

### 5.1 调试模式

```
OtaTopBarMenu 菜单
  │
  ├── 开启调试模式 → isDebugMode = true
  │     ├── 显示详细 DFU 通信日志
  │     ├── 显示原始数据包
  │     └── 导出调试日志
  │
  └── 高级选项
        ├── 强制重新连接
        ├── 显示设备信息
        └── 手动发送 DFU 命令
```

## 6. DFU 连接状态管理

```kotlin
sealed class DfuConnectionState {
    Idle                                        // 空闲
    Reconnecting(oldAddress, newAddress)         // 正在重连
    Reconnected(newAddress, peripheral)          // 已重连
    Failed(error)                               // 失败
}
```

### 6.1 状态流转

```
Idle
  │
  ├── startDfu() → 设备断开
  │     └── 开始扫描新地址
  │
  ├── 扫描中 → Reconnecting(oldAddr, newAddr)
  │
  ├── 找到 → Reconnected(newAddr, peripheral)
  │     ├── 升级成功 → Idle
  │     └── 升级失败 → Failed
  │
  └── 超时 → Failed("设备未找到")
```

## 7. 错误处理

| 场景 | 处理方式 |
|------|----------|
| 无已连接主设备 | 提示"请先连接主设备" |
| 固件文件无效 | 校验失败，停止升级 |
| 设备进入 DFU 模式失败 | 提示芯片复位失败 |
| DFU 设备扫描超时 | 提示"未找到 DFU 设备，请手动重置" |
| 上传中断 | 记录已发送位置，支持续传（DFU 协议支持） |
| 固件校验失败 | 提示"固件校验失败，请重新升级" |
| 升级成功 | 自动断开 DFU，设备恢复正常模式 |

## 8. 线程模型

| 操作 | 线程 | 说明 |
|------|------|------|
| DFU 扫描 | `Dispatchers.IO` 独立协程 | `scanForDeviceWithMac` |
| DFU 上传 | `Dispatchers.IO` | Nordic DFU library |
| 进度更新 | Main 线程 | StateFlow UI 更新 |
| 日志写入 | `Dispatchers.IO` | `LogManager.logOta()` |