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
    object SkyBlue {
        private val brand = BrandColors(
            primary                = Color(0xFF1E6DB5),
            onPrimary              = Color(0xFFFFFFFF),
            primaryContainer       = Color(0xFFD5E4FF),
            onPrimaryContainer     = Color(0xFF001B3D),
            secondary              = Color(0xFF34618E),
            onSecondary            = Color(0xFFFFFFFF),
            secondaryContainer     = Color(0xFFD3E4FF),
            onSecondaryContainer   = Color(0xFF001D34),
            tertiary               = Color(0xFF1B6E5C),
            onTertiary             = Color(0xFFFFFFFF),
            tertiaryContainer      = Color(0xFFA4F0DF),
            onTertiaryContainer    = Color(0xFF002019),
            inversePrimary         = Color(0xFFA4C9FF),
        )
        val Light = lightScheme(brand)
        private val darkBrand = BrandColors(
            primary                = Color(0xFFA4C9FF),
            onPrimary              = Color(0xFF003660),
            primaryContainer       = Color(0xFF004E83),
            onPrimaryContainer     = Color(0xFFD5E4FF),
            secondary              = Color(0xFFADCFF1),
            onSecondary            = Color(0xFF003353),
            secondaryContainer     = Color(0xFF194B70),
            onSecondaryContainer   = Color(0xFFD3E4FF),
            tertiary               = Color(0xFF88D8C6),
            onTertiary             = Color(0xFF00382E),
            tertiaryContainer      = Color(0xFF005045),
            onTertiaryContainer    = Color(0xFFA4F0DF),
            inversePrimary         = Color(0xFF1E6DB5),
        )
        val Dark = darkScheme(darkBrand)
    }

    object OceanBlue {
        private val brand = BrandColors(
            primary                = Color(0xFF0D47A1),
            onPrimary              = Color(0xFFFFFFFF),
            primaryContainer       = Color(0xFFD8E2FF),
            onPrimaryContainer     = Color(0xFF001845),
            secondary              = Color(0xFF3A5A8C),
            onSecondary            = Color(0xFFFFFFFF),
            secondaryContainer     = Color(0xFFD4E3FF),
            onSecondaryContainer   = Color(0xFF001C38),
            tertiary               = Color(0xFF1A5E6E),
            onTertiary             = Color(0xFFFFFFFF),
            tertiaryContainer      = Color(0xFFBCE9FF),
            onTertiaryContainer    = Color(0xFF001F27),
            inversePrimary         = Color(0xFFAAC7FF),
        )
        val Light = lightScheme(brand)
        private val darkBrand = BrandColors(
            primary                = Color(0xFFAAC7FF),
            onPrimary              = Color(0xFF002F65),
            primaryContainer       = Color(0xFF00458E),
            onPrimaryContainer     = Color(0xFFD8E2FF),
            secondary              = Color(0xFFAFC6F0),
            onSecondary            = Color(0xFF002D55),
            secondaryContainer     = Color(0xFF1F426D),
            onSecondaryContainer   = Color(0xFFD4E3FF),
            tertiary               = Color(0xFF90D1ED),
            onTertiary             = Color(0xFF003545),
            tertiaryContainer      = Color(0xFF004D5E),
            onTertiaryContainer    = Color(0xFFBCE9FF),
            inversePrimary         = Color(0xFF0D47A1),
        )
        val Dark = darkScheme(darkBrand)
    }

    object ForestGreen {
        private val brand = BrandColors(
            primary                = Color(0xFF2E7D32),
            onPrimary              = Color(0xFFFFFFFF),
            primaryContainer       = Color(0xFFA5E0A8),
            onPrimaryContainer     = Color(0xFF002204),
            secondary              = Color(0xFF3A6B3A),
            onSecondary            = Color(0xFFFFFFFF),
            secondaryContainer     = Color(0xFFBCF1B9),
            onSecondaryContainer   = Color(0xFF002204),
            tertiary               = Color(0xFF1B6E5C),
            onTertiary             = Color(0xFFFFFFFF),
            tertiaryContainer      = Color(0xFFA4F0DF),
            onTertiaryContainer    = Color(0xFF002019),
            inversePrimary         = Color(0xFF8CDA8F),
        )
        val Light = lightScheme(brand)
        private val darkBrand = BrandColors(
            primary                = Color(0xFF8CDA8F),
            onPrimary              = Color(0xFF00390B),
            primaryContainer       = Color(0xFF0F5519),
            onPrimaryContainer     = Color(0xFFA5E0A8),
            secondary              = Color(0xFFA0D59D),
            onSecondary            = Color(0xFF00390C),
            secondaryContainer     = Color(0xFF205220),
            onSecondaryContainer   = Color(0xFFBCF1B9),
            tertiary               = Color(0xFF88D8C6),
            onTertiary             = Color(0xFF00382E),
            tertiaryContainer      = Color(0xFF005045),
            onTertiaryContainer    = Color(0xFFA4F0DF),
            inversePrimary         = Color(0xFF2E7D32),
        )
        val Dark = darkScheme(darkBrand)
    }

    object WarmOrange {
        private val brand = BrandColors(
            primary                = Color(0xFFB95B00),
            onPrimary              = Color(0xFFFFFFFF),
            primaryContainer       = Color(0xFFFFDCC3),
            onPrimaryContainer     = Color(0xFF361700),
            secondary              = Color(0xFF6E5530),
            onSecondary            = Color(0xFFFFFFFF),
            secondaryContainer     = Color(0xFFFADCAB),
            onSecondaryContainer   = Color(0xFF241700),
            tertiary               = Color(0xFF3F6E4F),
            onTertiary             = Color(0xFFFFFFFF),
            tertiaryContainer      = Color(0xFFC0F7CF),
            onTertiaryContainer    = Color(0xFF002110),
            inversePrimary         = Color(0xFFFFB77D),
        )
        val Light = lightScheme(brand)
        private val darkBrand = BrandColors(
            primary                = Color(0xFFFFB77D),
            onPrimary              = Color(0xFF622D00),
            primaryContainer       = Color(0xFF8A4300),
            onPrimaryContainer     = Color(0xFFFFDCC3),
            secondary              = Color(0xFFDDBF92),
            onSecondary            = Color(0xFF3C2808),
            secondaryContainer     = Color(0xFF543E1D),
            onSecondaryContainer   = Color(0xFFFADCAB),
            tertiary               = Color(0xFFA5DAB4),
            onTertiary             = Color(0xFF0E3925),
            tertiaryContainer      = Color(0xFF26503A),
            onTertiaryContainer    = Color(0xFFC0F7CF),
            inversePrimary         = Color(0xFFB95B00),
        )
        val Dark = darkScheme(darkBrand)
    }

    object LavenderPurple {
        private val brand = BrandColors(
            primary                = Color(0xFF7B3FA0),
            onPrimary              = Color(0xFFFFFFFF),
            primaryContainer       = Color(0xFFF3DAFF),
            onPrimaryContainer     = Color(0xFF2D004F),
            secondary              = Color(0xFF65558F),
            onSecondary            = Color(0xFFFFFFFF),
            secondaryContainer     = Color(0xFFE9DDFF),
            onSecondaryContainer   = Color(0xFF210F48),
            tertiary               = Color(0xFF8B3A6B),
            onTertiary             = Color(0xFFFFFFFF),
            tertiaryContainer      = Color(0xFFFFD8EC),
            onTertiaryContainer    = Color(0xFF390026),
            inversePrimary         = Color(0xFFDAB9F5),
        )
        val Light = lightScheme(brand)
        private val darkBrand = BrandColors(
            primary                = Color(0xFFDAB9F5),
            onPrimary              = Color(0xFF480073),
            primaryContainer       = Color(0xFF62248B),
            onPrimaryContainer     = Color(0xFFF3DAFF),
            secondary              = Color(0xFFCCC1EB),
            onSecondary            = Color(0xFF36275F),
            secondaryContainer     = Color(0xFF4D3E76),
            onSecondaryContainer   = Color(0xFFE9DDFF),
            tertiary               = Color(0xFFFFAFDB),
            onTertiary             = Color(0xFF54003F),
            tertiaryContainer      = Color(0xFF6F2151),
            onTertiaryContainer    = Color(0xFFFFD8EC),
            inversePrimary         = Color(0xFF7B3FA0),
        )
        val Dark = darkScheme(darkBrand)
    }

    object RoseRed {
        private val brand = BrandColors(
            primary                = Color(0xFFB52630),
            onPrimary              = Color(0xFFFFFFFF),
            primaryContainer       = Color(0xFFFFDAD6),
            onPrimaryContainer     = Color(0xFF410006),
            secondary              = Color(0xFF8C4150),
            onSecondary            = Color(0xFFFFFFFF),
            secondaryContainer     = Color(0xFFFFD9DE),
            onSecondaryContainer   = Color(0xFF3A0712),
            tertiary               = Color(0xFF7C572A),
            onTertiary             = Color(0xFFFFFFFF),
            tertiaryContainer      = Color(0xFFFFDDB9),
            onTertiaryContainer    = Color(0xFF2C1600),
            inversePrimary         = Color(0xFFFFB3B0),
        )
        val Light = lightScheme(brand)
        private val darkBrand = BrandColors(
            primary                = Color(0xFFFFB3B0),
            onPrimary              = Color(0xFF680012),
            primaryContainer       = Color(0xFF8E0A22),
            onPrimaryContainer     = Color(0xFFFFDAD6),
            secondary              = Color(0xFFFFB2BC),
            onSecondary            = Color(0xFF541723),
            secondaryContainer     = Color(0xFF6F2D39),
            onSecondaryContainer   = Color(0xFFFFD9DE),
            tertiary               = Color(0xFFEBC290),
            onTertiary             = Color(0xFF462A04),
            tertiaryContainer      = Color(0xFF5F3F18),
            onTertiaryContainer    = Color(0xFFFFDDB9),
            inversePrimary         = Color(0xFFB52630),
        )
        val Dark = darkScheme(darkBrand)
    }
}

// ── Status colors ────────────────────────────────────────────────────

val StatusConnected = Color(0xFF4CAF50)
val StatusDisconnected = Color(0xFFF44336)
val StatusConnecting = Color(0xFFFF9800)
