# 模块依赖关系

## 1. 模块依赖图

```
app
 ├── :core:core-ui
 ├── :core:core-common
 ├── :core:core-model
 ├── :core:core-data
 ├── :core:core-database
 ├── :core:core-datastore
 ├── :core:core-storage
 ├── :core:core-network          ← 未在 app/build.gradle.kts 直接声明，但各 feature 依赖
 ├── :feature:feature-login
 ├── :feature:feature-connection
 ├── :feature:feature-demo
 ├── :feature:feature-factory
 ├── :feature:feature-settings
 ├── :feature:feature-ota
 ├── :ble:ble-scanner
 ├── :ble:ble-connection
 └── :ble:ble-protocol
```

## 2. Core 模块内部依赖

```
core-model          # 纯数据模型，无任何依赖
    ↑
core-common         # 通用工具，依赖 core-model
    ↑
core-ui             # 公共 UI 组件 + 主题，依赖 core-model
    ↑
core-datastore      # Preferences 存储，依赖 core-model
    ↑
core-database       # Room 数据库，依赖 core-model
    ↑
core-network        # 网络请求，依赖 core-model, core-datastore
    ↑
core-data           # 数据仓库，依赖 core-database, core-network, core-datastore
    ↑
core-storage        # 文件存储 / CSV 录制，依赖 core-model, core-common
```

## 3. BLE 模块内部依赖

```
core-model
    ↑
core-datastore
    ↑
ble-protocol        # 协议解析 (rpccore, gh3036, gh3220, gh3300)
    ↑
ble-scanner         # BLE 扫描 (BleScanner, GHealthScanner)
    └───┐
ble-connection      # BLE 连接管理 (BleConnectionManager)
    ↑   └─ 依赖 ble-protocol, ble-scanner, core-datastore, core-storage
core-storage
```

## 4. Feature 模块依赖

| Feature 模块 | 依赖的 Core 模块 | 依赖的 BLE 模块 |
|-------------|-----------------|----------------|
| feature-login | core-model, core-network, core-datastore, core-ui | - |
| feature-connection | core-model, core-ui, core-storage | ble-connection, ble-scanner, ble-protocol |
| feature-demo | core-model, core-ui, core-storage, core-data | ble-connection, ble-protocol |
| feature-factory | core-model, core-ui, core-storage | ble-connection, ble-protocol |
| feature-ota | core-model, core-ui, core-network | ble-connection |
| feature-settings | core-model, core-ui, core-datastore, core-storage | - |

## 5. 外部模块

| 模块 | 说明 | 依赖的 Android 库 |
|------|------|-----------------|
| external:libcom | 通用工具库 | - |
| external:libdfu2 | Nordic DFU 库封装 | no.nordicsemi.android:dfu |

## 6. 依赖注入关系（Hilt）

```
@HiltAndroidApp GHealthApp
  └── @AndroidEntryPoint MainActivity
        ├── @Inject BlePreferences (来自 core-datastore)
        │     └── DataStore<Preferences> → BlePreferences
        └── @Inject ThemeMode (通过 Flow 转换)
              └── GHealthTheme (主题)

@Singleton BleConnectionManager
  ├── @Inject BlePreferences
  ├── @Inject LogManager
  ├── @Inject CoroutineScope
  └── 内部创建 GHealthExecutor (根据 chipName)
        ├── Gh3036Executor → RpcCore + FrameParser + Gh3036FrameDecoder
        ├── Gh3220Executor → RpcCore + FrameParser + Gh3220FrameDecoder
        └── Gh3300Executor → RpcCore + FrameParser + Gh3300FrameDecoder

@HiltViewModel ConnectionViewModel
  ├── @Inject BleScanner
  ├── @Inject BleConnectionManager
  ├── @Inject BlePreferences
  ├── @Inject RecordingManager
  └── @Inject DownloadManager, ConfigDownloader, ConfigSyncManager

@HiltViewModel DemoViewModel
  ├── @Inject BleConnectionManager
  ├── @Inject RecordingManager
  └── @Inject LogManager
```

## 7. 关键数据持有者

| 持有者 | 类型 | 内容 | 消费者 |
|--------|------|------|--------|
| `BleConnectionManager.devices` | `StateFlow<Map<String, ConnectedDevice>>` | 已连接设备状态 | ConnectionVM, DemoVM, OTAVM |
| `BleConnectionManager.dataFlow` | `SharedFlow<Pair<String, ParseResult>>` | 命令响应 | ConnectionVM |
| `BleConnectionManager.ghFrameFlow` | `SharedFlow<Pair<String, GhFuncFrame>>` | G 协议实时帧 | DemoVM |
| `BleConnectionManager.connectionErrors` | `SharedFlow<Pair<String, ConnectionError>>` | 连接错误 | ConnectionVM |
| `BleConnectionManager.heartRateResults` | `StateFlow<Map<Int, Int>>` | 对比设备心率 | DemoVM |
| `BleConnectionManager.testConfig` | `StateFlow<TestConfig?>` | 当前测试配置 | ConnectionVM, DemoVM |
| `UserSessionManager` | DataStore | JWT Token / 用户信息 | TokenManager |
| `BlePreferences` | DataStore | 蓝牙 UUID / 芯片类型 / 主题 | BleConnectionManager, Settings |