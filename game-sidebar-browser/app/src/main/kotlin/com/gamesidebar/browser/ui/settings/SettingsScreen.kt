package com.gamesidebar.browser.ui.settings

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gamesidebar.browser.R
import com.gamesidebar.browser.browser.WebViewFactory
import com.gamesidebar.browser.data.ServiceLocator
import com.gamesidebar.browser.ui.components.GlassCard
import com.gamesidebar.browser.ui.components.SectionTitle
import com.gamesidebar.browser.ui.theme.GameSidebarColors
import com.gamesidebar.core.model.AppSettings
import com.gamesidebar.core.model.GlowColor
import com.gamesidebar.core.model.GlowIntensity
import com.gamesidebar.core.model.HandleSize
import com.gamesidebar.core.model.HorizontalAnchor
import com.gamesidebar.core.model.PanelSize
import com.gamesidebar.core.model.SearchEngine
import com.gamesidebar.core.model.VerticalAnchor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val locator = ServiceLocator.get(application)

    val settings: StateFlow<AppSettings> = locator.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings.DEFAULT)

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { locator.settingsRepository.update(transform) }
    }

    fun clearCache() {
        WebViewFactory.clearCacheOnly(getApplication())
    }

    fun clearBrowsingData() {
        WebViewFactory.clearBrowsingData(getApplication())
        viewModelScope.launch { locator.browserDataRepository.clearHistory() }
    }

    fun resetShortcuts() {
        viewModelScope.launch { locator.settingsRepository.resetShortcuts() }
    }

    fun clearHandlePosition() {
        viewModelScope.launch { locator.settingsRepository.clearHandlePosition() }
    }
}

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SettingsViewModel(context.applicationContext as Application) }
        },
    )
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var confirmClearData by remember { mutableStateOf(false) }
    // BuildConfig generation is disabled project-wide, so the version comes from the package info.
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0.0"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // ------------------------------------------------------------ General
        SectionTitle(stringResource(R.string.settings_general))
        GlassCard {
            ToggleRow(
                icon = R.drawable.ic_play,
                title = stringResource(R.string.settings_auto_start),
                body = stringResource(R.string.settings_auto_start_body),
                checked = settings.autoStartSidebar,
                onChange = { viewModel.update { s -> s.copy(autoStartSidebar = it) } },
            )
            ToggleRow(
                icon = R.drawable.ic_drag,
                title = stringResource(R.string.settings_remember_position),
                body = stringResource(R.string.settings_remember_position_body),
                checked = settings.rememberPosition,
                onChange = { viewModel.update { s -> s.copy(rememberPosition = it) } },
            )
            ToggleRow(
                icon = R.drawable.ic_panel,
                title = stringResource(R.string.settings_tap_outside),
                body = stringResource(R.string.settings_tap_outside_body),
                checked = settings.tapOutsideToClose,
                onChange = { viewModel.update { s -> s.copy(tapOutsideToClose = it) } },
            )
        }

        // ------------------------------------------------------------ Browser
        SectionTitle(stringResource(R.string.settings_browser))
        GlassCard {
            ChoiceRow(
                icon = R.drawable.ic_search,
                title = stringResource(R.string.settings_search_engine),
                options = SearchEngine.entries.map { it.displayName },
                selectedIndex = SearchEngine.entries.indexOf(settings.searchEngine),
                onSelect = { index ->
                    viewModel.update { s -> s.copy(searchEngine = SearchEngine.entries[index]) }
                },
            )
            ToggleRow(
                icon = R.drawable.ic_desktop,
                title = stringResource(R.string.settings_desktop_mode),
                body = null,
                checked = settings.desktopMode,
                onChange = { viewModel.update { s -> s.copy(desktopMode = it) } },
            )
            ToggleRow(
                icon = R.drawable.ic_bolt,
                title = stringResource(R.string.settings_javascript),
                body = stringResource(R.string.settings_javascript_body),
                checked = settings.javaScriptEnabled,
                onChange = { viewModel.update { s -> s.copy(javaScriptEnabled = it) } },
            )
            ToggleRow(
                icon = R.drawable.ic_shield,
                title = stringResource(R.string.settings_cookies),
                body = stringResource(R.string.settings_cookies_body),
                checked = settings.cookiesEnabled,
                onChange = { viewModel.update { s -> s.copy(cookiesEnabled = it) } },
            )
            ToggleRow(
                icon = R.drawable.ic_shield,
                title = stringResource(R.string.settings_https),
                body = stringResource(R.string.settings_https_body),
                checked = settings.httpsPreferred,
                onChange = { viewModel.update { s -> s.copy(httpsPreferred = it) } },
            )
            ToggleRow(
                icon = R.drawable.ic_tabs,
                title = stringResource(R.string.settings_swipe_tabs),
                body = null,
                checked = settings.swipeBetweenTabs,
                onChange = { viewModel.update { s -> s.copy(swipeBetweenTabs = it) } },
            )
            ActionRow(
                icon = R.drawable.ic_delete,
                title = stringResource(R.string.settings_clear_cache),
                onClick = { viewModel.clearCache() },
            )
        }

        // ------------------------------------------------------------ Overlay
        SectionTitle(stringResource(R.string.settings_overlay))
        GlassCard {
            ChoiceRow(
                icon = R.drawable.ic_panel,
                title = stringResource(R.string.settings_panel_size),
                options = PanelSize.entries.map { it.displayName },
                selectedIndex = PanelSize.entries.indexOf(settings.panelSize),
                onSelect = { index -> viewModel.update { s -> s.copy(panelSize = PanelSize.entries[index]) } },
            )
            ChoiceRow(
                icon = R.drawable.ic_drag,
                title = stringResource(R.string.settings_panel_position),
                options = VerticalAnchor.entries.flatMap { vertical ->
                    HorizontalAnchor.entries.map { horizontal ->
                        "${vertical.displayName} / ${horizontal.displayName}"
                    }
                },
                selectedIndex = settings.panelVerticalAnchor.ordinal * HorizontalAnchor.entries.size +
                    settings.panelHorizontalAnchor.ordinal,
                onSelect = { index ->
                    val vertical = VerticalAnchor.entries[index / HorizontalAnchor.entries.size]
                    val horizontal = HorizontalAnchor.entries[index % HorizontalAnchor.entries.size]
                    viewModel.update { s ->
                        s.copy(panelVerticalAnchor = vertical, panelHorizontalAnchor = horizontal)
                    }
                },
            )
            ChoiceRow(
                icon = R.drawable.ic_drag,
                title = stringResource(R.string.settings_handle_size),
                options = HandleSize.entries.map { it.displayName },
                selectedIndex = HandleSize.entries.indexOf(settings.handleSize),
                onSelect = { index -> viewModel.update { s -> s.copy(handleSize = HandleSize.entries[index]) } },
            )
            ChoiceRow(
                icon = R.drawable.ic_drag,
                title = stringResource(R.string.settings_handle_position),
                options = VerticalAnchor.entries.flatMap { vertical ->
                    HorizontalAnchor.entries.map { horizontal ->
                        "${vertical.displayName} / ${horizontal.displayName}"
                    }
                },
                selectedIndex = settings.handleVerticalAnchor.ordinal * HorizontalAnchor.entries.size +
                    settings.handleHorizontalAnchor.ordinal,
                onSelect = { index ->
                    val vertical = VerticalAnchor.entries[index / HorizontalAnchor.entries.size]
                    val horizontal = HorizontalAnchor.entries[index % HorizontalAnchor.entries.size]
                    viewModel.update { s ->
                        s.copy(handleVerticalAnchor = vertical, handleHorizontalAnchor = horizontal)
                    }
                },
            )
            SliderRow(
                icon = R.drawable.ic_glow,
                title = stringResource(R.string.settings_panel_opacity),
                value = settings.panelOpacity.toFloat(),
                valueLabel = "${settings.panelOpacity}%",
                steps = AppSettings.OPACITY_STEPS.map { it.toFloat() },
                onValueChange = { value -> viewModel.update { s -> s.withOpacity(value.toInt()) } },
            )
            ToggleRow(
                icon = R.drawable.ic_forward,
                title = stringResource(R.string.settings_auto_snap),
                body = stringResource(R.string.settings_auto_snap_body),
                checked = settings.autoSnap,
                onChange = { viewModel.update { s -> s.copy(autoSnap = it) } },
            )
            ToggleRow(
                icon = R.drawable.ic_panel,
                title = stringResource(R.string.settings_edge_hide),
                body = stringResource(R.string.settings_edge_hide_body),
                checked = settings.edgeHideMode,
                onChange = { viewModel.update { s -> s.copy(edgeHideMode = it) } },
            )
            ActionRow(
                icon = R.drawable.ic_reset,
                title = stringResource(R.string.settings_remember_position),
                onClick = { viewModel.clearHandlePosition() },
            )
        }

        // ------------------------------------------------------------ Gaming
        SectionTitle(stringResource(R.string.settings_gaming))
        GlassCard {
            ToggleRow(
                icon = R.drawable.ic_bolt,
                title = stringResource(R.string.settings_gaming_mode),
                body = stringResource(R.string.settings_gaming_mode_body),
                checked = settings.gamingMode,
                onChange = { viewModel.update { s -> s.copy(gamingMode = it) } },
            )
            ToggleRow(
                icon = R.drawable.ic_pause,
                title = stringResource(R.string.settings_reduced_animations),
                body = null,
                checked = settings.reducedAnimations,
                onChange = { viewModel.update { s -> s.copy(reducedAnimations = it) } },
            )
        }

        // ------------------------------------------------------------ Appearance
        SectionTitle(stringResource(R.string.settings_appearance))
        GlassCard {
            ToggleRow(
                icon = R.drawable.ic_glow,
                title = stringResource(R.string.settings_glow),
                body = null,
                checked = settings.glowEnabled,
                onChange = { viewModel.update { s -> s.copy(glowEnabled = it) } },
            )
            ChoiceRow(
                icon = R.drawable.ic_glow,
                title = stringResource(R.string.settings_glow_intensity),
                options = GlowIntensity.entries.map { it.displayName },
                selectedIndex = GlowIntensity.entries.indexOf(settings.glowIntensity),
                onSelect = { index ->
                    viewModel.update { s -> s.copy(glowIntensity = GlowIntensity.entries[index]) }
                },
            )
            ChoiceRow(
                icon = R.drawable.ic_glow,
                title = stringResource(R.string.settings_glow_color),
                options = GlowColor.entries.map { it.displayName },
                selectedIndex = GlowColor.entries.indexOf(settings.glowColor),
                onSelect = { index -> viewModel.update { s -> s.copy(glowColor = GlowColor.entries[index]) } },
            )
            Text(
                text = stringResource(R.string.settings_dark_mode),
                style = MaterialTheme.typography.bodySmall,
                color = GameSidebarColors.TextMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        // ------------------------------------------------------------ Privacy
        SectionTitle(stringResource(R.string.settings_privacy))
        GlassCard {
            ToggleRow(
                icon = R.drawable.ic_history,
                title = stringResource(R.string.settings_save_history),
                body = null,
                checked = settings.saveHistory,
                onChange = { viewModel.update { s -> s.copy(saveHistory = it) } },
            )
            ToggleRow(
                icon = R.drawable.ic_incognito,
                title = stringResource(R.string.settings_incognito),
                body = stringResource(R.string.settings_incognito_body),
                checked = settings.incognito,
                onChange = { viewModel.update { s -> s.copy(incognito = it) } },
            )
            ActionRow(
                icon = R.drawable.ic_delete,
                title = stringResource(R.string.settings_clear_cookies),
                onClick = { viewModel.clearBrowsingData() },
            )
            ActionRow(
                icon = R.drawable.ic_delete,
                title = stringResource(R.string.settings_clear_data),
                onClick = { confirmClearData = true },
            )
            ActionRow(
                icon = R.drawable.ic_reset,
                title = stringResource(R.string.shortcuts_manage),
                onClick = { viewModel.resetShortcuts() },
            )
            Text(
                text = stringResource(R.string.settings_offline_note),
                style = MaterialTheme.typography.bodySmall,
                color = GameSidebarColors.TextMuted,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        // ------------------------------------------------------------ About
        SectionTitle(stringResource(R.string.settings_about))
        GlassCard {
            InfoRow(title = stringResource(R.string.settings_version), value = versionName)
            InfoRow(title = stringResource(R.string.settings_developer), value = stringResource(R.string.settings_developer_value))
            InfoRow(
                title = stringResource(R.string.settings_licenses),
                value = "AndroidX, Jetpack Compose, Room, Kotlin coroutines (Apache 2.0)",
            )
        }
        Spacer(Modifier.size(24.dp))
    }

    if (confirmClearData) {
        AlertDialog(
            onDismissRequest = { confirmClearData = false },
            title = { Text(stringResource(R.string.confirm_clear_data_title)) },
            text = { Text(stringResource(R.string.confirm_clear_data_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearBrowsingData()
                    confirmClearData = false
                }) { Text(stringResource(R.string.action_clear_all)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearData = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ToggleRow(
    icon: Int,
    title: String,
    body: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 10.dp),
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            tint = GameSidebarColors.AccentBlue,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = GameSidebarColors.TextPrimary)
            if (body != null) {
                Text(text = body, style = MaterialTheme.typography.bodySmall, color = GameSidebarColors.TextMuted)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ChoiceRow(
    icon: Int,
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(vertical = 10.dp),
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            tint = GameSidebarColors.AccentBlue,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = GameSidebarColors.TextPrimary)
            Text(
                text = options.getOrNull(selectedIndex.coerceAtLeast(0)).orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = GameSidebarColors.AccentCyan,
            )
        }
        Icon(
            painter = painterResource(id = R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = GameSidebarColors.TextMuted,
            modifier = Modifier.size(18.dp),
        )
    }
    if (expanded) {
        AlertDialog(
            onDismissRequest = { expanded = false },
            title = { Text(title) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    options.forEachIndexed { index, option ->
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (index == selectedIndex) GameSidebarColors.AccentBlue
                            else GameSidebarColors.TextSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(index)
                                    expanded = false
                                }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = {},
        )
    }
}

@Composable
private fun SliderRow(
    icon: Int,
    title: String,
    value: Float,
    valueLabel: String,
    steps: List<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                tint = GameSidebarColors.AccentBlue,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = GameSidebarColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(text = valueLabel, style = MaterialTheme.typography.labelSmall, color = GameSidebarColors.AccentCyan)
        }
        // The slider snaps to the documented steps (20/40/60/80/90/100).
        Slider(
            value = value,
            onValueChange = { raw ->
                val nearest = steps.minByOrNull { kotlin.math.abs(it - raw) } ?: raw
                onValueChange(nearest)
            },
            valueRange = steps.first()..steps.last(),
            steps = (steps.size - 2).coerceAtLeast(0),
        )
    }
}

@Composable
private fun ActionRow(icon: Int, title: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            tint = GameSidebarColors.AccentRed,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = GameSidebarColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, color = GameSidebarColors.TextPrimary)
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = GameSidebarColors.TextMuted)
    }
}
