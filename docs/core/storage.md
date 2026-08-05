# Core-Storage 模块流程文档

## 1. 模块概述

`core-storage` 负责文件系统操作，包括 CSV 数据录制、BLE 日志管理、存储路径管理。是数据持久化到文件的最底层模块。

## 2. 核心组件

| 组件 | 文件 | 职责 |
|------|------|------|
| `RecordingManager` | `RecordingManager.kt` | 录制会话管理，Channel 异步写入协调 |
| `DataRecorder` | `DataRecorder.kt` | 旧版录制接口（已废弃，由 RecordingManager 替代） |
| `CsvWriter` | `CsvWriter.kt` | CSV 文件写入器（BufferedWriter 封装） |
| `CsvRuleParser` | `CsvRuleParser.kt` | CSV 列规则解析（`.claude/csv_rules/`） |
| `CsvUploadManager` | `CsvUploadManager.kt` | CSV 上传到服务器 |
| `LogManager` | `LogManager.kt` | BLE 原始字节日志 + Timber 日志管理 |
| `FileLoggingTree` | `FileLoggingTree.kt` | Timber Tree 实现（日志写入文件） |
| `StoragePath` | `StoragePath.kt` | 文件路径生成器 |
| `StorageModule` | `di/StorageModule.kt` | Hilt DI 绑定 |

## 3. 录制会话管理 (RecordingManager)

详见 [recording-framework.md](../architecture/recording-framework.md)。

### 3.1 核心概念

- **会话 (Session)**: 一次完整的录制周期，从 TestConfig 确认到手动/自动停止
- **FunctionMode**: 设备的功能模式（ADT, HR, SPO2, NADT 等 12 种）
- **Channel 架构**: 每 FunctionMode 一个 `Channel<WriteTask>(256)` + 消费者协程
- **懒创建 CSV**: Server CSV 的 `CsvWriter` 在首帧到达时才创建（避免空文件）

### 3.2 关键方法

```kotlin
class RecordingManager {
    val isSessionActive: StateFlow<Boolean>

    suspend fun startSession(config: TestConfig, devices: List<ConnectedDevice>)
    suspend fun endSession()
    fun writeFrame(address: String, mode: String, columnMap: Map<String, Any?>, role: DeviceRole)
    fun updateCompareHr(deviceIndex: Int, hr: Int)
}
```

## 4. CSV 写入器 (CsvWriter)

### 4.1 内部设计

```kotlin
class CsvWriter(
    file: File,
    rule: CsvRule,
    infoJson: String
) {
    private var writer: BufferedWriter?

    suspend fun open()        // 创建文件 + 写入 JSON 元数据 + 列标题
    suspend fun writeRow(values: Map<String, Any?>)  // 写入一行，每100行自动 flush
    suspend fun flush()       // 强制 flush
    suspend fun close()       // flush + close
}
```

### 4.2 文件结构

```
行1: {"MAC":"...", "App-version":"...", "name":"...", ...}   ← JSON 元数据
行2: TimeStamp,FRAME_ID,Ipd0,Ipd1,...,ALGO_RESULT0,...        ← 列标题
行3+: 228530,1,0.1234,0.5678,...,72,...                        ← 数据行（每帧一行）
```

### 4.3 线程模型

- 所有 I/O 操作使用 `withContext(Dispatchers.IO)` 切换线程
- 每 100 行自动 `flush()`，平衡性能与可靠写入
- 消费者协程内串行调用，无并发写入问题

## 5. 日志管理 (LogManager)

### 5.1 日志类型

| 方法 | 内容 | 文件格式 |
|------|------|---------|
| `logBle(deviceAddress, direction, data)` | BLE 原始字节（RX/TX），16-hex 空格分隔 | `ble_raw_{address}_{date}.log`（地址去冒号） |
| `logProtocol(message)` | 协议解析层日志 | `protocol_{date}.log` |
| `logApp(level, tag, message)` | 应用日志（由 FileLoggingTree 转发 Timber 日志） | `app_{date}.log` |

> 文件路径均为 `logs/{date}/` 子目录下，按日期分目录。

### 5.2 BLE 日志格式

```
文件: logs/20260530/ble_raw_AABBCCDDEEFF_20260530.log

[2026-05-30 14:30:00.123] AA:BB:CC:DD:EE:FF RX: 0A C4 1E 8F 92 ...
[2026-05-30 14:30:00.456] AA:BB:CC:DD:EE:FF TX: 01 FE D3 4A 7B ...
```

> **实现注意**：`LogManager` 内部使用 `android.util.Log` 而非 `Timber` 记录自身的调试/错误信息。因为 `FileLoggingTree` 会把 Timber 日志转发回 `LogManager.logApp()`，若 `LogManager` 自身调用 Timber 会形成无限递归导致 `OutOfMemoryError`。

### 5.3 FileLoggingTree

```kotlin
class FileLoggingTree(private val logManager: LogManager) : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        logManager.logInfo("[$priority] $tag: $message")
    }
}
```

在 `GHealthApp.onCreate()` 中注册：
```kotlin
Timber.plant(FileLoggingTree(logManager))
if (BuildConfig.DEBUG) {
    Timber.plant(Timber.DebugTree())  // Logcat 输出
}
```

## 6. 存储路径 (StoragePath)

### 6.1 路径生成

```kotlin
class StoragePath(
    val mode: String,          // ADT, HR, SPO2, ...
    val deviceRole: String,    // master, slave, compare
    val scenario: String,      // 测试场景
    val tester: String,        // 测试人员
    val chip: String,          // gh3036, gh3220, gh3300
    val deviceName: String,
    val deviceAddress: String,
    val appVersion: String,
    val date: String           // 日期 yyyyMMdd
) {
    fun serverPath(): String   // server/{mode}/extra_{role}_{scenario}_{tester}_{chip}_{mode}_{timestamp}.csv
    fun recordsPath(): String  // records/{mode}/extra_records_{mode}_{timestamp}.csv
    fun infoJson(): String     // JSON 元数据
}
```

### 6.2 目录结构

```
{externalStorage}/ghealth_tools/
├── {date}/
│   ├── server/
│   │   ├── ADT/
│   │   │   └── extra_master_adt_xxx.csv
│   │   ├── HR/
│   │   └── ...
│   ├── records/
│   │   ├── ADT/
│   │   │   └── extra_records_adt_xxx.csv
│   │   └── ...
│   └── logs/
│       └── ble_{date}.log
├── factory/
│   └── result_{chip}_{version}_{timestamp}.csv
└── configs/
    └── {projectId}/
        └── register_configs/
```

## 7. CSV 上传 (CsvUploadManager)

```
CsvUploadManager.uploadCsvFile(file, projectId)
  │
  ├── 读取 CSV 文件内容
  ├── UploadApi.uploadCsv(projectId, file)
  │     ├── Multipart 上传
  │     └── 进度回调
  │
  └── 上传结果回调
        ├── 成功 → 标记文件已上传
        └── 失败 → 重试 / 记录错误
```

## 8. Hilt DI 配置

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides @Singleton
    fun provideLogManager(@ApplicationContext context: Context): LogManager

    @Provides @Singleton
    fun provideRecordingManager(
        @ApplicationContext context: Context,
        appVersion: String
    ): RecordingManager

    @Provides @Singleton
    fun provideCsvUploadManager(
        uploadApi: UploadApi
    ): CsvUploadManager
}
```