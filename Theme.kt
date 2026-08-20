package com.nova.assistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val NovaAccent = Color(0xFF7C4DFF)
val NovaAccentLight = Color(0xFFB388FF)
val AmoledBlack = Color(0xFF000000)
val DarkSurface = Color(0xFF121016)
val LightSurface = Color(0xFFFAF9FC)

private val DarkColors = darkColorScheme(
    primary = NovaAccent,
    secondary = NovaAccentLight,
    background = DarkSurface,
    surface = DarkSurface
)

private val AmoledColors = darkColorScheme(
    primary = NovaAccent,
    secondary = NovaAccentLight,
    background = AmoledBlack,
    surface = AmoledBlack
)

private val LightColors = lightColorScheme(
    primary = NovaAccent,
    secondary = NovaAccentLight,
    background = LightSurface,
    surface = Color.White
)

enum class NovaThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

@Composable
fun NovaTheme(
    mode: NovaThemeMode = NovaThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDark = when (mode) {
        NovaThemeMode.SYSTEM -> isSystemInDarkTheme()
        NovaThemeMode.LIGHT -> false
        NovaThemeMode.DARK, NovaThemeMode.AMOLED -> true
    }
    val colors = when {
        mode == NovaThemeMode.AMOLED -> AmoledColors
        useDark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
