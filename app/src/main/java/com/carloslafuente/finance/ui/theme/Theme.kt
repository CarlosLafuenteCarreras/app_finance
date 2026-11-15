package com.carloslafuente.finance.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Cool slate neutrals with a navy primary. Green (tertiary) and red (error) are reserved for
// gains and losses, so they never appear as decoration.
private val FinanceLightColors = lightColorScheme(
    background = Color(0xFFF5F7FA),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFF5F7FA),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE5E9F0),
    onSurfaceVariant = Color(0xFF5B6472),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F3F7),
    surfaceContainer = Color(0xFFEBEFF4),
    surfaceContainerHigh = Color(0xFFE5E9F0),
    surfaceContainerHighest = Color(0xFFDDE2EA),
    surfaceBright = Color(0xFFF5F7FA),
    surfaceDim = Color(0xFFDDE2EA),
    surfaceTint = Color(0xFFF5F7FA),
    outline = Color(0xFF7B8494),
    outlineVariant = Color(0xFFD5DAE2),
    primary = Color(0xFF1D3F72),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3F8),
    onPrimaryContainer = Color(0xFF0D2547),
    secondary = Color(0xFF4A5568),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE3EE),
    onSecondaryContainer = Color(0xFF1A2333),
    tertiary = Color(0xFF16794A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3F0DF),
    onTertiaryContainer = Color(0xFF0A3A22),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    inverseSurface = Color(0xFF1F2733),
    inverseOnSurface = Color(0xFFEEF1F5),
    inversePrimary = Color(0xFF9DBBEA),
    scrim = Color(0xFF000000),
)

private val FinanceDarkColors = darkColorScheme(
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE6EAF0),
    surface = Color(0xFF0D1117),
    onSurface = Color(0xFFE6EAF0),
    surfaceVariant = Color(0xFF232A35),
    onSurfaceVariant = Color(0xFF9AA4B2),
    surfaceContainerLowest = Color(0xFF090C11),
    surfaceContainerLow = Color(0xFF12171E),
    surfaceContainer = Color(0xFF161B22),
    surfaceContainerHigh = Color(0xFF1C222B),
    surfaceContainerHighest = Color(0xFF232A35),
    surfaceBright = Color(0xFF2A323E),
    surfaceDim = Color(0xFF0D1117),
    surfaceTint = Color(0xFF0D1117),
    outline = Color(0xFF7D8796),
    outlineVariant = Color(0xFF2A323E),
    primary = Color(0xFF9DBBEA),
    onPrimary = Color(0xFF0D2547),
    primaryContainer = Color(0xFF1D3F72),
    onPrimaryContainer = Color(0xFFD6E3F8),
    secondary = Color(0xFFB7C2D3),
    onSecondary = Color(0xFF1F2937),
    secondaryContainer = Color(0xFF2B3544),
    onSecondaryContainer = Color(0xFFDCE3EE),
    tertiary = Color(0xFF5FD39A),
    onTertiary = Color(0xFF06351F),
    tertiaryContainer = Color(0xFF0F5132),
    onTertiaryContainer = Color(0xFFD3F0DF),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF5C1410),
    errorContainer = Color(0xFF7A1D17),
    onErrorContainer = Color(0xFFF9DEDC),
    inverseSurface = Color(0xFFE6EAF0),
    inverseOnSurface = Color(0xFF1F2733),
    inversePrimary = Color(0xFF1D3F72),
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
