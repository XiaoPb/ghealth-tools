# BLE 协议层 API 参考

## 1. Gh3036Executor — 命令执行器 (核心 API)

`Gh3036Executor` 是协议层对外的核心入口，封装了 RPC 核心 + G 协议帧解码 + 命令执行。每个 MASTER/SLAVE 角色设备对应一个实例。

### 1.1 创建与配置

```kotlin
// 默认配置：timeout=3000ms, retry=3次, frameSize=240
val executor = Gh3036Executor()

// 自定义配置
val executor = Gh3036Executor(
    RpcConfig(
        timeoutMs = 5000,
        retryCount = 5,
        retryDelayMs = 1000,
        frameSize = 240
    )
)
```

### 1.2 核心方法

#### `setSendFunction(func: SendFunction)`

绑定数据发送函数到 BLE 传输层。必须在任何 `call()`/`send()`/`sall()` 之前调用。

```kotlin
executor.setSendFunction { data ->
    // 通过 BLE write characteristic 发送
    try {
        writeToDevice(data)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

**参数：**
- `func`: `(ByteArray) -> Result<Unit>` — 发送函数，返回 `Result.success(Unit)` 表示成功

---

#### `registerGHandler(): Result<Unit>`

注册 G 协议数据帧处理器。注册后，所有 key="G" 的非安全帧将自动解码为 `GhFuncFrame` 并通过 `frameCallback` 回调。

```kotlin
executor.registerGHandler()
```

必须在 `registerFrameCallback()` 之后调用以确保回调已设置。

---

#### `registerFrameCallback(callback: FrameCallback)`

注册 G 协议帧解码后的回调。每次 `Gh3036FrameDecoder` 成功解码出 `GhFuncFrame` 时触发。

```kotlin
executor.registerFrameCallback { frame ->
    // frame: GhFuncFrame
    // 处理实时数据：波形渲染、数据记录、算法结果展示等
    Log.d("GProtocol", "Frame: funcId=${frame.funcId}, frameCnt=${frame.frameCnt}")
}
```

---

#### `process(data: ByteArray): List<Result<ParseResult>>`

处理接收到的 BLE 数据。将原始字节流送入帧解析器，自动分派到已注册的处理器或等待中的调用。

**这是 `onDataReceived` 应该调用的方法。**

```kotlin
// 在 BLE notify callback 中
peripheral.observe(notifyChar).onEach { data ->
    val results = executor.process(data)
    results.forEach { result ->
        result.onSuccess { parsed ->
            Log.d("RPC", "Frame: key=${parsed.key}, param=${parsed.param.size} bytes")
        }
        result.onFailure { error ->
            Log.e("RPC", "Parse error: $error")
        }
    }
}
```

**参数：**
- `data: ByteArray` — BLE Notify 接收到的原始字节数组

**返回值：**
- `List<Result<ParseResult>>` — 解析结果列表，每个结果包含 key/param/isSecure 等信息

**内部流程：**
1. `FrameParser.process(data)` → 状态机解析 → `List<ParseResult>`
2. 对每个 `ParseResult`：
   - 安全帧 (`isSecure=true`) → `handleSecureFrame()` → staticHandlers 或 pendingCalls
   - 非安全帧 (`isSecure=false`) → `handleUnsecureFrame()` → MultiFrameBuffer → 组装完成后触发

---

#### `call(key: String, format: String, params: ByteArray): Result<ByteArray>`

同步调用命令（非安全帧），等待设备响应。

```kotlin
val result = executor.call(
    key = KEY_GH3X_GET_VERSION,
    format = FMT_GH3X_GET_VERSION,
    params = byteArrayOf(0x01)  // ver_type
)
result.onSuccess { response ->
    Log.i("CMD", "Version: ${response.joinToString(" ") { "%02X".format(it) }}")
}
result.onFailure { error ->
    Log.e("CMD", "Failed: $error")
}
```

**参数：**
- `key: String` — 命令键名（如 `"GH3X_GetVersion"`）
- `format: String` — 参数格式字符串（如 `"<u8>"`）
- `params: ByteArray` — 已打包的参数数据

**返回值：**
- `Result<ByteArray>` — 成功时返回设备的响应数据

**超时行为：** 默认 3000ms，超时返回 `Result.failure(ProtocolError.Timeout)`

---

#### `send(key: String, format: String, params: ByteArray): Result<Unit>`

发送命令（安全帧），等待设备 ACK 确认。不返回响应数据，仅确认发送成功。

```kotlin
val result = executor.send(
    key = KEY_GH3X_CHIP_CTRL,
    format = FMT_GH3X_CHIP_CTRL,
    params = byteArrayOf(0xC2)  // SOFT_RESET
)
```

**返回值：**
- `Result<Unit>` — ACK 收到则成功，超时则失败

---

#### `sall(key: String, format: String, params: ByteArray): Result<ByteArray>`

安全同步调用（安全帧），等待设备响应数据。

```kotlin
val result = executor.sall(
    key = KEY_GH3X_REGS_READ_CMD,
    format = FMT_GH3X_REGS_READ_CMD,
    params = packRegReadCmd(0x1000, 4)
)
```

**返回值：**
- `Result<ByteArray>` — 成功时返回设备的响应数据

---

#### `publish(key: String, params: ByteArray): Result<Unit>`

发布命令（非安全帧），不等待响应。用于单向数据推送。

```kotlin
executor.publish(
    key = KEY_EVENT,
    params = eventData
)
```

**返回值：**
- `Result<Unit>` — 发送成功则返回 success

---

#### `register(key: String, handler: (ByteArray, Int, InvokeContext) -> Unit): Result<Unit>`

注册自定义命令处理器。当收到匹配 key 的帧时调用 handler。

```kotlin
executor.register("MyCustomCmd") { data, size, context ->
    // 处理数据
    val result = processCustomData(data, size)
    // 设置响应（非安全帧会自动通过 publish 发回）
    context.setResponse(result)
}
```

**参数：**
- `key: String` — 命令键名
- `handler: (ByteArray, Int, InvokeContext) -> Unit` — 处理函数

---

#### `reset()`

重置执行器状态：清空帧解析器、已注册处理器、等待中的调用、多帧缓冲区。

```kotlin
executor.reset()
```

---

### 1.3 完整初始化示例

```kotlin
// 1. 创建执行器
val executor = Gh3036Executor(RpcConfig(timeoutMs = 5000))

