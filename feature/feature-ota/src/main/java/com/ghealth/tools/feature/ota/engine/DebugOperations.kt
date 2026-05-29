package com.ghealth.tools.feature.ota.engine

import android.bluetooth.BluetoothGatt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import timber.log.Timber

data class DebugResult(
    val success: Boolean,
    val data: ByteArray? = null,
    val message: String = "",
)

class DebugOperations(
    private val gattProvider: () -> BluetoothGatt?,
) {

    private val _logEvents = MutableSharedFlow<String>(extraBufferCapacity = 256)
    val logEvents: Flow<String> = _logEvents.asSharedFlow()

    suspend fun readRam(address: Int, length: Int): Result<DebugResult> =
        withContext(Dispatchers.IO) {
            _logEvents.tryEmit("读取RAM 地址=0x${address.toString(16)} 长度=$length")
            val gatt = gattProvider() ?: return@withContext Result.failure(
                IllegalStateException("设备未连接")
            )
            try {
                val result = DebugResult(
                    success = true,
                    data = ByteArray(length) { 0 },
                    message = "RAM读取成功 (stub)",
                )
                _logEvents.tryEmit("RAM读取完成")
                Result.success(result)
            } catch (e: Exception) {
                Timber.e(e, "读取RAM失败")
                Result.failure(e)
            }
        }

    suspend fun writeRam(address: Int, data: ByteArray): Result<DebugResult> =
        withContext(Dispatchers.IO) {
            _logEvents.tryEmit("写入RAM 地址=0x${address.toString(16)} 数据长度=${data.size}")
            val gatt = gattProvider() ?: return@withContext Result.failure(
                IllegalStateException("设备未连接")
            )
            try {
                val result = DebugResult(success = true, message = "RAM写入成功 (stub)")
                _logEvents.tryEmit("RAM写入完成")
                Result.success(result)
            } catch (e: Exception) {
                Timber.e(e, "写入RAM失败")
                Result.failure(e)
            }
        }

    suspend fun readFlash(address: Int, length: Int): Result<DebugResult> =
        withContext(Dispatchers.IO) {
            _logEvents.tryEmit("读取Flash 地址=0x${address.toString(16)} 长度=$length")
            val gatt = gattProvider() ?: return@withContext Result.failure(
                IllegalStateException("设备未连接")
            )
            try {
                val result = DebugResult(
                    success = true,
                    data = ByteArray(length) { 0 },
                    message = "Flash读取成功 (stub)",
                )
                _logEvents.tryEmit("Flash读取完成")
                Result.success(result)
            } catch (e: Exception) {
                Timber.e(e, "读取Flash失败")
                Result.failure(e)
            }
        }

    suspend fun writeFlash(address: Int, data: ByteArray): Result<DebugResult> =
        withContext(Dispatchers.IO) {
            _logEvents.tryEmit("写入Flash 地址=0x${address.toString(16)} 数据长度=${data.size}")
            val gatt = gattProvider() ?: return@withContext Result.failure(
                IllegalStateException("设备未连接")
            )
            try {
                val result = DebugResult(success = true, message = "Flash写入成功 (stub)")
                _logEvents.tryEmit("Flash写入完成")
                Result.success(result)
            } catch (e: Exception) {
                Timber.e(e, "写入Flash失败")
                Result.failure(e)
            }
        }

    suspend fun readRegister(address: Int): Result<DebugResult> =
        withContext(Dispatchers.IO) {
            _logEvents.tryEmit("读取寄存器 地址=0x${address.toString(16)}")
            val gatt = gattProvider() ?: return@withContext Result.failure(
                IllegalStateException("设备未连接")
            )
            try {
                val result = DebugResult(
                    success = true,
                    data = ByteArray(4) { 0 },
                    message = "寄存器读取成功 (stub)",
                )
                _logEvents.tryEmit("寄存器读取完成")
                Result.success(result)
            } catch (e: Exception) {
                Timber.e(e, "读取寄存器失败")
                Result.failure(e)
            }
        }

    suspend fun writeRegister(address: Int, data: ByteArray): Result<DebugResult> =
        withContext(Dispatchers.IO) {
            _logEvents.tryEmit("写入寄存器 地址=0x${address.toString(16)}")
            val gatt = gattProvider() ?: return@withContext Result.failure(
                IllegalStateException("设备未连接")
            )
            try {
                val result = DebugResult(success = true, message = "寄存器写入成功 (stub)")
                _logEvents.tryEmit("寄存器写入完成")
                Result.success(result)
            } catch (e: Exception) {
                Timber.e(e, "写入寄存器失败")
                Result.failure(e)
            }
        }

    suspend fun readEfuse(): Result<DebugResult> =
        withContext(Dispatchers.IO) {
            _logEvents.tryEmit("读取eFuse")
            val gatt = gattProvider() ?: return@withContext Result.failure(
                IllegalStateException("设备未连接")
            )
            try {
                val result = DebugResult(success = true, message = "eFuse读取成功 (stub)")
                _logEvents.tryEmit("eFuse读取完成")
                Result.success(result)
            } catch (e: Exception) {
                Timber.e(e, "读取eFuse失败")
                Result.failure(e)
            }
        }

    suspend fun readNvds(): Result<DebugResult> =
        withContext(Dispatchers.IO) {
            _logEvents.tryEmit("读取NVDS")
            val gatt = gattProvider() ?: return@withContext Result.failure(
                IllegalStateException("设备未连接")
            )
            try {
                val result = DebugResult(success = true, message = "NVDS读取成功 (stub)")
                _logEvents.tryEmit("NVDS读取完成")
                Result.success(result)
            } catch (e: Exception) {
                Timber.e(e, "读取NVDS失败")
                Result.failure(e)
            }
        }

    suspend fun writeNvds(data: ByteArray): Result<DebugResult> =
        withContext(Dispatchers.IO) {
            _logEvents.tryEmit("写入NVDS 数据长度=${data.size}")
            val gatt = gattProvider() ?: return@withContext Result.failure(
                IllegalStateException("设备未连接")
            )
            try {
                val result = DebugResult(success = true, message = "NVDS写入成功 (stub)")
                _logEvents.tryEmit("NVDS写入完成")
                Result.success(result)
            } catch (e: Exception) {
                Timber.e(e, "写入NVDS失败")
                Result.failure(e)
            }
        }

    suspend fun readBootInfo(): Result<DebugResult> =
        withContext(Dispatchers.IO) {
            _logEvents.tryEmit("读取BootInfo")
            val gatt = gattProvider() ?: return@withContext Result.failure(
                IllegalStateException("设备未连接")
            )
            try {
                val result = DebugResult(success = true, message = "BootInfo读取成功 (stub)")
                _logEvents.tryEmit("BootInfo读取完成")
                Result.success(result)
            } catch (e: Exception) {
                Timber.e(e, "读取BootInfo失败")
                Result.failure(e)
            }
        }

    suspend fun writeControlPoint(hexData: String): Result<DebugResult> =
        withContext(Dispatchers.IO) {
            _logEvents.tryEmit("写控制点 数据=$hexData")
            val gatt = gattProvider() ?: return@withContext Result.failure(
                IllegalStateException("设备未连接")
            )
            try {
                val result = DebugResult(success = true, message = "控制点写入成功 (stub)")
                _logEvents.tryEmit("控制点写入完成")
                Result.success(result)
            } catch (e: Exception) {
                Timber.e(e, "写控制点失败")
                Result.failure(e)
            }
        }
}