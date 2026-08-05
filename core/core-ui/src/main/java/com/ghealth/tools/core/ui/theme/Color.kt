package com.ghealth.tools.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ── Complete M3 color roles ──────────────────────────────────────────

data class ThemeColors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val inversePrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,
)

fun ThemeColors.toLightColorScheme() = lightColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    inversePrimary = inversePrimary,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    error = error,
    onError = onError,
    errorContainer = errorContainer,
    onErrorContainer = onErrorContainer,
    background = background,
    onBackground = onBackground,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    outline = outline,
    outlineVariant = outlineVariant,
    surfaceContainerLow = surfaceContainerLow,
    surfaceContainer = surfaceContainer,
    surfaceContainerHigh = surfaceContainerHigh,
    surfaceContainerHighest = surfaceContainerHighest,
    inverseSurface = inverseSurface,
    inverseOnSurface = inverseOnSurface,
)

fun ThemeColors.toDarkColorScheme() = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    inversePrimary = inversePrimary,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    error = error,
    onError = onError,
    errorContainer = errorContainer,
    onErrorContainer = onErrorContainer,
    background = background,
    onBackground = onBackground,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    outline = outline,
    outlineVariant = outlineVariant,
    surfaceContainerLow = surfaceContainerLow,
    surfaceContainer = surfaceContainer,
    surfaceContainerHigh = surfaceContainerHigh,
    surfaceContainerHighest = surfaceContainerHighest,
    inverseSurface = inverseSurface,
    inverseOnSurface = inverseOnSurface,
)

// ── Per-theme brand color seeds ──────────────────────────────────────

private data class BrandColors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val inversePrimary: Color,
)

// ── Shared neutral & error tokens ────────────────────────────────────

