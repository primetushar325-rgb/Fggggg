package com.gamesoundpro.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gamesoundpro.app.GameSoundProApp
import com.gamesoundpro.app.domain.AppSettings
import com.gamesoundpro.app.domain.MixerVolumes
import com.gamesoundpro.app.domain.StorageStats
import com.gamesoundpro.app.domain.ThemeMode
import com.gamesoundpro.app.repository.SoundRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as GameSoundProApp).container
    private val settingsRepository = container.settingsRepository
    private val repository = container.soundRepository

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings.DEFAULT)

    val packs: StateFlow<List<com.gamesoundpro.app.database.entity.SoundPackEntity>> =
        repository.packs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _storageStats = MutableStateFlow<StorageStats?>(null)
    val storageStats: StateFlow<StorageStats?> = _storageStats.asStateFlow()

    init {
        refreshStorage()
    }

    fun refreshStorage() {
        viewModelScope.launch { _storageStats.value = repository.storageStats() }
    }

    // -- Preference setters ----------------------------------------------------------

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun setAccentIndex(index: Int) = viewModelScope.launch { settingsRepository.setAccentIndex(index) }
    fun setHaptics(enabled: Boolean) = viewModelScope.launch { settingsRepository.setHaptics(enabled) }
    fun setDefaultVolume(value: Float) = viewModelScope.launch { settingsRepository.setDefaultVolume(value) }
    fun setAutoStop(enabled: Boolean) = viewModelScope.launch { settingsRepository.setAutoStop(enabled) }
    fun setDucking(enabled: Boolean) = viewModelScope.launch { settingsRepository.setDucking(enabled) }
    fun setOverlayScale(scale: Float) = viewModelScope.launch { settingsRepository.setOverlayScale(scale) }
    fun resetOverlayPosition() = viewModelScope.launch { settingsRepository.resetOverlayPosition() }
    fun setGamingMode(enabled: Boolean) = container.gamingModeManager.setEnabled(enabled)

    fun setMasterVolume(value: Float) {
        container.audioEngine.setMasterVolumeLive(value)
        viewModelScope.launch { settingsRepository.setMasterVolume(value) }
    }

    // -- Storage actions -------------------------------------------------------------

    fun clearCache(onDone: (Long) -> Unit) {
        viewModelScope.launch { onDone(repository.clearCache()); refreshStorage() }
    }

    fun deleteUnusedSounds(onDone: (Int) -> Unit) {
        viewModelScope.launch { onDone(repository.deleteUnusedSounds()); refreshStorage() }
    }

    fun unusedSoundCount(onDone: (Int) -> Unit) {
        viewModelScope.launch { onDone(repository.unusedSoundCount()) }
    }

    fun exportPack(packId: String, uri: android.net.Uri, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            val pack = repository.packById(packId)
            if (pack == null) onResult(Result.failure(IllegalStateException("Pack not found")))
            else onResult(repository.exportPack(pack, uri))
            refreshStorage()
        }
    }

    fun importPack(uri: android.net.Uri, onResult: (Result<com.gamesoundpro.app.repository.PackImportResult>) -> Unit) {
        viewModelScope.launch {
            onResult(repository.importPack(uri))
            refreshStorage()
        }
    }
}
