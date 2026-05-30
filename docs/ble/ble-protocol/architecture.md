# BLE 协议层架构文档

## 1. 概述

BLE 协议层负责将 BLE 传输的原始字节流解析为结构化数据，支持命令/响应模式的 RPC 调用和 G 协议实时数据流解码。

本模块参照 Rust 版 `gh-rpc` + `rpc` 双库架构设计，分为 **三层**：

```
┌─────────────────────────────────────────────────┐
│               Application Layer                  │
│     ViewModel (ConnectionViewModel / DemoVM)     │
├─────────────────────────────────────────────────┤
│             gh-rpc 层 (Gh3036Executor)            │
│  - 芯片级命令封装 (Command/Response)              │
│  - G 协议帧解码 (Gh3036FrameDecoder)              │
│  - FrameCallback 回调 (GhFuncFrame)              │
├─────────────────────────────────────────────────┤
│              rpc 层 (RpcCore)                     │
│  - 帧解析/构建 (FrameParser / FrameBuilder)       │
│  - 命令路由与分发 (register / staticHandlers)      │
│  - 安全帧/非安全帧处理                             │
│  - 多帧拼接 (MultiFrameBuffer)                    │
│  - 超时重试 (PendingCall)                         │
│  - 发送函数绑定 (SendFunction)                     │
├─────────────────────────────────────────────────┤
│           Transport 层 (BLE)                      │
│  - BLE Notify Characteristic (RX)                │
│  - BLE Write Characteristic (TX)                 │
└─────────────────────────────────────────────────┘
```

## 2. 模块组成

### 2.1 `ble-protocol` - 协议核心模块

```
ble/ble-protocol/src/main/java/com/ghealth/tools/ble/protocol/
├── rpccore/                          # rpc 层：通用 RPC 帧协议
│   ├── ProtocolTypes.kt              # 常量、ParseState、TypeKey、ParseResult
│   ├── ProtocolError.kt              # 协议错误密封类 (14 种)
│   ├── FrameParser.kt                # 帧解析器（7 状态状态机）
│   ├── FrameBuilder.kt               # 帧构建器（编码+CRC）
│   ├── RpcParser.kt                  # RpcParser 接口 (encode/decode/reset)
│   ├── ChipFrameDecoder.kt           # 芯片帧解码器泛型接口
│   ├── Package.kt                    # 数据打包/解包 (TypeHeader, FormatInfo)
│   └── DataUnpacker.kt              # 通用数据解包器 (UnpackValue)
│
└── gh3036/                           # gh-rpc 层：GH3036 芯片协议
    ├── Gh3036Types.kt               # GhFuncFrame, PackHeader, GhFuncId
    ├── Gh3036Commands.kt            # 20 个 Command + 6 个 Response + 格式常量
    ├── Gh3036RpcParser.kt           # RpcParser 接口的 GH3036 实现（轻量，仅帧解析）
    ├── Gh3036FrameDecoder.kt        # G 协议帧解码器 (Varint/ZigZag/Delta)
    ├── Gh3036CommandMeta.kt         # 命令元数据 (UI 展示)
    └── Gh3036Executor.kt            # 命令执行器 (call/send/sall/publish/process)
```

### 2.2 `ble-connection` - BLE 连接模块

```
ble/ble-connection/src/main/java/com/ghealth/tools/ble/connection/
├── GHealthConnectionManager.kt       # BLE 连接管理器（中心调度）
├── ConnectedDevice.kt               # 设备数据模型
└── di/BleModule.kt                  # Hilt DI 模块
```

### 2.3 `ble-scanner` - BLE 扫描模块

```
ble/ble-scanner/src/main/java/com/ghealth/tools/ble/scanner/
├── BleScanner.kt                     # BLE 统一扫描器 (基于 Kable)
```

## 3. 核心类型定义

### 3.1 帧格式

```
+-------+-------+--------+---------+--------+----------+--------+------+
|Header |Length |TypeKey |KeyData  |InvokeIdx|FrameIdx |Param   |CRC   |
|0xAA,11|1 byte |1 byte  |N bytes  |1 byte  |1 byte   |N bytes |1 byte|
+-------+-------+--------+---------+--------+----------+--------+------+
```

- **Header**: 固定 `0xAA 0x11`
- **Length**: 帧总长度（不含 CRC），最大 240 字节
- **TypeKey**: 参数类型 + 安全帧/非安全帧标志 + 分片标志
- **KeyData**: 命令键名（如 "G", "GH3X_GetVersion"）
- **InvokeIdx**: 调用索引（仅 `call` / `send` / `sall` 模式包含）
- **FrameIdx**: 分片帧索引（多帧传输时使用，255=LAST_FRAME_FIX_INDEX 表示单帧）
- **Param**: 参数数据
- **CRC**: 累加和校验（Header 起至 Param 尾）

### 3.2 ParseResult

帧解析结果，由 `FrameParser.process()` 产生：

