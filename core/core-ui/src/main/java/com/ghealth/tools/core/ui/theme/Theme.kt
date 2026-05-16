package com.ghealth.tools.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private fun getColorSchemeForTheme(themeMode: ThemeMode, darkTheme: Boolean) = when (themeMode) {
    ThemeMode.OCEAN_BLUE -> if (darkTheme) {
        darkColorScheme(
            primary = AppColors.OceanBlue.PrimaryDark,
            secondary = AppColors.OceanBlue.SecondaryDark,
            tertiary = AppColors.OceanBlue.TertiaryDark,
            surface = SurfaceDark
        )
    } else {
        lightColorScheme(
            primary = AppColors.OceanBlue.PrimaryLight,
            secondary = AppColors.OceanBlue.SecondaryLight,
            tertiary = AppColors.OceanBlue.TertiaryLight,
            surface = SurfaceLight
        )
    }

    ThemeMode.FOREST_GREEN -> if (darkTheme) {
        darkColorScheme(
            primary = AppColors.ForestGreen.PrimaryDark,
            secondary = AppColors.ForestGreen.SecondaryDark,
            tertiary = AppColors.ForestGreen.TertiaryDark,
            surface = SurfaceDark
        )
    } else {
        lightColorScheme(
            primary = AppColors.ForestGreen.PrimaryLight,
            secondary = AppColors.ForestGreen.SecondaryLight,
            tertiary = AppColors.ForestGreen.TertiaryLight,
            surface = SurfaceLight
        )
    }

    ThemeMode.WARM_ORANGE -> if (darkTheme) {
        darkColorScheme(
            primary = AppColors.WarmOrange.PrimaryDark,
            secondary = AppColors.WarmOrange.SecondaryDark,
            tertiary = AppColors.WarmOrange.TertiaryDark,
            surface = SurfaceDark
        )
    } else {
        lightColorScheme(
            primary = AppColors.WarmOrange.PrimaryLight,
            secondary = AppColors.WarmOrange.SecondaryLight,
            tertiary = AppColors.WarmOrange.TertiaryLight,
            surface = SurfaceLight
        )
    }

    ThemeMode.LAVENDER_PURPLE -> if (darkTheme) {
        darkColorScheme(
            primary = AppColors.LavenderPurple.PrimaryDark,
            secondary = AppColors.LavenderPurple.SecondaryDark,
            tertiary = AppColors.LavenderPurple.TertiaryDark,
            surface = SurfaceDark
        )
    } else {
        lightColorScheme(
            primary = AppColors.LavenderPurple.PrimaryLight,
            secondary = AppColors.LavenderPurple.SecondaryLight,
            tertiary = AppColors.LavenderPurple.TertiaryLight,
            surface = SurfaceLight
        )
    }

    ThemeMode.ROSE_RED -> if (darkTheme) {
        darkColorScheme(
            primary = AppColors.RoseRed.PrimaryDark,
            secondary = AppColors.RoseRed.SecondaryDark,
            tertiary = AppColors.RoseRed.TertiaryDark,
            surface = SurfaceDark
        )
    } else {
        lightColorScheme(
            primary = AppColors.RoseRed.PrimaryLight,
            secondary = AppColors.RoseRed.SecondaryLight,
            tertiary = AppColors.RoseRed.TertiaryLight,
            surface = SurfaceLight
        )
    }
}

@Composable
fun GHealthTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeMode: ThemeMode = ThemeMode.OCEAN_BLUE,
    content: @Composable () -> Unit,
) {
    val colorScheme = getColorSchemeForTheme(themeMode, darkTheme)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
