# Core-Network 模块流程文档

## 1. 模块概述

`core-network` 负责与后端服务器（Django REST Framework）的网络通信。包括 API 定义、身份认证、Token 管理、文件下载/上传、配置同步等。

## 2. 核心组件

| 组件 | 文件 | 职责 |
|------|------|------|
| `AuthApi` | `api/AuthApi.kt` | 登录/注册/刷新 Token |
| `ProjectApi` | `api/ProjectApi.kt` | 项目 CRUD |
| `DownloadApi` | `api/DownloadApi.kt` | 文件下载 |
| `UploadApi` | `api/UploadApi.kt` | 文件上传 |
| `TokenManager` | `TokenManager.kt` | JWT Token 获取与刷新 |
| `AuthAuthenticator` | `AuthAuthenticator.kt` | OkHttp Authenticator (Token 过期自动刷新) |
| `AuthInterceptor` | `AuthInterceptor.kt` | OkHttp Interceptor (添加 Authorization Header) |
| `NetworkMonitor` | `NetworkMonitor.kt` | 网络连接状态监测 |
| `ConfigDownloader` | `ConfigDownloader.kt` | 寄存器配置文件下载 |
| `ConfigSyncManager` | `ConfigSyncManager.kt` | 配置同步到设备 |
| `ConfigPathProvider` | `ConfigPathProvider.kt` | 配置文件存储路径 |
| `ApiErrorParser` | `ApiErrorParser.kt` | API 错误响应解析 |
| `ApiModels` | `model/ApiModels.kt` | API 请求/响应数据模型 |
| `NetworkModule` | `di/NetworkModule.kt` | Hilt DI 配置 |

## 3. 网络层架构

```
┌──────────────────────────────────────────────────┐
│                   Feature 层                      │
│        LoginViewModel / ConnectionViewModel       │
├──────────────────────────────────────────────────┤
│                    API 接口层                      │
│  AuthApi / ProjectApi / DownloadApi / UploadApi  │
├──────────────────────────────────────────────────┤
│                   认证层                          │
│  AuthInterceptor → AuthAuthenticator → TokenMgr  │
├──────────────────────────────────────────────────┤
│               网络客户端层                         │
│       OkHttpClient + Retrofit + Moshi            │
├──────────────────────────────────────────────────┤
│                    传输层                         │
│              HTTPS (TLS 1.2+)                    │
└──────────────────────────────────────────────────┘
```

## 4. 认证流程

### 4.1 Token 管理

```
用户登录 → AuthApi.login(username, password)
  │
  ├── 成功: { "access": "jwt...", "refresh": "jwt..." }
  │     ├── TokenManager.saveTokens(access, refresh)
  │     │     └── EncryptedSharedPreferences 安全存储
  │     └── UserSessionManager 持久化用户会话
  │
  └── 失败: { "error": "..." }
        └── ApiErrorParser 解析 → 显示错误
```

### 4.2 AuthInterceptor (请求拦截)

```
每个 HTTP 请求发出前
  │
  ▼
AuthInterceptor.intercept(chain)
  │
  ├── TokenManager.getAccessToken()
  │     ├── 有 Token → 添加 Header: "Authorization: Bearer {token}"
  │     └── 无 Token → 跳过（如 login/register 请求）
  │
  └── chain.proceed(request)
```

### 4.3 AuthAuthenticator (Token 刷新)

```
HTTP 响应 401 Unauthorized
  │
  ▼
AuthAuthenticator.authenticate(route, response)
  │
  ├── 1. 获取 refreshToken
  ├── 2. 调用 AuthApi.refreshToken(refreshToken)
  │     ├── 成功 → 保存新 Token
  │     │     └── 重试原请求 (添加新 Token)
  │     └── 失败 → 清除 Token → 返回 null (跳转到登录页)
  │
  └── 最多重试 1 次 (避免无限循环)
```

### 4.4 Token 刷新流程

```
TokenManager.refreshAccessToken()
  │
  ├── 使用同步锁 (Mutex) 防止并发刷新
  ├── AuthApi.refreshToken(refreshToken)
  │     ├── 成功 → saveTokens(newAccess, newRefresh)
  │     └── 失败 → clearTokens()
  │
  └── 返回新的 accessToken 或 null
```

## 5. API 接口定义

### 5.1 AuthApi

```kotlin
interface AuthApi {
    @POST("api/auth/login/")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/auth/register/")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>

    @POST("api/auth/refresh/")
    suspend fun refreshToken(@Body request: RefreshRequest): Response<LoginResponse>
}
```

### 5.2 ProjectApi

