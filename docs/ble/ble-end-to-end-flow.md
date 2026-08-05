# BLE 端到端通信流程

本文档描述从 BLE 扫描到数据流入应用层的完整端到端流程。

## 1. 流程总览

```
  ┌──────────┐    ┌──────────┐    ┌───────────┐    ┌──────────┐    ┌──────────┐
  │ 扫描阶段  │───→│ 连接阶段  │───→│ 服务验证   │───→│ 数据通信   │───→│ 断开清理  │
  │ Scan     │    │ Connect  │    │ Validate  │    │ Exchange │    │ Cleanup  │
  └──────────┘    └──────────┘    └───────────┘    └──────────┘    └──────────┘
```

## 2. 扫描阶段

### 2.1 扫描器层次

```
BleScanner (统一扫描器)
  ├── scan() → Flow<BleDevice>
  ├── 内部缓存 Advertisement 供 BleConnectionManager 使用
  ├── hasScanPermission
  └── hasConnectPermission
```

### 2.2 扫描流程

```
ConnectionViewModel.startScan()
  │
  ├── 1. 检查权限 (hasScanPermission / hasConnectPermission)
  │
  ├── 2. 启动扫描
  │     └── BleScanner.scan() → Flow<BleDevice>
  │
  ├── 3. Kable Scanner 内部流程
  │     ├── 系统 BLE API 扫描
  │     ├── 广告包过滤 (rssi >= minRssi, 默认 -80dBm)
  │     └── 转换为 BleDevice(name, address, rssi) Flow
  │
  ├── 4. ConnectionViewModel 收集设备 Flow
  │     ├── 过滤已连接 / 正在连接的设备
  │     ├── 去重 (按 address)
  │     └── 更新 scanResults StateList
  │
  └── 5. 用户点击设备 → connect(address, name, role)
```

### 2.3 关键类型

```kotlin
data class BleDevice(
    val name: String?,
    val address: String,
    val rssi: Int
)
```

## 3. 连接阶段

### 3.1 设备角色

```kotlin
enum class DeviceRole {
    MASTER,    // 主设备 (1个) — 执行 RPC 命令 + 接收 G 协议实时数据
    SLAVE,    // 从设备 (1个) — 接收 G 协议实时数据
    COMPARE   // 对比设备 (最多5个) — 接收标准心率服务数据
}
```

### 3.2 连接约束检查

```
BleConnectionManager.connect(address, role)
  │
  ├── checkConnectionConstraint(role)
  │     ├── MASTER:  已连主设备? → MasterAlreadyConnected
  │     ├── SLAVE:   已连从设备? → SlaveAlreadyConnected
  │     └── COMPARE: 对比数≥5?  → CompareLimitReached
  │
  ├── 约束通过 → connect(peripheral, role) [suspend]
  │
  ├── 更新设备状态 → ConnectionState.CONNECTING
  │
  ├── peripheral.connect() — Kable 内部调用 BluetoothGatt.connect()
  │
  └── 状态监听
        ├── State.Connected    → validateServices()
        └── State.Disconnected → onDeviceDisconnected()
```

### 3.3 连接状态机

```
DISCONNECTED → CONNECTING → CONNECTED
       ↑            │            │
       │            ▼            │
       └──── DISCONNECTING ←────┘ (主动断开 / 异常断开)
```

## 4. 服务验证阶段

### 4.1 验证流程

```
validateServices(peripheral, address, role)
  │
  ├── peripheral.services.first() — 等待服务发现完成
  │     └── null → ServiceNotFound 错误
  │
  └── 按角色分支:
        │
        ├── MASTER / SLAVE:
        │     ├── 从 BlePreferences 读取 UUID 配置
        │     │     ├── writeCharUuid
        │     │     └── notifyCharUuid
        │     │     （serviceUuid 不再参与匹配，仅作异常回退）
        │     │
        │     ├── 把所有已发现服务的特征拍平为 (服务UUID, 特征UUID) 列表
        │     │
        │     ├── CharacteristicMatcher.match(...) 按特征 UUID 跨全部服务查找
        │     │     ├── 写入特征未命中 → WriteCharacteristicNotFound
        │     │     └── 通知特征未命中 → NotifyCharacteristicNotFound
        │     │
        │     ├── 记录写入特征实际所属服务 UUID → writeServiceUuidByAddress
        │     ├── 按写入特征属性选择 WriteType (WithResponse / WithoutResponse)
        │     │
        │     ├── peripheral.observe(characteristicOf(实际服务UUID, notifyUuid)) — 订阅通知
        │     │     └── 异常 → NotifyCharacteristicNotFound
        │     │
        │     └── 创建 GHealthExecutor (根据芯片类型)
        │           ├── GH3036 → Gh3036Executor
        │           ├── GH3220 → Gh3220Executor
        │           └── GH3300 → Gh3300Executor
        │
        └── COMPARE:
              ├── 查找标准心率服务 (0000180d-...)
              │     └── null → HeartRateServiceNotFound
              │
              └── 订阅心率测量特征 (00002a37-...)
                    └── onHeartRateReceived() 回调
```

