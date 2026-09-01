package com.ghealth.tools.core.datastore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ActiveChipTest {

    @Test
    fun `online session uses only the project chip`() {
        assertEquals("gh3220", activeChipFor(SessionMode.ONLINE, "gh3220", "gh3036"))
        assertEquals("", activeChipFor(SessionMode.ONLINE, "", "gh3036"))
    }

    @Test
    fun `offline session preserves and uses only the offline chip`() {
        assertEquals("gh3036", activeChipFor(SessionMode.OFFLINE, "gh3220", "gh3036"))
    }

    @Test
    fun `no session has no active chip`() {
        assertEquals("", activeChipFor(SessionMode.NONE, "gh3220", "gh3036"))
    }
}