// 2. 绑定 BLE 发送函数
executor.setSendFunction { data ->
    try {
        bleWrite(data)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

// 3. 注册 G 协议帧回调
executor.registerFrameCallback { frame ->
    onGhFuncFrame(frame)
}

// 4. 注册 G 协议处理器
executor.registerGHandler()

// 5. 处理接收数据
fun onBleDataReceived(data: ByteArray) {
    executor.process(data)
}

// 6. 发送命令
fun getVersion() {
    val result = executor.call(KEY_GH3X_GET_VERSION, FMT_GH3X_GET_VERSION, byteArrayOf(0x01))
    result.onSuccess { version -> handleVersion(version) }
}
```

---

## 2. 辅助类型

### 2.1 InvokeContext

调用上下文，在 handler 中使用：

```kotlin
class InvokeContext(val topic: String) {
    var isSecure: Boolean = false
    var isFin: Boolean = true
    var invokeIdx: Byte = 0
    var frameIdx: Byte = 0

    fun setResponse(data: ByteArray)
    fun getResponse(): ByteArray
}
```

### 2.2 RpcConfig

```kotlin
data class RpcConfig(
    val timeoutMs: Long = 3000,      // 超时时间 (毫秒)
    val retryCount: Byte = 3,        // 重试次数
    val retryDelayMs: Long = 3000,   // 重试延迟 (毫秒)
    val frameSize: Int = 240         // 帧大小
)
```

### 2.3 SendFunction

```kotlin
typealias SendFunction = (ByteArray) -> Result<Unit>
```

### 2.4 FrameCallback

```kotlin
typealias FrameCallback = (GhFuncFrame) -> Unit
```

---

## 3. Gh3036FrameDecoder — G 协议帧解码器

解码 Varint/ZigZag 编码的 G 协议实时数据流。

### 3.1 方法

#### `decode(data: ByteArray): List<GhFuncFrame>`

解码 G 协议原始字节数据。

```kotlin
val decoder = Gh3036FrameDecoder()
val frames = decoder.decode(rawGData)

frames.forEach { frame ->
    Log.d("G", "FuncId=${frame.funcId}, FrameCnt=${frame.frameCnt}")
    Log.d("G", "Rawdata: ${frame.rawdata.joinToString()}")
}
```

**编码特性：**
- **Varint 编码**: 变长整数编码，小数值占更少字节
- **ZigZag 编码**: 将有符号整数映射为无符号，配合 Varint 高效编码
- **Delta 压缩**: 相邻帧数据差值编码，减少数据量
- **断帧检测**: 帧序号不连续时自动重置解码器状态

#### `reset()`

重置解码器状态（清空通道缓冲、增量基准等）。

---

## 4. FrameParser — 帧解析器

底层状态机，逐字节解析二进制帧。

### 4.1 方法

#### `process(data: ByteArray): List<Result<ParseResult>>`

逐字节解析输入数据。

```kotlin
val parser = FrameParser()
val results = parser.process(rawBytes)
results.forEach { result ->
    result.onSuccess { parsed ->
        // parsed.key, parsed.param, parsed.isSecure, parsed.isFin, ...
    }
    result.onFailure { error ->
        // ProtocolError.CrcMismatch, ProtocolError.FormatError, ...
    }
}
```

#### `reset()`

重置解析器状态到 `FrameHeader`。

### 4.2 解析状态机

```
FrameHeader → CheckLength → CheckTypeKey → CheckKey → CheckIndex → CheckParam → CheckCrc
     ↑                                                                          │
     └──────────────────────── 完成后自动回到 ──────────────────────────────────┘
```

---

## 5. FrameBuilder — 帧构建器

按协议格式编码数据为二进制帧。

### 5.1 方法

#### `build(key, param, secure, invokeIdx): ByteArray`

构建单帧/多帧：

```kotlin
val builder = FrameBuilder()
val frame = builder.build(
    key = "GH3X_GetVersion",
    param = byteArrayOf(0x01),
    secure = false,
    invokeIdx = 1
)
```

**参数：**
- `key: String` — 命令键名
- `param: ByteArray` — 参数数据
- `secure: Boolean = false` — 是否安全帧
- `invokeIdx: Byte = 0` — 调用索引（0 表示 publish 模式，不含 invokeIdx/frameIdx）

**返回值：**
- `ByteArray` — 完整的帧数据（可能跨多帧），含帧头 + CRC

**自动处理：**
- 参数超过 `MAX_FRAME_SIZE - header` 时自动分片
- 每帧自动计算 CRC 累加和
- 安全帧自动添加 invokeIdx/frameIdx

---

## 6. Package / Unpackage — 数据打包与解包

### 6.1 Package (打包)

```kotlin
// 基本类型打包
Package.packU8(42)              // → ByteArray
Package.packU16(0x1234)         // → ByteArray (LE)
Package.packU32(0x12345678)     // → ByteArray (LE)
Package.packI16(-100)           // → ByteArray (LE)

// 数组打包
Package.packU8Array(byteArrayOf(1, 2, 3))   // → [len:U16] + [data]
Package.packU16Array(shortArrayOf(1, 2, 3))  // → [len:U16] + [data...]

// 格式打包
Package.pack("<u8><u16>", byteArrayOf(0x01, 0x34, 0x12))
```

### 6.2 Unpackage (解包)

```kotlin
Unpackage.unpackU8(data)        // → Result<Byte>
Unpackage.unpackU16(data)       // → Result<Short>
Unpackage.unpackU32(data)       // → Result<Int>
Unpackage.unpackU8Array(data)   // → Result<ByteArray>
Unpackage.unpackU16Array(data)   // → Result<ShortArray>
```

### 6.3 DataUnpacker (格式解包)

```kotlin
val unpacker = DataUnpacker()
val result = unpacker.unpack(data, "<u8><u16><u32>")
when (result) {
    is UnpackValue.U8 -> ...
    is UnpackValue.U16 -> ...
    is UnpackValue.U32 -> ...
    is UnpackValue.U8Array -> ...
}
```

---

## 7. ProtocolError — 协议错误

```kotlin
sealed class ProtocolError {
    object CrcMismatch      // CRC 校验失败
    object KeyOverMaxSize   // 键名超过最大长度 (32)
    object FormatError      // 格式字符串错误
    object FrameTooLarge    // 帧超过最大尺寸 (240)
    object ParamTooMuch     // 参数过多
    object UnpackageError   // 解包错误
    object Timeout          // 超时
    object ChannelClosed    // 通道关闭（SendFunction 未设置）
    object CommandNotFound  // 命令未找到
    object LoseFrame        // 丢帧
    object NotUnderInvoke   // 不在调用上下文中
}
```

---

## 8. Command / Response — 命令与响应

### 8.1 Command 密封类（20 个）

```kotlin
Command.Gh3xGetVersion(verType = 0x01)
Command.Gh3xChipCtrl(ctrlType = 0xC2)        // SOFT_RESET
Command.Gh3xRegsReadCmd(regAddr = 0x1000, readLen = 4)
Command.Gh3xRegsWriteCmd(regs = intArrayOf(0x1000, 0x1234))
Command.GhSetWorkModeCmd(workMode = 0x01)
Command.Gh3xSwFunctionCmd(targetFuncMode = 1, ctrlType = 0)
Command.FGetMode(testMode = 0)
Command.FSetMode(testMode = 1)
Command.GhTimeSet(ts = 1234567890, hourOffset = 8)
Command.GhTimestampSet(ts = 1234567890)
// ... 等 20 个
```

每个 Command 都包含 `key: String` 和 `format: String` 属性。

### 8.2 Response 密封类（6 个）

```kotlin
Response.Gh3xGetVersion(data: ByteArray)
Response.Gh3xRegsReadCmd(data: IntArray)
Response.Fw(data: ByteArray)
Response.GetChipLinkStatus(data: ByteArray)
Response.FGetMode(data: IntArray)
Response.Empty
```

---

## 9. 典型使用场景

### 场景 1：G 协议实时数据监控

```kotlin
val executor = Gh3036Executor()

executor.setSendFunction { data -> writeBle(data) }
executor.registerFrameCallback { frame ->
    updateWaveform(frame.rawdata)
    updateAlgorithmResults(frame.algoData)
    recordCsv(frame)
}
executor.registerGHandler()

fun onBleNotify(data: ByteArray) {
    executor.process(data)  // G 帧自动解码并通过 callback 回调
}
```

### 场景 2：获取芯片版本

```kotlin
suspend fun getChipVersion(): String {
    val result = executor.call(KEY_GH3X_GET_VERSION, FMT_GH3X_GET_VERSION, byteArrayOf(0x01))
    return result.fold(
        onSuccess = { bytes -> bytes.joinToString(" ") { "%02X".format(it) } },
        onFailure = { "Error: ${it.message}" }
    )
}
```

### 场景 3：寄存器读写

```kotlin
suspend fun readRegister(addr: Int, len: Int): IntArray {
    val param = Package.packU16(addr.toShort()) + Package.packI32(len)
    val result = executor.call(KEY_GH3X_REGS_READ_CMD, FMT_GH3X_REGS_READ_CMD, param)
    return result.fold(
        onSuccess = { Unpackage.unpackU16Array(it).getOrDefault(shortArrayOf()).toIntArray() },
        onFailure = { intArrayOf() }
    )
}
```