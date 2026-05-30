# GHealth Tools 项目文档

## 文档索引

### 架构文档 (Architecture)

| 文档 | 说明 |
|------|------|
| [项目框架总览](architecture/project-overview.md) | 技术栈、模块架构、分层设计、导航架构、数据流 |
| [模块依赖关系](architecture/module-dependency.md) | 模块依赖图、DI 注入关系、关键数据持有者 |
| [数据录制框架](architecture/recording-framework.md) | RecordingManager 生命周期、Channel 队列、CSV 写入策略 |

### BLE 通信文档 (BLE)

| 文档 | 说明 |
|------|------|
| [BLE 端到端流程](ble/ble-end-to-end-flow.md) | 扫描→连接→服务验证→数据通信→断开 全流程 |
| [BLE 协议层架构](ble/ble-protocol/architecture.md) | RPC 帧格式、TypeKey 位域、安全帧/非安全帧处理 |
| [BLE 协议层 API 参考](ble/ble-protocol/api-reference.md) | GHealthExecutor/Call/Send/Publish 接口文档 |

### 功能模块文档 (Features)

| 文档 | 说明 |
|------|------|
| [Login 模块](features/login.md) | 认证流程（在线/离线/自动登录）、项目管理 |
| [Connection 模块](features/connection.md) | 设备扫描/连接、命令交互、测试配置 |
| [Demo 模块](features/demo.md) | G 协议实时数据、波形展示、算法结果、设备对比 |
| [Factory 模块](features/factory.md) | 产测流程、配置加载、测试执行、CSV 导出 |
| [OTA 模块](features/ota.md) | DFU 固件升级、资源升级、设备重连 |
| [Settings 模块](features/settings.md) | BLE UUID 配置、主题、设备信息、日志导出 |

### 核心模块文档 (Core)

| 文档 | 说明 |
|------|------|
| [Storage 模块](core/storage.md) | CSV 录制、日志管理、存储路径、文件上传 |
| [Network 模块](core/network.md) | Retrofit API、Token 管理、鉴权拦截、配置同步 |
| [Database 模块](core/database.md) | Room 数据库、传感器记录实体、DAO |

## 快速入门

### 构建与运行

```bash
# 构建 Debug APK
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug

# 运行测试
./gradlew test

# 运行协议测试
./gradlew :ble:ble-protocol:test
```

### 项目模块列表

```
app/                        # 应用入口
core/
  core-model/               # 数据模型
  core-common/              # 工具 / 调度器
  core-ui/                  # 公共 UI 组件
  core-datastore/           # Preferences 存储
  core-database/            # Room 数据库
  core-data/                # 数据仓库
  core-network/             # 网络通信
  core-storage/             # 文件存储
ble/
  ble-scanner/              # BLE 扫描
  ble-connection/           # BLE 连接管理
  ble-protocol/             # 协议解析
feature/
  feature-login/            # 登录
  feature-connection/       # 设备连接
  feature-demo/             # 实时演示
  feature-factory/          # 产测
  feature-ota/              # OTA 升级
  feature-settings/         # 设置
external/
  libcom/                   # 通用工具库
  libdfu2/                  # Nordic DFU 库
```

## 后端服务

本项目配套后端为 Django REST Framework 服务，详见：
- 代码：`E:\Code\Python\ghealth_tools_server\`
- 文档：`E:\Code\Python\ghealth_tools_server\docs\`
  - `api.md` — 完整 API 接口文档
  - `deploy.md` — 部署说明（Docker Compose）
  - `development.md` — 开发环境搭建指南