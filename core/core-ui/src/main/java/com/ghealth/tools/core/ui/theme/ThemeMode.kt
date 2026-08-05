package com.ghealth.tools.core.ui.theme

import androidx.compose.ui.graphics.Color

enum class ThemeMode(
    val key: String,
    val displayName: String,
    val previewColor: Color,
    val lightColors: ThemeColors,
    val darkColors: ThemeColors,
) {
    BLUE_500(
        key = "blue_500",
        displayName = "活力蓝",
        previewColor = AppColors.Blue500.Light.primary,
        lightColors = AppColors.Blue500.Light,
        darkColors = AppColors.Blue500.Dark,
    ),
    EMERALD_500(
        key = "emerald_500",
        displayName = "翡翠绿",
        previewColor = AppColors.Emerald500.Light.primary,
        lightColors = AppColors.Emerald500.Light,
        darkColors = AppColors.Emerald500.Dark,
    ),
    PINK_500(
        key = "pink_500",
        displayName = "樱花粉",
        previewColor = AppColors.Pink500.Light.primary,
        lightColors = AppColors.Pink500.Light,
        darkColors = AppColors.Pink500.Dark,
    ),
    VIOLET_500(
        key = "violet_500",
        displayName = "紫罗兰",
        previewColor = AppColors.Violet500.Light.primary,
        lightColors = AppColors.Violet500.Light,
        darkColors = AppColors.Violet500.Dark,
    );

    companion object {
        fun fromKey(key: String): ThemeMode {
            return entries.find { it.key == key } ?: BLUE_500
        }
    }
}
