package com.gamesidebar.browser.ui.home

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gamesidebar.browser.R
import com.gamesidebar.browser.ui.components.ErrorState
import com.gamesidebar.browser.ui.components.GlassCard
import com.gamesidebar.browser.ui.components.GlowActionButton
import com.gamesidebar.browser.ui.components.SectionTitle
import com.gamesidebar.browser.ui.components.TileAction
import com.gamesidebar.browser.ui.theme.GameSidebarColors
import com.gamesidebar.browser.util.OverlayPermission

/**
 * Home: the premium landing screen and the sidebar's control panel.
 *
 * Order matters here - the permission explanation comes before any button that would fail, and every
 * failure mode the app can be in (no overlay permission, no WebView, service restricted) has its own
 * card with a recovery action rather than a dead button.
 */
@Composable
fun HomeScreen(
    onOpenBrowser: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HomeViewModel(context.applicationContext as Application) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Header(running = state.sidebarRunning)

        Spacer(Modifier.height(20.dp))

        GlassCard {
            Text(
                text = stringResource(
                    if (state.sidebarRunning) R.string.home_sidebar_active else R.string.home_sidebar_inactive,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = GameSidebarColors.TextPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.home_overlay_hint),
                style = MaterialTheme.typography.bodySmall,
                color = GameSidebarColors.TextMuted,
            )
            Spacer(Modifier.height(16.dp))
            GlowActionButton(
                text = stringResource(
                    if (state.sidebarRunning) R.string.home_stop_sidebar else R.string.home_start_sidebar,
                ),
                iconRes = if (state.sidebarRunning) R.drawable.ic_close else R.drawable.ic_bolt,
                emphasized = !state.sidebarRunning,
                onClick = {
                    if (state.sidebarRunning) viewModel.stopSidebar() else viewModel.startSidebar()
                },
            )
            if (state.sidebarRunning) {
                Spacer(Modifier.height(10.dp))
                GlowActionButton(
                    text = stringResource(R.string.notification_action_open),
                    iconRes = R.drawable.ic_expand,
                    onClick = { viewModel.openPanel() },
                )
            }
        }

        if (!state.overlayGranted) {
            Spacer(Modifier.height(16.dp))
            ErrorState(
                title = stringResource(R.string.error_overlay_permission),
                body = stringResource(R.string.error_overlay_body),
                actionLabel = stringResource(R.string.permission_enable),
                onAction = onRequestOverlayPermission,
                iconRes = R.drawable.ic_shield,
            )
        }

        if (!state.webviewAvailable) {
            Spacer(Modifier.height(16.dp))
            ErrorState(
                title = stringResource(R.string.error_webview_missing),
                body = stringResource(R.string.error_webview_body),
                actionLabel = stringResource(R.string.action_retry),
                onAction = { viewModel.refresh() },
            )
        }

        if (state.batteryOptimised && state.sidebarRunning) {
            Spacer(Modifier.height(16.dp))
            ErrorState(
                title = stringResource(R.string.error_service_restricted),
                body = stringResource(R.string.error_service_body),
                actionLabel = stringResource(R.string.settings_title),
                onAction = {
                    context.startActivity(OverlayPermission.batteryOptimizationsIntent(context))
                },
                iconRes = R.drawable.ic_warning,
            )
        }

        SectionTitle(stringResource(R.string.home_browser))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            TileAction(
                text = stringResource(R.string.home_browser),
                iconRes = R.drawable.ic_browser,
                onClick = onOpenBrowser,
                modifier = Modifier.weight(1f),
            )
            TileAction(
                text = stringResource(R.string.home_bookmarks),
                iconRes = R.drawable.ic_bookmark,
                onClick = onOpenBookmarks,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            TileAction(
                text = stringResource(R.string.home_history),
                iconRes = R.drawable.ic_history,
                onClick = onOpenHistory,
                modifier = Modifier.weight(1f),
            )
            TileAction(
                text = stringResource(R.string.home_notes),
                iconRes = R.drawable.ic_notes,
                onClick = onOpenNotes,
                modifier = Modifier.weight(1f),
            )
        }

        SectionTitle(stringResource(R.string.settings_gaming))
        GlassCard {
            SettingToggleRow(
                title = stringResource(R.string.settings_gaming_mode),
                body = stringResource(R.string.settings_gaming_mode_body),
                iconRes = R.drawable.ic_bolt,
                checked = state.settings.gamingMode,
                onCheckedChange = viewModel::setGamingMode,
            )
            Spacer(Modifier.height(10.dp))
            SettingToggleRow(
                title = stringResource(R.string.settings_auto_start),
                body = stringResource(R.string.settings_auto_start_body),
                iconRes = R.drawable.ic_play,
                checked = state.settings.autoStartSidebar,
                onCheckedChange = viewModel::setAutoStart,
            )
        }

        Spacer(Modifier.height(20.dp))
        GlowActionButton(
            text = stringResource(R.string.home_settings),
            iconRes = R.drawable.ic_settings,
            onClick = onOpenSettings,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Header(running: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(id = R.drawable.ic_panel),
            contentDescription = null,
            tint = GameSidebarColors.AccentBlue,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.size(12.dp))
        Column {
            Text(
                text = stringResource(R.string.app_name).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = GameSidebarColors.TextPrimary,
            )
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodySmall,
                color = GameSidebarColors.TextMuted,
            )
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    body: String,
    iconRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = GameSidebarColors.AccentBlue,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = GameSidebarColors.TextPrimary)
            Text(text = body, style = MaterialTheme.typography.bodySmall, color = GameSidebarColors.TextMuted)
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
