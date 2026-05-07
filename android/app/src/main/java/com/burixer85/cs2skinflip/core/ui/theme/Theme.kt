package com.burixer85.cs2skinflip.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AccentOrange,
    onPrimary = Background,
    primaryContainer = SurfaceElevated,
    onPrimaryContainer = AccentOrangeVariant,
    secondary = AccentBlue,
    onSecondary = Background,
    secondaryContainer = SurfaceVariant,
    onSecondaryContainer = AccentBlue,
    tertiary = AccentGreen,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = AccentRed,
    onError = Background,
    outline = DividerColor,
    outlineVariant = TextTertiary
)

@Composable
fun CS2SkinFlipTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = CS2Typography,
        content = content
    )
}
