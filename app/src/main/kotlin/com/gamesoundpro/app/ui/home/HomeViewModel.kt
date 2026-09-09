package com.gamesoundpro.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gamesoundpro.app.GameSoundProApp
import com.gamesoundpro.app.database.entity.SoundEntity
import com.gamesoundpro.app.database.entity.SoundPackEntity
import com.gamesoundpro.app.domain.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as GameSoundProApp).container
    private val repository = container.soundRepository

    val settings: StateFlow<AppSettings> = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings.DEFAULT)

    val mostPlayed: StateFlow<List<SoundEntity>> = repository.mostPlayed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recents: StateFlow<List<SoundEntity>> = repository.recents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favorites: StateFlow<List<SoundEntity>> = repository.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val packs: StateFlow<List<SoundPackEntity>> = repository.packs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val soundCount: StateFlow<Int> = repository.sounds
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val activeSounds = container.audioEngine.activeSounds

    fun play(sound: SoundEntity) = container.audioEngine.playSound(sound)

    fun toggleFavorite(sound: SoundEntity) {
        viewModelScope.launch { repository.toggleFavorite(sound) }
    }

    fun setGamingMode(enabled: Boolean) {
        if (enabled) container.audioEngine.warmUp()
        container.gamingModeManager.setEnabled(enabled)
    }
}
