package com.gamesoundpro.app.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamesoundpro.app.database.entity.SoundEntity
import com.gamesoundpro.app.database.entity.SoundPackEntity
import com.gamesoundpro.app.permissions.Permissions
import com.gamesoundpro.app.ui.components.SectionHeader
import com.gamesoundpro.app.ui.components.SoundTile
import com.gamesoundpro.app.ui.components.StatPill
import com.gamesoundpro.app.ui.theme.GlassSurface
import com.gamesoundpro.app.utils.Format

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    onOpenAdd: () -> Unit,
    onOpenRecord: () -> Unit,
    onOpenMixer: () -> Unit,
    onOpenPlayer: () -> Unit,
    onEditSound: (SoundEntity) -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val mostPlayed by viewModel.mostPlayed.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val packs by viewModel.packs.collectAsStateWithLifecycle()
    val soundCount by viewModel.soundCount.collectAsStateWithLifecycle()
    val active by viewModel.activeSounds.collectAsStateWithLifecycle()

    var showOverlayRationale by remember { mutableStateOf(false) }
    val gamingOn = settings.gamingMode

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("GameSound Pro", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "Your Gaming Soundboard",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onOpenPlayer) {
                    Text("🎵", fontSize = 20.sp)
                }
            }
        }

        item {
            GamingModeHero(
                enabled = gamingOn,
                canDrawOverlays = Permissions.canDrawOverlays(context),
                onToggle = {
                    if (!gamingOn && !Permissions.canDrawOverlays(context)) {
                        showOverlayRationale = true
                    } else {
                        viewModel.setGamingMode(!gamingOn)
                    }
                },
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction("➕", "Add Sound", Modifier.weight(1f)) { onOpenAdd() }
                QuickAction("🎙️", "Record", Modifier.weight(1f)) { onOpenRecord() }
                QuickAction("🎚️", "Mixer", Modifier.weight(1f)) { onOpenMixer() }
            }
        }

        if (mostPlayed.isNotEmpty()) {
            item { SectionHeader("Quick Play") }
            item {
                HorizontalSounds(sounds = mostPlayed.take(10), activeIds = active.map { it.id }.toSet(),
                    onPlay = viewModel::play, onToggleFavorite = viewModel::toggleFavorite, onLongClick = onEditSound)
            }
        }

        if (recents.isNotEmpty()) {
            item { SectionHeader("Recently Played") }
            item {
                HorizontalSounds(sounds = recents.take(10), activeIds = active.map { it.id }.toSet(),
                    onPlay = viewModel::play, onToggleFavorite = viewModel::toggleFavorite, onLongClick = onEditSound)
            }
        }

        if (favorites.isNotEmpty()) {
            item { SectionHeader("Favorites") }
            item {
                HorizontalSounds(sounds = favorites.take(10), activeIds = active.map { it.id }.toSet(),
                    onPlay = viewModel::play, onToggleFavorite = viewModel::toggleFavorite, onLongClick = onEditSound)
            }
        }

        item {
            SectionHeader(
                "My Sound Packs",
                trailing = {
                    Text(
                        "See all",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onNavigate("packs") }
                            .padding(4.dp),
                    )
                },
            )
        }
        item {
            if (packs.isEmpty()) {
                Text(
                    "No packs yet — create one from My Sounds.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(packs, key = { it.id }) { pack ->
                        PackCard(pack) { onNavigate("pack/${pack.id}") }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill("🎮", "$soundCount sounds")
                StatPill("📦", "${packs.size} packs")
            }
        }
    }

    if (showOverlayRationale) {
        AlertDialog(
            onDismissRequest = { showOverlayRationale = false },
            title = { Text("Allow display over other apps") },
            text = {
                Text(
                    "Gaming Mode shows a small, movable GameSound Pro bubble above your game so you can " +
                        "play sounds without leaving it. Android requires the \"Display over other apps\" " +
                        "permission for this — it is used only for the floating soundboard, nothing else."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showOverlayRationale = false
                    context.startActivity(Permissions.overlaySettingsIntent(context))
                }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayRationale = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun GamingModeHero(enabled: Boolean, canDrawOverlays: Boolean, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.linearGradient(
                    listOf(
                        scheme.primary.copy(alpha = 0.85f),
                        scheme.primary.copy(alpha = 0.25f),
                        scheme.surface,
                    )
                )
            )
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.SportsEsports,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("GAMING MODE", style = MaterialTheme.typography.titleLarge, color = Color0On())
                Text(
                    when {
                        enabled -> "ACTIVE — floating soundboard running"
                        !canDrawOverlays -> "Overlay permission needed to float above games"
                        else -> "Float your soundboard above any game"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color0On().copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = onToggle,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surface,
                    contentColor = if (enabled) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text(if (enabled) "Turn off" else "Turn on")
            }
        }
    }
}

// Tiny helper so hero text stays readable in both themes.
@Composable
private fun Color0On(): androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimary

@Composable
private fun QuickAction(emoji: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    GlassSurface(modifier = modifier.clickable(onClick = onClick), cornerRadius = 16.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(emoji, fontSize = 22.sp)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun HorizontalSounds(
    sounds: List<SoundEntity>,
    activeIds: Set<String>,
    onPlay: (SoundEntity) -> Unit,
    onToggleFavorite: (SoundEntity) -> Unit,
    onLongClick: (SoundEntity) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(sounds, key = { it.id }) { sound ->
            SoundTile(
                sound = sound,
                isPlaying = sound.id in activeIds,
                compact = true,
                modifier = Modifier.width(132.dp),
                onPlay = { onPlay(sound) },
                onLongClick = { onLongClick(sound) },
                onToggleFavorite = { onToggleFavorite(sound) },
            )
        }
    }
}

@Composable
private fun PackCard(pack: SoundPackEntity, onClick: () -> Unit) {
    GlassSurface(modifier = Modifier
        .width(150.dp)
        .clickable(onClick = onClick), cornerRadius = 16.dp) {
        Column(Modifier.padding(14.dp)) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Text(pack.icon, fontSize = 18.sp) }
            Spacer(Modifier.height(8.dp))
            Text(pack.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
