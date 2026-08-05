# Feature-Settings 模块流程文档

## 1. 模块概述

`feature-settings` 管理应用全局设置，包括蓝牙 UUID 配置、主题模式、设备信息查看、BLE 固件版本读取、日志导出等。

## 2. 核心组件

| 组件 | 文件 | 职责 |
|------|------|------|
| `SettingsViewModel` | `SettingsViewModel.kt` | 设置项管理、持久化存储、订阅共享固件版本 |
| `SettingsScreen` | `SettingsScreen.kt` | 设置界面（列表/开关/输入） |
| `DeviceInfoScreen` | `DeviceInfoScreen.kt` | 设备信息展示界面 |

## 3. SettingsUiState

```kotlin
data class SettingsUiState(
    val themeMode: ThemeMode,
    val serviceUuid: String,
    val writeCharUuid: String,
    val notifyCharUuid: String,
    val chipType: DeviceType,
    val appVersion: String,
    val connectedDeviceCount: Int,
    val logExporting: Boolean,
    val csvExportPath: String,
    val showProjectManage: Boolean,
    val bleVersion: String,          // BLE 固件版本（由 FirmwareVersionHolder 统一获取）
    val isReadingBleVersion: Boolean  // 版本读取进行中标识
)
```

## 4. 配置项管理

### 4.1 BLE UUID 配置

```
SettingsScreen BLE 配置区域
  │
  ├── Service UUID 输入框
  │     └── 默认: "0000190e-0000-1000-8000-00805f9b34fb"
  │
  ├── Write Characteristic UUID 输入框
  │     └── 默认: "00000004-0000-1000-8000-00805f9b34fb"
  │
  ├── Notify Characteristic UUID 输入框
  │     └── 默认: "00000003-0000-1000-8000-00805f9b34fb"
  │
  └── 修改后自动保存
        └── BlePreferences.serviceUuid = newValue
        └── BlePreferences.writeCharUuid = newValue
        └── BlePreferences.notifyCharUuid = newValue
```

> **注意**：Service UUID 仅作为参考，不参与连接时的服务匹配。`BleConnectionManager.validateServices` 按 write/notify 特征 UUID 跨全部已发现服务查找，匹配命中即完成服务验证。详见 [BLE 端到端流程](../ble/ble-end-to-end-flow.md) 第 4 节。

### 4.2 主题模式

应用提供 4 套基于 Tailwind 500 色板衍生的 Material 3 主题，每套均含亮色/暗色调色板：

```
SettingsScreen 主题选择
  │
  ├── 活力蓝 (Blue500)    → ThemeMode.BLUE_500   (#3B82F6)
  ├── 翡翠绿 (Emerald500) → ThemeMode.EMERALD_500 (#10B981)
  ├── 樱花粉 (Pink500)    → ThemeMode.PINK_500    (#EC4899)
  └── 紫罗兰 (Violet500)  → ThemeMode.VIOLET_500  (#8B5CF6)
  │
  └── 保存 → BlePreferences.themeMode = selectedMode.key
        │
        └── MainActivity 收集 BlePreferences.themeMode
              └── ThemeMode.fromKey(key)（未知 key 回退 BLUE_500）
              └── GHealthTheme(themeMode = ...) 重建 ColorScheme
```

主题颜色定义见 [core-ui/theme/Color.kt](../../core/core-ui/src/main/java/com/ghealth/tools/core/ui/theme/Color.kt)（`AppColors.Blue500/Emerald500/Pink500/Violet500`），枚举见 `ThemeMode.kt`。默认主题为 `BLUE_500`。

### 4.3 芯片类型

```
SettingsScreen 芯片选择
  │
  ├── GH3036
  ├── GH3220
  └── GH3300
  │
  └── 保存 → BlePreferences.effectiveChip = selectedChip
        │
        └── BleConnectionManager.createExecutor() 根据芯片创建对应执行器
        └── FactoryViewModel 根据芯片选择配置目录
```

## 5. BLE 固件版本读取

设置页的 BLE 版本读取由共享单例 `FirmwareVersionHolder` 统一负责，避免连接页与设置页重复下发版本读取命令。

```
SettingsViewModel 初始化
  │
  └── 注入 @Singleton FirmwareVersionHolder
        │
        └── collect firmwareVersionHolder.state
              ├── versionState.version  → SettingsUiState.bleVersion（空串表示无版本）
              └── versionState.isReading → SettingsUiState.isReadingBleVersion
```

### 5.1 版本获取策略

`FirmwareVersionHolder` 内部订阅 `BleConnectionManager.devices`：

```
主设备 CONNECTED（地址变化）→ 延迟 5 秒 → 下发版本读取命令
  │
  ├── 优先：verType = 0x09（BLE 版本）→ GH3X_GetVersion
  │     └── 解析为 "no_ver" 视为失败，触发回退
  │
  └── 回退：verType = 0x01（固件版本）→ GH3X_GetVersion
        └── 解析为 "no_ver" 视为失败
  │
  └── 两者都失败 → version = null（UI 显示 "no_ver"）
```

