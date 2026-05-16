package com.ghealth.tools.core.ui.theme

import androidx.compose.ui.graphics.Color

enum class ThemeMode(
    val key: String,
    val displayName: String,
    val previewColor: Color
) {
    OCEAN_BLUE(
        key = "ocean_blue",
        displayName = "海洋蓝",
        previewColor = AppColors.OceanBlue.PrimaryLight
    ),
    FOREST_GREEN(
        key = "forest_green",
        displayName = "森林绿",
        previewColor = AppColors.ForestGreen.PrimaryLight
    ),
    WARM_ORANGE(
        key = "warm_orange",
        displayName = "暖阳橙",
        previewColor = AppColors.WarmOrange.PrimaryLight
    ),
    LAVENDER_PURPLE(
        key = "lavender_purple",
        displayName = "薰衣紫",
        previewColor = AppColors.LavenderPurple.PrimaryLight
    ),
    ROSE_RED(
        key = "rose_red",
        displayName = "玫瑰红",
        previewColor = AppColors.RoseRed.PrimaryLight
    );

    companion object {
        fun fromKey(key: String): ThemeMode {
            return entries.find { it.key == key } ?: OCEAN_BLUE
        }
    }
}
