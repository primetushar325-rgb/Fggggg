package com.gamesoundpro.app.ui.diagnostics

import android.app.Application
import android.media.AudioManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gamesoundpro.app.GameSoundProApp
import com.gamesoundpro.app.audio.AudioSnapshot
import com.gamesoundpro.app.audio.TestSoundResult
import com.gamesoundpro.app.domain.ActiveSound
import com.gamesoundpro.app.domain.AppSettings
import com.gamesoundpro.app.domain.MusicState
import com.gamesoundpro.app.overlay.GamingModeManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Backing state for the Audio Diagnostics screen. Polls the media-stream volume (a plain
 * system read, cheap) once per second while visible so the row stays live.
 */
class DiagnosticsViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as GameSoundProApp).container
    private val engine = container.audioEngine
    private val audioManager =
        app.getSystemService(Application.AUDIO_SERVICE) as AudioManager

    val settings: StateFlow<AppSettings> = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings.DEFAULT)

    val snapshot: StateFlow<AudioSnapshot> = engine.snapshot

    val activeSounds: StateFlow<List<ActiveSound>> = engine.activeSounds

    val musicState: StateFlow<MusicState> = engine.musicState

    val overlayActive: StateFlow<Boolean> = GamingModeManager.overlayActive

    private val _mediaVolume = MutableStateFlow(0)
    val mediaVolume: StateFlow<Int> = _mediaVolume.asStateFlow()

    val testResult: StateFlow<TestSoundResult?> = engine.testResult

    init {
        viewModelScope.launch {
            while (isActive) {
                _mediaVolume.value = engine.mediaVolumePercent()
                delay(1_000)
            }
        }
    }

    fun testSound() = engine.testSound()

    /** Current output route label (speaker / wired / bluetooth). */
    fun audioRoute(): String = try {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val bt = outputs.any {
            it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
        val wired = outputs.any {
            it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET
        }
        when {
            bt -> "BLUETOOTH"
            wired -> "WIRED/USB HEADSET"
            else -> "DEVICE OUTPUT (speaker)"
        }
    } catch (_: Throwable) {
        "DEVICE OUTPUT"
    }
}
