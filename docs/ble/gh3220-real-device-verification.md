# GH3220 真机验证与抓包裁决验收清单

> 状态：待硬件就绪后执行（当前仅产出清单；真机验证与抓包裁决需 GH3x2x EVK 设备与抓包工具）
> 范围：GH3220 ITLVC 三阶段计划 Phase 5 验收项
> 依据：`.claude/gh3220_protocol/gh3220 protocol.md`（§3.5 / §3.7 / §3.9 / §3.11 / §3.35 / §4.3.5）、设备端源码 `.claude/gh3220_protocol/c_to_mcu/demo_kernel_code/`、已交付实现 `ble/ble-gh3220/`

## 背景与目的

`ble-gh3220` 已交付 0x08/0x09/0x0A/0x0B/0x2A rawdata 解码与 0x0F 固件升级流程，依据协议文档与设备端 C 源码交叉实现。文档与 C 端源码在以下 5 处存在解释分歧，当前实现按文档优先或计划约定取值；5 处分歧均在已交付代码 KDoc 或本清单中标注为待真机确认。本清单给出逐项抓包裁决步骤，供硬件就绪后执行：

- 裁决通过 → 更新 KDoc 标注为实测格式，必要时固化真实抓包 golden 向量；
- 裁决失败 → 按实测字节修正解码并补 golden 向量。

## 差异裁决清单（5 项）

| # | 差异点 | 实现侧假设 | 文档/源码依据 | 真机裁决方法 | 涉及代码 |
| --- | --- | --- | --- | --- | --- |
| 1 | 0x0B 包头 | 7B：`[dataType][chMask 4B BE][flag][len]` | 文档 §3.7.2 7B；`gh_uprotocol.c` 8B（FunctionID 独立字节） | 抓 0x0B 首包，比对第 2 字节是否为 FunctionID | `RawDataDecoder.decode0B` |
| 2 | 0x0B chMask | 大端位掩码 | C 端大端掩码；文档 §3.7.4 seq/chnlCnt | 比对多通道上报的通道掩码 | `RawDataDecoder.decode0B`（`be32`/`activeChannelIndices`）、`Gh3220RawDataTypes.Gh3220RawDataPackage.channelMask` |
| 3 | 0x09/0x0A 偶数包首帧 | 从 0 差分（类型 14） | 文档 §3.5/§3.7.7 首帧=绝对值（编码未定义）；`gh_zip.c` 写 4B/通道原始绝对值 | 抓偶数包首帧，比对差值字段 | `RawDataDecoder.decode09/decode0A`、`DiffDecoder`；测试 `RawDataDecoderZipTest` |
| 4 | 0x2A len | 4B 小端 | 文档 4B 小端；C 端 `[len 1B][idChangeFlag 1B]` | 抓 0x2A 上报，比对长度字段宽度 | `RawDataDecoder.decode2A`；测试 `RawDataDecoder2ATest` |
| 5 | 0x0F 分包 / 0x0D 字节序 | blockSize 分块 + 块内 ≤56B；Total Len=块字节数；0x0D 小端 | 计划假设；文档 §3.11 未标注 | 真机升级 + 电流电池上报比对 | `FwUpgradeFlow`；`ReportDecoder.decodeCurrentBattery`（`Gh3220ReportModels.kt`）；测试 `FwUpgradeFlowTest`、`EventAckHandlerTest` |

**裁决要点说明（补充，不改变上表含义）：**

- 第 1 项：C 端 8B 包头为 `[FunctionID][dataType][mask 4B][flag][len]`（`gh_uprotocol.c` `Gh2x2xPackPakcageHeader()`，`UPROTOCOL_FUNCTION_ID_INDEX=0` 独立 1B），文档/实现 7B 包头为 `[dataType][chMask 4B BE][flag][len]`（FunctionID 并入 dataType 高 nibble）。实际判别以 FrameData 起始偏移（8 或 7）及第 2 字节（payload[1]）语义为准：若为独立 FunctionID/dataType 位型字段而非通道掩码/计数起始字节 → C 端 8B 格式。
- 第 2 项：C 端按 `unCompeletMask |= 1 << unCnt` 组 32bit 大端位掩码（`00 00 00 03` 表示通道 0/1）；文档 §3.7.4 为 `[seq 1B][chnlCnt 1B][预留 2B]`（如 `00 02 00 00`）。多通道连续抓包可同时观察 seq 是否递增。
- 第 3 项：`gh_zip.c` 偶数包首帧（`uchEvenFirstFrameFlag` 分支）直接写 4B/通道大端原始绝对值、无 rawLen/tagFlag 前缀；文档 §3.5/§3.7.7 描述为压缩算法编码的绝对值，当前实现以从 0 差分（类型 14 = 32bit 正差分）解码。类型 14/15 是按文档压缩表 0–13 规律外推的 32bit 差分码（不在文档表中），需在真机上确认。
- 第 4 项：`gh_drv_control.c` 打包 `[fifoId 1B][len 1B][idChangeFlag 1B][00 00][data...]`（数据偏移 5）；文档 §3.35 为 `[fifoId 1B][len 4B][data]`，当前实现按 4B 小端解析，C 格式下会因 len overflow 拒绝，抓包可直接分辨。
- 第 5 项：`FwUpgradeFlow` 按 blockSize 分块、块内 ≤56B 分包，`Total Len`=当前块字节数、`Current Index`=块内字节偏移，多字节字段小端；`ReportDecoder.decodeCurrentBattery` 对 0x0D 按 u16le 解析（CardiffCurrent/TxCurrent/BleSendPackageCnt）。C demo 无 0x0F 传输实现可对照，文档 §3.11/§3.9 未标注字节序。注意文档 §3.9 字段列名 `Cardiff电流_H|_L`、`BleSendPackageCnt_H|_L` 按惯例暗示高字节在前：真机比对时需判别字节到达顺序是 H-first（按文档列名命名）还是 L-first（按当前实现 u16le）。
- 备注：`RawDataDecoder.decode0B` KDoc 另列 flag 位位置（bit2/bit5）、AGC 3B/4B、多功能 FifoID 等次要偏差，不在本清单 5 项内，可在同一抓包会话顺带记录供后续复核。

