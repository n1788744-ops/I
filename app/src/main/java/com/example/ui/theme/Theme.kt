package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = CyanPulse,
    onPrimary = OnCyan,
    primaryContainer = CyanContainer,
    onPrimaryContainer = CyanPulse,
    secondary = EmeraldSignal,
    onSecondary = OnEmerald,
    secondaryContainer = EmeraldContainer,
    onSecondaryContainer = EmeraldSignal,
    tertiary = AmberAlert,
    onTertiary = Color.Black,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = AmberAlert,
    error = CoralError,
    onError = Color.White,
    errorContainer = CoralContainer,
    onErrorContainer = CoralError,
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerHigh = ObsidianSurfaceBright,
    outline = ObsidianOutline,
    outlineVariant = ObsidianSurfaceBright,
)

private val LightColorScheme = lightColorScheme(
    primary = CyanDeepLight,
    onPrimary = Color.White,
    primaryContainer = CyanContainerLight,
    onPrimaryContainer = CyanDeepLight,
    secondary = EmeraldDeepLight,
    onSecondary = Color.White,
    secondaryContainer = EmeraldContainerLight,
    onSecondaryContainer = EmeraldDeepLight,
    tertiary = AmberAlert,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    error = CoralError,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),
    background = SlateBgLight,
    onBackground = TextPrimaryLight,
    surface = SlateSurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SlateSurfaceHighLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerHigh = SlateSurfaceHighLight,
    outline = SlateOutlineLight,
    outlineVariant = SlateOutlineLight,
)

val RouterShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun Router4GTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = RouterTypography,
        shapes = RouterShapes,
        content = content,
    )
}
