# GHealth Tools 项目框架总览

## 1. 项目简介

`ghealth-tools` 是一款面向 GHealth 系列生物传感芯片（GH3036/GH3220/GH3300）的 Android 调试与产测工具应用。通过 BLE 与芯片设备通信，实现实时数据监控、波形展示、产测流程执行、OTA 固件升级、CSV 数据录制与上传等功能。

## 2. 技术栈

| 类别 | 技术选型 | 版本 |
|------|---------|------|
| 语言 | Kotlin + Compose | 2.3.20 (K2) / JVM 17 |
| UI 框架 | Jetpack Compose + Material3 | BOM 2024.09.00 |
| 导航 | Navigation Compose | 2.8.4 |
| 依赖注入 | Hilt (Dagger) | 2.58 |
| 异步 | Kotlin Coroutines + Flow | 1.8.1 |
| 网络 | Retrofit + OkHttp + Moshi | 2.11.0 / 4.12.0 |
| 数据库 | Room | 2.8.4 |
| 键值存储 | DataStore Preferences | 1.1.1 |
| BLE 通信 | Kable (Android BLE) | 0.42.0 |
| 日志 | Timber | 5.0.1 |
| 图表 | Vico | 2.0.0-beta.2 |
| CSV | kotlin-csv | 1.10.0 |
| 测试 | JUnit 5 + MockK + Turbine | 5.10.3 |
| 构建 | Gradle KTS + Version Catalog | AGP 8.10.0 |

## 3. 模块架构

项目采用 **多模块 + Clean Architecture 分层** 设计，按职责划分为 6 个模块组：

```
ghealth-tools/
├── app/                          # 应用入口模块
│   ├── GHealthApp.kt             # Application (Hilt 入口 + Timber 初始化)
│   ├── MainActivity.kt           # 主 Activity (权限管理 + Compose 挂载)
│   ├── navigation/               # 顶层导航图
│   │   ├── Routes.kt             # 路由常量
│   │   └── GHealthNavHost.kt     # NavHost (登录 → 主界面 → 子页面)
│   └── di/AppModule.kt           # 顶层 DI (版本号等)
│
├── core/                         # 核心基础设施层 (不依赖 feature)
│   ├── core-model/               # 跨模块共享数据模型
│   ├── core-common/              # 工具类 / 通用 Result 封装 / 协程调度器
│   ├── core-ui/                  # 公共 UI 组件 / 主题 / 自适应布局
│   ├── core-datastore/           # DataStore Preferences (用户/蓝牙偏好)
│   ├── core-database/            # Room 数据库 (传感器记录)
│   ├── core-data/                # 数据仓库层 (Repository)
│   ├── core-network/             # 网络 API / Token 管理 / 下载管理
│   └── core-storage/             # 文件存储 (CSV 录制 / 日志管理)
│
├── ble/                          # BLE 通信层 (不依赖 feature)
│   ├── ble-scanner/              # BLE 扫描器
│   ├── ble-connection/           # BLE 连接管理器 (中心调度)
│   └── ble-protocol/             # 协议解析 (RPC 帧 / G 协议帧)
│
├── feature/                      # 功能模块层 (依赖 core + ble)
│   ├── feature-login/            # 登录 / 注册 / 项目管理
│   ├── feature-connection/       # 设备连接 / 命令面板
│   ├── feature-demo/             # 实时数据演示 / 波形展示
│   ├── feature-factory/          # 产测流程
│   ├── feature-ota/              # OTA 固件升级
│   └── feature-settings/         # 应用设置 / 设备信息
│
├── external/                     # 外部依赖封装
│   ├── libcom/                   # 通用工具库
│   └── libdfu2/                  # Nordic DFU 库封装
│
├── build-logic/                  # 构建约定插件
│   └── convention/               # Android Library / Compose 公共配置
│
└── scripts/                      # 运维脚本
    └── pull_debug_data.sh        # 拉取设备调试数据 (LOG + CSV)
```

## 4. 分层架构

```
┌──────────────────────────────────────────────────────────────┐
│                     app/ (Navigation + DI)                    │
├──────────────────────────────────────────────────────────────┤
│                  feature/ (Screen + ViewModel)                │
│    feature-login  feature-connection  feature-demo           │
│    feature-factory  feature-ota  feature-settings            │
├──────────────────┬──────────────────┬────────────────────────┤
│     ble/         │     core/        │     external/          │
│ ble-scanner      │ core-model       │ libcom                 │
│ ble-connection   │ core-ui          │ libdfu2                │
│ ble-protocol     │ core-storage     │                        │
│                  │ core-network     │                        │
│                  │ core-database    │                        │
│                  │ core-datastore   │                        │
│                  │ core-data        │                        │
│                  │ core-common      │                        │
├──────────────────┴──────────────────┴────────────────────────┤
│              Android SDK / Kable / Hilt / Compose             │
└──────────────────────────────────────────────────────────────┘
```

