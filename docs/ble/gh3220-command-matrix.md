# GH3220 命令矩阵

> 依据 `.claude/gh3220_protocol/gh3220 protocol.md`（V1.1）与设备端源码交叉整理。
> 方向：H→D 上位机→下位机；D→H 下位机→上位机。

| ID | 名称 | 方向 | 实现方式 | 超时(ms) | 透传白名单 | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| 0x00 | NOP | H→D | spec 已注册 | 1000 | - | 门面未暴露 |
| 0x01 | 操作响应 | D→H | （响应帧通用处理） | - | - | |
| 0x02 | 状态查询 | - | （未实现） | - | - | spec 未注册 |
| 0x03 | 读写寄存器 | H→D / D→H | 类型化 `RegisterCommands` | 1000 | - | |
| 0x04 | 阻抗测试 | - | raw passthrough | - | - | 文档标注"略" |
| 0x05 | 通讯包测试 | H→D / D→H | 类型化 `BasicCommands.packageTest` | 1000 | - | 回显 |
| 0x07 | 读 OTP | H→D / D→H | raw passthrough | - | - | 无格式说明 |
| 0x08 | Rawdata | D→H | 解码 `RawDataDecoder.decode08` | - | - | 上报 |
| 0x09 | Rawdata 压缩（偶） | D→H | 解码 `decode09` | - | - | 上报 |
| 0x0A | Rawdata 压缩（奇） | D→H | 解码 `decode0A` | - | - | 上报 |
| 0x0B | 新 Rawdata | D→H | 解码 `decode0B` | - | - | 上报，多通道分包 |
| 0x0C | 启动 HBD | H→D / D→H | 类型化 `BasicCommands.startHbd` | 1000 | - | |
| 0x0D | 电流电池 | D→H | 解码 `ReportDecoder.decodeCurrentBattery` | - | - | 上报 |
| 0x0E | ECG 电压 | D→H | raw passthrough | - | - | 文档标注"略" |
| 0x0F | 固件升级 | H→D / D→H | 流程 `FwUpgradeFlow` | 3000 | - | 版本/组包/分包 |
| 0x10 | 工作模式 | H→D / D→H | 类型化 `ConfigCommands.workMode` | 1000 | - | |
| 0x11 | G-sensor | H→D / D→H | 类型化 `ConfigCommands.gsensorSet` | 1000 | - | |
| 0x12 | FIFO 阈值 | H→D / D→H | 类型化 `ConfigCommands.fifoThreshold` | 1000 | - | |
| 0x13 | 事件设置 | H→D / D→H | 类型化 `ConfigCommands.eventSet` | 1000 | - | |
| 0x14 | 设备事件 | D→H | 解码 `ReportDecoder.decodeDeviceEvent` | - | - | 上报 |
| 0x15 | 通道映射 | H→D / D→H | 类型化 `ConfigCommands.funcMap` | 1000 | - | 固定 64B |
| 0x16 | Cardiff 事件 | D→H | `EventAckHandler` | - | - | 上报 + 自动 ACK |
| 0x17 | Cardiff 复位 | H→D / D→H | 类型化 `BasicCommands.chipCtrl` | 1000 | - | |
| 0x18 | 电流校准 | H→D / D→H | 类型化 `BasicCommands.calibrateCurrent` | 1000 | - | |
| 0x19 | 获取版本 | H→D / D→H | 类型化 `BasicCommands.getVersion` | 1000 | ✅ | |
| 0x1A | 连接状态 | H→D / D→H | 类型化（空 payload） | 1000 | ✅ | |
| 0x1B | 采样率 | H→D / D→H | 类型化 `ConfigCommands.sampleRates` | 1000 | - | |
| 0x1C | 切换 SlotEn | H→D / D→H | 类型化 `ConfigCommands.slotEn` | 1000 | - | |
| 0x1D | ECG 控制 | H→D / D→H | 类型化 `ConfigCommands.ecgCtrl` | 1000 | - | |
| 0x1E | 工作模式设置 | H→D / D→H | 类型化 `ConfigCommands.workModeSet` | 1000 | ✅ | |
| 0x1F | 驱动配置下发 | H→D / D→H | 流程 `DriverConfigFlow` | 3000 | - | 单包 ≤230 |
| 0x20 | 应用模块 | H→D / D→H | 类型化 `BasicCommands.appModule` | 1000 | - | |
| 0x21 | 从机 Log | D→H | 解码 `ReportDecoder.decodeSlaveLog` | - | ✅ | 上报 |
| 0x22 | Lead 检测频率 | - | raw passthrough | - | - | 文档标注"略" |
| 0x23 | Dump 模式 | - | raw passthrough | - | - | 文档标注"略" |
| 0x24 | 软件调光 | - | raw passthrough | - | - | 文档标注"略" |
| 0x25 | 获取采样状态 | - | raw passthrough | - | - | 文档标注"略" |
| 0x26 | RTC 时间 | - | raw passthrough | - | - | 文档标注"略" |
| 0x2A | Rawdata FIFO | D→H | 解码 `RawDataDecoder.decode2A` | - | ✅ | 上报 |
| 0x2D | SPI Flash 测试 | - | raw passthrough | - | - | 文档标注"略" |
| 0x2E | 切换芯片 | H→D / D→H | 类型化 `BasicCommands.switchChip` | 1000 | - | |
| 0xA1 | 写寄存器数组 | H→D / D→H | 类型化 `RegisterCommands.regArrayWrite` | 1000 | - | |
| 0xA2 | Debug | H→D / D→H | raw passthrough | - | - | 无格式说明 |

## 透传模式（文档 §4.3.5）

- 白名单：`0x19 / 0x1A / 0x1E / 0x21 / 0x2A`，建模为 `CommandSpec.allowedInPassThrough`。
- `ItlvcConfig(passThroughMode = true)` 时，非白名单命令以 `CommandError.Unsupported` 拒绝且不写入传输。

## CRC8 参考向量

| 帧字节 | CRC8 |
| --- | --- |
| `AA 11 1A 00` | `AE` |
| `AA 11 19 01 01` | `EC` |
| `AA 11 05 04 DE AD BE EF` | `70` |
| `AA 11 03 04 00 01 00 00` | `27` |
| `AA 11 16 03 00 02 03` | `EB` |
