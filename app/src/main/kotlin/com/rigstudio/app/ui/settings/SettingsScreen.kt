package com.rigstudio.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rigstudio.app.editor.SettingsViewModel
import com.rigstudio.app.ui.components.FieldLabel
import com.rigstudio.app.ui.components.RigChip
import com.rigstudio.app.ui.components.RigTopBar
import com.rigstudio.app.ui.components.SectionCard
import com.rigstudio.app.ui.theme.RigColors
import com.rigstudio.core.export.ExportFrameRate
import com.rigstudio.core.export.ExportResolution

/**
 * Settings (V4 §49): export defaults and playback defaults, stored on this device only.
 *
 * The About row hides the developer unlock (V4 §52): tapping the version seven times reveals
 * the debug-overlay switch — invisible to normal users, one gesture for developers.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxWidth()) {
        RigTopBar(
            title = "Settings",
            subtitle = "Defaults for new exports and the editor. Saved on this device.",
            onBack = onBack,
        )

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard(title = "Export defaults") {
                FieldLabel("Resolution")
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ExportResolution.entries.forEach { option ->
                        RigChip(
                            label = option.label.substringBefore(" ("),
                            selected = option == state.settings.defaultResolution,
                            onClick = { viewModel.setResolution(option) },
                            sublabel = "${option.width} × ${option.height}",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                FieldLabel("Frame rate")
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ExportFrameRate.entries.forEach { option ->
                        RigChip(
                            label = "${option.fps} fps",
                            selected = option == state.settings.defaultFrameRate,
                            onClick = { viewModel.setFrameRate(option) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            SectionCard(title = "Editor") {
                SettingsSwitchRow(
                    title = "Loop animations by default",
                    subtitle = "Play new clips in a loop instead of holding the last frame",
                    checked = state.settings.loopByDefault,
                    onCheckedChange = viewModel::setLoopByDefault,
                )
                if (state.debugUnlocked) {
                    SettingsSwitchRow(
                        title = "Developer overlays",
                        subtitle = "Show sprite bounds, pivots, z-order and FPS on the stage",
                        checked = state.settings.debugOverlays,
                        onCheckedChange = viewModel::setDebugOverlays,
                    )
                }
            }

            SectionCard(title = "About") {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    androidx.compose.foundation.text.ClickableText(
                        text = androidx.compose.ui.text.AnnotatedString(
                            if (state.debugUnlocked) "RigStudio V4"
                            else "RigStudio V4${if (state.tapsRemaining in 1..3) " · ${state.tapsRemaining}…" else ""}"
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = RigColors.TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        ),
                        onClick = { viewModel.onVersionRowTap() },
                    )
                    FieldLabel(
                        "Offline character animation · no account, no cloud, no permissions",
                        Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            androidx.compose.material3.Text(title, style = MaterialTheme.typography.bodyMedium, color = RigColors.TextPrimary)
            androidx.compose.material3.Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = RigColors.TextSecondary,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = RigColors.Primary,
                checkedThumbColor = RigColors.OnPrimary,
            ),
        )
    }
}
