package com.ghealth.tools.ble.connection

import com.ghealth.tools.ble.gh3220.Gh3220Layout
import com.ghealth.tools.ble.gh3220.rawdata.Gh3220RawDataPackage
import com.ghealth.tools.ble.itlvc.codec.ItlvcFrame
import com.ghealth.tools.ble.itlvc.codec.ItlvcFrameCodec
import com.ghealth.tools.ble.protocol.gh3036.GhFuncFrame
import com.ghealth.tools.core.model.DeviceType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * GH3220 连接通路契约测试（可注入层等价断言）。
 *
 * `BleConnectionManager` 是 @Singleton 具体类，依赖 BlePreferences(Context)/LogManager(File)/BleScanner
 * 等 Android 侧具体类，JVM 单测无法直接实例化；此处对「bridge 独占接收 + 仅订阅 rawdataFrames」的
 * 对外契约做断言，防止双重喂帧 / 重复上报回归：
 * - [GHealthPeripheral] 的 GH3220 槽位必须携带 itlvcBridge 且 executor 为空（不再建旧 RPC 执行器）；
 * - 单次 notify 喂帧只产生一次 rawdata 上报，且经 [Gh3220FrameAdapter.toGhFuncFrame] 恰好映射为一个
 *   [GhFuncFrame]；
 * - 0x0B 包同时出现在 rawdataPackages 与 rawdataFrames（client 双路由），manager 只订阅后者，
 *   订阅两会造成重复上报——本测试固化该去重契约。
 */
class Gh3220ConnectionPathTest {

    private val codec = ItlvcFrameCodec(Gh3220Layout.layout)

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private fun frame(type: Int, value: ByteArray): ByteArray =
        codec.encode(ItlvcFrame(bytes(type), value))

    @Test
    fun `gh3220 peripheral slot carries itlvc bridge and no legacy executor`() {
        val notify = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
        val bridge = Gh3220ItlvcBridge(
            notifyFlow = notify,
            writer = { Result.success(Unit) },
            mtu = 240,
        )
        val peripheral = GHealthPeripheral(
            peripheral = FakePeripheral(),
            role = DeviceRole.MASTER,
            executor = null,
            deviceType = DeviceType.GH3220,
            itlvcBridge = bridge,
        )
        assertSame(bridge, peripheral.itlvcBridge)
        assertNull(peripheral.executor)
    }

    @Test
    fun `single notify feed yields exactly one rawdata report mapped to one gh func frame`() = runTest {
        val notify = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
        val bridge = Gh3220ItlvcBridge(
            notifyFlow = notify,
            writer = { Result.success(Unit) },
            mtu = 240,
        )
        bridge.attach(this)

        val reported = mutableListOf<GhFuncFrame>()
        // 与 BleConnectionManager 一致：仅订阅 rawdataFrames 并逐帧映射，不额外订阅 rawdataPackages。
        val subscription = launch {
            bridge.client.rawdataFrames.collect { rawFrame ->
                reported += Gh3220FrameAdapter.toGhFuncFrame(rawFrame)
            }
        }
        testScheduler.runCurrent()

        // 0x08 单帧：dataType=0x00（无 acc/agc/amb/algo 位），帧 = [frameId=0][rawdata 4B]
        notify.emit(frame(0x08, bytes(0x00, 5, 0x00, 0x01, 0x02, 0x03, 0x04)))
        testScheduler.runCurrent()

        assertEquals(1, reported.size, "单次 notify 喂帧应恰好产生一次 ghFrameFlow 上报（双重喂帧回归）")
        assertEquals(0, reported[0].frameCnt)
        assertContentEquals(intArrayOf(0x01020304), reported[0].rawdata)

        subscription.cancel()
        bridge.detach()
    }

    @Test
    fun `0x0b package frames are reported exactly once via rawdataFrames only`() = runTest {
        val notify = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
        val bridge = Gh3220ItlvcBridge(
            notifyFlow = notify,
            writer = { Result.success(Unit) },
            mtu = 240,
        )
        bridge.attach(this)

        val framesReported = mutableListOf<GhFuncFrame>()
        val packages = mutableListOf<Gh3220RawDataPackage>()
        val framesSubscription = launch {
            bridge.client.rawdataFrames.collect { rawFrame ->
                framesReported += Gh3220FrameAdapter.toGhFuncFrame(rawFrame)
            }
        }
        // 仅作对照：0x0B 包同时出现在 rawdataPackages，证明若 manager 同时订阅两会重复上报。
        val packagesSubscription = launch {
            bridge.client.rawdataPackages.collect { packages += it }
        }
        testScheduler.runCurrent()

        // 0x0B 多功能单帧：dataType=0x00，chMask=0x00000001，flag=0x04（多功能），
        // 帧 = [frameId=0][fifoId=1][rawdata 4B=0x01020304]
        val payload = bytes(0x00, 0x00, 0x00, 0x00, 0x01, 0x04, 0x06) + bytes(0x00, 0x01, 0x01, 0x02, 0x03, 0x04)
        notify.emit(frame(0x0B, payload))
        testScheduler.runCurrent()

        assertEquals(1, packages.size, "0x0B 包应出现在 rawdataPackages（与 rawdataFrames 双路由，需去重）")
        assertEquals(1, framesReported.size, "manager 仅订阅 rawdataFrames 时，单次 notify 只产生一次上报")

        framesSubscription.cancel()
        packagesSubscription.cancel()
        bridge.detach()
    }
}