package com.ghealth.tools.ble.connection

import com.ghealth.tools.ble.gh3220.rawdata.Gh3220RawDataFrame
import com.ghealth.tools.ble.gh3220.rawdata.Gh3220RawDataPackage
import com.ghealth.tools.ble.gh3220.rawdata.Gh3220Result
import com.ghealth.tools.ble.protocol.gh3036.GhFuncId
import com.ghealth.tools.ble.itlvc.codec.ItlvcFrame
import com.ghealth.tools.ble.itlvc.codec.ItlvcFrameCodec
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Gh3220ItlvcBridgeTest {

    private val codec = ItlvcFrameCodec()

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private fun frame(type: Int, value: ByteArray): ByteArray =
        codec.encode(ItlvcFrame(bytes(type), value))

    @Test
    fun `bridge routes notify chunks into session and command response resolves`() = runTest {
        val notify = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
        val written = mutableListOf<ByteArray>()
        val bridge = Gh3220ItlvcBridge(
            notifyFlow = notify,
            writer = { data -> written.add(data); Result.success(Unit) },
            mtu = 240,
        )
        bridge.attach(this)

        val job = async { bridge.client.getConnectionStatus() }
        testScheduler.runCurrent()
        assertContentEquals(frame(0x1A, ByteArray(0)), written.single())

        // 0x1A 响应必须是 1 字节（byte0=0 表示已连接）；分片模拟 Notify 分包。
        val resp = frame(0x1A, bytes(0x00))
        notify.emit(resp.copyOfRange(0, 2))
        notify.emit(resp.copyOfRange(2, resp.size))
        assertTrue(job.await().isSuccess)
        bridge.detach()
    }

    @Test
    fun `bridge adapter maps rawdata frame to gh func frame columns`() {
        val gh = Gh3220FrameAdapter.toGhFuncFrame(
            Gh3220RawDataFrame(
                dataType = 0,
                funcId = 6,
                frameId = 7,
                acc = intArrayOf(1, 2, 3),
                rawdata = intArrayOf(0x01020304, 0x05060708),
                agc = intArrayOf(0x010203),
                amb = intArrayOf(0x0A, 0x0B),
                results = listOf(Gh3220Result(1, 0xDEADBEEF.toInt())),
            ),
        )
        // GH3220 6=SPO2：映射到公共枚举必须是 SPO2（公共 6 是 TEST1），直接透传会路由错误。
        assertEquals(GhFuncId.SPO2, gh.funcId)
        assertEquals(7, gh.frameCnt)
        assertContentEquals(intArrayOf(0x01020304, 0x05060708), gh.rawdata)
        assertContentEquals(intArrayOf(1, 2, 3), gh.gsData)
        assertContentEquals(intArrayOf(0x010203), gh.agcInfo)
        assertContentEquals(intArrayOf(0x0A, 0x0B), gh.phyValue)
        assertContentEquals(intArrayOf(0xDEADBEEF.toInt()), gh.algoData)
    }

    @Test
    fun `toGhFuncFrames maps package frames with func id translation`() {
        val frames = Gh3220FrameAdapter.toGhFuncFrames(
            Gh3220RawDataPackage(
                dataType = 0x0B,
                funcId = 6,
                channelMask = 0b101,
                activeChannels = intArrayOf(0, 2),
                compressed = false,
                oddPacket = false,
                multiFunction = true,
                splicePackCount = 0,
                splicePackOver = true,
                frames = listOf(
                    Gh3220RawDataFrame(
                        dataType = 0x0B,
                        funcId = 6,
                        frameId = 0,
                        acc = null,
                        rawdata = intArrayOf(11),
                        agc = null,
                        amb = null,
                        results = emptyList(),
                        channel = 0,
                    ),
                    Gh3220RawDataFrame(
                        dataType = 0x0B,
                        funcId = 6,
                        frameId = 1,
                        acc = null,
                        rawdata = intArrayOf(22),
                        agc = null,
                        amb = null,
                        results = emptyList(),
                        channel = 2,
                    ),
                ),
            ),
        )
        assertEquals(2, frames.size)
        assertTrue(frames.all { it.funcId == GhFuncId.SPO2 })
        assertEquals(0, frames[0].frameCnt)
        assertEquals(1, frames[1].frameCnt)
        assertContentEquals(intArrayOf(11), frames[0].rawdata)
        assertContentEquals(intArrayOf(22), frames[1].rawdata)
    }
}
