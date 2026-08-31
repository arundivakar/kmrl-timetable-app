package com.example.kmrltimetable.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val KmrlLightColorScheme = lightColorScheme(
    primary = KmrlTeal,
    onPrimary = Color.White,
    primaryContainer = KmrlTeal.copy(alpha = 0.1f),
    onPrimaryContainer = KmrlTeal,
    secondary = KmrlLime,
    onSecondary = Color.White,
    background = BgLight,
    surface = BgLight,
    surfaceVariant = SurfaceLight,
    onBackground = TextDark,
    onSurface = TextDark
)

@Composable
fun KmrlTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KmrlLightColorScheme,
        content = content
    )
}
