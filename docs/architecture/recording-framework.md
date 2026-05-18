# 数据录制框架

## 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                        BLE 数据流入                          │
│  BLE Notify → onDataReceived(suspend) → RpcCore.process()  │
│    → FrameParser(单协程串行) → handleGData                  │
│      → Gh3036FrameDecoder.decode() → GhFuncFrame            │
│        → ghFrameFlow.tryEmit(address, frame)                │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                   DemoViewModel                              │
│  ghFrameFlow.collect → onFrameReceived()                    │
│    → toColumnMap() 列映射                                    │
│    → recordingManager.writeFrame(mode, addr, values, role)  │
│    　→ channel.trySend(WriteTask)  ← 非阻塞入队              │
└────────────────────────┬────────────────────────────────────┘
                         │
          ┌──────────────┼──────────────┐
          ▼              ▼              ▼
    ┌─────────┐   ┌─────────┐   ┌─────────┐
    │ADT Chan │   │HR Chan  │   │NADT Chan│  ... 12个 FunctionMode
    │(256)    │   │(256)    │   │(256)    │
    └────┬────┘   └────┬────┘   └────┬────┘
         │              │              │
         ▼              ▼              ▼
    ┌─────────┐   ┌─────────┐   ┌─────────┐
    │Consumer │   │Consumer │   │Consumer │   每 mode 一个消费者协程
    │(IO线程) │   │(IO线程) │   │(IO线程) │
    │         │   │         │   │         │
    │ for(task│   │ for(task│   │ for(task│   Channel 关闭自动退出
    │  in ch) │   │  in ch) │   │  in ch) │
    │  ↓      │   │  ↓      │   │  ↓      │
    │ writeRow│   │ writeRow│   │ writeRow│   懒创建 CSV writer
    │ + flush │   │ + flush │   │ + flush │   首帧触发文件创建
    │ records │   │ records │   │ records │   每秒写 records 行
    └─────────┘   └─────────┘   └─────────┘
```

---

## 1. 会话生命周期

### 自动启动

```
用户连接主设备 → 填写 TestConfig → 确认
  → ConnectionViewModel.confirmTestConfig(config)
    → connectionManager.resetFrameDecoders()
    → recordingManager.startSession(config, devices...)
      → endSession() 安全清理（前一次会话如果还在）
      → 创建 SessionConfig（场景、测试人员、设备信息）
      → 为全部 12 个 FunctionMode 创建：
         ├── Channel<WriteTask>(256)     ← 入队缓冲区
         ├── Consumer Job (scope.launch) ← 消费者协程
         ├── CsvWriter (records)         ← 立即打开
         └── RecordsBufferState          ← 内存状态
      → isSessionActive = true
```

**关键点：**
- CSV 文件**懒创建**：Channel 和 Consumer 在 `startSession()` 时创建，但 Server CSV 的 `CsvWriter` 在该 mode+device 的**首个 WriteTask 到达时**才创建。避免为未激活模式生成空文件。
- Records CSV 在 `startSession()` 时立即创建和打开（每 mode 一份，跨设备共享）。
- 不需要等待首帧——全部 modes 的 Channel 已就绪，首帧到达即入队。

### 手动停止

```
用户点击录制按钮（TopAppBar 录制指示器）
  → DemoViewModel.toggleRecording()
    → recordingManager.endSession()
      → 所有 Channel.close()            ← 停止新写入
      → consumerJob.join()              ← 等待消费者排空
      → CsvWriter.flush() + close()     ← 安全关闭文件
      → modeStates.clear()
      → isSessionActive = false
    → connectionManager.notifyRecordingStopped()
      → ConnectionViewModel.stopMonitoring()
```

### 手动恢复

```
用户再次点击录制按钮
  → DemoViewModel.toggleRecording()
    → connectionManager.resetFrameDecoders()
    → 读取 connectionManager.testConfig（上次配置仍保留）
    → recordingManager.startSession(config, devices...)
      → 创建新的 CSV 文件（新时间戳、新文件名）
```

### 断联自动停止

```
BLE 断开 → devices StateFlow 变空
  → ConnectionViewModel: devices 收集器检测 isEmpty
    → stopMonitoring()
    → recordingManager.endSession()
  → DemoViewModel: devices 收集器检测 isEmpty
    → autoRecordingStopped = false（允许重连后自动恢复）
```

**关键点：**
- 断联时 `endSession()` 确保**所有 Channel 中的数据排空并写入 CSV**，不会因断联丢失已在缓存中的帧。
- `endSession()` 是 `suspend` 函数，调用方在协程内等待排空完成。

### 重连自动恢复

```
重新连接设备 → 填写 TestConfig → 确认
  → 同"自动启动"流程
  → 创建新 CSV 文件（新文件名带新时间戳）
  → 新的录制会话
