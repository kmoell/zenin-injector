package com.zenin.injector

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ZeninColors = darkColorScheme(
    primary = Color(0xFF7C5CFF),
    onPrimary = Color.White,
    background = Color(0xFF0D0D12),
    surface = Color(0xFF16161F),
    onSurface = Color.White
)

@Composable
fun ZeninTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ZeninColors,
        content = content
    )
}
