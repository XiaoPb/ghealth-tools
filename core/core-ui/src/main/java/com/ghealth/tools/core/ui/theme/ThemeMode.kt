package com.ghealth.tools.core.ui.theme

import androidx.compose.ui.graphics.Color

enum class ThemeMode(
    val key: String,
    val displayName: String,
    val previewColor: Color,
    val lightColors: ThemeColors,
    val darkColors: ThemeColors,
) {
    SKY_BLUE(
        key = "sky_blue",
        displayName = "天蓝色",
        previewColor = AppColors.SkyBlue.Light.primary,
        lightColors = AppColors.SkyBlue.Light,
        darkColors = AppColors.SkyBlue.Dark,
    ),
    OCEAN_BLUE(
        key = "ocean_blue",
        displayName = "海洋蓝",
        previewColor = AppColors.OceanBlue.Light.primary,
        lightColors = AppColors.OceanBlue.Light,
        darkColors = AppColors.OceanBlue.Dark,
    ),
    FOREST_GREEN(
        key = "forest_green",
        displayName = "森林绿",
        previewColor = AppColors.ForestGreen.Light.primary,
        lightColors = AppColors.ForestGreen.Light,
        darkColors = AppColors.ForestGreen.Dark,
    ),
    WARM_ORANGE(
        key = "warm_orange",
        displayName = "暖阳橙",
        previewColor = AppColors.WarmOrange.Light.primary,
        lightColors = AppColors.WarmOrange.Light,
        darkColors = AppColors.WarmOrange.Dark,
    ),
    LAVENDER_PURPLE(
        key = "lavender_purple",
        displayName = "薰衣紫",
        previewColor = AppColors.LavenderPurple.Light.primary,
        lightColors = AppColors.LavenderPurple.Light,
        darkColors = AppColors.LavenderPurple.Dark,
    ),
    ROSE_RED(
        key = "rose_red",
        displayName = "玫瑰红",
        previewColor = AppColors.RoseRed.Light.primary,
        lightColors = AppColors.RoseRed.Light,
        darkColors = AppColors.RoseRed.Dark,
    );

    companion object {
        fun fromKey(key: String): ThemeMode {
            return entries.find { it.key == key } ?: SKY_BLUE
        }
    }
}
