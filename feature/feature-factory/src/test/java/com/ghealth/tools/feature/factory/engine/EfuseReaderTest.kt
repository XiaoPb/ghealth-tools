package com.ghealth.tools.feature.factory.engine

import com.ghealth.tools.ble.connection.BleConnectionManager
import com.ghealth.tools.ble.protocol.gh3036.KEY_GH3X_REG_BIT_FIELD_WRITE_CMD
import com.ghealth.tools.ble.protocol.gh3036.KEY_GH3X_REGS_READ_CMD
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EfuseReaderTest {

    private val rdataBytes = byteArrayOf(
        4, 0,
        0x11, 0x11, 0x22, 0x22, 0x33, 0x33, 0x44, 0x44
    )

    /** done 标志在第 [pollsUntilDone] 次轮询后置位；数据寄存器恒回 0x1111/0x2222/0x3333/0x4444。 */
    private fun manager(pollsUntilDone: Int = 1, rdata: ByteArray = rdataBytes): BleConnectionManager {
        val manager = mockk<BleConnectionManager>()
        var doneReads = 0
        coEvery { manager.sendCommand(any(), KEY_GH3X_REGS_READ_CMD, any()) } answers {
            val param = thirdArg<ByteArray>()
            val addr = (param[0].toInt() and 0xFF) or ((param[1].toInt() and 0xFF) shl 8)
            when (addr) {
                EfuseReader.RG_EFUSE_READ_DONE_MANUAL_ADDR -> {
                    doneReads++
                    Result.success(byteArrayOf(1, 0, if (doneReads >= pollsUntilDone) 1 else 0, 0))
                }
                EfuseReader.RG_EFUSE_RDATA_0_ADDR -> Result.success(rdata)
                else -> Result.success(ByteArray(0))
            }
        }
        coEvery { manager.sendCommand(any(), KEY_GH3X_REG_BIT_FIELD_WRITE_CMD, any()) } returns
            Result.success(ByteArray(0))
        return manager
    }

    @Test
    fun `读取单个段按 SDK 流程发送命令并拼接 64bit`() = runTest {
        val manager = manager()
        val reader = EfuseReader(manager)

        val value = reader.readSegment("AA:BB", 2)

        assertEquals(0x4444_3333_2222_1111L, value)
        coVerify(exactly = 1) {
            manager.sendCommand("AA:BB", KEY_GH3X_REG_BIT_FIELD_WRITE_CMD, byteArrayOf(0x80.toByte(), 0x05, 0, 0, 0, 0))
            manager.sendCommand("AA:BB", KEY_GH3X_REG_BIT_FIELD_WRITE_CMD, byteArrayOf(0x80.toByte(), 0x05, 2, 3, 2, 0))
            manager.sendCommand("AA:BB", KEY_GH3X_REG_BIT_FIELD_WRITE_CMD, byteArrayOf(0x84.toByte(), 0x05, 0, 0, 1, 0))
            manager.sendCommand("AA:BB", KEY_GH3X_REG_BIT_FIELD_WRITE_CMD, byteArrayOf(0x8A.toByte(), 0x05, 0, 0, 1, 0))
            manager.sendCommand("AA:BB", KEY_GH3X_REGS_READ_CMD, byteArrayOf(0xA6.toByte(), 0x05, 1, 0, 0, 0))
            manager.sendCommand("AA:BB", KEY_GH3X_REGS_READ_CMD, byteArrayOf(0x9E.toByte(), 0x05, 4, 0, 0, 0))
            manager.sendCommand("AA:BB", KEY_GH3X_REG_BIT_FIELD_WRITE_CMD, byteArrayOf(0x84.toByte(), 0x05, 0, 0, 0, 0))
        }
    }

    @Test
    fun `done 标志延迟置位时轮询直到完成`() = runTest {
        val reader = EfuseReader(manager(pollsUntilDone = 2))

        assertEquals(0x4444_3333_2222_1111L, reader.readSegment("AA:BB", 0))
    }

    @Test
    fun `轮询超时返回 null`() = runTest {
        val manager = manager(pollsUntilDone = Int.MAX_VALUE)
        val reader = EfuseReader(manager)

        assertNull(reader.readSegment("AA:BB", 0))
        coVerify(exactly = 1) {
            manager.sendCommand(any(), KEY_GH3X_REG_BIT_FIELD_WRITE_CMD, byteArrayOf(0x84.toByte(), 0x05, 0, 0, 0, 0))
        }
    }

    @Test
    fun `位域写失败返回 null`() = runTest {
        val manager = mockk<BleConnectionManager>()
        coEvery { manager.sendCommand(any(), KEY_GH3X_REG_BIT_FIELD_WRITE_CMD, any()) } returns
            Result.failure(IllegalStateException("write failed"))
        coEvery { manager.sendCommand(any(), KEY_GH3X_REGS_READ_CMD, any()) } returns Result.success(ByteArray(0))

        assertNull(EfuseReader(manager).readSegment("AA:BB", 0))
    }

    @Test
    fun `START 写失败时返回 null 并关闭读使能`() = runTest {
        val manager = mockk<BleConnectionManager>()
        coEvery { manager.sendCommand(any(), KEY_GH3X_REG_BIT_FIELD_WRITE_CMD, any()) } answers {
            val param = thirdArg<ByteArray>()
            val addr = (param[0].toInt() and 0xFF) or ((param[1].toInt() and 0xFF) shl 8)
            if (addr == EfuseReader.RG_EFUSE_START_ADDR) {
                Result.failure(IllegalStateException("start failed"))
            } else {
                Result.success(ByteArray(0))
            }
        }
        coEvery { manager.sendCommand(any(), KEY_GH3X_REGS_READ_CMD, any()) } returns Result.success(ByteArray(0))

        assertNull(EfuseReader(manager).readSegment("AA:BB", 0))
        coVerify(exactly = 1) {
            manager.sendCommand("AA:BB", KEY_GH3X_REG_BIT_FIELD_WRITE_CMD, byteArrayOf(0x84.toByte(), 0x05, 0, 0, 0, 0))
        }
    }

    @Test
    fun `数据寄存器响应不足 4 个返回 null`() = runTest {
        val manager = manager(rdata = byteArrayOf(1, 0, 0x11, 0x11))

        assertNull(EfuseReader(manager).readSegment("AA:BB", 0))
    }

    @Test
    fun `段号越界返回 null`() = runTest {
        assertNull(EfuseReader(manager()).readSegment("AA:BB", 4))
    }

    @Test
    fun `readAll 依次读取 4 段并拼装 32 字节`() = runTest {
        val reader = EfuseReader(manager())

        val bytes = reader.readAll("AA:BB")

        val seg = byteArrayOf(0x11, 0x11, 0x22, 0x22, 0x33, 0x33, 0x44, 0x44)
        val expected = ByteArray(32)
        for (i in 0..3) System.arraycopy(seg, 0, expected, i * 8, 8)
        assertArrayEquals(expected, bytes)
    }

    @Test
    fun `readAll 某段失败返回 null`() = runTest {
        val manager = manager(rdata = byteArrayOf(1, 0, 0x11, 0x11))

        assertNull(EfuseReader(manager).readAll("AA:BB"))
    }
}
