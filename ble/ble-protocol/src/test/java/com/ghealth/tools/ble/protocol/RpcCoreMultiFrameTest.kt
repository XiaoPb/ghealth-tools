package com.ghealth.tools.ble.protocol

import com.ghealth.tools.ble.protocol.rpccore.FrameBuilder
import com.ghealth.tools.ble.protocol.rpccore.RpcCore
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RpcCoreMultiFrameTest {

    private fun deliverG(param: ByteArray): Pair<List<ByteArray>, List<ByteArray>> {
        val core = RpcCore()
        val received = mutableListOf<ByteArray>()
        core.register("G") { data, _, _ -> received.add(data.copyOf()) }
        val fragments = FrameBuilder().buildMultiFrame(key = "G", param = param)
        runBlocking {
            fragments.forEach { frame -> core.process(frame) }
        }
        return fragments to received
    }

    @Test
    fun `reassembles multi fragment unsecure message with fin fragment`() {
        // 300B 超过单片上限 240B，FrameBuilder 会拆成 233B(非末片) + 67B(末片)
        val param = ByteArray(300) { it.toByte() }
        val (fragments, received) = deliverG(param)
        assertEquals(2, fragments.size)
        assertEquals(1, received.size)
        assertArrayEquals(param, received[0])
    }

    @Test
    fun `delivers single fragment message unchanged`() {
        val param = ByteArray(10) { (it + 1).toByte() }
        val (_, received) = deliverG(param)
        assertEquals(1, received.size)
        assertArrayEquals(param, received[0])
    }
}