- 单次读取超时 3000ms（`withTimeoutOrNull`）。
- 主设备断开时取消在途读取任务并清空版本状态，避免旧版本回填。
- 读取前再次校验目标地址仍为当前主设备且处于 CONNECTED，防止断连后 stale 结果写入。

### 5.2 UI 展示

`SettingsScreen` 设备信息区域：
- `isReadingBleVersion == true` → 显示读取中指示（CircularProgressIndicator）。
- 否则显示 `bleVersion.ifEmpty { "no_ver" }`，空串或 `"no_ver"` 用 `onSurfaceVariant` 弱化颜色。

> **单一数据源**：连接页主屏的 `masterFirmwareVersion` 与设置页的 `bleVersion` 均订阅同一个 `FirmwareVersionHolder.state`，确保两处版本一致。详见 [Feature-Connection 文档](connection.md) 第 10 节与 [BLE 端到端流程](../ble/ble-end-to-end-flow.md) 第 4.4 节。

## 6. 设备信息

### 6.1 DeviceInfoScreen

```
SettingsScreen → 点击"设备信息"
  │
  ▼
DeviceInfoScreen
  │
  ├── 应用版本: BuildConfig.VERSION_NAME
  ├── 已连接设备列表
  ├── 芯片类型: BlePreferences.effectiveChip
  ├── BLE UUID 配置
  ├── 主题模式
  ├── 设备 Android 版本
  └── 存储使用情况
```

## 7. 日志导出

```
SettingsScreen → 点击"导出日志"
  │
  ▼
SettingsViewModel.exportLogs()
  │
  ├── 1. 压缩日志目录
  │     ├── 来源: LogManager 记录的 BLE 日志 + Timber 日志
  │     └── 打包为 .zip 文件
  │
  ├── 2. 保存到外部存储
  │     └── /Download/ghealth_logs_{timestamp}.zip
  │
  └── 3. 分享 (可选)
        └── Android Share Intent
```

### 7.1 调试数据拉取

```
adb 脚本: scripts/pull_debug_data.sh
  │
  ├── 拉取当天的 LOG 和 CSV 文件
  ├── 支持指定日期: pull_debug_data.sh 2026-05-30
  ├── 拉取全部: pull_debug_data.sh -a
  └── 附带 crash log: dumpsys dropbox
```

## 8. 项目管理入口

```
SettingsScreen → 项目相关操作
  │
  ├── "切换项目"
  │     └── onSwitchProject() → 导航到 ProjectSelectionScreen
  │
  └── "管理项目"
        └── onNavigateToProjectManage() → 导航到 ProjectManageScreen
```

## 9. 数据持久化

### 9.1 BlePreferences (DataStore)

```kotlin
class BlePreferences @Inject constructor(
    private val context: Context
) {
    val serviceUuid: Flow<String>
    val writeCharUuid: Flow<String>
    val notifyCharUuid: Flow<String>
    val effectiveChip: Flow<String>
    val themeMode: Flow<String>
    // ... 其他设置项

    companion object {
        const val DEFAULT_SERVICE_UUID  = "0000190e-0000-1000-8000-00805f9b34fb"
        const val DEFAULT_WRITE_CHAR_UUID  = "00000004-0000-1000-8000-00805f9b34fb"
        const val DEFAULT_NOTIFY_CHAR_UUID = "00000003-0000-1000-8000-00805f9b34fb"
    }
}
```

### 9.2 设置项列表

| 设置项 | 类型 | 默认值 | 作用域 |
|--------|------|--------|--------|
| `serviceUuid` | String | `0000190e-...` | BLE 连接参考服务 UUID（不参与匹配） |
| `writeCharUuid` | String | `00000004-...` | BLE 写入特征 UUID（跨服务匹配） |
| `notifyCharUuid` | String | `00000003-...` | BLE 通知特征 UUID（跨服务匹配） |
| `effectiveChip` | String | `"gh3036"` | 当前芯片型号 |
| `themeMode` | String | `"blue_500"` | 主题模式 key（未知值回退 `blue_500`） |

### 9.3 UserPreferences (DataStore)

```kotlin
class UserPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    val username: Flow<String>
    val rememberPassword: Flow<Boolean>
    val savedPassword: Flow<String>  // 加密存储
    val lastProjectId: Flow<Int>
}
```

## 10. 线程模型

| 操作 | 线程 | 说明 |
|------|------|------|
| DataStore 读取 | `Dispatchers.IO` | Flow 自动管理 |
| 设置修改 | 同步 edit | DataStore.edit() |
| 日志导出 | `Dispatchers.IO` | 文件压缩操作 |
| 版本状态订阅 | `Dispatchers.IO` | `FirmwareVersionHolder` 内部 scope |
| UI 更新 | Main 线程 | StateFlow collect |
