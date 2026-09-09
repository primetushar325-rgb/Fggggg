package com.gamesoundpro.app.ui.mysounds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamesoundpro.app.database.entity.SoundEntity
import com.gamesoundpro.app.ui.components.CategoryChipRow
import com.gamesoundpro.app.ui.components.EmptyState
import com.gamesoundpro.app.ui.components.SortMenu
import com.gamesoundpro.app.ui.components.SoundTile
import com.gamesoundpro.app.ui.sheets.PackCreateDialog
import com.gamesoundpro.app.ui.theme.GlassSurface

@Composable
fun MySoundsScreen(
    onOpenAdd: () -> Unit,
    onOpenRecord: () -> Unit,
    onEditSound: (SoundEntity) -> Unit,
    onOpenPack: (String) -> Unit,
    viewModel: MySoundsViewModel = viewModel(),
) {
    val sounds by viewModel.sounds.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val packs by viewModel.packs.collectAsStateWithLifecycle()
    val active by viewModel.activeSounds.collectAsStateWithLifecycle()
    val activeIds = active.map { it.id }.toSet()

    var showCreatePack by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionChip("📥", "Import", Modifier.weight(1f), onOpenAdd)
            ActionChip("🎙️", "Record", Modifier.weight(1f), onOpenRecord)
            ActionChip("📦", "New Pack", Modifier.weight(1f)) { showCreatePack = true }
        }

        OutlinedTextField(
            value = filter.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("Search my sounds…") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Search sounds") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryChipRow(
                selected = filter.category,
                onSelect = viewModel::setCategory,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(4.dp))
            SortMenu(current = filter.sort, onSelect = viewModel::setSort)
        }

        if (sounds.isEmpty()) {
            EmptyState(
                emoji = "🎚️",
                title = if (filter.query.isBlank()) "Your library is empty" else "Nothing found",
                subtitle = "Import an MP3/WAV/M4A/OGG, record your voice, or add a starter sound from the Soundboard.",
                actionLabel = "Import a sound",
                onAction = onOpenAdd,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(sounds, key = { it.id }) { sound ->
                    SoundTile(
                        sound = sound,
                        isPlaying = sound.id in activeIds,
                        onPlay = { viewModel.play(sound) },
                        onLongClick = { onEditSound(sound) },
                        onToggleFavorite = { viewModel.toggleFavorite(sound) },
                    )
                }
            }
        }
    }

    if (showCreatePack) {
        PackCreateDialog(
            onCreate = { name, icon ->
                viewModel.createPack(name, icon)
                showCreatePack = false
            },
            onDismiss = { showCreatePack = false },
        )
    }
}

@Composable
private fun ActionChip(emoji: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    GlassSurface(modifier = modifier.clickable(onClick = onClick), cornerRadius = 14.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(emoji)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
