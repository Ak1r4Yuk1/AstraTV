package com.stalker.player.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkBg = Color(0xFF11161D)
val DarkWidget = Color(0xFF1A212B)
val AccentCyan = Color(0xFF7FA6C9)
val TextPrimary = Color(0xFFE7EDF4)
val TextSecondary = Color(0xFF8C99AA)

private val CleanColors = darkColorScheme(
    primary = AccentCyan,
    secondary = AccentCyan,
    background = DarkBg,
    surface = DarkWidget,
    onPrimary = DarkBg,
    onSecondary = DarkBg,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = Color(0xFF2A3442),
)

@Composable
fun StalkerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CleanColors,
        typography = Typography(),
        content = content
    )
}