**依赖规则：**
- `feature/*` → `core/*` + `ble/*`（单向依赖）
- `ble/*` → `core/*`（不依赖 feature）
- `core/*` 内部有层次：`core-model` ← 其他 core 模块
- `app/` 组装所有模块（无业务逻辑）

## 5. 导航架构

应用采用**两级导航**设计：

### 5.1 外层导航（认证 → 主界面）

```
LOGIN ──(登录成功)──→ PROJECT_SELECTION ──(选择项目)──→ MAIN
  │                     │
  ├──(离线模式)──→ CHIP_SELECTION ──→ MAIN
  │                     │
  ├──(注册)────→ REGISTER ──→ PROJECT_SELECTION
  │                     │
  └──────────────────────┘
```

**路由：**
- `login` → `register` → `chip_selection` → `project_selection`
- `project_create` → `config_upload/{projectId}/{projectName}` → `project_selection`
- `project_manage` → `project_edit/{projectId}`
- `csv_file_list/{projectId}/{projectName}`

### 5.2 内层导航（主界面 Tab）

```
MAIN ──(底部导航栏 / 侧边导航栏)──
  ├── Connection（主界面 / 设备连接）
  ├── Demo（演示 / 波形展示）
  └── Settings（设置）
       └── DeviceInfo
  ├── Factory（产测）
  └── OTA（固件升级）
```

**自适应布局：**
- 宽屏设备（`WindowWidthSizeClass.Expanded`）：使用 `NavigationRail` 侧边栏
- 窄屏设备：使用 `NavigationBar` 底部导航栏

## 6. 依赖注入架构

使用 Hilt 管理依赖，关键模块：

| 模块 | Hilt DI 文件 | 提供内容 |
|------|-------------|---------|
| app | `di/AppModule.kt` | `@Named("app_version")` 版本号 |
| core-common | `di/DispatcherModule.kt` | `CoroutineScope`, `Dispatchers` |
| core-datastore | `di/DataStoreModule.kt` | `DataStore<Preferences>`, `BlePreferences`, `UserPreferences` |
| core-database | `di/DatabaseModule.kt` | `GHealthDatabase`, `SensorRecordDao` |
| core-network | `di/NetworkModule.kt` | Retrofit, OkHttpClient, API 接口 |
| core-storage | `di/StorageModule.kt` | `LogManager`, `CsvWriter`, `RecordingManager` |
| ble-connection | `di/BleModule.kt` | `BleConnectionManager`, `CoroutineScope` |

### 6.1 默认配置打包

APK 内置默认配置位于仓库根目录 `defaults/`（`application/config/{chip}/...`、
`factory/config/{chip}/{project-name}/...`），由 `app/build.gradle.kts` 的 assets 源目录打包进 APK；
`DefaultConfigInstaller`（core-storage）在应用启动时解压到 `GHealthTools/application/config/` 与
`GHealthTools/factory/config/`，用于离线登录支持。

## 7. 数据流总览

```
BLE 设备 ──Notify──→ Kable Peripheral
                         │
                         ▼
              BleConnectionManager.onDataReceived()
                         │
                         ▼
              GHealthExecutor.process(data)
                    ├─ FrameParser (帧解析)
                    ├─ RpcCore (命令路由)
                    └─ Gh3036FrameDecoder (G帧解码)
                         │
              ┌──────────┼──────────┐
              ▼          ▼          ▼
        dataFlow    ghFrameFlow  connectionErrors
        (命令响应)   (实时数据)    (错误事件)
              │          │
              ▼          ▼
    ConnectionVM      DemoVM
    (命令/响应)       (波形/算法/录制)
                          │
                          ▼
                   RecordingManager
                   (CSV 文件写入)
```

## 8. 构建系统

- **Gradle KTS** + **Version Catalog** (`gradle/libs.versions.toml`)
- 自定义 **build-logic/convention** 插件统一模块配置
- 构建产物：`app/build/outputs/apk/debug/app-debug.apk`
- 签名配置：`ghealth-release.keystore`（Release 构建）