## 抓包前置准备

### 硬件与固件

- GH3x2x EVK 设备 1 台，烧录与协议文档一致的 demo 固件；先通过 `0x19` 或 0x0F 0x01 确认固件版本。
- Android 手机（BLE 5.0 及以上）：安装调试 APK（`./gradlew installDebug`），或使用 Nordic nRF Connect 直接订阅 RX 特征 Notify 观察原始字节。

### 抓包工具（三选一或并用）

1. BLE Sniffer（如 Nordic nRF Sniffer + Wireshark、TI CC254x 方案）：抓空中包，过滤 ATT `Handle Value Notification`。
2. Android 侧直读：nRF Connect 连接 EVK，开启 RX 特征 Notify，记录通知 hex 数据（适合快速比对 0x0B/0x0D/0x2A 单包）。
3. 厂商抓包工具：若厂商提供固件侧抓包/日志导出，优先与空中包交叉验证。

### 帧剥离与数据对照

- ITLVC 帧格式 `AA 11 [T][L][V...][CRC8]`，payload = 帧中偏移 4 起的 L 字节；一次 Notify 可能多帧或跨多次 Notify 分片，按 L 字段切帧。帧结构与 CRC8 参考向量见 `docs/ble/gh3220-command-matrix.md`。
- 透传模式（文档 §4.3.5）下 `0x2A` 属白名单命令（`0x19/0x1A/0x1E/0x21/0x2A`）；抓包前确认设备是否处于透传模式，避免模式差异导致误判。
- 设备侧 LOG/CSV 对照：`./scripts/pull_debug_data.sh`（无参=当天、日期参数=指定日期、`-a`=全部、`-d 设备`=指定设备；同时拉取 `dumpsys dropbox` 崩溃日志），用于与抓包解析值比对。

### 复现用例

| 用例 | 操作 | 采集目标 |
| --- | --- | --- |
| 0x0B 单功能 | `0x15` 通道映射 + `0x1B` 采样率 + `0x0C` 启动 HBD，连续抓 5–10 组包 | 包头长度（7B/8B）、第 2 字节语义、chMask 4B |
| 0x0B 多通道 | 配置 2 通道（如 ECG 双通道）重复上例 | 多通道掩码 vs seq/chnlCnt 格式 |
| 0x09/0x0A | 启动采样持续上报，抓完整偶数/奇数包对 | 偶数包首帧编码形态（差分类型 14 vs 4B 原始绝对值） |
| 0x2A | 触发 FIFO 上报（满 200B/块） | 长度字段宽度与 idChangeFlag 位置 |
| 0x0D | 连续运行 ≥1s，多采几组 | u16 字段字节序（重点看 BleSendPackageCnt） |
| 0x0F | 使用真实固件 bin 完整升级一次，另测跨块边界与末块 | 分包回显 Total Len/Current Index/Payload Len |

每个用例保存：抓包文件（pcap/hex 日志）、设备侧 LOG/CSV、配置摘要（固件版本、通道数、采样率、日期、是否透传模式）。

## 裁决通过/失败处置

### 裁决通过（抓包与当前实现一致）

1. 更新 KDoc 标注：删除/改写「真机抓包未验证」，注明实测格式、验证日期与样本位置。
2. 将代表性抓包字节固化为 golden 向量（增强回归），补充到对应测试。
3. 同步更新 `docs/ble/gh3220-command-matrix.md` 备注（如 0x0B 包头实测 7B、0x2A 长度字段实测宽度）。

### 裁决失败（与当前实现不一致）

1. 按实测字节修正解码：`RawDataDecoder`（0x0B 包头/chMask、0x09/0x0A 首帧、0x2A len）、`FwUpgradeFlow`（0x0F 分包与字节序）、`ReportDecoder`（0x0D）。
2. 将实测包固化为 golden 向量，补充到 `RawDataDecoder0BTest` / `RawDataDecoderZipTest` / `RawDataDecoder2ATest` / `FwUpgradeFlowTest` / `EventAckHandlerTest`。
3. 同步更新 KDoc 标注、协议差异说明与命令矩阵。
4. 验证：`./gradlew :ble:ble-gh3220:test` 通过后运行 `./gradlew test`，再按仓库约定提交（Conventional Commits，如 `fix(ble-gh3220): ...`）。

### 裁决结果记录（执行时填写）

| # | 差异点 | 裁决日期 | 实测结论 | 样本位置 | 处置 |
| --- | --- | --- | --- | --- | --- |
| 1 | 0x0B 包头 | | | | |
| 2 | 0x0B chMask | | | | |
| 3 | 0x09/0x0A 偶数包首帧 | | | | |
| 4 | 0x2A len | | | | |
| 5 | 0x0F 分包 / 0x0D 字节序 | | | | |

## 阻塞说明与完成定义

- **阻塞项**：需要 GH3x2x EVK 设备、BLE 抓包工具（或厂商抓包通道）及与协议文档一致的 demo 固件；当前环境无硬件，本任务仅产出验收清单，真机验证与抓包裁决待硬件就绪后执行。
- **完成定义**：5 项差异全部裁决完成，KDoc 标注更新为实测结论，失败项已修正并补充 golden 向量，`docs/ble/gh3220-command-matrix.md` 与协议差异说明同步更新。