```kotlin
interface ProjectApi {
    @GET("api/projects/")
    suspend fun getProjects(): List<ProjectResponse>

    @POST("api/projects/")
    suspend fun createProject(@Body request: CreateProjectRequest): ProjectResponse

    @PUT("api/projects/{id}/")
    suspend fun updateProject(@Path("id") id: Int, @Body request: UpdateProjectRequest)

    @DELETE("api/projects/{id}/")
    suspend fun deleteProject(@Path("id") id: Int)
}
```

### 5.3 DownloadApi

```kotlin
interface DownloadApi {
    @GET("api/configs/{projectId}/")
    suspend fun listConfigs(@Path("projectId") projectId: Int): List<ConfigInfo>

    @GET("api/configs/{projectId}/download/")
    @Streaming
    suspend fun downloadConfig(
        @Path("projectId") projectId: Int,
        @Query("configName") configName: String
    ): Response<ResponseBody>
}
```

### 5.4 UploadApi

```kotlin
interface UploadApi {
    @Multipart
    @POST("api/projects/{projectId}/upload/")
    suspend fun uploadCsv(
        @Path("projectId") projectId: Int,
        @Part file: MultipartBody.Part
    ): Response<UploadResponse>
}
```

## 6. 网络监测 (NetworkMonitor)

```kotlin
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val isOnline: StateFlow<Boolean>

    // 使用 ConnectivityManager.registerDefaultNetworkCallback
    // 实时监测网络连接状态变化
}
```

使用场景：
- 登录前检查网络状态
- 上传/下载前检查网络
- UI 显示离线状态

## 7. 配置下载与同步

### 7.1 配置下载

```
ConnectionViewModel.downloadRegisterConfig()
  │
  ▼
ConfigDownloader.downloadConfig(projectId)
  │
  ├── 1. 调用 DownloadApi.listConfigs(projectId)
  │     └── 获取 configFiles 列表
  │
  ├── 2. 逐文件下载
  │     └── DownloadApi.downloadConfig(projectId, configName)
  │           └── @Streaming 流式下载
  │
  └── 3. 保存到本地
        └── ConfigPathProvider.getConfigDir(projectId)
              └── {externalDir}/configs/{projectId}/
```

### 7.2 配置同步

```
ConfigSyncManager.syncToDevice(deviceAddress, configFile)
  │
  ├── 1. 读取配置文件 (寄存器地址-值对)
  ├── 2. 逐条发送寄存器写入命令
  │     └── BleConnectionManager.sendCommand(address, "GH3X_RegsWriteCmd", params)
  │
  └── 3. 同步完成通知
```

## 8. 数据模型 (ApiModels)

```kotlin
data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val access: String,
    val refresh: String,
    val user: UserInfo
)

data class UserInfo(
    val id: Int,
    val username: String,
    val email: String?
)

data class ProjectResponse(
    val id: Int,
    val name: String,
    val description: String?,
    val chipType: String,
    val createdAt: String
)

data class CreateProjectRequest(
    val name: String,
    val description: String?,
    val chipType: String
)

data class ConfigInfo(
    val name: String,
    val size: Long,
    val uploadedAt: String
)
```

## 9. Hilt DI 配置

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        authAuthenticator: AuthAuthenticator,
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient

    @Provides @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit

    @Provides @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi

    @Provides @Singleton
    fun provideProjectApi(retrofit: Retrofit): ProjectApi

    @Provides @Singleton
    fun provideDownloadApi(retrofit: Retrofit): DownloadApi

    @Provides @Singleton
    fun provideUploadApi(retrofit: Retrofit): UploadApi

    @Provides @Singleton
    fun provideTokenManager(
        @ApplicationContext context: Context
    ): TokenManager
}
```

## 10. 错误处理

### 10.1 ApiErrorParser

```kotlin
object ApiErrorParser {
    fun parse(errorBody: ResponseBody?): ApiError {
        // 解析 Django REST Framework 错误格式
        // {"detail": "...", "code": "..."}
        // 或
        // {"username": ["..."]}
    }
}

data class ApiError(
    val message: String,
    val code: String?,
    val fieldErrors: Map<String, List<String>>?
)
```

### 10.2 网络异常处理

| 异常 | 处理 |
|------|------|
| `SocketTimeoutException` | 提示"网络超时，请重试" |
| `UnknownHostException` | 提示"无法连接到服务器" |
| `HttpException 401` | 触发 AuthAuthenticator 刷新 Token |
| `HttpException 403` | 提示"无权限" |
| `HttpException 500` | 提示"服务器内部错误" |
| `IOException` | 提示"网络错误" + NetworkMonitor 检测 |