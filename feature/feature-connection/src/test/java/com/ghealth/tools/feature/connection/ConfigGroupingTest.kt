package com.ghealth.tools.feature.connection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ConfigGroupingTest {

    private fun config(fileName: String, displayPath: String) = ConfigFileInfo(
        fileName = fileName,
        displayPath = displayPath,
        fullPath = File(displayPath),
        chipName = "gh3036"
    )

    @Test
    fun `online mode flat files do not render duplicated headers`() {
        val groups = groupConfigsForDisplay(
            listOf(
                config("gh3036.config", "gh3036.config"),
                config("sleep.config", "sleep.config")
            )
        )

        assertEquals(2, groups.size)
        assertTrue(groups.none { it.showHeader })
    }

    @Test
    fun `offline mode multiple projects keep project headers`() {
        val groups = groupConfigsForDisplay(
            listOf(
                config("gh3036.config", "projectA/gh3036.config"),
                config("sleep.config", "projectA/sleep.config"),
                config("gh3036.config", "projectB/gh3036.config")
            )
        )

        assertEquals(2, groups.size)
        assertTrue(groups.all { it.showHeader })
        assertEquals(listOf("projectA", "projectB"), groups.map { it.name })
    }

    @Test
    fun `offline mode single project shows no header`() {
        val groups = groupConfigsForDisplay(
            listOf(
                config("gh3036.config", "projectA/gh3036.config"),
                config("sleep.config", "projectA/sleep.config")
            )
        )

        assertEquals(1, groups.size)
        assertFalse(groups.first().showHeader)
        assertEquals(2, groups.first().configs.size)
    }

    @Test
    fun `single flat file shows no header`() {
        val groups = groupConfigsForDisplay(
            listOf(config("gh3036.config", "gh3036.config"))
        )

        assertEquals(1, groups.size)
        assertFalse(groups.first().showHeader)
        assertEquals(1, groups.first().configs.size)
    }
}