### 4.2 芯片类型自动识别

```
BleConnectionManager.createExecutor(address)
  │
  ├── 读取 blePreferences.effectiveChip
  │     └── 用户选择的芯片类型: "gh3036" / "gh3220" / "gh3300"
  │
  └── 创建对应执行器
        ├── gh3036 → Gh3036Executor
        ├── gh3220 → Gh3220Executor
        └── gh3300 → Gh3300Executor
```

### 4.3 电池服务读取（Battery Service 0x180F）

服务验证完成、设备进入 CONNECTED 后，`BleConnectionManager` 以 fire-and-forget 方式异步触发 `readBatteryService(peripheral, address)`，不阻塞 CONNECTED 状态迁移，失败仅记日志。

```
CONNECTED → scope.launch { readBatteryService(peripheral, address) }
  │
  ├── 拍平所有已发现特征为 (服务UUID, 特征UUID) 列表
  │
  ├── BatteryServiceMatcher.match(refs)
  │     ├── 按特征 UUID 0x2A19 跨服务查找所属服务 UUID（必选）
  │     └── 按特征 UUID 0x2A1E 跨服务查找所属服务 UUID（可选）
  │
  ├── 0x2A19 Battery Level（电量百分比）
  │     ├── peripheral.read() 初次读取 → parseLevel → updateBatteryStatus(level)
  │     └── 若支持 notify/indicate → peripheral.observe() 持续刷新
  │
  └── 0x2A1E Battery Level Status（充放电状态，可选）
        └── 若存在且支持 notify → peripheral.observe() → parseChargeState → updateBatteryStatus(chargeState)
  │
  └── batteryStatus: StateFlow<Map<String, BatteryStatus>>  [按地址索引]
        └── 消费者: ConnectionViewModel → DeviceStatusCard → BatteryIndicator
```

**UUID 常量**（`BatteryServiceUuids`）：

| 名称 | UUID | 说明 |
|------|------|------|
| Battery Service | `0000180f-...` | 标准电池服务 |
| Battery Level | `00002a19-...` | 1 字节 uint8，0–100（必选） |
| Battery Level Status | `00002a1e-...` | flags + 可选字段（可选） |

**BatteryLevelStatusParser** 解析 0x2A1E：
- 基于 GSS v7（pre-BAS v1.1）结构，flags 各 bit 表示独立可选字段存在性。
- 按字段出现顺序计算偏移：Battery Level(1) → Charge Level(2) → Charge Type(1) → Status(1)。
- 无 Status 字段时，依据外接电源位（wired/wireless）保守推断为 Charging。
- BAS v1.1 引入不兼容的 Power State 字段结构，新设备需另行实现。

**原子更新**：`updateBatteryStatus(address, transform)` 通过 `_batteryStatus.update { currentMap + (address to transform(currentMap[address])) }` 保证电量与充放电状态并发更新不丢失。

### 4.4 固件版本读取（FirmwareVersionHolder）

固件版本由 `@Singleton` 的 `FirmwareVersionHolder` 统一获取，连接页与设置页共享同一份状态，避免重复下发读取命令。

