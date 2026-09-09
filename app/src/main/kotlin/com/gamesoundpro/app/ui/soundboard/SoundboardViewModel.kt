package com.gamesoundpro.app.ui.soundboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gamesoundpro.app.GameSoundProApp
import com.gamesoundpro.app.database.entity.SoundEntity
import com.gamesoundpro.app.domain.BoardFilterState
import com.gamesoundpro.app.domain.Category
import com.gamesoundpro.app.domain.LibraryFilter
import com.gamesoundpro.app.domain.SortOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SoundboardViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as GameSoundProApp).container
    private val repository = container.soundRepository

    private val _filter = MutableStateFlow(BoardFilterState())
    val filter: StateFlow<BoardFilterState> = _filter.asStateFlow()

    val sounds: StateFlow<List<SoundEntity>> = combine(
        repository.sounds,
        repository.packs,
        _filter,
    ) { sounds, packs, state ->
        LibraryFilter.apply(sounds, packs.associate { it.id to it.name }, state.query, state.category, state.sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeSounds = container.audioEngine.activeSounds

    fun setQuery(query: String) = _filter.update { it.copy(query = query) }
    fun setCategory(category: Category?) = _filter.update { it.copy(category = category) }
    fun setSort(sort: SortOption) = _filter.update { it.copy(sort = sort) }

    fun play(sound: SoundEntity) = container.audioEngine.playSound(sound)
    fun stopSound(id: String) = container.audioEngine.stopSound(id)

    fun toggleFavorite(sound: SoundEntity) {
        viewModelScope.launch { repository.toggleFavorite(sound) }
    }

    fun deleteSound(sound: SoundEntity) {
        viewModelScope.launch {
            container.audioEngine.stopSound(sound.id)
            repository.deleteSound(sound)
        }
    }
}