```

---

## 2. 数据解析链路

### BLE 通知 → 协议帧

```
BLE Notify characteristic 数据
  │
  ▼
GHealthConnectionManager.onDataReceived(address, data)  [suspend, 单协程串行]
  │
  ▼
Gh3036Executor.process(data)
  → RpcCore.process(data)  [Mutex 保护, 同协程内执行]
    → FrameParser.process(data)  [逐字节状态机, 同步]
      ├── 帧头识别: 0xAA 0x11
      ├── Length → TypeKey → Key → Index → Param → CRC
      └── 输出: ParseResult(key, param, isSecure, isFin, invokeIdx, frameIdx)
    → handleParseResult()
      ├── 不安全帧 → MultiFrameBuffer 拼包 → staticHandlers["G"]
      └── 安全帧 → 直接路由到 handler
  │
  ▼
staticHandlers["G"] 回调 → Gh3036Executor.handleGData(data)
  → unpackU8Array(data)  [剥离 u8* 格式头]
  → Gh3036FrameDecoder.decode(param)  [varint + zigzag + delta 解码]
    ├── decodeSingleFrame() × N  [每个 G param 含 N 个 GhFuncFrame]
    │   ├── readVarint → packHeader (位域标志各字段有无)
    │   ├── 按 packHeader 逐字段读取: rawdata, phyValue, gsData, flags, algoData, agcInfo, timestamp
    │   └── 输出: RawFrame
    └── processDelta() × N  [delta 累加]
        ├── 第1帧 (startFlag=true): 绝对值
        └── 第2+帧 (startFlag=false): lastValue + rawDelta
  │
  ▼
frameCallback → GHealthConnectionManager.onGhFuncFrame(address, frame)
  → _ghFrameFlow.tryEmit(address to frame)  [非阻塞发射]
```

### 关键设计

| 层级 | 线程模型 | 说明 |
|------|----------|------|
| BLE observe | `Dispatchers.IO` 单协程 | `peripheral.observe().onEach{}.launchIn(scope)` |
| FrameParser | 同步（协程内） | 逐字节状态机，有状态但无并发 |
| MultiFrameBuffer | 同步（协程内） | 按 frameIdx 顺序拼包，gap 检测 |
| Gh3036FrameDecoder | 同步（协程内） | varint+zigzag+delta，`decode()` 时 reset() |
| ghFrameFlow 发射 | `tryEmit` 非阻塞 | SharedFlow(extraBufferCapacity=64) |
| GhFuncFrame → CSV | Channel 异步 | 不同协程，消息队列解耦 |

### 协议帧内多帧解码

一个 G param 包含**同一 funcId** 的多个 GhFuncFrame（例如 1 个 base + 4 个 delta）：

```
G param (139 bytes):
  ├── Frame 1 (base):  packHeader=383, rawdata=[228530], timestamp=1.6T (绝对)
  ├── Frame 2 (delta): packHeader=327, rawdata=[5378],   timestamp=200 (delta)
  ├── Frame 3 (delta): packHeader=327, rawdata=[-8604],  timestamp=200 (delta)
  ├── Frame 4 (delta): packHeader=343, rawdata=[-19357], timestamp=200 (delta)
  └── Frame 5 (delta): packHeader=327, rawdata=[3763],   timestamp=200 (delta)

解码结果:
  Frame 1: rawdata=228530, timestamp=1.6T
  Frame 2: rawdata=228530+5378=233908, timestamp=1.6T+200
  Frame 3: rawdata=233908-8604=225304, timestamp=1.6T+400
  Frame 4: rawdata=225304-19357=205947, timestamp=1.6T+600
  Frame 5: rawdata=205947+3763=209710, timestamp=1.6T+800
```

---

## 3. 数据队列架构

### Channel 设计

```kotlin
// 每 FunctionMode 一个 Channel
val channel = Channel<WriteTask>(capacity = 256)

