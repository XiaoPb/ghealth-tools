package com.ghealth.tools.feature.settings

import com.ghealth.tools.core.datastore.UpdatePreferences
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpdateCheckDecisionTest {

    @Test
    fun `手动检查无论是否忽略都显示`() {
        assertTrue(
            UpdateCheckDecision.shouldShowDialog(
                respectIgnored = false,
                ignoredVersionCode = 628,
                latestVersionCode = 628,
            )
        )
    }

    @Test
    fun `自动检查遇到已忽略的版本不显示`() {
        assertFalse(
            UpdateCheckDecision.shouldShowDialog(
                respectIgnored = true,
                ignoredVersionCode = 628,
                latestVersionCode = 628,
            )
        )
    }

    @Test
    fun `自动检查未忽略的版本显示`() {
        assertTrue(
            UpdateCheckDecision.shouldShowDialog(
                respectIgnored = true,
                ignoredVersionCode = UpdatePreferences.NO_IGNORED_VERSION,
                latestVersionCode = 628,
            )
        )
    }

    @Test
    fun `自动检查出现更新的版本时重新显示`() {
        assertTrue(
            UpdateCheckDecision.shouldShowDialog(
                respectIgnored = true,
                ignoredVersionCode = 628,
                latestVersionCode = 629,
            )
        )
    }
}
