# Feature-OTA 模块流程文档

## 1. 模块概述

`feature-ota` 负责 OTA 固件升级，支持两套互不兼容的升级协议：

| 协议 | 设备 | 实现 |
|------|------|------|
| Goodix DFU | GR5xxx 系列 | `external/libdfu2` 的 `GR5xxxDfu2` |
| Nordic SMP | nRF54L15（MCUboot + Zephyr/NCS） | Nordic 官方 `no.nordicsemi.android:mcumgr-ble:2.8.0` |

进入页面后通过 GATT 服务 UUID 自动识别协议，不依赖设备名称。识别不到升级服务时仍可进入页面，但会提示“不支持”并禁止升级。

> 说明：`mcumgr-ble` 依赖 `minCompileSdk=37`，而本项目当前 `compileSdk=36`，因此选用 2.8.0（`minCompileSdk=36`，API 与 3.x 一致，仅包名为 `io.runtime.mcumgr`）。后续升级 compileSdk 后可平滑切换。

## 2. 核心组件

| 组件 | 文件 | 职责 |
|------|------|------|
| `OtaViewModel` | `OtaViewModel.kt` | 协议识别、按协议分派升级流程 |
| `OtaScreen` | `OtaScreen.kt` | 设备选择、协议区、SMP/Goodix 卡片、进度与日志 |
| `OtaEngine` | `engine/OtaEngine.kt` | Goodix DFU 引擎；同时提供 `detectProtocols()` |
| `NordicSmpEngine` | `engine/NordicSmpEngine.kt` | Nordic SMP 传输、升级、取消与连接交接 |
| `OtaProtocol` | `engine/OtaProtocol.kt` | 协议枚举与 `OtaProtocolDetector` 纯函数匹配 |
| `SmpZipPackage` | `engine/SmpZipPackage.kt` | 解析 Zephyr/NCS 固件 ZIP，生成 `ImageSet` |
| `libdfu2` | `external/libdfu2/` | Goodix DFU 库封装 |

## 3. 协议识别

```
OtaScreen 进入
  │
  ▼
loadAvailableDevices()
  └── detectProtocols(device)
        ├── BleConnectionManager.getRawChannel(address)   // 设备未连接则提示重新连接
        ├── channel.discoverServices()
        └── OtaProtocolDetector.detect(services)
              ├── Goodix: A6ED0401-… / A6ED0701-…
              └── Nordic SMP: 8D53DC1D-1DB7-4CD3-868B-8A527460AA84
                    且特征 DA2E7828-FBCE-4E01-AE9E-261174997C48 同时具备
                    Write Without Response(0x04) 与 Notify(0x10)
```

识别结果写入 `OtaUiState.supportedProtocols`：

- 空集合：红色提示“未发现升级服务（Goodix DFU / Nordic SMP），该设备不支持 OTA”，不绑定任何引擎。
- 单协议：直接选中。
- 多协议：默认 Goodix，页面提供 `FilterChip` 切换；切换时解绑另一协议并清空已选文件。

## 4. OtaUiState（关键字段）

```kotlin
data class OtaUiState(
    val supportedProtocols: Set<OtaProtocol>,
    val selectedProtocol: OtaProtocol?,
    val isDetectingProtocol: Boolean,

    val firmwareFile: FirmwareFileInfo,       // Goodix 固件
    val resourceFile: FirmwareFileInfo,       // Goodix 资源
    val smpPackage: SmpPackageSummary?,       // SMP 固件包（解析结果摘要）
    val smpMode: SmpUpgradeMode,              // 默认 TEST_AND_CONFIRM

    val otaState: OtaState,
    val progressPercent: Float,
    val statusText: String?,                  // 细阶段文案（SMP）
    val canCancelUpgrade: Boolean,            // SMP 仅上传阶段为 true
    val isUpgrading: Boolean,
    val logLines: List<String>,
)
```

SMP 镜像字节存放在 ViewModel 的 `smpImages: ImageSet`，不放 UI state，避免大对象参与重组。

## 5. Goodix DFU 流程

```
用户点击“开始升级”
  │
  ▼
OtaViewModel.prepareAndExecuteUpgrade()
  │
  ├── 1. 准备 (PREPARING)：读取并解析 DFU 包
  ├── 2. 连接/重连 (CONNECTING)
  │     ├── 芯片复位 → 设备断开 → 切换 bootloader
  │     ├── scanForDeviceWithMac(targetMac, timeoutMs=31000)
  │     └── notifyDfuReconnect(oldAddress, newPeripheral)
  ├── 3. 上传 (TRANSFERRING)：GR5xxxDfu2 分包发送，进度 0.0~1.0
  ├── 4. 校验/激活 (VERIFYING)
  └── 5. 完成/失败 (COMPLETED / ERROR / CANCELLED)
```