data class WriteTask(
    val deviceAddress: String,      // 设备 MAC
    val columnMap: Map<String, Any?>, // CSV 列映射 (TimeStamp, FRAME_ID, Ipd0..31, ...)
    val role: DeviceRole,            // MASTER / SLAVE / COMPARE
    val timestamp: Long              // 硬件时间戳（用于 records 秒边界判断）
)
```

### 入队（生产者）

```kotlin
// DemoViewModel.onFrameReceived() 中调用
fun writeFrame(deviceAddress: String, mode: String, columnMap: Map<String, Any?>, role: DeviceRole) {
    val state = modeStates[mode] ?: return
    val task = WriteTask(deviceAddress, columnMap, role, timestamp)
    val result = state.channel.trySend(task)
    if (result.isFailure) {
        Timber.w("WriteTask dropped: channel full or closed")
    }
}
```

- **非阻塞**：`trySend` 不挂起，如果 Channel 满则丢弃并打日志。
- Channel 容量 256：正常采集速率下（ADT 50Hz × 1通道 = 50 task/s，HR 25Hz × 2通道），256 容量可缓冲约 5 秒数据。消费者写入速度远超生产速度，实际不会满。

### 出队（消费者）

```kotlin
// 每 mode 一个消费者协程
private suspend fun consumeModeChannel(
    mode: String,
    channel: Channel<WriteTask>,
    serverWriters: ConcurrentHashMap<String, CsvWriter>,
    recordsWriter: CsvWriter?,
    recordsBuffer: RecordsBufferState,
    lock: Mutex
) {
    for (task in channel) {  // Channel 关闭时自动退出循环
        try {
            writeTaskToCsv(mode, task, serverWriters, recordsWriter, recordsBuffer, lock)
        } catch (e: Exception) {
            Timber.e(e, "Error writing task for mode=$mode")
        }
    }
}
```

- **单消费者串行**：每个 mode 一个消费者，保证写入顺序，无 CSV 交错。
- **`for (task in channel)`**：Channel 关闭且排空后自动退出，无需手动管理退出条件。
- **异常隔离**：单个 task 写入失败不影响后续 task 处理。

---

## 4. CSV 写入策略

### 文件结构

每个录制会话生成两类文件：

**Server CSV**（每 device × 每 mode 一份，懒创建）：
```
路径: server/{mode}/extra_{role}_{scenario}_{tester}_gh3036_{mode}_{timestamp}.csv
行1: JSON元数据 {"MAC":"...", "App-version":"...", "name":"...", ...}
行2: 列标题 TimeStamp,FRAME_ID,ACCX,...,GYRO_Z
行3+: 数据行（每 GhFuncFrame 一行）
```

**Records CSV**（每 mode 一份，跨 device 共享，`startSession()` 时立即创建）：
```
路径: records/{mode}/extra_records_{mode}_{timestamp}.csv
列: TimeStamp, MasterAlgo, SlaveAlgo, Compare0_HR, ..., Compare4_HR
每秒写入一行（按硬件时间戳/1000 的秒边界）
```

### Server CSV 懒创建

```kotlin
// 消费者中，首个 WriteTask 到达时才创建 CsvWriter
private suspend fun createServerWriter(mode: String, task: WriteTask): CsvWriter? {
    val cfg = currentConfig ?: return null
    val path = StoragePath(
        mode = mode,
        deviceRole = role,
        scenario = cfg.scenario,
        tester = cfg.tester,
        chip = "gh3036",
        deviceName = deviceName,
        deviceAddress = task.deviceAddress,
        appVersion = appVersion,
        date = sessionDate
    )
    val writer = CsvWriter(File(baseDir, path.serverPath()), rule, path.infoJson())
    writer.open()  // 写入 JSON 元数据行 + 列标题行
    return writer
}
```

**原因**：12 个 FunctionMode 中只有芯片实际运行的模式会产生数据。懒创建意味着未激活模式的 Server CSV 不会生成，避免空文件污染。

### Records CSV 写入

Records 按**硬件时间戳秒边界**写入，不在每帧写入：

```kotlin
val currentSecond = task.timestamp / 1000
if (currentSecond != recordsBuffer.lastWrittenSecond) {
    flushRecordsRow(mode, task.timestamp, recordsWriter, recordsBuffer, lock)
    recordsBuffer.lastWrittenSecond = currentSecond
}
```

Records 行聚合当前秒内的最新值：
- `MasterAlgo`: 主设备最近一帧的 ALGO_RESULT0
- `SlaveAlgo`: 从设备最近一帧的 ALGO_RESULT0
- `Compare0_HR..4_HR`: 比较设备的心率值（来自 `updateCompareHr()` 实时更新）

### CsvWriter 内部

```kotlin
class CsvWriter(file: File, rule: CsvRule, infoJson: String) {
    private var writer: BufferedWriter? = null

