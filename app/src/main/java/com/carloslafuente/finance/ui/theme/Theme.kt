package com.carloslafuente.finance.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FinanceLightColors = lightColorScheme(
    background = PaperBackground,
    onBackground = PaperInk,
    surface = PaperSurface,
    onSurface = PaperInk,
    surfaceVariant = PaperSurfaceVariant,
    onSurfaceVariant = PaperMuted,
    surfaceContainer = PaperSurfaceContainer,
    surfaceContainerLow = PaperSurfaceContainerLow,
    surfaceContainerHigh = PaperSurfaceContainerHigh,
    outline = PaperMuted,
    outlineVariant = PaperHairline,
    primary = PaperPrimary,
    onPrimary = PaperOnPrimary,
    primaryContainer = PaperPrimaryContainer,
    onPrimaryContainer = PaperOnPrimaryContainer,
    secondary = PaperSecondary,
    onSecondary = PaperOnSecondary,
    tertiary = PaperTertiary,
    onTertiary = PaperOnTertiary,
    error = PaperError,
    onError = PaperOnError,
    // Every remaining role is set explicitly so no default Material purple can leak in.
    secondaryContainer = Color(0xFFECE3D6),
    onSecondaryContainer = Color(0xFF2A231B),
    tertiaryContainer = Color(0xFFDFE4D5),
    onTertiaryContainer = Color(0xFF1C2414),
    errorContainer = Color(0xFFF3DCD5),
    onErrorContainer = Color(0xFF3A1A12),
    surfaceContainerLowest = Color(0xFFFFFDF9),
    surfaceContainerHighest = Color(0xFFE6DECF),
    surfaceBright = PaperBackground,
    surfaceDim = Color(0xFFE9E2D6),
    surfaceTint = PaperBackground,
    inverseSurface = Color(0xFF2E2A25),
    inverseOnSurface = Color(0xFFF3EEE6),
    inversePrimary = Color(0xFFD6A580),
    scrim = Color(0xFF000000),
)

private val FinanceDarkColors = darkColorScheme(
    background = CharcoalBackground,
    onBackground = CharcoalText,
    surface = CharcoalSurface,
    onSurface = CharcoalText,
    surfaceVariant = CharcoalSurfaceVariant,
    onSurfaceVariant = CharcoalMuted,
    surfaceContainer = CharcoalSurfaceContainer,
    surfaceContainerLow = CharcoalSurfaceContainerLow,
    surfaceContainerHigh = CharcoalSurfaceContainerHigh,
    outline = CharcoalMuted,
    outlineVariant = CharcoalHairline,
    primary = CharcoalPrimary,
    onPrimary = CharcoalOnPrimary,
    primaryContainer = CharcoalPrimaryContainer,
    onPrimaryContainer = CharcoalOnPrimaryContainer,
    secondary = CharcoalSecondary,
    onSecondary = CharcoalOnSecondary,
    tertiary = CharcoalTertiary,
    onTertiary = CharcoalOnTertiary,
    error = CharcoalError,
    onError = CharcoalOnError,
    secondaryContainer = Color(0xFF3A342D),
    onSecondaryContainer = Color(0xFFEDE4D6),
    tertiaryContainer = Color(0xFF343A2C),
    onTertiaryContainer = Color(0xFFDDE3D2),
    errorContainer = Color(0xFF5A2E24),
    onErrorContainer = Color(0xFFF3DCD5),
    surfaceContainerLowest = Color(0xFF121110),
    surfaceContainerHighest = Color(0xFF302D2A),
    surfaceBright = Color(0xFF2A2826),
    surfaceDim = CharcoalBackground,
    surfaceTint = CharcoalBackground,
    inverseSurface = Color(0xFFE8E2D6),
    inverseOnSurface = Color(0xFF2A2724),
    inversePrimary = Color(0xFF7A4A2E),
    scrim = Color(0xFF000000),
)

@Composable
fun FinanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) FinanceDarkColors else FinanceLightColors
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
