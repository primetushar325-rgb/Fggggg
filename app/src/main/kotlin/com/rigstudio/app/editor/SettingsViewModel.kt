package com.rigstudio.app.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rigstudio.app.RigStudioApplication
import com.rigstudio.core.export.ExportFrameRate
import com.rigstudio.core.export.ExportResolution
import com.rigstudio.core.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The Settings screen (V4 §49): export defaults, loop-by-default and — after tapping the
 * version row seven times — the hidden developer overlays (V4 §52). Everything persists to a
 * local JSON file; nothing leaves the device.
 */
class SettingsViewModel(private val app: RigStudioApplication) : ViewModel() {

    data class SettingsState(
        val settings: AppSettings = AppSettings.DEFAULT,
        /** True once the user has unlocked the developer row this session. */
        val debugUnlocked: Boolean = false,
        /** Taps towards the unlock, for the classic "3 more taps" feedback. */
        val tapsRemaining: Int = TAPS_TO_UNLOCK,
        val savedTick: Int = 0,
    )

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        val loaded = app.settingsStore.load()
        _state.value = SettingsState(
            settings = loaded,
            debugUnlocked = loaded.debugOverlays,
            tapsRemaining = if (loaded.debugOverlays) 0 else TAPS_TO_UNLOCK,
        )
    }

    fun setResolution(resolution: ExportResolution) = update { it.copy(settings = it.settings.copy(defaultResolution = resolution)) }

    fun setFrameRate(rate: ExportFrameRate) = update { it.copy(settings = it.settings.copy(defaultFrameRate = rate)) }

    fun setLoopByDefault(loop: Boolean) = update { it.copy(settings = it.settings.copy(loopByDefault = loop)) }

    fun setDebugOverlays(enabled: Boolean) = update { it.copy(settings = it.settings.copy(debugOverlays = enabled)) }

    /** The About row's tap counter that reveals the developer toggle (V4 §52). */
    fun onVersionRowTap() {
        val current = _state.value
        if (current.debugUnlocked) return
        val remaining = current.tapsRemaining - 1
        _state.update {
            it.copy(
                tapsRemaining = remaining.coerceAtLeast(0),
                debugUnlocked = remaining <= 0 || it.debugUnlocked,
            )
        }
    }

    fun resetToDefaults() = update { it.copy(settings = AppSettings.DEFAULT) }

    fun save() {
        val settings = _state.value.settings
        app.settingsStore.save(settings)
        _state.update { it.copy(savedTick = it.savedTick + 1) }
    }

    private inline fun update(block: (SettingsState) -> SettingsState) {
        _state.update(block)
        // Auto-save on every change: settings are small and local (V4 §36 spirit).
        app.settingsStore.save(_state.value.settings)
        _state.update { it.copy(savedTick = it.savedTick + 1) }
    }

    companion object {
        const val TAPS_TO_UNLOCK = 7

        fun factory(app: RigStudioApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(app) as T
            }
    }
}
