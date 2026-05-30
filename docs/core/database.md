# Core-Database 模块流程文档

## 1. 模块概述

`core-database` 基于 Room 提供本地持久化存储，用于传感器记录的结构化查询与离线管理。

## 2. 核心组件

| 组件 | 文件 | 职责 |
|------|------|------|
| `GHealthDatabase` | `GHealthDatabase.kt` | Room 数据库定义（版本、实体、DAO） |
| `SensorRecordEntity` | `SensorRecordEntity.kt` | 传感器记录实体映射 |
| `SensorRecordDao` | `SensorRecordDao.kt` | 传感器记录数据访问接口 |
| `DatabaseModule` | `di/DatabaseModule.kt` | Hilt DI 绑定 |

## 3. 数据库定义

```kotlin
@Database(
    entities = [SensorRecordEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(GHealthConverters::class)
abstract class GHealthDatabase : RoomDatabase() {
    abstract fun sensorRecordDao(): SensorRecordDao
}
```

## 4. 数据实体

### 4.1 SensorRecordEntity

```kotlin
@Entity(tableName = "sensor_records")
data class SensorRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "device_address")
    val deviceAddress: String,

    @ColumnInfo(name = "function_mode")
    val functionMode: String,

    @ColumnInfo(name = "frame_id")
    val frameId: Int,

    @ColumnInfo(name = "raw_data")
    val rawData: String,  // JSON 序列化的 IntArray

    @ColumnInfo(name = "algo_data")
    val algoData: String?,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
```

## 5. DAO 接口

```kotlin
@Dao
interface SensorRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: SensorRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<SensorRecordEntity>): List<Long>

    @Query("SELECT * FROM sensor_records WHERE session_id = :sessionId ORDER BY timestamp ASC")
    suspend fun getBySession(sessionId: String): List<SensorRecordEntity>

    @Query("SELECT * FROM sensor_records WHERE device_address = :address ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getByDevice(address: String, limit: Int = 100): List<SensorRecordEntity>

    @Query("SELECT DISTINCT session_id FROM sensor_records ORDER BY session_id DESC")
    suspend fun getSessionIds(): List<String>

    @Query("DELETE FROM sensor_records WHERE session_id = :sessionId")
    suspend fun deleteBySession(sessionId: String)

    @Query("DELETE FROM sensor_records WHERE created_at < :before")
    suspend fun deleteOlderThan(before: Long)
}
```

## 6. Hilt DI 配置

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): GHealthDatabase {
        return Room.databaseBuilder(
            context,
            GHealthDatabase::class.java,
            "ghealth-tools.db"
        )
            .addCallback(DatabaseCallback())
            .build()
    }

    @Provides
    fun provideSensorRecordDao(db: GHealthDatabase): SensorRecordDao {
        return db.sensorRecordDao()
    }
}
```

## 7. 数据库回调

```kotlin
class DatabaseCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        // 数据库首次创建时执行
        // 可在此创建初始索引
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        // 数据库每次打开时执行
    }
}
```

## 8. 数据使用场景

### 8.1 历史数据查询

```
DemoScreen → 查看历史录制数据
  │
  ├── SensorRecordDao.getSessionIds()
  │     └── 显示历史会话列表
  │
  └── SensorRecordDao.getBySession(sessionId)
        └── 回放历史数据波形
```

### 8.2 离线数据缓存

```
网络不可用时 → 数据暂存 SQLite
  │
  ├── SensorRecordDao.insert(record)
  │
  └── 网络恢复时 → 批量上传
        └── CsvUploadManager 读取 → 上传 → 删除本地记录
```

### 8.3 数据清理

```
定期清理 / 用户手动清理
  │
  ├── SensorRecordDao.deleteBySession(sessionId)
  │     └── 删除指定会话的全部记录
  │
  └── SensorRecordDao.deleteOlderThan(days * 86400000L)
        └── 删除 N 天前的记录
```

## 9. 与其他模块的关系

```
core-database (Room)
    ↑
core-data (Repository 层)
    ↑
feature-demo (ViewModel 查询历史数据)
feature-connection (ViewModel 暂存离线数据)
```

## 10. 注意事项

- Room 操作均为 `suspend` 函数，在 `Dispatchers.IO` 执行
- 主键自增（`autoGenerate = true`），插入无需手动分配 ID
- `rawData` / `algoData` 以 JSON 字符串存储 IntArray（绕过 Room 不支持数组列的限制）
- 数据库文件路径：`{app_private}/databases/ghealth-tools.db`
- 版本升级需要提供 `Migration` 策略