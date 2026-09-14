# GH3036 G 帧解析与 AGC 稳定判断修复设计

## 背景

GH3036 C 编码器只对 `rawdata`、`phy_value`、`gs_data` 和 `timestamp` 做差分编码。`flags`、`algo_data` 和 `agc_info` 在发生变化时发送当前绝对值，未发送时由接收端沿用最近值。Android 解码器当前错误地对后三类字段继续做差分累加。

C 端 `gh_agc_upload_t` 只定义 56 位有效数据，其 64 位 union 的最高 8 位没有语义。AGC 打包局部变量未清零，使最高填充字节可能进入数据流。Android 产测又比较完整的原始 AGC 数组，因此无效填充位变化会被误判为 AGC 调整。

## 目标

1. Android 解码结果与实际 C 编码器的 wire semantics 一致。
2. BASE_NOISE/PPG_NOISE 只根据有效 AGC 位判断稳定性。
3. 损坏或越界报文不得造成字段错位，也不得静默伪装成少帧。
4. 修正仓库内 C 参考实现，使其不再产生或错误累加无效数据。

## 设计

### Kotlin G 帧解码

- 保留 `rawdata`、`phyValue`、`gsData` 和 timestamp 的现有差分恢复。
- `flags`、`algoData`、`agcInfo`、`agcInfoHigh` 在字段存在时直接替换历史绝对值；字段缺失时复制最近值。
- 每次 `decode()` 重置历史状态不变，因为 C 编码器每次 `GHRPC_publish` 后会重新产生绝对首帧。
- 数组长度必须位于对应上限内；负数或超限直接抛出带字段名的 `DecodeException`，不再截断后继续解析。
- varint 最多五字节，第五字节只允许低四位有效；拒绝溢出编码。
- `decode()` 不再吞掉异常。异常携带字节偏移并交给 `Gh3036Executor` 的既有异常日志路径记录；本次 G publish 不返回部分帧，避免半包数据进入采集。

### AGC 稳定判断

- 对低 32 位完整比较。
- 对高 32 位仅比较协议定义的低 24 位，即 `value and 0x00FFFFFF`，忽略最高填充字节。
- 保留通道数量变化会重置稳定计数的行为。

### C 参考代码

- `agc_info_combine()` 中零初始化 AGC struct/union，保证未定义最高字节恒为零。
- C 解码器中的 `flags`、`algo_data`、`agc_info` 改为绝对值替换而非累加。
- 在清空输出 frame 前保存并恢复调用者提供的 `p_data`、`p_algo_res` 等输出缓冲指针，避免 `memset` 清除预分配地址。
- 数组容量和输出帧容量问题不在当前公开函数参数中具备完整容量信息；本次先对 wire 数组长度按协议常量拒绝越界，输出帧容量 API 调整另行设计，避免无兼容依据地修改接口。

## 测试

- 用两帧构造报文证明 AGC、flags、algo 更新是绝对值且缺失时回填。
- 用日志中的 `0x00A00000`/`0x20000000` 模式证明不会产生 `0x01400000`。
- 证明 AGC high 最高字节变化不重置稳定计数，而有效低 24 位变化会重置。
- 覆盖负数长度、超限长度、截断报文和 32 位 varint 溢出。
- 运行 `:ble:ble-protocol:testDebugUnitTest`、`:feature:feature-factory:testDebugUnitTest`，并执行 `assembleDebug` 验证集成编译。

## 非目标

- 不改变 G RPC 外层封包、CRC 或多分片重组。
- 不改变 noise 测试要求连续 300 帧 AGC 稳定的产品规则。
- 不修改 C 解码器公开 API 或未知固件工程的构建配置。
