package com.sony.dtv.camera_tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// TV は暗い背景が基本のため DarkColorScheme を使用し、配色は TvColors に揃える。
private val DarkColors = darkColorScheme(
    primary = TvColors.Accent,
    onPrimary = TvColors.OnSurface,
    secondary = TvColors.Accent,
    background = TvColors.Background,
    onBackground = TvColors.OnSurface,
    surface = TvColors.Surface,
    onSurface = TvColors.OnSurface,
    surfaceVariant = TvColors.SurfaceVariant,
    onSurfaceVariant = TvColors.OnSurfaceMuted,
    error = TvColors.Danger,
)

private val AppShapes = Shapes(
    small = TvShapes.Small,
    medium = TvShapes.Medium,
    large = TvShapes.Large,
)

@Composable
fun CameraTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        shapes = AppShapes,
        content = content,
    )
}
