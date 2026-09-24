package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = MidnightBg,
    primaryContainer = SurfaceVariantDark,
    onPrimaryContainer = ElectricCyan,
    secondary = EmeraldSuccess,
    onSecondary = MidnightBg,
    tertiary = AmberWarning,
    onTertiary = MidnightBg,
    background = MidnightBg,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondary,
    error = RoseHazard,
    onError = MidnightBg
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
