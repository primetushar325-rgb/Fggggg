package com.gamesidebar.browser.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = GameSidebarColors.AccentBlue,
    onPrimary = Color.White,
    primaryContainer = GameSidebarColors.SurfaceElevated,
    onPrimaryContainer = GameSidebarColors.TextPrimary,
    secondary = GameSidebarColors.AccentPurple,
    onSecondary = Color.White,
    tertiary = GameSidebarColors.AccentCyan,
    background = GameSidebarColors.Background,
    onBackground = GameSidebarColors.TextPrimary,
    surface = GameSidebarColors.Surface,
    onSurface = GameSidebarColors.TextPrimary,
    surfaceVariant = GameSidebarColors.SurfaceElevated,
    onSurfaceVariant = GameSidebarColors.TextSecondary,
    outline = GameSidebarColors.OutlineStrong,
    outlineVariant = GameSidebarColors.Outline,
    error = GameSidebarColors.AccentRed,
    onError = Color.White,
)

/**
 * The app is dark by design: it is meant to be read on top of a bright game without washing out, and
 * a light theme would fight the panel's glass surface. [androidx.compose.material3.MaterialTheme]
 * still drives every component, so the design language stays Material 3.
 */
@Composable
fun GameSidebarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = GameSidebarTypography,
        content = content,
    )
}
