package com.ghealth.tools.ble.connection

import com.ghealth.tools.ble.gh3220.Gh3220Layout
import com.ghealth.tools.ble.itlvc.codec.ItlvcFrame
import com.ghealth.tools.ble.itlvc.codec.ItlvcFrameCodec
import com.ghealth.tools.ble.itlvc.state.SessionState
import com.ghealth.tools.ble.protocol.gh3036.GhFuncFrame
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * GH3220 断连/重连生命周期契约测试（可注入层等价断言）。
 *
 * `BleConnectionManager` 是 @Singleton 具体类，依赖 BlePreferences(Context)/LogManager(File)/BleScanner
 * 等 Android 侧具体类，JVM 单测无法直接实例化；此处对 manager 断连收尾所依赖的桥生命周期语义
 * 做断言（与 Task 2 的 `Gh3220ConnectionPathTest` 同一策略）：
 * - `bridge.detach()` 后会话回到 [SessionState.DISCONNECTED]，且继续喂 notify 不再产生
 *   rawdataFrames 上报（session 接收协程已取消）；
 * - manager 侧保存的 collect Job 取消后，即使 bridge 仍 attach（接收协程存活）也不再转发
 *   （对应 manager 新增的 `gh3220CollectJobs` 收尾契约）；
 * - detach 后重新 attach（重连场景）可恢复上报，验证重连无需额外初始化。
 */
class Gh3220BridgeLifecycleTest {

    private val codec = ItlvcFrameCodec(Gh3220Layout.layout)

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private fun frame(type: Int, value: ByteArray): ByteArray =
        codec.encode(ItlvcFrame(bytes(type), value))

    private fun gh3220Frame(): ByteArray =
        // 0x08 单帧：dataType=0x00（无 acc/agc/amb/algo 位），帧 = [frameId=0][rawdata 4B]
        frame(0x08, bytes(0x00, 5, 0x00, 0x01, 0x02, 0x03, 0x04))

    @Test
    fun `bridge detach marks session disconnected and stops rawdata reporting`() = runTest {
        val notify = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
        val bridge = Gh3220ItlvcBridge(
            notifyFlow = notify,
            writer = { Result.success(Unit) },
            mtu = 240,
        )
        bridge.attach(this)

        val reported = mutableListOf<GhFuncFrame>()
        // 与 BleConnectionManager GH3220 分支一致：仅订阅 rawdataFrames 并逐帧映射。
        val subscription = launch {
            bridge.client.rawdataFrames.collect { rawFrame ->
                reported += Gh3220FrameAdapter.toGhFuncFrame(rawFrame)
            }
        }
        testScheduler.runCurrent()

        notify.emit(gh3220Frame())
        testScheduler.runCurrent()
        assertEquals(1, reported.size, "attach 后喂帧应产生上报")

        bridge.detach()
        assertEquals(
            SessionState.DISCONNECTED,
            bridge.session.sessionState,
            "detach 后会话必须回到 DISCONNECTED",
        )

        // 接收协程已取消：继续喂 notify 不应再产生 rawdataFrames 上报。
        notify.emit(gh3220Frame())
        testScheduler.runCurrent()
        assertEquals(1, reported.size, "detach 后继续喂帧不应再产生上报（接收协程已取消）")

        subscription.cancel()
    }

    @Test
    fun `cancelling collect job stops forwarding while bridge stays attached`() = runTest {
        val notify = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
        val bridge = Gh3220ItlvcBridge(
            notifyFlow = notify,
            writer = { Result.success(Unit) },
            mtu = 240,
        )
        bridge.attach(this)

        val forwarded = mutableListOf<GhFuncFrame>()
        // 与 BleConnectionManager 一致：collect Job 保存句柄，断连收尾时取消。
        val collectJob = launch {
            bridge.client.rawdataFrames.collect { rawFrame ->
                forwarded += Gh3220FrameAdapter.toGhFuncFrame(rawFrame)
            }
        }
        testScheduler.runCurrent()

        notify.emit(gh3220Frame())
        testScheduler.runCurrent()
        assertEquals(1, forwarded.size, "取消前应正常转发")

        collectJob.cancel()
        testScheduler.runCurrent()

        // bridge 仍 attach（接收协程存活），但 manager 侧 collect Job 已取消 → 不再转发到 ghFrameFlow。
        notify.emit(gh3220Frame())
        testScheduler.runCurrent()
        assertEquals(1, forwarded.size, "取消 collect Job 后不应再转发")

        bridge.detach()
    }

    @Test
    fun `bridge reattach after detach restores rawdata reporting`() = runTest {
        val notify = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
        val bridge = Gh3220ItlvcBridge(
            notifyFlow = notify,
            writer = { Result.success(Unit) },
            mtu = 240,
        )
        bridge.attach(this)

        val reported = mutableListOf<GhFuncFrame>()
        val subscription = launch {
            bridge.client.rawdataFrames.collect { rawFrame ->
                reported += Gh3220FrameAdapter.toGhFuncFrame(rawFrame)
            }
        }
        testScheduler.runCurrent()

        notify.emit(gh3220Frame())
        testScheduler.runCurrent()
        assertEquals(1, reported.size, "首次 attach 后应正常上报")

        bridge.detach()
        // 重连：同桥重新 attach（ItlvcSession.attach 先 detach，client.attach 幂等）。
        bridge.attach(this)
        assertEquals(SessionState.CONNECTED, bridge.session.sessionState, "重新 attach 后会话回到 CONNECTED")
        // replay=0 的 SharedFlow：重新 attach 后须先 runCurrent 对齐新接收协程订阅再 emit。
        testScheduler.runCurrent()

        notify.emit(gh3220Frame())
        testScheduler.runCurrent()
        assertEquals(2, reported.size, "重新 attach 后应恢复 rawdata 上报")

        subscription.cancel()
        bridge.detach()
    }
}
