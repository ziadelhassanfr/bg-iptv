package com.bgiptv.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val BgBlue = Color(0xFF4A9EFF)
val BgRed = Color(0xFFFF4444)
val BgGreen = Color(0xFF44CC77)
val BgYellow = Color(0xFFFFCC00)
val BgSurface = Color(0xFF121212)
val BgSurfaceVariant = Color(0xFF1E1E1E)
val BgOverlay = Color(0xB3000000) // 70% black

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BgIptvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = BgBlue,
            secondary = BgGreen,
            error = BgRed,
            background = Color.Black,
            surface = BgSurface,
            surfaceVariant = BgSurfaceVariant,
            onPrimary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White,
        ),
        content = content
    )
}
