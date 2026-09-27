package com.levidor.kehribarvideo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val KehribarDarkColorScheme = darkColorScheme(
    primary = Champagne,
    onPrimary = Obsidian,
    secondary = AntiqueGold,
    onSecondary = Obsidian,
    background = Obsidian,
    onBackground = SoftIvory,
    surface = Ink,
    onSurface = SoftIvory,
    surfaceVariant = Carbon,
    onSurfaceVariant = MutedIvory,
    outline = Graphite,
    error = ErrorRed
)

private val FproLightColorScheme = lightColorScheme(
    primary = AntiqueGold,
    onPrimary = Color.White,
    secondary = DeepBrown,
    onSecondary = Color.White,
    background = Porcelain,
    onBackground = DeepBrown,
    surface = LightSurface,
    onSurface = DeepBrown,
    surfaceVariant = WarmWhite,
    onSurfaceVariant = Color(0xFF6F6558),
    outline = LightOutline,
    error = Color(0xFFB3261E)
)

@Composable
fun FproAiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) KehribarDarkColorScheme else FproLightColorScheme,
        typography = KehribarTypography,
        content = content
    )
}

@Composable
fun KehribarVideoTheme(content: @Composable () -> Unit) {
    FproAiTheme(content = content)
}