private fun lightScheme(brand: BrandColors) = ThemeColors(
    primary = brand.primary,
    onPrimary = brand.onPrimary,
    primaryContainer = brand.primaryContainer,
    onPrimaryContainer = brand.onPrimaryContainer,
    inversePrimary = brand.inversePrimary,
    secondary = brand.secondary,
    onSecondary = brand.onSecondary,
    secondaryContainer = brand.secondaryContainer,
    onSecondaryContainer = brand.onSecondaryContainer,
    tertiary = brand.tertiary,
    onTertiary = brand.onTertiary,
    tertiaryContainer = brand.tertiaryContainer,
    onTertiaryContainer = brand.onTertiaryContainer,
    // shared light neutrals
    background              = Color(0xFFEDEFF5),
    onBackground            = Color(0xFF1A1C1E),
    surface                 = Color(0xFFEDEFF5),
    onSurface               = Color(0xFF1A1C1E),
    surfaceVariant          = Color(0xFFD5D8E0),
    onSurfaceVariant        = Color(0xFF43474E),
    outline                 = Color(0xFF73777F),
    outlineVariant          = Color(0xFFC3C6CF),
    surfaceContainerLow     = Color(0xFFFBFBFF),
    surfaceContainer        = Color(0xFFFAFBFC),
    surfaceContainerHigh    = Color(0xFFF7F8FB),
    surfaceContainerHighest = Color(0xFFF3F5FA),
    inverseSurface          = Color(0xFF2F3033),
    inverseOnSurface        = Color(0xFFF1F0F4),
    // shared light errors
    error           = Color(0xFFBA1A1A),
    onError         = Color(0xFFFFFFFF),
    errorContainer  = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private fun darkScheme(brand: BrandColors) = ThemeColors(
    primary = brand.primary,
    onPrimary = brand.onPrimary,
    primaryContainer = brand.primaryContainer,
    onPrimaryContainer = brand.onPrimaryContainer,
    inversePrimary = brand.inversePrimary,
    secondary = brand.secondary,
    onSecondary = brand.onSecondary,
    secondaryContainer = brand.secondaryContainer,
    onSecondaryContainer = brand.onSecondaryContainer,
    tertiary = brand.tertiary,
    onTertiary = brand.onTertiary,
    tertiaryContainer = brand.tertiaryContainer,
    onTertiaryContainer = brand.onTertiaryContainer,
    // shared dark neutrals
    background              = Color(0xFF111318),
    onBackground            = Color(0xFFE2E2E9),
    surface                 = Color(0xFF111318),
    onSurface               = Color(0xFFE2E2E9),
    surfaceVariant          = Color(0xFF43474E),
    onSurfaceVariant        = Color(0xFFC3C6CF),
    outline                 = Color(0xFF8D9199),
    outlineVariant          = Color(0xFF43474E),
    surfaceContainerLow     = Color(0xFF0D0F13),
    surfaceContainer        = Color(0xFF191C20),
    surfaceContainerHigh    = Color(0xFF23262B),
    surfaceContainerHighest = Color(0xFF2E3137),
    inverseSurface          = Color(0xFFE2E2E9),
    inverseOnSurface        = Color(0xFF2F3033),
    // shared dark errors
    error           = Color(0xFFFFB4AB),
    onError         = Color(0xFF690005),
    errorContainer  = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

// ── Theme palettes ───────────────────────────────────────────────────

object AppColors {
    // Tailwind blue-500 (#3B82F6) 衍生的 M3 调色板
    object Blue500 {
        private val brand = BrandColors(
            primary                = Color(0xFF3B82F6),
            onPrimary              = Color(0xFFFFFFFF),
            primaryContainer       = Color(0xFFDBE7FF),
            onPrimaryContainer     = Color(0xFF001A41),
            secondary              = Color(0xFF555F71),
            onSecondary            = Color(0xFFFFFFFF),
            secondaryContainer     = Color(0xFFD9E3F8),
            onSecondaryContainer   = Color(0xFF121C2B),
            tertiary               = Color(0xFF1F6E5C),
            onTertiary             = Color(0xFFFFFFFF),
            tertiaryContainer      = Color(0xFFA4F0DF),
            onTertiaryContainer    = Color(0xFF002019),
            inversePrimary         = Color(0xFFB0C6FF),
        )
        val Light = lightScheme(brand)
        private val darkBrand = BrandColors(
            primary                = Color(0xFFB0C6FF),
            onPrimary              = Color(0xFF002E6F),
            primaryContainer       = Color(0xFF00429A),
            onPrimaryContainer     = Color(0xFFDBE7FF),
            secondary              = Color(0xFFBDC7DC),
            onSecondary            = Color(0xFF273141),
            secondaryContainer     = Color(0xFF3D4858),
            onSecondaryContainer   = Color(0xFFD9E3F8),
            tertiary               = Color(0xFF88D8C6),
            onTertiary             = Color(0xFF00382E),
            tertiaryContainer      = Color(0xFF005045),
            onTertiaryContainer    = Color(0xFFA4F0DF),
            inversePrimary         = Color(0xFF3B82F6),
        )
        val Dark = darkScheme(darkBrand)
    }

    // Tailwind emerald-500 (#10B981) 衍生的 M3 调色板
    object Emerald500 {
        private val brand = BrandColors(
            primary                = Color(0xFF10B981),
            onPrimary              = Color(0xFFFFFFFF),
            primaryContainer       = Color(0xFFA5F0D8),
            onPrimaryContainer     = Color(0xFF002117),
            secondary              = Color(0xFF4C6358),
            onSecondary            = Color(0xFFFFFFFF),
            secondaryContainer     = Color(0xFFCFE9DA),
            onSecondaryContainer   = Color(0xFF092018),
            tertiary               = Color(0xFF426278),
            onTertiary             = Color(0xFFFFFFFF),
            tertiaryContainer      = Color(0xFFC7E8FF),
            onTertiaryContainer    = Color(0xFF001E2E),
            inversePrimary         = Color(0xFF54DDA6),
        )
        val Light = lightScheme(brand)
        private val darkBrand = BrandColors(
            primary                = Color(0xFF54DDA6),
            onPrimary              = Color(0xFF003824),
            primaryContainer       = Color(0xFF005138),
            onPrimaryContainer     = Color(0xFFA5F0D8),
            secondary              = Color(0xFFB3CDBE),
            onSecondary            = Color(0xFF1D352A),
            secondaryContainer     = Color(0xFF344B41),
            onSecondaryContainer   = Color(0xFFCFE9DA),
            tertiary               = Color(0xFFAACBE5),
            onTertiary             = Color(0xFF113548),
            tertiaryContainer      = Color(0xFF2A4C60),
            onTertiaryContainer    = Color(0xFFC7E8FF),
            inversePrimary         = Color(0xFF10B981),
        )
        val Dark = darkScheme(darkBrand)
    }

    // Tailwind pink-500 (#EC4899) 衍生的 M3 调色板
    object Pink500 {
        private val brand = BrandColors(
            primary                = Color(0xFFEC4899),
            onPrimary              = Color(0xFFFFFFFF),
            primaryContainer       = Color(0xFFFFD9E2),
            onPrimaryContainer     = Color(0xFF3E001D),
            secondary              = Color(0xFF745760),
            onSecondary            = Color(0xFFFFFFFF),
            secondaryContainer     = Color(0xFFFFD9E2),
            onSecondaryContainer   = Color(0xFF2B151C),
            tertiary               = Color(0xFF7D5733),
            onTertiary             = Color(0xFFFFFFFF),
            tertiaryContainer      = Color(0xFFFFDCC2),
            onTertiaryContainer    = Color(0xFF2E1500),
            inversePrimary         = Color(0xFFFFB0CC),
        )
        val Light = lightScheme(brand)
        private val darkBrand = BrandColors(
            primary                = Color(0xFFFFB0CC),
            onPrimary              = Color(0xFF650038),
            primaryContainer       = Color(0xFFB30E73),
            onPrimaryContainer     = Color(0xFFFFD9E2),
            secondary              = Color(0xFFE3BDC7),
            onSecondary            = Color(0xFF42272E),
            secondaryContainer     = Color(0xFF5A3D44),
            onSecondaryContainer   = Color(0xFFFFD9E2),
            tertiary               = Color(0xFFF0BD86),
            onTertiary             = Color(0xFF482900),
            tertiaryContainer      = Color(0xFF623F10),
            onTertiaryContainer    = Color(0xFFFFDCC2),
            inversePrimary         = Color(0xFFEC4899),
        )
        val Dark = darkScheme(darkBrand)
    }

    // Tailwind violet-500 (#8B5CF6) 衍生的 M3 调色板
    object Violet500 {
        private val brand = BrandColors(
            primary                = Color(0xFF8B5CF6),
            onPrimary              = Color(0xFFFFFFFF),
            primaryContainer       = Color(0xFFEBDCFF),
            onPrimaryContainer     = Color(0xFF2A0066),
            secondary              = Color(0xFF62597C),
            onSecondary            = Color(0xFFFFFFFF),
            secondaryContainer     = Color(0xFFE8DDFF),
            onSecondaryContainer   = Color(0xFF1E1635),
            tertiary               = Color(0xFF7E5260),
            onTertiary             = Color(0xFFFFFFFF),
            tertiaryContainer      = Color(0xFFFFD9E2),
            onTertiaryContainer    = Color(0xFF31101D),
            inversePrimary         = Color(0xFFC9B0FF),
        )
        val Light = lightScheme(brand)
        private val darkBrand = BrandColors(
            primary                = Color(0xFFC9B0FF),
            onPrimary              = Color(0xFF3F008F),
            primaryContainer       = Color(0xFF6E2BD6),
            onPrimaryContainer     = Color(0xFFEBDCFF),
            secondary              = Color(0xFFCBC0E9),
            onSecondary            = Color(0xFF332B4B),
            secondaryContainer     = Color(0xFF4A4263),
            onSecondaryContainer   = Color(0xFFE8DDFF),
            tertiary               = Color(0xFFF0B0C5),
            onTertiary             = Color(0xFF4A2535),
            tertiaryContainer      = Color(0xFF643B4C),
            onTertiaryContainer    = Color(0xFFFFD9E2),
            inversePrimary         = Color(0xFF8B5CF6),
        )
        val Dark = darkScheme(darkBrand)
    }
}

// ── Status colors ────────────────────────────────────────────────────

val StatusConnected = Color(0xFF4CAF50)
val StatusDisconnected = Color(0xFFF44336)
val StatusConnecting = Color(0xFFFF9800)
