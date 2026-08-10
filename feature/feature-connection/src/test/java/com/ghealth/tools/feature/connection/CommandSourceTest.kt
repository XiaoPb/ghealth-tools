package com.ghealth.tools.feature.connection

import com.ghealth.tools.ble.protocol.gh3036.CommandGroup
import com.ghealth.tools.ble.protocol.gh3036.CommandPayloadBuilder
import com.ghealth.tools.ble.protocol.gh3036.Gh3036CommandMeta
import com.ghealth.tools.ble.protocol.gh3036.KEY_F_GET_MODE
import com.ghealth.tools.ble.protocol.gh3036.KEY_DOWNLOAD_CONFIG
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandSourceTest {

    @Test
    fun `gh3220 source lists version status commands`() {
        val keys = Gh3220CommandSource.getCommandsByGroup(CommandGroup.VERSION_STATUS).map { it.key }.toSet()
        assertTrue("GH3220_GET_VERSION" in keys)
        assertTrue("GH3220_CONN_STATUS" in keys)
    }

    @Test
    fun `gh3220 source builds get version payload`() {
        val meta = Gh3220CommandSource.getCommandByKey("GH3220_GET_VERSION")!!
        val payload = Gh3220CommandSource.buildPayload(meta, mapOf("versionType" to 1))
        assertArrayEquals(byteArrayOf(0x01), payload)
    }

    @Test
    fun `gh3220 source func mode bits reuse gh3220 table`() {
        val bits = Gh3220CommandSource.getFuncModeBits("gh3220")
        assertEquals(Gh3036CommandMeta.FUNC_MODE_BITS_GH3220, bits)
    }

    @Test
    fun `gh3036 source keeps existing command behavior`() {
        val downloadMeta = Gh3036CommandSource.getCommandByKey(KEY_DOWNLOAD_CONFIG)
        assertNotNull(downloadMeta)
        assertTrue(Gh3036CommandSource.getCommandsByGroup(CommandGroup.VERSION_STATUS).isNotEmpty())

        val getMode = Gh3036CommandSource.getCommandByKey(KEY_F_GET_MODE)!!
        val paramValues = mapOf("testMode" to 1)
        val payload = Gh3036CommandSource.buildPayload(getMode, paramValues)
        assertArrayEquals(CommandPayloadBuilder.buildCommandParams(getMode, paramValues), payload)
        assertArrayEquals(byteArrayOf(0x01), payload)
    }
}