```kotlin
data class ParseResult(
    val key: String,        // 命令键
    val param: ByteArray,   // 参数数据
    val isSecure: Boolean,  // 是否安全帧
    val isFin: Boolean,     // 是否最后一帧
    val invokeIdx: Byte,    // 调用索引
    val frameIdx: Byte      // 帧索引
)
```

### 3.3 TypeKey 位域

```
Bit:  7      6      5-3     2        1-0
    [fin] [secure] [width] [is_array] [pack_type]
```

| 位域 | 含义 |
|------|------|
| `[1:0] pack_type` | 打包类型：00=DOUBLE, 01=UNSIGNED, 10=SIGNED, 11=PACK |
| `[2] is_array` | 是否数组 |
| `[5:3] width` | 宽度(log2)：000=1byte, 001=2byte, 010=4byte, 011=8byte |
| `[6] secure` | 是否安全帧 |
| `[7] fin` | 是否最后一帧 |

### 3.4 GhFuncFrame

G 协议功能帧，由 `Gh3036FrameDecoder.decode()` 产生：

```kotlin
data class GhFuncFrame(
    val frameCnt: Int,              // 帧计数
    val timestamp: Long,            // 时间戳
    val funcId: GhFuncId,           // 功能类型 (ADT/HR/SPO2/...)
    val chNum: Int,                 // 通道数
    val gsensorEn: Int,             // G 传感器使能
    val fifoEndFlag: Int,           // FIFO 结束标志
    val rawdata: IntArray,          // 原始数据 (最多 32 通道)
    val phyValue: IntArray,         // 物理值
    val gsData: IntArray,           // G 传感器数据 (3 轴加速度)
    val flags: IntArray,            // 数据标志
    val algoData: IntArray,         // 算法数据 (最多 32 项)
    val agcInfo: LongArray,         // AGC 信息
    val slotCfg: Int                // 时隙配置
)
```

### 3.5 命令键常量

| 常量名 | 值 | 描述 |
|--------|-----|------|
| `KEY_G` | `"G"` | G 协议实时数据帧 |
| `KEY_F` | `"F"` | F 命令（FIFO 数据） |
| `KEY_FW` | `"FW"` | 固件升级 |
| `KEY_GH3X_GET_VERSION` | `"GH3X_GetVersion"` | 获取版本 |
| `KEY_GH3X_REGS_READ_CMD` | `"GH3X_RegsReadCmd"` | 寄存器读取 |
| `KEY_GH3X_REGS_WRITE_CMD` | `"GH3X_RegsWriteCmd"` | 寄存器写入 |
| `KEY_GH3X_CHIP_CTRL` | `"GH3X_ChipCtrl"` | 芯片控制 (复位/唤醒/休眠) |
| `KEY_GH_SET_WORK_MODE_CMD` | `"GHSetWorkModeCmd"` | 设置工作模式 |
| `KEY_GH3X_SW_FUNCTION_CMD` | `"GH3X_SwFunctionCmd"` | 切换功能模式 |
| `KEY_EVENT` | `"Event"` | 事件上报 |
| `KEY_F_GET_MODE` | `"F_GetMode"` | 获取工厂模式 |
| `KEY_F_SET_MODE` | `"F_SetMode"` | 设置工厂模式 |
| ... | ... | 共 20 个 |

### 3.6 RPC 通信模式

| 模式 | 方法 | 帧类型 | 等待响应 | 说明 |
|------|------|--------|----------|------|
| **publish** | `executor.publish()` | 非安全帧 | 否 | 单向数据推送 |
| **send** | `executor.send()` | 安全帧 | 是 (ACK) | 发送并等待设备 ACK 确认 |
| **call** | `executor.call()` | 非安全帧 | 是 (Response) | 同步调用，等待响应数据 |
| **sall** | `executor.sall()` | 安全帧 | 是 (Response) | 安全同步调用 |

## 4. 调用流程

### 4.1 数据接收流程 (RX)

```
BLE Notify Characteristic
    │
    ▼
GHealthConnectionManager.onDataReceived(address, data)
    │
    ▼
Gh3036Executor.process(data)
    │
    ├─ FrameParser.process(data)
    │   ├─ 状态机逐字节解析
    │   ├─ CRC 校验
    │   └─ 返回 List<ParseResult>
    │
    ├─ 对每个 ParseResult:
    │   ├─ isSecure == true  → handleSecureFrame()
    │   │   ├─ 查找 staticHandlers (已注册的命令处理器)
    │   │   │   └─ 找到: 调用 handler(data, size, context)
    │   │   │         └─ handler 设置 context.setResponse()
    │   │   │              └─ 通过 sendFunction 发回响应帧
    │   │   └─ 未找到: 查找 pendingCalls (等待中的调用)
    │   │       └─ 根据 msgType: 0=ACK, 1=响应, 2/3=错误
    │   │
    │   └─ isSecure == false → handleUnsecureFrame()
    │       ├─ MultiFrameBuffer.addFrame() — 多帧拼接
    │       ├─ 查找 staticHandlers
    │       │   └─ 找到: 调用 handler + setResponse() → publish
    │       └─ 未找到: 查找 pendingCalls → 发送响应
    │
    └─ 特定处理器:
        ├─ "G" handler (自动注册):
        │   ├─ unpackU8Array(data) — 数据解包
        │   ├─ Gh3036FrameDecoder.decode() — Varint/ZigZag/Delta 解码
        │   └─ frameCallback?.invoke(GhFuncFrame) — 回调到应用层
        │
        └─ 其他命令 handler (按需注册):
            └─ 命令特定处理逻辑
```

