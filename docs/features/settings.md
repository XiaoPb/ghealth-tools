# Feature-Settings 模块流程文档

## 1. 模块概述

`feature-settings` 管理应用全局设置，包括蓝牙 UUID 配置、主题模式、设备信息查看、日志导出等。

## 2. 核心组件

| 组件 | 文件 | 职责 |
|------|------|------|
| `SettingsViewModel` | `SettingsViewModel.kt` | 设置项管理、持久化存储 |
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
    val showProjectManage: Boolean
)
```

## 4. 配置项管理

### 4.1 BLE UUID 配置

```
SettingsScreen BLE 配置区域
  │
  ├── Service UUID 输入框
  │     └── 默认: "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
  │
  ├── Write Characteristic UUID 输入框
  │     └── 默认: "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
  │
  ├── Notify Characteristic UUID 输入框
  │     └── 默认: "6e400003-b5a3-f393-e0a9-e50e24dcca9e"
  │
  └── 修改后自动保存
        └── BlePreferences.serviceUuid = newValue
        └── BlePreferences.writeCharUuid = newValue
        └── BlePreferences.notifyCharUuid = newValue
```

### 4.2 主题模式

```
SettingsScreen 主题选择
  │
  ├── Sky Blue (天蓝色) → ThemeMode.SKY_BLUE
  ├── Ocean (海洋色)     → ThemeMode.OCEAN
  ├── Forest (森林色)    → ThemeMode.FOREST
  └── Sunset (日落色)    → ThemeMode.SUNSET
  │
  └── 保存 → BlePreferences.themeMode = selectedMode.key
        │
        └── MainActivity 收集 BlePreferences.themeMode
              └── GHealthTheme(themeMode = ...) 重建主题
```

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

## 5. 设备信息

### 5.1 DeviceInfoScreen

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

## 6. 日志导出

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

### 6.1 调试数据拉取

```
adb 脚本: scripts/pull_debug_data.sh
  │
  ├── 拉取当天的 LOG 和 CSV 文件
  ├── 支持指定日期: pull_debug_data.sh 2026-05-30
  ├── 拉取全部: pull_debug_data.sh -a
  └── 附带 crash log: dumpsys dropbox
```

## 7. 项目管理入口

```
SettingsScreen → 项目相关操作
  │
  ├── "切换项目"
  │     └── onSwitchProject() → 导航到 ProjectSelectionScreen
  │
  └── "管理项目"
        └── onNavigateToProjectManage() → 导航到 ProjectManageScreen
```

## 8. 数据持久化

### 8.1 BlePreferences (DataStore)

```kotlin
class BlePreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    val serviceUuid: Flow<String>
    val writeCharUuid: Flow<String>
    val notifyCharUuid: Flow<String>
    val effectiveChip: Flow<String>
    val themeMode: Flow<String>
    // ... 其他设置项
}
```

### 8.2 设置项列表

| 设置项 | 类型 | 默认值 | 作用域 |
|--------|------|--------|--------|
| `serviceUuid` | String | 6e400001-... | BLE 连接服务 UUID |
| `writeCharUuid` | String | 6e400002-... | BLE 写入特征 UUID |
| `notifyCharUuid` | String | 6e400003-... | BLE 通知特征 UUID |
| `effectiveChip` | String | "gh3036" | 当前芯片型号 |
| `themeMode` | String | "sky_blue" | 主题模式 key |

### 8.3 UserPreferences (DataStore)

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

## 9. 线程模型

| 操作 | 线程 | 说明 |
|------|------|------|
| DataStore 读取 | `Dispatchers.IO` | Flow 自动管理 |
| 设置修改 | 同步 edit | DataStore.edit() |
| 日志导出 | `Dispatchers.IO` | 文件压缩操作 |
| UI 更新 | Main 线程 | StateFlow collect |