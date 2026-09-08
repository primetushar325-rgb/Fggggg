package com.gamesoundpro.app.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamesoundpro.app.database.entity.SoundEntity
import com.gamesoundpro.app.ui.components.EmptyState
import com.gamesoundpro.app.ui.components.SoundTile

@Composable
fun FavoritesScreen(
    onEditSound: (SoundEntity) -> Unit,
    viewModel: FavoritesViewModel = viewModel(),
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val active by viewModel.activeSounds.collectAsStateWithLifecycle()
    val activeIds = active.map { it.id }.toSet()

    Column(Modifier.fillMaxSize()) {
        Text(
            "Favorites",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
        if (favorites.isEmpty()) {
            EmptyState(
                emoji = "⭐",
                title = "No favorites yet",
                subtitle = "Tap the star on any sound to pin it here for one-tap access.",
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(favorites, key = { it.id }) { sound ->
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
}