### 4.2 命令发送流程 (TX)

```
ViewModel.sendCommand() / executeCommand()
    │
    ▼
Gh3036Executor.call(key, format, params)
    │
    ├─ Package.pack(format, params) — 参数打包
    │
    ├─ FrameBuilder.build(key, data, secure=false, invokeIdx)
    │   ├─ 计算最大载荷 (MAX_FRAME_SIZE - header)
    │   ├─ 自动分片 (>最大载荷时)
    │   └─ 添加帧头 + CRC
    │
    ├─ PendingCall 注册到 pendingCalls
    │
    ├─ sendFunction.invoke(frames) — 通过 BLE 发送
    │   └─ GHealthConnectionManager.writeToDevice(frame)
    │       └─ Peripheral.write(writeChar, frame, WriteType.WithResponse)
    │
    └─ PendingCall.waitForResponse(timeoutMs) — 等待响应
        ├─ onResponse() 由 process() 中匹配 pendingCalls 触发
        ├─ 超时 → ProtocolError.Timeout
        └─ 成功 → 返回响应 ByteArray
```

### 4.3 安全帧/非安全帧处理差异

| 特性 | 非安全帧 (call/publish) | 安全帧 (send/sall) |
|------|------------------------|---------------------|
| TypeKey.secure 位 | 0 | 1 |
| invokeIdx | 包含 | 包含 |
| 响应路径 | pendingCalls 匹配 key | pendingCalls 匹配 key |
| 多帧拼接 | MultiFrameBuffer (isFin 判断) | 无（安全帧按 invokeIdx 匹配） |
| handler 触发 | isFin == true 时 | 立即触发 |
| ACK 机制 | 无 | msgType=0 为 ACK |

## 5. Rust 与 Kotlin 架构对照

| Rust (`rpc` + `gh-rpc`) | Kotlin (`ble-protocol`) | 说明 |
|--------------------------|------------------------|------|
| `RpcCore` | `Gh3036Executor` (内嵌 RPC 核心) | 需拆分为独立的 `RpcCore` |
| `RpcConfig` | `RpcConfig` (已定义) | ✅ 一致 |
| `CommandExecutor` | `Gh3036Executor` (内嵌命令执行) | 需拆分为独立的 `CommandExecutor` |
| `FrameParser` | `FrameParser` | ✅ 一致 |
| `FrameBuilder` | `FrameBuilder` | ✅ 一致 |
| `Package` / `Unpackage` | `Package.kt` | ✅ 一致 |
| `DataUnpacker` | `DataUnpacker.kt` | ✅ 一致 |
| `RpcError` | `ProtocolError` | ✅ 一致 |
| `InvokeContext` | `InvokeContext` | ✅ 一致 |
| `PendingCall` | `PendingCall` (内嵌类) | ✅ 一致 |
| `MultiFrameBuffer` | `MultiFrameBuffer` (内嵌类) | ✅ 一致 |
| `SendFunction` | `SendFunction` (typealias) | ✅ 一致 |
| `RpcHandler` | `(ByteArray, Int, InvokeContext) -> Unit` | ✅ 一致 |
| `FrameDecoder` | `Gh3036FrameDecoder` | ✅ 一致 |
| `FrameCallback` | `FrameCallback` (typealias) | ✅ 一致 |
| `Command` enum | `Command` sealed class | ✅ 一致 |
| `Response` enum | `Response` sealed class | ✅ 一致 |
| `set_send_function()` | `setSendFunction()` | ✅ 一致 |
| `process()` | `process()` | ✅ 一致 |
| `call() / send() / sall() / publish()` | `call() / send() / sall() / publish()` | ✅ 一致 |
| `register()` | `register()` | ✅ 一致 |
| `register_g_handler()` | `registerGHandler()` | ✅ 一致 |
| `handle_frame_data()` | 内嵌于 `handleGData()` | ✅ 一致 |
| `register_frame_callback()` | `registerFrameCallback()` | ✅ 一致 |

## 6. 关键常量

| 常量 | 值 | 说明 |
|------|-----|------|
| `FRAME_HEADER` | `[0xAA, 0x11]` | 帧头标识 |
| `MAX_FRAME_SIZE` | `240` | 单帧最大字节数 |
| `MAX_KEY_SIZE` | `32` | 键名最大长度 |
| `DEFAULT_TIMEOUT_MS` | `3000` | 超时时间 (毫秒) |
| `MAX_RETRY_COUNT` | `3` | 最大重试次数 |
| `LAST_FRAME_FIX_INDEX` | `255` | 单帧标识 (FrameIdx=255 表示非分片) |