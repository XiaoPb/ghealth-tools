# Feature-Login 模块流程文档

## 1. 模块概述

`feature-login` 负责用户认证与项目管理。支持在线登录、离线模式、用户注册、项目创建/管理/配置上传等功能。

## 2. 核心组件

| 组件 | 文件 | 职责 |
|------|------|------|
| `LoginViewModel` | `LoginViewModel.kt` | 认证状态管理、Token 持久化、登出逻辑 |
| `LoginScreen` | `LoginScreen.kt` | 登录界面（账号密码、记住密码、离线模式） |
| `RegisterScreen` | `RegisterScreen.kt` | 用户注册界面 |
| `ChipSelectionScreen` | `ChipSelectionScreen.kt` | 离线模式芯片选择界面 |
| `ProjectSelectionScreen` | `ProjectSelectionScreen.kt` | 项目列表选择界面 |
| `ProjectCreateScreen` | `ProjectCreateScreen.kt` | 创建新项目界面 |
| `ProjectConfigUploadScreen` | `ProjectConfigUploadScreen.kt` | 上传项目配置文件界面 |
| `ProjectManageScreen` | `ProjectManageScreen.kt` | 项目管理（列表/删除）界面 |
| `ProjectEditScreen` | `ProjectEditScreen.kt` | 编辑项目信息界面 |
| `CsvFileListScreen` | `CsvFileListScreen.kt` | CSV 文件列表查看界面 |

## 3. 认证流程

### 3.1 在线登录流程

```
用户输入账号密码 → 点击登录
  │
  ▼
LoginViewModel.login(username, password)
  │
  ├── 调用 AuthApi.login(username, password)
  │     ├── 成功 → TokenManager 保存 JWT Token
  │     │     └── UserSessionManager 持久化用户信息
  │     │           └── DataStore: token, username, userId, ...
  │     │
  │     └── 失败 → LoginUiState.error 更新
  │           └── UI 显示错误信息
  │
  └── 登录成功
        ├── onLoginSuccess() 回调
        └── 导航到 ProjectSelectionScreen
```

### 3.2 离线模式流程

```
用户点击"离线模式"
  │
  ▼
LoginScreen → onOfflineMode()
  │
  ▼
导航到 ChipSelectionScreen
  │
  ├── 用户选择芯片类型 (GH3036 / GH3220 / GH3300)
  │     └── BlePreferences.effectiveChip = 选择的芯片
  │
  ├── 点击确认
  │     └── onChipSelected()
  │
  └── 导航到 MAIN 主界面
```

### 3.3 自动登录

```
GHealthApp / MainActivity 启动
  │
  ▼
LoginViewModel 初始化
  │
  ├── 检查 UserSessionManager.isLoggedIn (DataStore)
  │     ├── 已登录 → 自动跳转 ProjectSelectionScreen
  │     └── 未登录 → 显示 LoginScreen
  │
  └── 检查 Token 有效性 (TokenManager)
        ├── 有效 → 维持登录态
        └── 过期 → 尝试刷新 Token
              ├── 刷新成功 → 维持登录态
              └── 刷新失败 → 退回到 LoginScreen
```

## 4. 项目管理流程

### 4.1 项目选择

```
ProjectSelectionScreen
  │
  ├── 加载项目列表: ProjectApi.getProjects()
  ├── 显示项目卡片列表
  │
  ├── 用户点击项目 → onProjectSelected()
  │     └── 保存选中项目到 UserPreferences
  │     └── 导航到 MAIN
  │
  ├── 创建项目按钮 → ProjectCreateScreen
  ├── 管理项目按钮 → ProjectManageScreen
  └── 退出登录按钮 → onLogout()
```

### 4.2 创建项目

```
ProjectCreateScreen
  │
  ├── 填写项目信息 (名称、描述等)
  ├── 点击确认
  │     └── ProjectApi.createProject(name, desc)
  │           ├── 成功 → 返回 projectId
  │           │     └── 导航到 ConfigUploadScreen(projectId, projectName)
  │           └── 失败 → 显示错误
  │
  └── ConfigUploadScreen
        └── 上传设备配置文件到服务器
```

### 4.3 登出流程

```
logout()
  │
  ├── LoginViewModel.logout()
  │     ├── TokenManager.clearToken()
  │     ├── UserSessionManager.clear()
  │     └── 清理所有鉴权状态
  │
  └── 导航到 LoginScreen
        └── popUpTo(0) { inclusive = true }
```

## 5. 数据模型

### 5.1 LoginUiState

```kotlin
data class LoginUiState(
    val username: String,
    val password: String,
    val rememberPassword: Boolean,
    val isLoading: Boolean,
    val error: String?,
    val isLoggedIn: Boolean
)
```

### 5.2 关键依赖

- `AuthApi` (Retrofit) — 登录/注册 API
- `TokenManager` — JWT Token 管理
- `UserSessionManager` — DataStore 持久化用户会话
- `UserPreferences` — 用户偏好设置（记住密码等）
- `ProjectApi` — 项目 CRUD API
- `BlePreferences` — 芯片类型设置（离线模式用）

## 6. 导航路由

```
LOGIN
 ├── → REGISTER (注册页面)
 ├── → CHIP_SELECTION (离线模式芯片选择)
 └── → PROJECT_SELECTION (登录成功后)

PROJECT_SELECTION
 ├── → MAIN (选好项目进入主界面)
 ├── → PROJECT_CREATE (创建新项目)
 └── → PROJECT_MANAGE (管理项目列表)

PROJECT_CREATE
 └── → CONFIG_UPLOAD/{projectId} (上传配置)

PROJECT_MANAGE
 ├── → PROJECT_EDIT/{projectId} (编辑项目)
 └── → CSV_FILE_LIST/{projectId} (查看CSV文件)

REGISTER
 └── → PROJECT_SELECTION (注册成功自动登录)
```