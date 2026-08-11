package com.ghealth.tools.core.storage

import org.junit.jupiter.api.Assertions.assertEquals
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

    @Test
    fun `金标心率注入 REF_RESULT 对应列且血氧保持 REF_RESULT5 起`() {
        val values = mutableMapOf<String, Any?>("REF_RESULT0" to 0, "REF_RESULT2" to 0, "REF_RESULT5" to 0)
        injectCompareValues(values, mapOf(0 to 72, 2 to 68), mapOf(0 to 98.5f))
        assertEquals(72, values["REF_RESULT0"])
        assertEquals(68, values["REF_RESULT2"])
        assertEquals(98.5f, values["REF_RESULT5"])
    }

    @Test
    fun `无金标值时 REF_RESULT 占位保持 0 不为空`() {
        val values = mutableMapOf<String, Any?>("REF_RESULT0" to 0)
        injectCompareValues(values, emptyMap(), emptyMap())
        assertEquals(0, values["REF_RESULT0"])
    }
}
