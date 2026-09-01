package com.ghealth.tools.feature.login

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProjectSessionDecisionTest {

    @Test
    fun `archiving the selected project clears the project session`() {
        assertTrue(isCurrentProject(targetProjectId = 42, selectedProjectId = 42))
    }

    @Test
    fun `archiving another project preserves the project session`() {
        assertFalse(isCurrentProject(targetProjectId = 42, selectedProjectId = 7))
        assertFalse(isCurrentProject(targetProjectId = 42, selectedProjectId = null))
        assertFalse(isCurrentProject(targetProjectId = 42, selectedProjectId = 0))
    }
}
