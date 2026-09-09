package com.gamesoundpro.app.ui.diagnostics

import android.app.Application
import android.media.AudioDeviceInfo
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
import com.gamesoundpro.app.permissions.Permissions
import com.gamesoundpro.app.audio.VoiceRecorder
import com.gamesoundpro.app.utils.DebugLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** User's answer to the audible tone question (null = not asked yet). */
enum class HeardAnswer { YES, NO }

/** State machine for the explicit, user-triggered microphone test. */
sealed class MicTestState {
    object Idle : MicTestState()
    object Recording : MicTestState()
    data class Recorded(val file: File, val durationMs: Long) : MicTestState()
    data class Failed(val reason: String) : MicTestState()
}

/**
 * One selectable audio mode in the routing diagnostics. Only modes the device can actually
 * use are marked available — the app never advertises unsupported or fake routing.
 */
data class AudioModeOption(
    val key: String,
    val label: String,
    val available: Boolean,
    val detail: String,
)

/**
 * Backing state for the Audio Routing Diagnostics screen: live engine/overlay/permission
 * rows, the three-step AUDIO TEST (tone → mic sample → verdict) and the audio-mode
 * availability list. The microphone is only ever touched by an explicit user action.
 */
class DiagnosticsViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        const val MIC_TEST_MS = 3_000L
    }

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

    val testResult: StateFlow<TestSoundResult?> = engine.testResult

    private val recorder = VoiceRecorder(app)
    val recorderState: StateFlow<com.gamesoundpro.app.audio.RecorderState> = recorder.state
    val recorderElapsed: StateFlow<Long> = recorder.elapsedMs

    private val _micTest = MutableStateFlow<MicTestState>(MicTestState.Idle)
    val micTest: StateFlow<MicTestState> = _micTest.asStateFlow()

    private val _heardAnswer = MutableStateFlow<HeardAnswer?>(null)
    val heardAnswer: StateFlow<HeardAnswer?> = _heardAnswer.asStateFlow()

    private val _selectedMode = MutableStateFlow<String?>("device")
    val selectedMode: StateFlow<String?> = _selectedMode.asStateFlow()

    private val _mediaVolume = MutableStateFlow(0)
    val mediaVolume: StateFlow<Int> = _mediaVolume.asStateFlow()

    private var autoStopJob: Job? = null

    init {
        viewModelScope.launch {
            while (true) {
                _mediaVolume.value = engine.mediaVolumePercent()
                delay(1_000)
            }
        }
    }

    fun testSound() = engine.testSound()

    fun setHeardAnswer(answer: HeardAnswer) {
        _heardAnswer.value = answer
    }

    // ---- Microphone test (explicit user action only) ----------------------------------

    fun startMicTest() {
        if (_micTest.value is MicTestState.Recording) return
        if (!Permissions.hasRecordAudio(getApplication())) {
            _micTest.value = MicTestState.Failed("Microphone permission denied")
            return
        }
        if (!microphoneHardwareAvailable()) {
            _micTest.value = MicTestState.Failed("No microphone hardware reported available")
            return
        }
        if (recorder.start()) {
            _micTest.value = MicTestState.Recording
            DebugLog.d("AudioEngine", "MIC TEST start (user action) duration=$MIC_TEST_MS ms")
            autoStopJob?.cancel()
            autoStopJob = viewModelScope.launch {
                delay(MIC_TEST_MS)
                if (_micTest.value is MicTestState.Recording) stopMicTest()
            }
        } else {
            _micTest.value = MicTestState.Failed("Microphone busy or unavailable")
        }
    }

    fun stopMicTest() {
        autoStopJob?.cancel()
        if (_micTest.value !is MicTestState.Recording) return
        val file = recorder.stop()
        _micTest.value = if (file != null) {
            DebugLog.d("AudioEngine", "MIC TEST ok file=${file.name} bytes=${file.length()}")
            MicTestState.Recorded(file, recorder.elapsedMs.value)
        } else {
            MicTestState.Failed("No audio captured — try again")
        }
    }

    /** Plays the captured sample locally (never leaves the device). */
    fun playRecording() {
        val state = _micTest.value
        if (state is MicTestState.Recorded) {
            engine.playPreview(state.file.absolutePath, volume = 1f, trimStartMs = 0, trimEndMs = 0)
        }
    }

    fun discardRecording() {
        autoStopJob?.cancel()
        recorder.discard()
        _micTest.value = MicTestState.Idle
    }

    override fun onCleared() {
        autoStopJob?.cancel()
        if (recorderState.value == com.gamesoundpro.app.audio.RecorderState.RECORDING ||
            recorderState.value == com.gamesoundpro.app.audio.RecorderState.PAUSED
        ) {
            recorder.discard()
        }
        super.onCleared()
    }

    // ---- Modes / permissions / hardware -------------------------------------------------

    fun hasMicPermission(): Boolean = Permissions.hasRecordAudio(getApplication())

    fun hasOverlayPermission(): Boolean = Permissions.canDrawOverlays(getApplication())

    /** True when the device reports at least one usable microphone input. */
    fun microphoneHardwareAvailable(): Boolean = try {
        audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).any {
            it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
    } catch (_: Throwable) {
        false
    }

    /** Output modes the device currently supports — no fake routing options. */
    fun audioModes(): List<AudioModeOption> {
        val route = snapshot.value.audioRoute
        val wired = hasOutputType(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
        )
        val bt = hasOutputType(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        )
        return listOf(
            AudioModeOption(
                "device", "Device playback", available = true,
                detail = if (route.startsWith("DEVICE")) "Active output" else "Switch output at the system level to use",
            ),
            AudioModeOption(
                "wired", "Headset / wired output", available = wired,
                detail = if (wired) if (route.startsWith("WIRED")) "Active output" else "Connected — Android switches automatically" else "No wired headset connected",
            ),
            AudioModeOption(
                "bluetooth", "Bluetooth output", available = bt,
                detail = if (bt) if (route.startsWith("BLUETOOTH")) "Active output" else "Connected — Android switches automatically" else "No Bluetooth audio device connected",
            ),
            AudioModeOption(
                "mictest", "Microphone test", available = hasMicPermission(),
                detail = if (hasMicPermission()) "Records only on explicit user action" else "Needs microphone permission",
            ),
        )
    }

    fun selectMode(key: String) {
        _selectedMode.value = key
        DebugLog.d("AudioRoute", "mode selected: $key")
    }

    private fun hasOutputType(vararg types: Int): Boolean = try {
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in types }
    } catch (_: Throwable) {
        false
    }

    /** Current output route label (speaker / wired / bluetooth). */
    fun audioRoute(): String = try {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val bt = outputs.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
        val wired = outputs.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
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
