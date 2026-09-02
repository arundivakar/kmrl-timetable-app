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
    onSurface = TextDark,
    onSurfaceVariant = TextGrey,
    outline = BorderGrey,
    outlineVariant = BorderGrey
)

val KmrlDarkColorScheme = androidx.compose.material3.darkColorScheme(
    primary = KmrlTeal,
    onPrimary = Color.White,
    primaryContainer = KmrlTeal.copy(alpha = 0.2f),
    onPrimaryContainer = KmrlTeal,
    secondary = KmrlLime,
    onSecondary = Color.White,
    background = BgDark,
    surface = SurfaceDark,
    surfaceVariant = BorderGreyDark,
    onBackground = TextLight,
    onSurface = TextLight,
    onSurfaceVariant = TextGreyDark,
    outline = BorderGreyDark,
    outlineVariant = BorderGreyDark
)

@Composable
fun KmrlTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) KmrlDarkColorScheme else KmrlLightColorScheme
    
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
