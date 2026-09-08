package com.gamesoundpro.app.ui.soundboard

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamesoundpro.app.database.entity.SoundEntity
import com.gamesoundpro.app.ui.theme.LocalGamingMode
import com.gamesoundpro.app.ui.components.CategoryChipRow
import com.gamesoundpro.app.ui.components.EmptyState
import com.gamesoundpro.app.ui.components.SortMenu
import com.gamesoundpro.app.ui.components.SoundTile

@Composable
fun SoundboardScreen(
    onEditSound: (SoundEntity) -> Unit,
    viewModel: SoundboardViewModel = viewModel(),
) {
    val sounds by viewModel.sounds.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val active by viewModel.activeSounds.collectAsStateWithLifecycle()
    val activeIds = active.map { it.id }.toSet()
    val gamingMode = LocalGamingMode.current

    // Denser grid on Gaming Mode; otherwise adapt to the screen width.
    val minTile = if (gamingMode) 110.dp else 150.dp

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = filter.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search sounds, categories, packs…") },
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
                emoji = "🎧",
                title = if (filter.query.isBlank()) "No sounds yet" else "Nothing found",
                subtitle = if (filter.query.isBlank())
                    "Tap the + button to import an audio file or record your own."
                else
                    "Try a different search or clear the filters.",
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = minTile),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(sounds, key = { it.id }) { sound ->
                    SoundTile(
                        sound = sound,
                        isPlaying = sound.id in activeIds,
                        compact = gamingMode,
                        onPlay = { viewModel.play(sound) },
                        onLongClick = { onEditSound(sound) },
                        onToggleFavorite = { viewModel.toggleFavorite(sound) },
                    )
                }
            }
        }
    }
}
