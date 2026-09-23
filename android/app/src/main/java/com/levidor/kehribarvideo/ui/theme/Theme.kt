package com.levidor.kehribarvideo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val KehribarDarkColorScheme = darkColorScheme(
    primary = Amber,
    onPrimary = Color.Black,
    secondary = AmberDark,
    background = BackgroundDark,
    onBackground = OnDark,
    surface = SurfaceDark,
    onSurface = OnDark,
    surfaceVariant = SurfaceVariantDark,
    error = ErrorRed
)

@Composable
fun KehribarVideoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KehribarDarkColorScheme,
        typography = KehribarTypography,
        content = content
    )
}
