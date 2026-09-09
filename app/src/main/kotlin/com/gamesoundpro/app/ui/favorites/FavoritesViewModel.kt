package com.gamesoundpro.app.ui.favorites

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gamesoundpro.app.GameSoundProApp
import com.gamesoundpro.app.database.entity.SoundEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FavoritesViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as GameSoundProApp).container
    private val repository = container.soundRepository

    val favorites: StateFlow<List<SoundEntity>> = repository.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeSounds = container.audioEngine.activeSounds

    fun play(sound: SoundEntity) = container.audioEngine.playSound(sound)

    fun toggleFavorite(sound: SoundEntity) {
        viewModelScope.launch { repository.toggleFavorite(sound) }
    }
}
