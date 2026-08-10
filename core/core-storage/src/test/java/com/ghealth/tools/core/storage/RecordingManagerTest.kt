package com.ghealth.tools.core.storage

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecordingManagerTest {

    private fun counts() = mutableMapOf<String, Int>()

    @Test
    fun `new-test marker rotates server file even when frame id is zero`() {
        assertTrue(shouldRotateServerFile(mapOf("NEW_TEST" to true, "FRAME_ID" to 0), counts(), "k"))
    }

    @Test
    fun `new-test marker false does not rotate on frame id wrap`() {
        assertFalse(shouldRotateServerFile(mapOf("NEW_TEST" to false, "FRAME_ID" to 0), counts(), "k"))
    }

    @Test
    fun `legacy frame id zero first occurrence does not rotate`() {
        assertFalse(shouldRotateServerFile(mapOf("FRAME_ID" to 0), counts(), "k"))
    }

    @Test
    fun `legacy frame id zero second occurrence rotates`() {
        val c = counts()
        assertFalse(shouldRotateServerFile(mapOf("FRAME_ID" to 0), c, "k"))
        assertTrue(shouldRotateServerFile(mapOf("FRAME_ID" to 0), c, "k"))
    }

    @Test
    fun `legacy frame id non-zero never rotates`() {
        assertFalse(shouldRotateServerFile(mapOf("FRAME_ID" to 3), counts(), "k"))
    }

    @Test
    fun `new-test marker only rotates when flag value carries bit1`() {
        assertFalse(shouldRotateServerFile(mapOf("NEW_TEST" to false), counts(), "k"))
        assertTrue(shouldRotateServerFile(mapOf("NEW_TEST" to true), counts(), "k"))
    }
}
