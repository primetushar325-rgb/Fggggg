package com.gamesidebar.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gamesidebar.browser.ui.theme.GameSidebarColors

/**
 * Shared building blocks for the in-app screens.
 *
 * The look is deliberately quiet: translucent navy surfaces, a hairline border, and at most one soft
 * glow per screen. Every icon is a vector drawable from `res/drawable` - there is no emoji anywhere in
 * this app, in code or in resources.
 */
object Glass {
    val CardShape = RoundedCornerShape(20.dp)
    val PillShape = RoundedCornerShape(50)
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Glass.CardShape)
            .background(
                Brush.verticalGradient(
                    listOf(GameSidebarColors.SurfaceGlass, GameSidebarColors.Surface),
                ),
            )
            .border(1.dp, GameSidebarColors.Outline, Glass.CardShape)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = GameSidebarColors.TextMuted,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp, top = 12.dp),
    )
}

@Composable
fun GlowActionButton(
    text: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val background = if (emphasized) GameSidebarColors.AccentBlue else GameSidebarColors.SurfaceElevated
    val border = if (emphasized) GameSidebarColors.AccentBlue else GameSidebarColors.OutlineStrong
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Glass.PillShape)
            .background(background)
            .border(1.dp, border, Glass.PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = if (emphasized) Color.White else GameSidebarColors.AccentBlue,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
        )
    }
}

@Composable
fun TileAction(
    text: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(Glass.CardShape)
            .background(GameSidebarColors.Surface)
            .border(1.dp, GameSidebarColors.Outline, Glass.CardShape)
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(GameSidebarColors.SurfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = GameSidebarColors.AccentBlue,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = GameSidebarColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun IconAction(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = GameSidebarColors.TextSecondary,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun EmptyState(
    iconRes: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = GameSidebarColors.TextMuted,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(text = title, style = MaterialTheme.typography.titleSmall, color = GameSidebarColors.TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = GameSidebarColors.TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ErrorState(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    iconRes: Int = com.gamesidebar.browser.R.drawable.ic_warning,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Glass.CardShape)
            .background(GameSidebarColors.Surface)
            .border(1.dp, GameSidebarColors.AccentRed.copy(alpha = 0.4f), Glass.CardShape)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = GameSidebarColors.AccentRed,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(text = title, style = MaterialTheme.typography.titleSmall, color = GameSidebarColors.TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = GameSidebarColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = actionLabel,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier
                .clip(Glass.PillShape)
                .background(GameSidebarColors.AccentBlue)
                .clickable(onClick = onAction)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}
