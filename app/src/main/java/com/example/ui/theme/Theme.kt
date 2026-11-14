package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val EasyTerminalDarkColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = OnCyan,
    primaryContainer = CyanContainer,
    onPrimaryContainer = CyanAccent,
    secondary = EmeraldAccent,
    onSecondary = OnEmerald,
    secondaryContainer = EmeraldContainer,
    onSecondaryContainer = EmeraldAccent,
    tertiary = AmberWarning,
    onTertiary = ObsidianCanvas,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = AmberWarning,
    error = CoralError,
    onError = ObsidianCanvas,
    errorContainer = CoralContainer,
    onErrorContainer = CoralError,
    background = ObsidianCanvas,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedSlate,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = TerminalCanvas,
    surfaceContainerLow = SlateSurface,
    surfaceContainer = ElevatedSlate,
    surfaceContainerHigh = ElevatedSlateHigh,
    outline = HairlineBorder,
    outlineVariant = TextMuted
)

val EasyTerminalShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun EasyTerminalTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = EasyTerminalDarkColorScheme,
        typography = EasyTerminalTypography,
        shapes = EasyTerminalShapes,
        content = content
    )
}
