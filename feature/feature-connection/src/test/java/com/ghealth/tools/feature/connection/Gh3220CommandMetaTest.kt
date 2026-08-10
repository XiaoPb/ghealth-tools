package com.ghealth.tools.feature.connection

import com.ghealth.tools.ble.gh3220.Gh3220Payload
import com.ghealth.tools.ble.gh3220.Gh3220Function
import com.ghealth.tools.ble.gh3220.Gh3220ProtocolClient
import com.ghealth.tools.ble.gh3220.commands.BasicCommands
import com.ghealth.tools.ble.gh3220.commands.ConfigCommands
import com.ghealth.tools.ble.gh3220.commands.RegisterCommands
import com.ghealth.tools.ble.protocol.gh3036.CommandGroup
import com.ghealth.tools.ble.protocol.gh3036.CommandMeta
import com.ghealth.tools.ble.protocol.gh3036.CommandParamDef
import com.ghealth.tools.ble.protocol.gh3036.Gh3036CommandMeta
import com.ghealth.tools.ble.protocol.gh3036.ParamType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Gh3220CommandMetaTest {

    private val expectedKeys = listOf(
        "GH3220_GET_VERSION",
        "GH3220_CONN_STATUS",
        "GH3220_START_HBD",
        "GH3220_READ_REG",
        "GH3220_WORK_MODE",
        "GH3220_RAW_SEND",
        "GH3220_PACKAGE_TEST",
        "GH3220_GSENSOR_SET",
        "GH3220_FIFO_THRESHOLD",
        "GH3220_EVENT_SET",
        "GH3220_FUNC_MAP",
        "GH3220_CHIP_RESET",
        "GH3220_CALIBRATE_CURRENT",
        "GH3220_SAMPLE_RATES",
        "GH3220_SLOT_EN",
        "GH3220_ECG_CTRL",
        "GH3220_WORK_MODE_SET",
        "GH3220_APP_MODULE",
        "GH3220_SWITCH_CHIP",
        "GH3220_REG_ARRAY_WRITE",
    )

    @Test
    fun `meta covers core command set`() {
        val keys = Gh3220CommandMeta.all.map { it.key }.toSet()
        assertTrue("GH3220_GET_VERSION" in keys)
        assertTrue("GH3220_CONN_STATUS" in keys)
        assertTrue("GH3220_READ_REG" in keys)
        assertTrue("GH3220_START_HBD" in keys)
        assertTrue("GH3220_WORK_MODE" in keys)
        assertTrue("GH3220_RAW_SEND" in keys)
    }

    @Test
    fun `all command keys match planned full set and are unique`() {
        assertEquals(expectedKeys, Gh3220CommandMeta.all.map { it.key })
        assertEquals(expectedKeys.size, Gh3220CommandMeta.all.map { it.key }.toSet().size)
    }

    @Test
    fun `get version meta has version type param`() {
        val meta = Gh3220CommandMeta.getCommandByKey("GH3220_GET_VERSION")!!
        assertEquals(1, meta.params.size)
        assertEquals(ParamType.U8, meta.params[0].type)
    }

    @Test
    fun `function params of gh3220 control commands use func mode bits`() {
        val functionParam = { key: String ->
            Gh3220CommandMeta.getCommandByKey(key)!!.params.single { it.name == "function" }
        }
        listOf("GH3220_START_HBD", "GH3220_WORK_MODE", "GH3220_SLOT_EN").forEach { key ->
            val def = functionParam(key)
            assertEquals(
                ParamType.FUNC_MODE_BITS,
                def.type,
                "命令 $key 的 function 参数类型应为 FUNC_MODE_BITS",
            )
        }
        // 0x10 WORK_MODE 的 function 会写入设备 g_unAllFuncMode（0x0C 启动功能必须是其子集），
        // 默认全功能避免「0x0C 必然设置失败」。
        assertEquals(Gh3220Function.allMask, functionParam("GH3220_WORK_MODE").defaultValue)
        assertEquals(0L, functionParam("GH3220_START_HBD").defaultValue)
        assertEquals(0L, functionParam("GH3220_SLOT_EN").defaultValue)
    }

    @Test
    fun `panel func mode bits gh3220 match protocol gh3220 function entries`() {
        // 面板元数据（Gh3036CommandMeta.FUNC_MODE_BITS_GH3220）与协议层权威映射
        // （ble-gh3220 Gh3220Function，C 端 GH3X2X_FUNCTION_* 宏）必须完全同步。
        val panelBits = Gh3036CommandMeta.FUNC_MODE_BITS_GH3220.map { it.name to it.bit }
        val protocolBits = Gh3220Function.entries.map { it.name to it.bit }
        assertEquals(20, Gh3220Function.entries.size, "Gh3220Function 应含全部 20 个功能位")
        assertEquals(20, panelBits.size, "FUNC_MODE_BITS_GH3220 应含全部 20 个功能位")
        assertEquals(protocolBits, panelBits)
    }

    @Test
    fun `every command is reachable via key lookup and expects a response`() {
        Gh3220CommandMeta.all.forEach {
            assertSame(it, Gh3220CommandMeta.getCommandByKey(it.key))
        }
        assertTrue(Gh3220CommandMeta.all.all { it.meta.hasResponse })
    }

    @Test
    fun `version type options follow gh3220 protocol section 3_21`() {
        val values = Gh3220CommandMeta.VERSION_TYPE_OPTIONS.map { it.value as Int }.toSet()
        assertEquals(
            setOf(0x01, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E),
            values,
        )
    }

    @Test
    fun `work mode options follow gh3220 protocol section 3_12`() {
        val values = Gh3220CommandMeta.WORK_MODE_OPTIONS.map { it.value as Int }.toSet()
        assertEquals((0..6).toSet(), values)
    }

    @Test
    fun `reset calibrate and switch chip options follow protocol docs`() {
        assertEquals(
            setOf(0x5A, 0xC2, 0xC3, 0xC4),
            Gh3220CommandMeta.CHIP_RESET_OPTIONS.map { it.value as Int }.toSet(),
        )
        assertEquals(
            setOf(0, 1),
            Gh3220CommandMeta.CALIBRATE_MODE_OPTIONS.map { it.value as Int }.toSet(),
        )
        assertEquals(
            setOf(1, 2),
            Gh3220CommandMeta.SWITCH_CHIP_OPTIONS.map { it.value as Int }.toSet(),
        )
    }

    @Test
    fun `group lookup returns only matching commands`() {
        val register = Gh3220CommandMeta.getCommandsByGroup(CommandGroup.REGISTER).map { it.key }.toSet()
        assertEquals(setOf("GH3220_READ_REG", "GH3220_REG_ARRAY_WRITE"), register)
    }

    @Test
    fun `payload builder encodes get version`() {
        val payload = Gh3220CommandPayloadBuilder.build(
            Gh3220CommandMeta.getCommandByKey("GH3220_GET_VERSION")!!,
            listOf(0x01),
        ).getOrThrow()
        assertTrue(payload.contentEquals(byteArrayOf(0x01)))
    }

    @Test
    fun `payload builder encodes conn status as empty body`() {
        val payload = Gh3220CommandPayloadBuilder.build(
            Gh3220CommandMeta.getCommandByKey("GH3220_CONN_STATUS")!!,
            emptyList(),
        ).getOrThrow()
        assertTrue(payload.isEmpty())
    }

    @Test
    fun `payload builder encodes start hbd`() {
        val payload = Gh3220CommandPayloadBuilder.build(
            Gh3220CommandMeta.getCommandByKey("GH3220_START_HBD")!!,
            listOf(1, 2, 0x01020304L),
        ).getOrThrow()
        // on=启动(1) 编码为 0x00，mode=2，slotEn 占位 0x00，function u32le（C 端 7B 布局）
        assertArrayEquals(byteArrayOf(0x00, 0x02, 0x00, 0x04, 0x03, 0x02, 0x01), payload)
    }

    @Test
    fun `payload builder encodes read reg`() {
        val payload = Gh3220CommandPayloadBuilder.build(
            Gh3220CommandMeta.getCommandByKey("GH3220_READ_REG")!!,
            listOf(0x1234, 2),
        ).getOrThrow()
        assertArrayEquals(byteArrayOf(0x00, 0x02, 0x12, 0x34), payload)
    }

    @Test
    fun `payload builder encodes raw send as type plus payload`() {
        val payload = Gh3220CommandPayloadBuilder.build(
            Gh3220CommandMeta.getCommandByKey("GH3220_RAW_SEND")!!,
            listOf(0x23, byteArrayOf(0x01, 0x02)),
        ).getOrThrow()
        assertArrayEquals(byteArrayOf(0x23, 0x01, 0x02), payload)
    }

    @Test
    fun `payload builder encodes sample rates from raw bytes`() {
        val payload = Gh3220CommandPayloadBuilder.build(
            Gh3220CommandMeta.getCommandByKey("GH3220_SAMPLE_RATES")!!,
            listOf(byteArrayOf(0x11, 0x23)),
        ).getOrThrow()
        // 1 项：高 4bit=1，低 12bit=0x123 → 0x1123 → [0x11, 0x23]，前导 funcNum=0x01
        assertArrayEquals(byteArrayOf(0x01, 0x11, 0x23), payload)
    }

    @Test
    fun `payload builder encodes reg array write blocks`() {
        val payload = Gh3220CommandPayloadBuilder.build(
            Gh3220CommandMeta.getCommandByKey("GH3220_REG_ARRAY_WRITE")!!,
            listOf(byteArrayOf(0x00, 0x01, 0x12, 0x34)),
        ).getOrThrow()
        assertArrayEquals(byteArrayOf(0x00, 0x01, 0x12, 0x34), payload)
    }

    @Test
    fun `payload builder rejects unknown key`() {
        val unknown = Gh3220CommandMeta(
            meta = CommandMeta(
                key = "GH3220_UNKNOWN",
                displayName = "未知",
                description = "测试用",
                requestFormat = "",
                params = emptyList(),
                hasResponse = true,
            ),
            type = 0,
        ) { _, _ -> Result.success(ByteArray(0)) }
        assertTrue(Gh3220CommandPayloadBuilder.build(unknown, emptyList()).isFailure)
    }

    @Test
    fun `payload builder rejects missing required params`() {
        val result = Gh3220CommandPayloadBuilder.build(
            Gh3220CommandMeta.getCommandByKey("GH3220_GET_VERSION")!!,
            emptyList(),
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `payload builder rejects wrong param type`() {
        val result = Gh3220CommandPayloadBuilder.build(
            Gh3220CommandMeta.getCommandByKey("GH3220_GET_VERSION")!!,
            listOf("0x01"),
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `payload builder rejects malformed sample rates bytes`() {
        val result = Gh3220CommandPayloadBuilder.build(
            Gh3220CommandMeta.getCommandByKey("GH3220_SAMPLE_RATES")!!,
            listOf(byteArrayOf(0x01, 0x02, 0x03)),
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `get version executor maps version info to bytes`() = runTest {
        val client = mockk<Gh3220ProtocolClient>()
        coEvery { client.getVersion(1) } returns Result.success(BasicCommands.VersionInfo(1, "AB"))
        val result = Gh3220CommandMeta.getCommandByKey("GH3220_GET_VERSION")!!.executor(client, listOf(1))
        assertTrue(result.isSuccess)
        assertArrayEquals(byteArrayOf(0x01, 0x41, 0x42), result.getOrThrow())
    }

    @Test
    fun `conn status executor maps int response to single byte`() = runTest {
        val client = mockk<Gh3220ProtocolClient>()
        coEvery { client.getConnectionStatus() } returns Result.success(0)
        val result = Gh3220CommandMeta.getCommandByKey("GH3220_CONN_STATUS")!!.executor(client, emptyList())
        assertTrue(result.isSuccess)
        assertArrayEquals(byteArrayOf(0x00), result.getOrThrow())
    }

    @Test
    fun `read reg executor splits registers into big endian bytes`() = runTest {
        val client = mockk<Gh3220ProtocolClient>()
        coEvery { client.readRegisters(0x1234, 1) } returns Result.success(intArrayOf(0x0A0B))
        val result = Gh3220CommandMeta.getCommandByKey("GH3220_READ_REG")!!.executor(client, listOf(0x1234, 1))
        assertTrue(result.isSuccess)
        assertArrayEquals(byteArrayOf(0x0A, 0x0B), result.getOrThrow())
    }

    @Test
    fun `start hbd executor converts form values to client args`() = runTest {
        val client = mockk<Gh3220ProtocolClient>()
        coEvery { client.startHbd(any(), any(), any()) } returns Result.success(0)
        val result = Gh3220CommandMeta.getCommandByKey("GH3220_START_HBD")!!.executor(client, listOf(1, 2, 3L))
        assertTrue(result.isSuccess)
        coVerify { client.startHbd(true, 2, 3L) }
        assertArrayEquals(byteArrayOf(0x00), result.getOrThrow())
    }

    @Test
    fun `sample rates executor parses raw bytes into pairs`() = runTest {
        val client = mockk<Gh3220ProtocolClient>()
        coEvery { client.sampleRates(any()) } returns Result.success(0)
        val result = Gh3220CommandMeta.getCommandByKey("GH3220_SAMPLE_RATES")!!.executor(
            client,
            listOf(byteArrayOf(0x11, 0x23)),
        )
        assertTrue(result.isSuccess)
        coVerify { client.sampleRates(listOf(1 to 0x123)) }
    }

    @Test
    fun `reg array write executor parses blocks and returns empty response`() = runTest {
        val client = mockk<Gh3220ProtocolClient>()
        coEvery { client.regArrayWrite(any()) } returns Result.success(Unit)
        val result = Gh3220CommandMeta.getCommandByKey("GH3220_REG_ARRAY_WRITE")!!.executor(
            client,
            listOf(byteArrayOf(0x00, 0x01, 0x12, 0x34)),
        )
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
        coVerify {
            client.regArrayWrite(match { blocks ->
                blocks.size == 1 && blocks[0].contentEquals(intArrayOf(0x00, 0x01, 0x12, 0x34))
            })
        }
    }

    @Test
    fun `raw send executor passes type and payload and returns raw response`() = runTest {
        val client = mockk<Gh3220ProtocolClient>()
        coEvery { client.sendRaw(any(), any()) } returns Result.success(byteArrayOf(0x7F))
        val result = Gh3220CommandMeta.getCommandByKey("GH3220_RAW_SEND")!!.executor(
            client,
            listOf(0x23, byteArrayOf(0x01, 0x02)),
        )
        assertTrue(result.isSuccess)
        assertArrayEquals(byteArrayOf(0x7F), result.getOrThrow())
        coVerify {
            client.sendRaw(eq(0x23), match { it.contentEquals(byteArrayOf(0x01, 0x02)) })
        }
    }

    @Test
    fun `payload builder encodes every command with legal params`() {
        Gh3220CommandMeta.all.forEach { meta ->
            val result = Gh3220CommandPayloadBuilder.build(meta, legalParams(meta))
            assertTrue(result.isSuccess, "${meta.key} 应能编码: ${result.exceptionOrNull()?.message}")
        }
    }

    @Test
    fun `payload builder accepts unsigned int param values`() {
        val getVersion = Gh3220CommandMeta.getCommandByKey("GH3220_GET_VERSION")!!
        assertTrue(Gh3220CommandPayloadBuilder.build(getVersion, listOf(1u.toUByte())).isSuccess)

        val readReg = Gh3220CommandMeta.getCommandByKey("GH3220_READ_REG")!!
        assertTrue(
            Gh3220CommandPayloadBuilder.build(readReg, listOf(0x10u.toUShort(), 1u.toUByte())).isSuccess,
        )

        val workMode = Gh3220CommandMeta.getCommandByKey("GH3220_WORK_MODE")!!
        assertTrue(
            Gh3220CommandPayloadBuilder.build(workMode, listOf(1u.toUByte(), 0x01020304u)).isSuccess,
        )

        val startHbd = Gh3220CommandMeta.getCommandByKey("GH3220_START_HBD")!!
        assertTrue(
            Gh3220CommandPayloadBuilder.build(startHbd, listOf(1u.toUByte(), 0u.toUByte(), 1uL)).isSuccess,
        )
    }

    @Test
    fun `executor converts unsigned param values`() = runTest {
        val client = mockk<Gh3220ProtocolClient>()
        coEvery { client.getVersion(1) } returns Result.success(BasicCommands.VersionInfo(1, "A"))
        val result = Gh3220CommandMeta.getCommandByKey("GH3220_GET_VERSION")!!.executor(
            client,
            listOf(1u.toUByte()),
        )
        assertTrue(result.isSuccess)
        coVerify { client.getVersion(1) }
    }

    @Test
    fun `executor returns failure for missing params instead of throwing`() = runTest {
        val client = mockk<Gh3220ProtocolClient>()
        val result = Gh3220CommandMeta.getCommandByKey("GH3220_GET_VERSION")!!.executor(client, emptyList())
        assertTrue(result.isFailure)
    }

    @Test
    fun `executor returns failure for wrong param type instead of throwing`() = runTest {
        val client = mockk<Gh3220ProtocolClient>()
        val result = Gh3220CommandMeta.getCommandByKey("GH3220_START_HBD")!!.executor(client, listOf("x", 0, 0L))
        assertTrue(result.isFailure)
    }

    private fun legalParams(meta: Gh3220CommandMeta): List<Any?> = meta.params.map { def ->
        when (def.type) {
            ParamType.U8 -> 1
            ParamType.U16 -> 0x10
            ParamType.U32 -> 1L
            ParamType.FUNC_MODE_BITS -> 1L
            ParamType.U8_ARRAY -> when (meta.key) {
                "GH3220_FUNC_MAP" -> ByteArray(64)
                "GH3220_SAMPLE_RATES" -> byteArrayOf(0x11, 0x23)
                "GH3220_REG_ARRAY_WRITE" -> byteArrayOf(0x00, 0x01, 0x12, 0x34)
                else -> byteArrayOf(0x01)
            }
            else -> def.defaultValue
        }
    }

    @Test
    fun `command type matches protocol doc command id`() {
        val expected = mapOf(
            "GH3220_GET_VERSION" to 0x19,
            "GH3220_CONN_STATUS" to 0x1A,
            "GH3220_START_HBD" to 0x0C,
            "GH3220_READ_REG" to 0x03,
            "GH3220_WORK_MODE" to 0x10,
            "GH3220_RAW_SEND" to 0x23,
            "GH3220_PACKAGE_TEST" to 0x05,
            "GH3220_GSENSOR_SET" to 0x11,
            "GH3220_FIFO_THRESHOLD" to 0x12,
            "GH3220_EVENT_SET" to 0x13,
            "GH3220_FUNC_MAP" to 0x15,
            "GH3220_CHIP_RESET" to 0x17,
            "GH3220_CALIBRATE_CURRENT" to 0x18,
            "GH3220_SAMPLE_RATES" to 0x1B,
            "GH3220_SLOT_EN" to 0x1C,
            "GH3220_ECG_CTRL" to 0x1D,
            "GH3220_WORK_MODE_SET" to 0x1E,
            "GH3220_APP_MODULE" to 0x20,
            "GH3220_SWITCH_CHIP" to 0x2E,
            "GH3220_REG_ARRAY_WRITE" to 0xA1,
        )
        Gh3220CommandMeta.all.forEach { meta ->
            assertEquals(expected[meta.key], meta.type, "命令 ${meta.key} 的 type 应与协议文档命令 ID 一致")
        }
    }

    @Test
    fun `payload builder bytes match low-level command encoders`() {
        // 对拍测试：面板路径（buildPayload → sendRaw）与类型化 executor 路径共用同一批
        // ble-gh3220 编码器，字节一致性由此测试锁定（代表性命令全覆盖）。
        fun build(key: String, params: List<Any?>): ByteArray =
            Gh3220CommandPayloadBuilder.build(Gh3220CommandMeta.getCommandByKey(key)!!, params).getOrThrow()

        assertArrayEquals(
            BasicCommands.getVersion(0x0B),
            build("GH3220_GET_VERSION", listOf(0x0B)),
        )
        assertArrayEquals(
            BasicCommands.startHbd(on = true, mode = 2, function = 0x01020304L),
            build("GH3220_START_HBD", listOf(1, 2, 0x01020304L)),
        )
        assertArrayEquals(
            RegisterCommands.regRead(0x1234, 2),
            build("GH3220_READ_REG", listOf(0x1234, 2)),
        )
        assertArrayEquals(
            ConfigCommands.workMode(5, 0x01020304L),
            build("GH3220_WORK_MODE", listOf(5, 0x01020304L)),
        )
        assertArrayEquals(
            ConfigCommands.slotEn(1, 0, 0x01020304L, on = true),
            build("GH3220_SLOT_EN", listOf(1, 0, 0x01020304L, 1)),
        )
        assertArrayEquals(
            ConfigCommands.sampleRates(listOf(1 to 0x123)),
            build("GH3220_SAMPLE_RATES", listOf(byteArrayOf(0x11, 0x23))),
        )
        assertArrayEquals(
            RegisterCommands.regArrayWrite(listOf(intArrayOf(0x00, 0x01, 0x12, 0x34))),
            build("GH3220_REG_ARRAY_WRITE", listOf(byteArrayOf(0x00, 0x01, 0x12, 0x34))),
        )
        assertArrayEquals(
            Gh3220Payload.u8(0x23) + byteArrayOf(0x01, 0x02),
            build("GH3220_RAW_SEND", listOf(0x23, byteArrayOf(0x01, 0x02))),
        )
    }

    @Test
    fun `u16 array validation accepts short array like gh3036 panel`() {
        val def = CommandParamDef(name = "regs", label = "寄存器", type = ParamType.U16_ARRAY)
        assertNull(validateGh3220Params(listOf(def), listOf(shortArrayOf(0x01, 0x02))))
        assertNull(validateGh3220Params(listOf(def), listOf(intArrayOf(0x01, 0x02))))
        assertNotNull(validateGh3220Params(listOf(def), listOf("x")))
    }
}
