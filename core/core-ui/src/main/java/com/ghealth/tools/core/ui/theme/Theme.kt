package com.ghealth.tools.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun GHealthTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeMode: ThemeMode = ThemeMode.SKY_BLUE,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) {
        themeMode.darkColors.toDarkColorScheme()
    } else {
        themeMode.lightColors.toLightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
