package com.sony.dtv.camera_tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// TV は暗い背景が基本のため DarkColorScheme を使用
private val DarkColors = darkColorScheme()

@Composable
fun CameraTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