```
FirmwareVersionHolder 订阅 BleConnectionManager.devices
  │
  ├── 主设备 CONNECTED（地址变化）→ scheduleFetch(address)
  │     ├── fetchJob?.cancel()  取消上一次在途读取
  │     ├── state.isReading = true, version = null
  │     └── delay(5_000)  连接稳定后再读取
  │           ├── 读取前校验 isStillCurrentMaster(address)  防止 stale
  │           ├── resolveFirmwareVersion(sendCmd)
  │           │     ├── 优先 verType=0x09（BLE 版本）→ GH3X_GetVersion
  │           │     │     └── 解析为 "no_ver" 视为失败，触发回退
  │           │     ├── 回退 verType=0x01（固件版本）→ GH3X_GetVersion
  │           │     │     └── 解析为 "no_ver" 视为失败
  │           │     └── 两者都失败 → null
  │           ├── 单次读取超时 3000ms（withTimeoutOrNull）
  │           └── 再次校验 isStillCurrentMaster 后写入 state
  │
  └── 主设备断开（无 CONNECTED 主设备）
        ├── fetchJob?.cancel()  取消在途读取
        └── state = FirmwareVersionState()  清空版本，避免 stale 回填
```

**关键约束**：
- `CancellationException` 会被重新抛出（不吞掉协程取消信号）。
- 其他异常捕获后记 `Timber.w` 并返回 null，不中断订阅。
- `state: StateFlow<FirmwareVersionState(version, isReading)>` 被 `ConnectionViewModel`（主屏设备卡）与 `SettingsViewModel`（设置页）共同订阅。

## 5. 数据通信阶段

### 5.1 GHealthExecutor 初始化

```
createExecutor() → 创建执行器实例
  │
  ├── setupExecutor(executor, address)
  │     ├── executor.setSendFunction { data → writeToDevice(address, data) }
  │     │     └── peripheral.write(writeChar, data, WriteType.WithResponse)
  │     │     └── logManager.logBle(address, "TX", data)
  │     │
  │     ├── executor.registerFrameCallback { frame → onGhFuncFrame(address, frame) }
  │     │     └── _ghFrameFlow.tryEmit(address to frame)
  │     │
  │     └── executor.registerGHandler()
  │           └── 注册 "G" key 的处理器 → 自动解码 G 协议帧
```

### 5.2 数据接收流程 (RX)

```
BLE Notify Characteristic 数据到达
  │
  ▼
Kable observe callback (onEach)
  │  线程: Dispatchers.IO 单协程 (串行化)
  ▼
BleConnectionManager.onDataReceived(address, data)
  │
  ├── logManager.logBle(address, "RX", data) — 记录原始 BLE 日志
  │
  └── executor.process(data) — 送入协议解析器
        │
        ├── FrameParser.process(data)  [逐字节状态机]
        │     ├── 帧头匹配: 0xAA 0x11
        │     ├── Length → TypeKey → Key → Index → Param → CRC
        │     └── 输出: ParseResult(key, param, isSecure, isFin, invokeIdx, frameIdx)
        │
        ├── 安全帧 (isSecure=true)
        │     ├── staticHandlers 匹配 → 调用 handler
        │     └── pendingCalls 匹配 → 响应 ACK/数据
        │
        └── 非安全帧 (isSecure=false)
              ├── MultiFrameBuffer 拼包 (多帧时)
              ├── staticHandlers["G"] → handleGData()
              │     ├── unpackU8Array(data)
              │     ├── Gh3036FrameDecoder.decode()
              │     │     ├── Varint 解码
              │     │     ├── ZigZag 解码
              │     │     └── Delta 累加
              │     └── frameCallback(GhFuncFrame)
              │
              └── _dataFlow.emit(address, ParseResult) — 命令响应
```

### 5.3 命令发送流程 (TX)

```
ConnectionViewModel.executeCommand(commandMeta)
  │
  ▼
BleConnectionManager.sendCommand(address, key, param)
  │
  ├── 获取命令元数据 (Gh3036CommandMeta)
  │     ├── requestFormat — 参数打包格式
  │     └── hasResponse — 是否等待响应
  │
  ├── hasResponse == true:
  │     ├── executor.call(key, format, params)
  │     │     ├── FrameBuilder.build() — 构建帧
  │     │     ├── PendingCall 注册 (timeout=3000ms)
  │     │     ├── sendFunction(frame) — 通过 BLE 发送
  │     │     │     └── writeToDevice(address, frame)
  │     │     │           └── logManager.logBle(address, "TX", frame)
  │     │     └── PendingCall.waitForResponse()
  │     │           ├── 收到响应 → Result.success(response)
  │     │           └── 超时     → Result.failure(ProtocolError.Timeout)
  │     │
  │     └── Unpackage.unpackWithFormat(raw, responseFormat)
  │
  └── hasResponse == false:
        └── executor.send(key, format, params)
              └── sendFunction(frame) → Result.success(Unit)
```

## 6. 断开连接阶段

### 6.1 主动断开

