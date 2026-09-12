package com.gamesidebar.browser.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Premium gaming-utility palette.
 *
 * Near-black navy surfaces, white type, and one restrained accent family (blue / purple / cyan) used
 * for glow and focus. No gradients, no neon: the app sits on top of a game and must not compete with
 * it for attention.
 */
object GameSidebarColors {
    val Background = Color(0xFF070A12)
    val Surface = Color(0xFF0E1320)
    val SurfaceElevated = Color(0xFF161D2E)
    val SurfaceGlass = Color(0xE6101625)
    val Outline = Color(0x33FFFFFF)
    val OutlineStrong = Color(0x59FFFFFF)

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xB3FFFFFF)
    val TextMuted = Color(0x80FFFFFF)

    val AccentBlue = Color(0xFF4C8DFF)
    val AccentPurple = Color(0xFF9A6BFF)
    val AccentCyan = Color(0xFF37E2D5)
    val AccentRed = Color(0xFFFF5C6C)
}
