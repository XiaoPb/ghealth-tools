package com.ghealth.tools.ble.connection

import com.ghealth.tools.ble.gh3220.event.Gh3220CardiffEvent
import com.ghealth.tools.ble.gh3220.rawdata.Gh3220RawDataFrame
import com.ghealth.tools.ble.gh3220.rawdata.Gh3220RawDataPackage
import com.ghealth.tools.ble.itlvc.codec.ItlvcFrame
import com.ghealth.tools.ble.itlvc.codec.ItlvcFrameCodec
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * GH3220 端到端集成测试（模拟 Notify 通道）：
 * 伪造 Notify 字节流 → [Gh3220ItlvcBridge]（NotifyTransport + ItlvcSession + Gh3220ProtocolClient）
 * → 命令响应 / rawdata 上报 / 0x16 自动 ACK / 0x0B 分包元数据透出。
 *
 * 所有流均为 replay=0 的 SharedFlow：先 launch collector 并 testScheduler.runCurrent() 对齐订阅
 * 再 emit；collector 用完 cancel；每个测试结尾必须 bridge.detach()。
 *
 * 0x0B 分包说明：当前 RawDataDecoder 不做跨包拼接，分包信息（splicePackCount/splicePackOver）
 * 仅作为元数据透出 + 帧逐包上抛，本测试锁定该契约，不实现新拼接逻辑。
 */
class Gh3220EndToEndTest {

    private val codec = ItlvcFrameCodec()

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private fun frame(type: Int, value: ByteArray): ByteArray =
        codec.encode(ItlvcFrame(bytes(type), value))

    private fun newBridge(
        notify: MutableSharedFlow<ByteArray>,
        written: MutableList<ByteArray>,
    ): Gh3220ItlvcBridge = Gh3220ItlvcBridge(
        notifyFlow = notify,
        writer = { data -> written.add(data); Result.success(Unit) },
        mtu = 240,
    )

    @Test
    fun `command 0x1A response resolves over chunked notify frames`() = runTest {
        val notify = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
        val written = mutableListOf<ByteArray>()
        val bridge = newBridge(notify, written)
        bridge.attach(this)

        val job = async { bridge.client.getConnectionStatus() }
        testScheduler.runCurrent()
        // 0x1A 命令载荷为空：写入的命令帧即 0x1A + 空 payload。
        assertContentEquals(frame(0x1A, ByteArray(0)), written.single())

        // 响应必须是 1 字节（byte0=0 表示已连接）；分片模拟 Notify 跨 MTU 分包。
        val resp = frame(0x1A, bytes(0x00))
        notify.emit(resp.copyOfRange(0, 1))
        notify.emit(resp.copyOfRange(1, resp.size))
        val result = job.await()
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull())
        bridge.detach()
    }

    @Test
    fun `rawdata 0x08 report reaches rawdataFrames with decoded value`() = runTest {
        val notify = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
        val written = mutableListOf<ByteArray>()
        val bridge = newBridge(notify, written)
        bridge.attach(this)

        val frames = mutableListOf<Gh3220RawDataFrame>()
        val subscription = launch { bridge.client.rawdataFrames.collect { frames.add(it) } }
        testScheduler.runCurrent()

        // 0x08 单帧：dataType=0x00（默认 SamplingConfig，无 acc/agc/amb/algo），
        // 帧 = [frameId=0x00][rawdata 4B 0x01020304]。
        notify.emit(frame(0x08, bytes(0x00, 0x05, 0x00, 0x01, 0x02, 0x03, 0x04)))
        testScheduler.runCurrent()

        assertEquals(1, frames.size)
        assertEquals(0, frames[0].frameId)
        assertContentEquals(intArrayOf(0x01020304), frames[0].rawdata)
        assertNull(frames[0].channel, "0x08 非多功能帧 channel 应为 null")
        subscription.cancel()
        bridge.detach()
    }

    @Test
    fun `0x16 cardiff event auto acks with event report id`() = runTest {
        val notify = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
        val written = mutableListOf<ByteArray>()
        val bridge = newBridge(notify, written)
        bridge.attach(this)

        val events = mutableListOf<Gh3220CardiffEvent>()
        val subscription = launch { bridge.client.cardiffEvents.collect { events.add(it) } }
        testScheduler.runCurrent()

        // 0x16 事件 payload 需 >=3 字节 [eventHi][eventLo][eventReportId]；
        // 短于 3 字节 ReportDecoder 解析失败，事件被丢弃且不 ACK。
        notify.emit(frame(0x16, bytes(0x00, 0x05, 0xAA)))
        testScheduler.runCurrent()

        assertEquals(1, events.size)
        assertEquals(Gh3220CardiffEvent(event = 5, eventReportId = 0xAA), events[0])
        // 自动 ACK：session.send 单向上报 0x16 + [eventReportId]，进入 written。
        assertEquals(1, written.size)
        assertContentEquals(frame(0x16, bytes(0xAA)), written.single())
        subscription.cancel()
        bridge.detach()
    }

    @Test
    fun `0x0B split package exposes splice metadata and forwards frames`() = runTest {
        val notify = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
        val written = mutableListOf<ByteArray>()
        val bridge = newBridge(notify, written)
        bridge.attach(this)

        val packages = mutableListOf<Gh3220RawDataPackage>()
        val frames = mutableListOf<Gh3220RawDataFrame>()
        val packageSubscription = launch { bridge.client.rawdataPackages.collect { packages.add(it) } }
        val frameSubscription = launch { bridge.client.rawdataFrames.collect { frames.add(it) } }
        testScheduler.runCurrent()

        // 非多功能 0x0B：payload = [dataType 0x00][chMask 4B BE 0x00000001][pkgFlag 0x28][dataLen 0x05]
        //               [frameId 0x00][rawdata 4B 0x01020304]。
        // pkgFlag=0x28 → bits3-4 splicePackCount=1、bit5 splicePackOver=true。
        // ITLVC 帧分两段 emit，顺带覆盖 Notify 跨分片组帧。
        val itlvcFrame = frame(0x0B, bytes(0x00, 0x00, 0x00, 0x00, 0x01, 0x28, 0x05, 0x00, 0x01, 0x02, 0x03, 0x04))
        notify.emit(itlvcFrame.copyOfRange(0, 7))
        notify.emit(itlvcFrame.copyOfRange(7, itlvcFrame.size))
        testScheduler.runCurrent()

        assertEquals(1, packages.size)
        val pkg = packages[0]
        assertEquals(0, pkg.dataType, "payload 首字节 dataType=0x00（acc/agc/amb/algo 位全关）")
        assertFalse(pkg.multiFunction)
        assertEquals(1, pkg.splicePackCount)
        assertTrue(pkg.splicePackOver)
        assertContentEquals(intArrayOf(0), pkg.activeChannels)
        assertEquals(1, pkg.frames.size)
        assertContentEquals(intArrayOf(0x01020304), pkg.frames[0].rawdata)
        // rawdataFrames 同步收到该帧（0x0B 包逐帧转发契约）。
        assertEquals(1, frames.size)
        assertContentEquals(intArrayOf(0x01020304), frames[0].rawdata)
        packageSubscription.cancel()
        frameSubscription.cancel()
        bridge.detach()
    }
}
