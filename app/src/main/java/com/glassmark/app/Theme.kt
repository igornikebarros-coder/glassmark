package com.glassmark.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF090B12)
val Glass = Color(0x24FFFFFF)
val TextMain = Color(0xFFF4F5FA)
val Muted = Color(0xFF9CA3B5)
val Accent = Color(0xFFB9C7FF)

@Composable
fun GlassmarkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Ink, surface = Color(0xFF111521), primary = Accent,
            onBackground = TextMain, onSurface = TextMain
        ),
        content = content
    )
}
