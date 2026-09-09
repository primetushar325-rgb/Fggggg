package com.gamesoundpro.app.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamesoundpro.app.domain.AppSettings

// ---------------------------------------------------------------------------
// Palette
// ---------------------------------------------------------------------------

data class Accent(val name: String, val primary: Color, val deep: Color)

val ACCENTS = listOf(
    Accent("Neon Violet", Color(0xFF8B5CF6), Color(0xFF5B21B6)),
    Accent("Cyber Cyan", Color(0xFF22D3EE), Color(0xFF0E7490)),
    Accent("Plasma Green", Color(0xFF34D399), Color(0xFF047857)),
    Accent("Hyper Pink", Color(0xFFF472B6), Color(0xFFBE185D)),
    Accent("Solar Orange", Color(0xFFFB923C), Color(0xFFC2410C)),
    Accent("Crimson Pulse", Color(0xFFF87171), Color(0xFFB91C1C)),
)

private val Color0Background = Color(0xFF0A0D17)
private val Color0Surface = Color(0xFF12162A)
private val Color0SurfaceVariant = Color(0xFF1B2138)
private val Color0Outline = Color(0xFF2B3354)
private val Color0OnBackground = Color(0xFFE8ECFB)
private val Color0OnSurfaceVariant = Color(0xFF98A1C9)

private val LightBackground = Color(0xFFF3F4FB)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceVariant = Color(0xFFE5E8F5)
private val LightOutline = Color(0xFFC7CCE0)
private val LightOnBackground = Color(0xFF171B30)
private val LightOnSurfaceVariant = Color(0xFF545B80)

private fun darkScheme(accent: Accent) = darkColorScheme(
    primary = accent.primary,
    onPrimary = Color.White,
    primaryContainer = accent.deep,
    onPrimaryContainer = Color.White,
    secondary = accent.primary.copy(alpha = 0.75f),
    onSecondary = Color.White,
    background = Color0Background,
    onBackground = Color0OnBackground,
    surface = Color0Surface,
    onSurface = Color0OnBackground,
    surfaceVariant = Color0SurfaceVariant,
    onSurfaceVariant = Color0OnSurfaceVariant,
    outline = Color0Outline,
    outlineVariant = Color0Outline.copy(alpha = 0.6f),
    error = Color(0xFFF87171),
    onError = Color(0xFF2B0505),
)

private fun lightScheme(accent: Accent) = lightColorScheme(
    primary = accent.deep,
    onPrimary = Color.White,
    secondary = accent.primary,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = Color(0xFFB3261E),
)

// ---------------------------------------------------------------------------
// Theme
// ---------------------------------------------------------------------------

val LocalGamingMode = staticCompositionLocalOf { false }

@Composable
fun GameSoundProTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val accent = ACCENTS[settings.accentIndex.coerceIn(ACCENTS.indices)]
    val dark = when (settings.themeMode) {
        com.gamesoundpro.app.domain.ThemeMode.SYSTEM -> isSystemInDarkTheme()
        com.gamesoundpro.app.domain.ThemeMode.DARK -> true
        com.gamesoundpro.app.domain.ThemeMode.LIGHT -> false
    }
    val scheme = if (dark) darkScheme(accent) else lightScheme(accent)

    CompositionLocalProvider(LocalGamingMode provides settings.gamingMode) {
        MaterialTheme(
            colorScheme = scheme,
            typography = GameTypography,
            shapes = GameShapes,
            content = content,
        )
    }
}

// ---------------------------------------------------------------------------
// Typography & shapes
// ---------------------------------------------------------------------------

private val GameTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Black, fontSize = 32.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.2).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 0.2.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.4.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.5.sp),
)

private val GameShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

// ---------------------------------------------------------------------------
// Glassmorphism building blocks
// ---------------------------------------------------------------------------

/** Frosted glass card: translucent gradient fill + hairline gradient border. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    shape: CornerBasedShape = RoundedCornerShape(cornerRadius),
    glowing: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val glowColor = if (glowing) scheme.primary else Color.Transparent
    Box(
        modifier
            .clip(shape)
            .then(if (glowing) Modifier.glowPulse(shape, glowColor) else Modifier)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        scheme.surface.copy(alpha = 0.95f),
                        scheme.surfaceVariant.copy(alpha = 0.70f),
                    ),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        scheme.primary.copy(alpha = if (glowing) 0.65f else 0.28f),
                        Color.White.copy(alpha = 0.06f),
                    ),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                ),
                shape = shape,
            ),
        content = content,
    )
}

/** Soft pulsing outer glow used on the currently-playing card. */
private fun Modifier.glowPulse(shape: CornerBasedShape, color: Color): Modifier = composed {
    if (color == Color.Transparent) return@composed this
    val transition = rememberInfiniteTransition(label = "glow")
    val alpha by transition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "glowAlpha",
    )
    drawBehind {
        drawRoundRect(
            color = color.copy(alpha = alpha * 0.5f),
            cornerRadius = CornerRadius(shape.topStart.toPx(size, this), shape.topStart.toPx(size, this)),
            style = Stroke(width = 6.dp.toPx()),
        )
    }
}

/** Subtle press-down scale; suppressed in Gaming Mode to save battery/GPU. */
@Composable
fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier {
    val gamingMode = LocalGamingMode.current
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !gamingMode) 0.96f else 1f,
        animationSpec = if (gamingMode) snap() else spring(dampingRatio = 0.55f, stiffness = 900f),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
