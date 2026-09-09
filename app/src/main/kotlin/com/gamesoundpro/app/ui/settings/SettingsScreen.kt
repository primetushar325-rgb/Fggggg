package com.gamesoundpro.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamesoundpro.app.database.entity.SoundPackEntity
import com.gamesoundpro.app.domain.AppSettings
import com.gamesoundpro.app.domain.AudioFocusBehavior
import com.gamesoundpro.app.domain.ThemeMode
import com.gamesoundpro.app.permissions.Permissions
import com.gamesoundpro.app.ui.components.ConfirmDialog
import com.gamesoundpro.app.ui.components.SectionHeader
import com.gamesoundpro.app.ui.theme.ACCENTS
import com.gamesoundpro.app.ui.theme.GlassSurface
import com.gamesoundpro.app.utils.Format

@Composable
fun SettingsScreen(
    onMessage: (String) -> Unit,
    onOpenDiagnostics: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val storage by viewModel.storageStats.collectAsStateWithLifecycle()
    val packs by viewModel.packs.collectAsStateWithLifecycle()

    var deleteUnusedCount by remember { mutableStateOf<Int?>(null) }
    var askDeleteUnused by remember { mutableStateOf(false) }
    var pendingExportPack by remember { mutableStateOf<SoundPackEntity?>(null) }
    var showPrivacyDetail by rememberSaveable { mutableStateOf(false) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        onMessage(if (granted) "Notifications allowed 🔔" else "Notifications denied")
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val pack = pendingExportPack
        if (uri != null && pack != null) {
            viewModel.exportPack(pack.id, uri) { result ->
                onMessage(
                    result.fold(
                        { count -> "Exported $count sounds 📦" },
                        { it.message ?: "Export failed" },
                    )
                )
            }
        }
        pendingExportPack = null
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importPack(uri) { result ->
                onMessage(
                    result.fold(
                        { r ->
                            "Imported ${r.imported} sounds into \"${r.packName}\"" +
                                if (r.skipped > 0) " (${r.skipped} skipped)" else ""
                        },
                        { it.message ?: "That file isn't a valid GameSound Pro pack" },
                    )
                )
            }
        }
    }

    LaunchedEffect(askDeleteUnused) {
        if (askDeleteUnused) {
            askDeleteUnused = false
            viewModel.unusedSoundCount { count ->
                if (count == 0) onMessage("No unused sounds — nothing to delete") else deleteUnusedCount = count
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 8.dp))

        // ---------------- Appearance ----------------
        SectionHeader("Appearance")
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Theme", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }
                Text("Accent color", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ACCENTS.forEachIndexed { index, accent ->
                        val selected = settings.accentIndex == index
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(accent.primary)
                                .border(
                                    width = if (selected) 3.dp else 0.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else accent.primary,
                                    shape = CircleShape,
                                )
                                .clickable { viewModel.setAccentIndex(index) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Text("✓", color = MaterialTheme.colorScheme.surface)
                        }
                    }
                }
            }
        }

        // ---------------- Feedback ----------------
        SectionHeader("Feedback")
        GlassSurface(Modifier.fillMaxWidth()) {
            ToggleRow(
                "Haptic feedback",
                "Subtle vibration on taps and long-presses",
                settings.haptics,
                Modifier.padding(14.dp),
            ) { viewModel.setHaptics(it) }
        }

        // ---------------- Playback ----------------
        SectionHeader("Playback")
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ToggleRow(
                    "Auto Stop",
                    "Stop all audio when the app goes to the background",
                    settings.autoStop,
                ) { viewModel.setAutoStop(it) }
                ToggleRow(
                    "Duck music on effects",
                    "Temporarily lower music while a sound effect plays",
                    settings.ducking,
                ) { viewModel.setDucking(it) }
                Spacer(Modifier.height(6.dp))
                Text("Audio focus behavior", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioFocusBehavior.entries.forEach { behavior ->
                        FilterChip(
                            selected = settings.audioFocusBehavior == behavior,
                            onClick = { viewModel.setAudioFocusBehavior(behavior) },
                            label = { Text(behavior.label) },
                        )
                    }
                }
                Text(
                    settings.audioFocusBehavior.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Default sound volume · ${Format.percent(settings.defaultVolume / 1.5f)}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Slider(
                    value = settings.defaultVolume,
                    onValueChange = { viewModel.setDefaultVolume(it) },
                    valueRange = 0.1f..1.5f,
                )
            }
        }

        // ---------------- Gaming Mode & Overlay ----------------
        SectionHeader("Gaming Mode & Overlay")
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val canOverlay = Permissions.canDrawOverlays(context)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDiagnostics() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Audio diagnostics", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Engine, focus, playback & overlay health + TEST SOUND",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("→", color = MaterialTheme.colorScheme.primary)
                }
                ToggleRow(
                    title = "Gaming Mode",
                    subtitle = if (canOverlay) "Show the floating soundboard above other apps"
                    else "Needs the \"Display over other apps\" permission",
                    checked = settings.gamingMode,
                ) { enabled ->
                    if (enabled && !canOverlay) {
                        onMessage("Allow \"Display over other apps\" first — opening Settings")
                        context.startActivity(Permissions.overlaySettingsIntent(context))
                    } else {
                        viewModel.setGamingMode(enabled)
                    }
                }
                Text(
                    "Overlay size · ${Format.percent((settings.overlayScale - 0.7f) / 0.9f)}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Slider(
                    value = settings.overlayScale,
                    onValueChange = { viewModel.setOverlayScale(it) },
                    valueRange = 0.7f..1.6f,
                )
                ToggleRow(
                    title = "Sidebar keyboard",
                    subtitle = "Allow the search field to open the keyboard (grabs input while open)",
                    checked = settings.overlayKeyboard,
                ) { viewModel.setOverlayKeyboard(it) }
                OutlinedButton(onClick = { viewModel.resetOverlayPosition() }) {
                    Text("Reset overlay position")
                }
                if (!canOverlay) {
                    OutlinedButton(onClick = { context.startActivity(Permissions.overlaySettingsIntent(context)) }) {
                        Text("Grant overlay permission")
                    }
                }
            }
        }

        // ---------------- Notifications ----------------
        SectionHeader("Notifications")
        GlassSurface(Modifier.fillMaxWidth()) {
            val canNotify = Permissions.hasNotifications(context)
            ToggleRow(
                title = "Playback & Gaming Mode notifications",
                subtitle = if (canNotify) "Media controls and the gaming overlay indicator"
                else "Tap to allow notifications (Android 13+)",
                checked = canNotify,
                modifier = Modifier.padding(14.dp),
            ) { enabled ->
                if (enabled && !canNotify) {
                    notifPermissionLauncher.launch(Permissions.POST_NOTIFICATIONS)
                }
            }
        }

        // ---------------- Storage ----------------
        SectionHeader("Storage")
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${storage?.totalSounds ?: 0} Sounds · ${storage?.totalPacks ?: 0} Packs · " +
                        "${Format.bytes(storage?.audioBytes ?: 0)} used",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Cache: ${Format.bytes(storage?.cacheBytes ?: 0)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        viewModel.clearCache { freed -> onMessage("Cache cleared — ${Format.bytes(freed)} freed 🧹") }
                    }) { Text("Clear Cache") }
                    OutlinedButton(onClick = { askDeleteUnused = true }) { Text("Delete Unused") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        if (packs.isEmpty()) onMessage("No packs to export yet") else pendingExportPack = packs.first()
                    }) { Text("Export Pack") }

                    OutlinedButton(onClick = {
                        importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                    }) { Text("Import Pack") }
                }
            }
        }

        // ---------------- Privacy ----------------
        SectionHeader("Privacy")
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Your audio stays on your device", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "No servers. No tracking. No game access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { showPrivacyDetail = !showPrivacyDetail }) {
                        Icon(
                            if (showPrivacyDetail) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = "Toggle details",
                        )
                    }
                }
                AnimatedVisibility(visible = showPrivacyDetail) {
                    Text(
                        "• The microphone is used only while you actively record a sound — never in the background.\n" +
                            "• Imported sounds and recordings remain in the app's private storage on this device and are never uploaded.\n" +
                            "• The app does not access, read or modify any game: no game files, no game memory, no game credentials.\n" +
                            "• No analytics, no advertising identifiers, no network permission.\n" +
                            "• Uninstalling removes everything the app stored.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        // ---------------- About ----------------
        SectionHeader("About")
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("GameSound Pro", style = MaterialTheme.typography.titleMedium)
                val versionName = remember {
                    try {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
                    } catch (_: Exception) {
                        "?"
                    }
                }
                Text(
                    "Version $versionName · Built with Kotlin, Jetpack Compose, Room & Media3",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "An independent soundboard. It plays audio like any normal media app and never " +
                        "interacts with games or other apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // Pack picker for the Export Pack action.
    if (pendingExportPack != null && packs.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingExportPack = null },
            title = { Text("Export Pack") },
            text = {
                Column {
                    packs.forEach { pack ->
                        TextButton(onClick = {
                            pendingExportPack = null
                            exportLauncher.launch("${pack.name}.gsoundpack.zip")
                        }) { Text("${pack.icon} ${pack.name}") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pendingExportPack = null }) { Text("Cancel") } },
        )
    }

    deleteUnusedCount?.let { count ->
        ConfirmDialog(
            title = "Delete $count unused sound${if (count == 1) "" else "s"}?",
            text = "These sounds have never been played. Their audio files will be removed from the device.",
            onConfirm = {
                deleteUnusedCount = null
                viewModel.deleteUnusedSounds { deleted -> onMessage("Deleted $deleted unused sounds 🗑️") }
            },
            onDismiss = { deleteUnusedCount = null },
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onChange: (Boolean) -> Unit,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