资源升级（`startResourceUpgrade`）复用同一流程，仅写入目标改为资源区，并区分内外置 Flash。

## 6. Nordic SMP 流程

### 6.1 升级步骤

```
用户选择 ZIP → SmpZipPackage.parse()
  │
  ▼
OtaViewModel.startSmpUpgrade()
  └── NordicSmpEngine.upgrade(context, address, name, images, mode, settings)
        ├── 1. 释放 Kable 连接
        │     ├── BleConnectionManager.disconnect(address)
        │     ├── 等待 devices[address].state != CONNECTED（5s 超时）
        │     └── delay(300ms) 等待 GATT 资源释放
        ├── 2. McuMgrBleTransport(context, BluetoothDevice) 独立连接 SMP 服务
        ├── 3. FirmwareUpgradeManager.start(ImageSet, Settings)
        │     ├── VALIDATE  → 校验设备镜像状态
        │     ├── UPLOAD    → 上传镜像（唯一允许取消的阶段）
        │     ├── TEST      → 测试启动新固件
        │     ├── RESET     → 设备重启
        │     └── CONFIRM   → 确认新固件
        └── 4. finally：取消残留会话 → transport.release()
              → connectionManager.reconnectInBackground(address, name)
```

关键约束：升级期间只允许 Nordic transport 操作该设备连接，不能与 Kable 同时使用。`KableBleConnection.close()` 只关闭通知通道、不会断开底层连接，因此交接必须调用 `connectionManager.disconnect(address)`。

### 6.2 升级模式

页面可选，默认 `TEST_AND_CONFIRM`：

| 模式 | 行为 |
|------|------|
| `TEST_AND_CONFIRM` | 上传 → 测试启动 → 确认（推荐） |
| `CONFIRM_ONLY` | 仅确认已上传镜像 |
| `TEST_ONLY` | 仅测试启动，不确认（重启后可能回滚） |
| `NONE` | 上传后直接重启 |

`Settings` 使用默认值（`estimatedSwapTime=0`、`windowCapacity=1`、`memoryAlignment=1`、`eraseAppSettings=false`）。

### 6.3 固件包格式

首版仅支持 Zephyr/NCS 构建产物 ZIP：

- 必须包含 `manifest.json`，其中 `files` 数组声明镜像；
- 仅上传 `type` 为 `application` / `bin` 的条目；
- `image_index` 缺省 0，`slot` 缺省 `SLOT_SECONDARY`；
- 拒绝目录条目、绝对路径、`../` 路径穿越和 `:` 路径；
- 明确拒绝 `suit-envelope` / `.suit`（提示使用 MCUboot ZIP）；
- 原始 `.bin` 单文件首版不支持。

`TargetImage` 构造时会解析 MCUboot 镜像头与 TLV hash，非法镜像在解析阶段即报错。

## 7. 取消与错误处理

| 场景 | 处理方式 |
|------|----------|
| 读不到 GATT 服务 | 提示“无法读取设备服务，请重新连接” |
| 未发现升级服务 | 页面提示“该设备不支持 OTA”，禁用升级 |
| Goodix 固件无效 | 校验失败，停止升级 |
| Goodix 扫描超时 | 提示“未找到 DFU 设备，请手动重置” |
| SMP 未选择/解析失败 ZIP | 展示解析错误，禁用“开始升级” |
| SMP `VALIDATE` 失败 | 提示“镜像校验失败” |
| SMP `UPLOAD` 中断 | 提示“镜像上传中断”，允许重新升级 |
| SMP `TEST` 失败 | 提示“新固件测试启动失败，设备可能已回滚” |
| SMP `RESET` 失败 | 提示“设备重启或重连失败” |
| SMP `CONFIRM` 失败 | 提示“固件已上传但确认失败” |
| SMP 非上传阶段 | 隐藏取消按钮，避免误以为可安全回滚 |

`canCancelUpgrade` 仅在上传阶段为 true；进度弹窗的取消按钮与 `BackHandler` 都受它控制。`TEST`/`RESET`/`CONFIRM` 阶段只展示进度与日志。

## 8. 线程模型

| 操作 | 线程 | 说明 |
|------|------|------|
| 协议识别 | `Dispatchers.IO` | `discoverServices()` |
| Goodix 扫描/上传 | `Dispatchers.IO` | `scanForDeviceWithMac` / `GR5xxxDfu2` |
| SMP 升级 | `Dispatchers.IO` | `McuMgrBleTransport` 回调驱动 |
| 进度更新 | Main 线程 | `StateFlow` UI 更新 |
| 日志 | Main 线程追加 | `logEvents` 汇入 `logLines` |