```
ConnectionViewModel.disconnect(address) / disconnectAll()
  │
  ▼
BleConnectionManager.disconnect(address)
  │
  ├── executor.reset() — 清理帧解析器、等待中的调用
  ├── updateDeviceState(DISCONNECTING)
  ├── peripheral.disconnect() — Kable → BluetoothGatt.disconnect()
  ├── peripheral.close() — 释放 GATT 资源
  │
  └── 状态监听触发 onDeviceDisconnected()
        ├── peripherals.remove(address)
        ├── _devices 移除
        ├── _batteryStatus 移除该地址（清除电池状态）
        ├── clearWriteServiceUuid(address)（清除写入特征所属服务 UUID 缓存）
        ├── COMPARE 设备: 清空心率结果
        └── RecordingManager.endSession() (自动停止录制)
        └── 注：主设备断开后 FirmwareVersionHolder 自行取消读取并清空版本（见 4.4）
```

### 6.2 被动断开（设备端断开）

```
Kable State.Disconnected 事件
  │
  ▼
BleConnectionManager.onDeviceDisconnected(address)
  │
  ├── 自动从设备列表移除
  ├── ConnectionViewModel 检测设备列表为空
  │     ├── stopMonitoring()
  │     └── recordingManager.endSession()
  │
  └── DemoViewModel 检测设备列表为空
        └── autoRecordingStopped = false (允许重连自动恢复)
```

### 6.3 disconnectAll()

```
BleConnectionManager.disconnectAll()
  │
  ├── 遍历所有 connected 设备
  │     └── scope.launch { disconnect(address) }
  │
  └── 所有设备并发断开
```

## 7. 错误处理

### 7.1 连接错误类型

```kotlin
sealed class ConnectionError {
    ServiceNotFound           // 目标服务 UUID 未找到
    WriteCharacteristicNotFound // 写入特征未找到
    NotifyCharacteristicNotFound // 通知特征未找到
    HeartRateServiceNotFound   // 心率服务未找到
    ConnectionFailed(errorMessage) // 连接失败（含约束违规）
}
```

### 7.2 错误传播

```
BLE 操作异常
  │
  ├── BleConnectionManager 捕获
  │     └── emitConnectionError(address, error)
  │           └── _connectionErrors.emit(address to error)
  │
  ▼
ConnectionViewModel 收集 connectionErrors
  │
  ├── 解析错误类型 → ConnectionErrorState
  └── UI 展示 Toast / ErrorDisplay
```

### 7.3 写入路径的服务 UUID

`writeToDevice` 使用 `validateServices` 期间缓存的、写入特征**实际所属**服务 UUID 构建 `characteristicOf`，而非配置的 `serviceUuid`。这样设备固件使用与配置不同的服务 UUID 时，订阅通知与写入命令都能定位到正确的特征。断连与 DFU 切换地址时通过 `clearWriteServiceUuid` 清理缓存。

## 8. DFU 模式连接（OTA 用）

OTA 升级时设备会切换 MAC 地址，需要特殊处理：

```
OtaViewModel.startDfu(firmwareFile)
  │
  ▼
BleConnectionManager.scanForDeviceWithMac(targetMac, timeoutMs=31000)
  │
  ├── 创建独立 Scanner 扫描指定 MAC
  ├── withTimeoutOrNull(31000) 超时
  │
  ├── 找到设备 → Peripheral(advertisement)
  │     └── onServicesDiscovered: requestMtu(247)
  │
  └── notifyDfuReconnect(oldAddress, newPeripheral)
        ├── 迁移设备状态: oldAddress → newAddress
        └── _dfuState = Reconnected(newAddress, peripheral)
```

## 9. 线程模型

| 层级 | 线程/协程 | 说明 |
|------|----------|------|
| BLE Observe | `Dispatchers.IO` 单协程 | `onEach{}.launchIn(scope)` 保证串行 |
| FrameParser | 同步 (协程内) | 逐字节状态机，无并发问题 |
| RpcCore.process() | Mutex 保护 | 同一协程内，Mutex 冗余但安全 |
| ghFrameFlow 发射 | `tryEmit` 非阻塞 | SharedFlow(extraBufferCapacity=64) |
| DemoVM 收集 | 主线程/Main.immediate | collect Flow，更新 UI State |
| Consumer CSV 写入 | `Dispatchers.IO` 独立协程 | 每 mode 一个消费者 |