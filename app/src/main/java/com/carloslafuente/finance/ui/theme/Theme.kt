package com.carloslafuente.finance.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Broker-style monochrome: pure black or white surfaces and ink-coloured controls, so the only
// colour on screen is green (tertiary) for gains and red (error) for losses.
private val FinanceLightColors = lightColorScheme(
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0B0B0C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0B0B0C),
    surfaceVariant = Color(0xFFF1F2F4),
    onSurfaceVariant = Color(0xFF6B7078),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F7F8),
    surfaceContainer = Color(0xFFF4F4F6),
    surfaceContainerHigh = Color(0xFFEEEFF1),
    surfaceContainerHighest = Color(0xFFE7E8EB),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE7E8EB),
    surfaceTint = Color(0xFFFFFFFF),
    outline = Color(0xFF8A8F98),
    outlineVariant = Color(0xFFE3E4E8),
    primary = Color(0xFF0B0B0C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF0B0B0C),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF3F434A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFECEDF0),
    onSecondaryContainer = Color(0xFF0B0B0C),
    tertiary = Color(0xFF0B8A4A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD6F2E2),
    onTertiaryContainer = Color(0xFF06351F),
    error = Color(0xFFD5352F),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBDEDC),
    onErrorContainer = Color(0xFF410E0B),
    inverseSurface = Color(0xFF1B1C1F),
    inverseOnSurface = Color(0xFFF4F4F6),
    inversePrimary = Color(0xFFFFFFFF),
    scrim = Color(0xFF000000),
)

private val FinanceDarkColors = darkColorScheme(
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1B1C1F),
    onSurfaceVariant = Color(0xFF9A9FA8),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0D0E10),
    surfaceContainer = Color(0xFF131416),
    surfaceContainerHigh = Color(0xFF1B1C1F),
    surfaceContainerHighest = Color(0xFF232529),
    surfaceBright = Color(0xFF2C2E33),
    surfaceDim = Color(0xFF000000),
    surfaceTint = Color(0xFF000000),
    outline = Color(0xFF777C85),
    outlineVariant = Color(0xFF232529),
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF0B0B0C),
    primaryContainer = Color(0xFFFFFFFF),
    onPrimaryContainer = Color(0xFF0B0B0C),
    secondary = Color(0xFFC9CCD2),
    onSecondary = Color(0xFF0B0B0C),
    secondaryContainer = Color(0xFF232529),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF2FD07F),
    onTertiary = Color(0xFF06351F),
    tertiaryContainer = Color(0xFF0F5132),
    onTertiaryContainer = Color(0xFFD6F2E2),
    error = Color(0xFFFF5C57),
    onError = Color(0xFF410E0B),
    errorContainer = Color(0xFF7A1D17),
    onErrorContainer = Color(0xFFFBDEDC),
    inverseSurface = Color(0xFFF4F4F6),
    inverseOnSurface = Color(0xFF1B1C1F),
    inversePrimary = Color(0xFF0B0B0C),
    scrim = Color(0xFF000000),
)

@Composable
fun FinanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) FinanceDarkColors else FinanceLightColors,
        content = content,
    )
}