    suspend fun open() = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        writer = BufferedWriter(FileWriter(file))
        if (infoJson.isNotEmpty()) { writeRawRow(infoJson) }
        writeRawRow(rule.columns)  // 列标题
    }

    suspend fun writeRow(values: Map<String, Any?>) = withContext(Dispatchers.IO) {
        // 每 100 行自动 flush
    }

    suspend fun flush() = withContext(Dispatchers.IO) { writer?.flush() }
    suspend fun close() = withContext(Dispatchers.IO) { writer?.flush(); writer?.close(); writer = null }
}
```

- 每 100 行自动 `flush()`，平衡性能和可靠写入。
- `withContext(Dispatchers.IO)` 确保文件 I/O 不阻塞消费者协程的任务迭代。

---

## 5. 会话关闭保障

### endSession() 四步安全关闭

```kotlin
suspend fun endSession() {
    if (!_isSessionActive.value) return

    // 1. 关闭所有 Channel — 生产者无法再入队
    modeStates.forEach { (_, state) -> state.channel.close() }

    // 2. 等待所有消费者排空并退出
    modeStates.forEach { (_, state) -> state.consumerJob.join() }

    // 3. Flush + Close 所有 CsvWriter
    modeStates.forEach { (_, state) ->
        state.serverWriters.values.forEach { it.flush(); it.close() }
        state.recordsWriter?.let { it.flush(); it.close() }
    }

    // 4. 清理内存状态
    modeStates.clear()
    currentConfig = null
    _isSessionActive.value = false
}
```

**保障机制：**
- Step 1 确保 `trySend` 返回失败，生产者感知到会话结束。
- Step 2 `join()` 阻塞等待消费者处理完 Channel 中所有残留 task。
- Step 3 在消费者退出后安全关闭文件——不会出现"关闭时仍有协程在写入"的竞争。
- 全程 `suspend`，调用方（ConnectionViewModel / DemoViewModel）在协程内等待完成。

### 异常场景处理

| 场景 | 处理方式 |
|------|----------|
| 录制中 App 崩溃 | OS 关闭文件描述符，BufferedWriter 数据可能丢失未 flush 部分 |
| 录制中 BLE 断联 | `endSession()` 完整执行，已入队数据全部写入 |
| startSession 时上次会话未清理 | 先调用 `endSession()` 安全清理 |
| startSession 时无已连接设备 | Channel 创建但无 CSV 文件（懒创建），后续连接不会自动补建 |
| 消费者写入异常 | `try-catch` 捕获，单条失败不中断消费者循环 |

---

## 6. 录制相关组件交互

```
ConnectionViewModel           DemoViewModel            RecordingManager
      │                           │                         │
      │ confirmTestConfig()       │                         │
      │─────startSession()──────────────────────────────────▶│
      │                           │                         │ 创建 Channel + Consumer
      │                           │                         │ isSessionActive = true
      │                           │◀── isSessionActive ─────│
      │                           │  uiState.isRecording=true
      │                           │                         │
      │                           │ onFrameReceived()       │
      │                           │──writeFrame()──────────▶│
      │                           │                         │ channel.trySend(task)
      │                           │                         │ consumer → writeRow
      │                           │                         │
      │        [用户点击停止录制]    │                         │
      │                           │ toggleRecording()       │
      │                           │──endSession()──────────▶│
      │                           │                         │ close channels → join → flush
      │ notifyRecordingStopped()◀─│                         │
      │ stopMonitoring()          │                         │
      │                           │                         │
      │        [BLE 断联]          │                         │
      │ devices.isEmpty           │ devices.isEmpty         │
      │──endSession()──────────────────────────────────────▶│
      │                           │                         │
```

---

## 7. 与旧架构对比

| 维度 | 旧架构 (DataRecorder) | 新架构 (RecordingManager) |
|------|----------------------|--------------------------|
| 录制启动 | 懒启动（首帧触发 ensureRecording） | 主动启动（TestConfig 确认即启动） |
| CSV 创建 | 每 mode 首帧时创建 | Channel 预创建，CSV 懒创建 |
| 写入并发 | 每帧 scope.launch 新协程 | 每 mode 单消费者串行 |
| 入队机制 | 无（直接 writeRow） | Channel<WriteTask>(256) |
| 会话关闭 | 无 flush 保障，协程可能未完成 | 四步安全关闭，join 等待排空 |
| 跨 mode 首帧 | 容易丢失（NADT 帧1-5） | 不丢失（全部 mode Channel 就绪） |
| 线程安全 | mutableMap 无同步 | ConcurrentHashMap + Mutex |
| 断联处理 | stopAllRecording 同步调用 | endSession() suspend 安全排空 |
| Records 生成 | 依赖 hardware timestamp 秒边界 | 同上（修复 timestamp 后正常） |
