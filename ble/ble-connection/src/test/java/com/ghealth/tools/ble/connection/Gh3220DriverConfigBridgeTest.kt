package com.ghealth.tools.ble.connection

import com.ghealth.tools.ble.itlvc.codec.ItlvcFrame
import com.ghealth.tools.ble.itlvc.codec.ItlvcFrameCodec
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class Gh3220DriverConfigBridgeTest {

    private val codec = ItlvcFrameCodec()

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private fun frame(type: Int, value: ByteArray): ByteArray =
        codec.encode(ItlvcFrame(bytes(type), value))

    @Test
    fun `null bridge returns clear failure`() = runTest {
        val bridge: Gh3220ItlvcBridge? = null
        val result = bridge.sendDriverConfigOrFailure("AA:BB", byteArrayOf(1), save = true) { _, _ -> }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message!!.contains("bridge not available for AA:BB"))
    }

    @Test
    fun `driver config frames carry position and handle flag`() = runTest {
        val notify = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
        val written = mutableListOf<ByteArray>()
        val bridge = Gh3220ItlvcBridge(
            notifyFlow = notify,
            writer = { data -> written.add(data); Result.success(Unit) },
            mtu = 240,
        )
        bridge.attach(this)

        // DriverConfigFlow 每包经 session.execute 等待 0x1F 响应，逐包回 `[0x00]` 驱动下发继续。
        val responder = launch {
            for (i in 0 until 2) {
                while (written.size <= i) delay(1)
                notify.emit(frame(0x1F, bytes(0x00)))
            }
        }

        // 250 字节 → 2 包：230 + 20；pos=0/230，handleFlag=0（非最后）/2（最后且保存）
        val data = ByteArray(250) { it.toByte() }
        val result = bridge.sendDriverConfigOrFailure("AA:BB", data, save = true) { _, _ -> }
        assertTrue(result.isSuccess)

        val p1 = bytes(0x00, 0x00, 0x00) + data.copyOfRange(0, 230)
        val p2 = bytes(0xE6, 0x00, 0x02) + data.copyOfRange(230, 250)
        assertContentEquals(frame(0x1F, p1), written[0])
        assertContentEquals(frame(0x1F, p2), written[1])
        responder.join()
        bridge.detach()
    }
}
