package com.gamesoundpro.app.audio

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes as LegacyAudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ClippingMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.gamesoundpro.app.R
import com.gamesoundpro.app.database.entity.SoundEntity
import com.gamesoundpro.app.domain.ActiveSound
import com.gamesoundpro.app.domain.AudioFocusBehavior
import com.gamesoundpro.app.domain.Category
import com.gamesoundpro.app.domain.MixerVolumes
import com.gamesoundpro.app.domain.MusicState
import com.gamesoundpro.app.repository.SoundRepository
import com.gamesoundpro.app.service.PlaybackNotificationService
import com.gamesoundpro.app.settings.SettingsRepository
import com.gamesoundpro.app.utils.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * THE single authoritative audio engine for the whole app.
 *
 * Ownership: created once by [com.gamesoundpro.app.di.AppContainer] (Application scope).
 * It holds no reference to any Activity, Composable or Service — MainActivity being
 * destroyed, the sidebar closing or the app going to background can never release it.
 * Every consumer (Compose UI, floating overlay, notification service) talks to this one
 * object and observes [snapshot]/[musicState]/[activeSounds] — nobody else touches a
 * player's lifecycle.
 *
 * Concurrency model: ExoPlayer requires the main thread, so all commands run on the main
 * looper (the UI and the overlay are already main-thread). Re-entrancy is made safe by:
 *  - a single command flag preventing interleaved state mutations,
 *  - atomic slot picking (re-trigger same sound reuses its slot; otherwise a free slot or
 *    round-robin steal),
 *  - self-healing slots: a player that errored is released and rebuilt, never reused broken,
 *  - one shared [Handler] for deferred work so nothing posts to an arbitrary scope mid-layout.
 *
 * Focus policy (game-compatible, never aggressive):
 *  - Effects: by default NO focus is requested — playback is independent, the game keeps
 *    its audio. If the user picks "Duck other audio", a transient-may-duck focus is taken
 *    while effects are audible and abandoned afterwards.
 *  - Music: handled by Media3 (standard player behavior: pauses on loss, resumes after
 *    transient loss).
 *
 * The engine never injects into or interacts with any other app (including games); it plays
 * out of the device's normal audio output like any media app.
 */
class AudioEngine(
    private val context: Context,
    private val settings: SettingsRepository,
    private val repository: SoundRepository,
) {

    companion object {
        private const val TAG = "AudioEngine"
        private const val EFFECT_POOL_SIZE = 4
        private const val DUCK_FACTOR = 0.35f
        private val MEDIA_ATTRS = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val dataSourceFactory = DefaultDataSource.Factory(context)
    private val progressiveFactory = ProgressiveMediaSource.Factory(dataSourceFactory)

    // ---- Public observable state ------------------------------------------------------

    private val _snapshot = MutableStateFlow(AudioSnapshot())
    val snapshot: StateFlow<AudioSnapshot> = _snapshot.asStateFlow()

    private val _activeSounds = MutableStateFlow<List<ActiveSound>>(emptyList())
    val activeSounds: StateFlow<List<ActiveSound>> = _activeSounds.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private val _testResult = MutableStateFlow<TestSoundResult?>(null)
    val testResult: StateFlow<TestSoundResult?> = _testResult.asStateFlow()

    private val _musicState = MutableStateFlow(MusicState())
    val musicState: StateFlow<MusicState> = _musicState.asStateFlow()

    // ---- Internal state -----------------------------------------------------------------

    private class EffectSlot(var player: ExoPlayer) {
        var soundId: String? = null
        var name: String = ""
        var icon: String = "🔊"
        var category: String = Category.EFFECTS.key
        var baseVolume: Float = 1f

        // Playback source kept on the slot so a rebuild can retry without touching the DB.
        var filePath: String = ""
        var trimStartMs: Long = 0L
        var trimEndMs: Long = 0L

        val isActive: Boolean get() = soundId != null
    }

    private val slots: MutableList<EffectSlot> by lazy {
        val list = mutableListOf<EffectSlot>()
        repeat(EFFECT_POOL_SIZE) { list.add(newSlot()) }
        DebugLog.d(TAG, "pool created players=${list.size}")
        list
    }

    private var poolCursor = 0

    @Volatile private var volumes: MixerVolumes = settings.snapshot.mixer
    @Volatile private var duckingEnabled: Boolean = settings.snapshot.ducking
    @Volatile private var focusBehavior: AudioFocusBehavior = settings.snapshot.audioFocusBehavior

    private var effectFocusRequest: AudioFocusRequest? = null
    private var effectFocusHeld = false

    private var musicPlayerField: ExoPlayer? = null
    private val musicPlayer: ExoPlayer
        get() = musicPlayerField ?: createMusicPlayer().also { musicPlayerField = it }

    private var playlist: List<SoundEntity> = emptyList()
    private var previewPlayerField: ExoPlayer? = null

    /** True while a play/stop command is mutating shared state (re-entrancy guard). */
    private var commandInFlight = false

    init {
        scope.launch {
            settings.settings.collect { s ->
                volumes = s.mixer
                duckingEnabled = s.ducking
                focusBehavior = s.audioFocusBehavior
                applyVolumesToSlots()
                applyMusicVolume()
                if (s.audioFocusBehavior == AudioFocusBehavior.NONE) abandonEffectFocus()
            }
        }
        // [AudioRoute] logging: react to output device changes (headset/BT plug events)
        // by refreshing the published route — this is also what makes routing diagnostics
        // live without any polling.
        try {
            audioManager.registerAudioDeviceCallback(object : AudioDeviceCallback() {
                override fun onAudioDeviceAdded(addedDevices: IntArray?) {
                    val type = addedDevices?.firstOrNull() ?: -1
                    DebugLog.d("AudioRoute", "device added type=$type")
                    publishSnapshot { copy(audioRoute = audioRoute()) }
                }

                override fun onAudioDeviceRemoved(removedDevices: IntArray?) {
                    val type = removedDevices?.firstOrNull() ?: -1
                    DebugLog.d("AudioRoute", "device removed type=$type")
                    publishSnapshot { copy(audioRoute = audioRoute()) }
                }
            }, mainHandler)
        } catch (t: Throwable) {
            DebugLog.w("AudioRoute", "device callback registration failed", t)
        }
        scope.launch {
            // Progress ticker for the music UI. Cheap: reads the player only while playing.
            while (isActive) {
                val player = musicPlayerField
                if (player != null && player.isPlaying) {
                    _musicState.value = _musicState.value.copy(
                        positionMs = player.currentPosition.coerceAtLeast(0L),
                        durationMs = player.duration.takeIf { it > 0 } ?: 0L,
                    )
                }
                delay(500)
            }
        }
        DebugLog.d(TAG, "engine initialized scope=main thread=${Looper.myLooper() == Looper.getMainLooper()}")
    }

    /**
     * Pre-builds the effect pool and music player. Called when Gaming Mode turns ON so the
     * first in-game tap never pays player-construction cost while the game is loading.
     */
    fun warmUp() {
        mainHandler.post {
            try {
                val count = slots.size
                musicPlayer
                publishSnapshot { copy(state = if (state == AudioState.ERROR) AudioState.ERROR else AudioState.IDLE) }
                DebugLog.d(TAG, "warmUp complete players=$count musicReady=${musicPlayerField != null}")
            } catch (t: Throwable) {
                DebugLog.e(TAG, "warmUp failed", t)
            }
        }
    }

    // =====================================================================================
    // Sound effects
    // =====================================================================================

    /** Plays a sound. Safe to call rapidly and repeatedly; never throws. */
    fun playSound(sound: SoundEntity) {
        runOnMain {
            playSoundInternal(sound, countPlay = true)
        }
    }

    private fun playSoundInternal(sound: SoundEntity, countPlay: Boolean) {
        if (commandInFlight) {
            // Serialize commands: finish the current mutation first.
            mainHandler.post { playSoundInternal(sound, countPlay) }
            return
        }
        commandInFlight = true
        try {
            val file = File(sound.filePath)
            if (!file.exists() || file.length() == 0L) {
                DebugLog.w(TAG, "PLAY refused fileMissing sound=${sound.name}")
                publishSnapshot {
                    copy(
                        state = AudioState.ERROR,
                        currentSound = ActiveSound(sound.id, sound.name, sound.icon),
                        lastError = context.getString(R.string.error_file_unavailable),
                    )
                }
                _errors.tryEmit(context.getString(R.string.error_file_unavailable))
                return
            }

            requestEffectFocus()
            val slot = pickSlot(sound.id)
            try {
                slot.soundId = sound.id
                slot.name = sound.name
                slot.icon = sound.icon
                slot.category = sound.category
                slot.baseVolume = sound.volume
                slot.filePath = sound.filePath
                slot.trimStartMs = sound.trimStartMs
                slot.trimEndMs = sound.trimEndMs
                slot.player.setMediaSource(buildSource(sound.filePath, sound.trimStartMs, sound.trimEndMs))
                slot.player.volume = slotVolume(slot)
                slot.player.prepare()
                slot.player.play()
                DebugLog.d(TAG, "PLAY sound=${sound.name} slot=${slots.indexOf(slot)} state=PLAYING")
                publishSnapshot {
                    copy(
                        state = AudioState.PLAYING,
                        currentSound = ActiveSound(sound.id, sound.name, sound.icon),
                        lastError = null,
                    )
                }
                if (countPlay) {
                    scope.launch(Dispatchers.IO) { repository.onSoundPlayed(sound.id) }
                }
            } catch (t: Throwable) {
                DebugLog.e(TAG, "PLAY failed sound=${sound.name} — rebuilding slot", t)
                rebuildSlot(slot)
                publishSnapshot {
                    copy(
                        state = AudioState.ERROR,
                        currentSound = ActiveSound(sound.id, sound.name, sound.icon),
                        lastError = context.getString(R.string.error_playback),
                    )
                }
                _errors.tryEmit(context.getString(R.string.error_playback))
            }
            refreshActiveSounds()
            updateDucking()
        } finally {
            commandInFlight = false
        }
    }

    /** Plays a raw file for the add/edit sheets without touching the library or play counts. */
    fun playPreview(filePath: String, volume: Float, trimStartMs: Long, trimEndMs: Long) {
        runOnMain {
            if (!File(filePath).exists()) {
                _errors.tryEmit(context.getString(R.string.error_file_unavailable))
                return@runOnMain
            }
            val player = previewPlayerField ?: createEffectPlayer().also {
                previewPlayerField = it
            }
            try {
                player.setMediaSource(buildSource(filePath, trimStartMs, trimEndMs))
                player.volume = (volume.coerceIn(0.1f, 1.5f) * volumes.master).coerceAtMost(1f)
                player.prepare()
                player.play()
            } catch (t: Throwable) {
                DebugLog.e(TAG, "preview failed", t)
                safeRelease(previewPlayerField)
                previewPlayerField = null
                _errors.tryEmit(context.getString(R.string.error_playback))
            }
        }
    }

    fun stopPreview() {
        runOnMain {
            previewPlayerField?.let {
                try {
                    it.stop()
                } catch (t: Throwable) {
                    DebugLog.w(TAG, "preview stop failed", t)
                }
            }
        }
    }

    fun stopSound(soundId: String) {
        runOnMain {
            slots.firstOrNull { it.soundId == soundId }?.let { slot ->
                try {
                    slot.player.stop()
                } catch (t: Throwable) {
                    DebugLog.w(TAG, "stopSound failed", t)
                }
                deactivate(slot)
            }
        }
    }

    fun stopAllEffects() {
        runOnMain {
            slots.forEach { slot ->
                if (slot.isActive) {
                    try {
                        slot.player.stop()
                    } catch (t: Throwable) {
                        DebugLog.w(TAG, "stopAllEffects slot stop failed", t)
                    }
                    deactivate(slot)
                }
            }
            previewPlayerField?.let {
                try {
                    it.stop()
                } catch (_: Throwable) {
                }
            }
            updateDucking()
            DebugLog.d(TAG, "state=STOPPED reason=stopAllEffects")
        }
    }

    /** Global Stop All: every effect stops and music pauses. The engine itself stays alive. */
    fun stopAll() {
        stopAllEffects()
        runOnMain {
            musicPlayerField?.let { player ->
                try {
                    if (player.isPlaying) player.pause()
                } catch (_: Throwable) {
                }
            }
            refreshMusicState()
        }
    }

    private fun pickSlot(soundId: String): EffectSlot {
        // Retriggering the same sound reuses its slot (restarts it).
        slots.firstOrNull { it.soundId == soundId }?.let { return it }
        slots.firstOrNull { !it.isActive }?.let { return it }
        return slots[poolCursor % slots.size].also { poolCursor++ }
    }

    private fun buildSource(filePath: String, trimStartMs: Long, trimEndMs: Long): MediaSource {
        val item = MediaItem.fromUri(Uri.fromFile(File(filePath)))
        val base: MediaSource = progressiveFactory.createMediaSource(item)
        return if (trimStartMs > 0 || trimEndMs > 0) {
            val fromUs = trimStartMs.coerceAtLeast(0L) * 1000
            val toUs = if (trimEndMs > trimStartMs) trimEndMs * 1000 else C.TIME_END_OF_SOURCE
            ClippingMediaSource(base, fromUs, toUs)
        } else {
            base
        }
    }

    private fun deactivate(slot: EffectSlot) {
        val wasActive = slot.isActive
        slot.soundId = null
        if (wasActive) {
            refreshActiveSounds()
            abandonEffectFocusIfIdle()
            updateDucking()
        }
    }

    private fun refreshActiveSounds() {
        _activeSounds.value = slots.filter { it.isActive }.map { ActiveSound(it.soundId!!, it.name, it.icon) }
    }

    private fun slotVolume(slot: EffectSlot): Float {
        val channel = if (slot.category == Category.VOICE.key) volumes.effectiveVoice() else volumes.effectiveEffects()
        return (channel * slot.baseVolume).coerceIn(0f, 1f)
    }

    private fun applyVolumesToSlots() {
        slots.forEach { if (it.isActive) it.player.volume = slotVolume(it) }
    }

    /** Optional "duck music while an effect plays" behavior (Settings → Playback). */
    private fun updateDucking() {
        val music = musicPlayerField ?: return
        if (!duckingEnabled) return
        if (slots.any { it.isActive }) {
            music.volume = (volumes.effectiveMusic() * DUCK_FACTOR).coerceAtMost(1f)
        } else {
            applyMusicVolume()
        }
    }

    // =====================================================================================
    // Audio focus (effects) — explicit, non-aggressive policy
    // =====================================================================================

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        // Audio focus callbacks arrive on the main thread; log + publish state only.
        mainHandler.post {
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    effectFocusHeld = false
                    DebugLog.d("AudioFocus", "LOSS")
                    publishSnapshot { copy(focus = FocusState.LOST) }
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                -> {
                    DebugLog.d("AudioFocus", "LOSS_TRANSIENT (effects keep playing)")
                    publishSnapshot { copy(focus = FocusState.LOST) }
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    effectFocusHeld = slots.any { it.isActive }
                    DebugLog.d("AudioFocus", "GAIN held=$effectFocusHeld")
                    publishSnapshot { copy(focus = if (effectFocusHeld) FocusState.HELD else FocusState.NONE) }
                }
            }
        }
    }

    private fun requestEffectFocus() {
        if (focusBehavior == AudioFocusBehavior.NONE) return
        if (effectFocusHeld) return
        try {
            val granted = if (Build.VERSION.SDK_INT >= 26) {
                val request = effectFocusRequest ?: AudioFocusRequest.Builder(
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                ).setAudioAttributes(
                    LegacyAudioAttributes.Builder()
                        .setUsage(LegacyAudioAttributes.USAGE_MEDIA)
                        .setContentType(LegacyAudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                ).setOnAudioFocusChangeListener(focusListener).build()
                    .also { effectFocusRequest = it }
                audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    focusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
            effectFocusHeld = granted
            DebugLog.d("AudioFocus", "request behavior=DUCK_OTHERS granted=$granted")
            publishSnapshot { copy(focus = if (granted) FocusState.HELD else FocusState.LOST) }
        } catch (t: Throwable) {
            DebugLog.w("AudioFocus", "request failed (playing anyway)", t)
        }
    }

    private fun abandonEffectFocusIfIdle() {
        if (slots.none { it.isActive }) abandonEffectFocus()
    }

    private fun abandonEffectFocus() {
        if (!effectFocusHeld) return
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                effectFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(focusListener)
            }
        } catch (t: Throwable) {
            DebugLog.w("AudioFocus", "abandon failed", t)
        }
        effectFocusHeld = false
        DebugLog.d("AudioFocus", "abandoned")
        publishSnapshot { copy(focus = FocusState.NONE) }
    }

    // =====================================================================================
    // Music
    // =====================================================================================

    private fun createMusicPlayer(): ExoPlayer = ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(MEDIA_ATTRS, /* handleAudioFocus = */ true)
        setHandleAudioBecomingNoisy(true)
        repeatMode = Player.REPEAT_MODE_ALL
        addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                refreshMusicState()
                if (isPlaying) startPlaybackService()
            }

            override fun onPlaybackStateChanged(playbackState: Int) = refreshMusicState()

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = refreshMusicState()

            override fun onPlayerError(error: PlaybackException) {
                val name = playlist.getOrNull(currentMediaItemIndexSafe())?.name ?: ""
                _errors.tryEmit(friendlyError(error, name))
                DebugLog.e(TAG, "music error sound=$name", error)
                refreshMusicState()
            }
        })
        DebugLog.d(TAG, "music player created")
    }

    private fun currentMediaItemIndexSafe(): Int = musicPlayerField?.currentMediaItemIndex ?: 0

    fun playMusic(tracks: List<SoundEntity>, startIndex: Int) {
        runOnMain {
            val available = tracks.filter { File(it.filePath).exists() }
            if (available.isEmpty()) {
                _errors.tryEmit(context.getString(R.string.error_file_unavailable))
                return@runOnMain
            }
            playlist = available
            val index = startIndex.coerceIn(0, available.lastIndex)
            val items = available.map { MediaItem.fromUri(Uri.fromFile(File(it.filePath))) }
            try {
                musicPlayer.setMediaItems(items, index, 0L)
                musicPlayer.prepare()
                musicPlayer.play()
            } catch (t: Throwable) {
                DebugLog.e(TAG, "playMusic failed", t)
                _errors.tryEmit(context.getString(R.string.error_playback))
            }
            refreshMusicState()
        }
    }

    fun playPauseMusic() {
        runOnMain {
            val player = musicPlayerField
            if (player == null || playlist.isEmpty()) {
                // No playlist here — the UI layer decides what to queue. Nothing to do safely.
                return@runOnMain
            }
            try {
                if (player.isPlaying) player.pause() else player.play()
            } catch (t: Throwable) {
                DebugLog.e(TAG, "playPauseMusic failed", t)
            }
            refreshMusicState()
        }
    }

    fun nextTrack() {
        runOnMain {
            val player = musicPlayerField ?: return@runOnMain
            try {
                if (player.hasNextMediaItem()) player.seekToNextMediaItem() else player.seekTo(0)
                player.play()
            } catch (t: Throwable) {
                DebugLog.e(TAG, "nextTrack failed", t)
            }
            refreshMusicState()
        }
    }

    fun previousTrack() {
        runOnMain {
            val player = musicPlayerField ?: return@runOnMain
            try {
                if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() else player.seekTo(0)
                player.play()
            } catch (t: Throwable) {
                DebugLog.e(TAG, "previousTrack failed", t)
            }
            refreshMusicState()
        }
    }

    fun seekMusicTo(positionMs: Long) {
        runOnMain {
            try {
                musicPlayerField?.seekTo(positionMs.coerceAtLeast(0L))
            } catch (_: Throwable) {
            }
            refreshMusicState()
        }
    }

    fun setMusicLoop(enabled: Boolean) {
        runOnMain {
            musicPlayer.repeatMode = if (enabled) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            refreshMusicState()
        }
    }

    fun setMusicShuffle(enabled: Boolean) {
        runOnMain {
            musicPlayer.shuffleModeEnabled = enabled
            refreshMusicState()
        }
    }

    fun stopMusic() {
        runOnMain {
            musicPlayerField?.let {
                try {
                    it.stop()
                    it.clearMediaItems()
                } catch (_: Throwable) {
                }
            }
            playlist = emptyList()
            refreshMusicState()
            stopPlaybackService()
        }
    }

    private fun refreshMusicState() {
        val player = musicPlayerField
        if (player == null || playlist.isEmpty()) {
            _musicState.value = MusicState(
                loopEnabled = _musicState.value.loopEnabled,
                shuffleEnabled = _musicState.value.shuffleEnabled,
            )
            publishSnapshot { copy(state = if (slots.none { it.isActive }) AudioState.STOPPED else state) }
            stopPlaybackService()
            return
        }
        val index = player.currentMediaItemIndex
        _musicState.value = MusicState(
            track = playlist.getOrNull(index),
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it > 0 } ?: playlist.getOrNull(index)?.durationMs ?: 0L,
            loopEnabled = player.repeatMode != Player.REPEAT_MODE_OFF,
            shuffleEnabled = player.shuffleModeEnabled,
        )
    }

    private fun applyMusicVolume() {
        musicPlayerField?.volume = volumes.effectiveMusic().coerceIn(0f, 1f)
    }

    /** Live music volume (mixer slider). Persisting is done by the caller. */
    fun setMusicVolumeLive(value: Float) {
        volumes = volumes.copy(music = value.coerceIn(0f, 1f))
        runOnMain { applyMusicVolume() }
    }

    /** Live master volume (mixer slider + overlay). Persisting is done by the caller. */
    fun setMasterVolumeLive(value: Float) {
        volumes = volumes.copy(master = value.coerceIn(0f, 1f))
        runOnMain {
            applyVolumesToSlots()
            applyMusicVolume()
            publishSnapshot { copy(mediaVolumePercent = mediaVolumePercent()) }
        }
    }

    private fun startPlaybackService() {
        try {
            context.startForegroundService(Intent(context, PlaybackNotificationService::class.java))
        } catch (_: Exception) {
            // Starting a service can fail in restricted states; foreground playback is unaffected.
        }
    }

    private fun stopPlaybackService() {
        try {
            context.stopService(Intent(context, PlaybackNotificationService::class.java))
        } catch (_: Exception) {
        }
    }

    // =====================================================================================
    // TEST SOUND (Gaming Mode audio test + diagnostics)
    // =====================================================================================

    /**
     * Plays the bundled test tone through the normal effect pipeline and reports the result.
     * Never silently fails: the outcome (plus a volume hint when the media stream is muted)
     * lands in [testResult].
     */
    fun testSound() {
        runOnMain {
            val file = ensureTestAsset()
            if (file == null) {
                _testResult.value = TestSoundResult(success = false, failureReason = "Test sound asset missing")
                return@runOnMain
            }
            _testResult.value = null
            playSoundInternal(
                SoundEntity(
                    id = "__test_sound__",
                    name = "Test Sound",
                    filePath = file.absolutePath,
                    category = Category.EFFECTS.key,
                    icon = "🔔",
                    durationMs = 500L,
                    createdAt = 0L,
                ),
                countPlay = false,
            )
            val ok = _snapshot.value.state == AudioState.PLAYING
            val hint = if (ok && mediaVolumePercent() == 0) {
                "Media volume is 0 — press a volume key during playback and raise \"Media\"."
            } else {
                null
            }
            _testResult.value = if (ok) {
                DebugLog.d(TAG, "TEST SOUND ok")
                TestSoundResult(success = true, hint = hint)
            } else {
                DebugLog.w(TAG, "TEST SOUND failed state=${_snapshot.value.state}")
                TestSoundResult(success = false, failureReason = _snapshot.value.lastError ?: "Playback did not start")
            }
        }
    }

    private fun ensureTestAsset(): File? = try {
        val dir = File(context.cacheDir, "test").apply { mkdirs() }
        val target = File(dir, "blip.wav")
        if (!target.exists() || target.length() == 0L) {
            context.assets.open("starter/blip.wav").use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        target
    } catch (t: Throwable) {
        DebugLog.e(TAG, "test asset copy failed", t)
        null
    }

    // =====================================================================================
    // Diagnostics
    // =====================================================================================

    fun mediaVolumePercent(): Int = try {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100) / max
    } catch (_: Throwable) {
        0
    }

    private fun audioRoute(): String = try {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val bt = outputs.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        val wired = outputs.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
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

    private fun publishSnapshot(transform: AudioSnapshot.() -> AudioSnapshot) {
        _snapshot.update { current ->
            val next = current.transform()
                .copy(
                    effectPlayersAlive = slots.count { it.isActive },
                    musicPlayerCreated = musicPlayerField != null,
                    mediaVolumePercent = mediaVolumePercent(),
                )
            val route = audioRoute()
            if (route != current.audioRoute) {
                DebugLog.d("AudioRoute", "route ${current.audioRoute} -> $route")
            }
            next.copy(audioRoute = route)
        }
    }

    // =====================================================================================
    // Player construction / self-healing
    // =====================================================================================

    private fun createEffectPlayer(): ExoPlayer {
        val player = ExoPlayer.Builder(context).build()
        player.setAudioAttributes(MEDIA_ATTRS, /* handleAudioFocus = */ false)
        player.setHandleAudioBecomingNoisy(true)
        return player
    }

    private fun newSlot(): EffectSlot {
        val slot = EffectSlot(createEffectPlayer())
        attachSlotListener(slot)
        return slot
    }

    private fun attachSlotListener(slot: EffectSlot) {
        slot.player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                    deactivate(slot)
                    if (slots.none { it.isActive }) {
                        DebugLog.d(TAG, "state=STOPPED all slots idle")
                        publishSnapshot { copy(state = AudioState.STOPPED) }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val label = slot.name.ifBlank { "sound" }
                DebugLog.e(TAG, "slot error sound=$label — rebuilding player", error)
                _errors.tryEmit(friendlyError(error, label))
                publishSnapshot {
                    copy(
                        state = AudioState.ERROR,
                        lastError = friendlyError(error, label),
                    )
                }
                rebuildSlot(slot)
                refreshActiveSounds()
                abandonEffectFocusIfIdle()
            }
        })
    }

    /**
     * Releases a broken player and puts a freshly built one into the same slot, preserving
     * its metadata. A player that fails once (routing change, decoder hiccup) is never
     * reused — this is what keeps sounds audible after entering a game or toggling BT.
     */
    private fun rebuildSlot(slot: EffectSlot) {
        val keepId = slot.soundId
        val keepName = slot.name
        val keep = slot.filePath
        val keepTrimStart = slot.trimStartMs
        val keepTrimEnd = slot.trimEndMs

        try {
            slot.player.release()
        } catch (t: Throwable) {
            DebugLog.w(TAG, "release failed during rebuild", t)
        }
        slot.player = createEffectPlayer()
        slot.soundId = null // detached until it plays again
        attachSlotListener(slot)

        // One immediate retry with the healthy player — fixes transient routing failures
        // (the "works in app, silent in game" class of bugs). Self-contained: no DB access.
        if (keepId != null && keep.isNotBlank() && File(keep).exists()) {
            try {
                slot.soundId = keepId
                slot.player.setMediaSource(buildSource(keep, keepTrimStart, keepTrimEnd))
                slot.player.volume = slotVolume(slot)
                slot.player.prepare()
                slot.player.play()
                publishSnapshot { copy(state = AudioState.PLAYING, lastError = null) }
                DebugLog.d(TAG, "retry ok sound=$keepName")
            } catch (t: Throwable) {
                DebugLog.e(TAG, "retry failed sound=$keepName", t)
                deactivate(slot)
            }
        }
        refreshActiveSounds()
    }

    private fun safeRelease(player: ExoPlayer?) {
        try {
            player?.release()
        } catch (_: Throwable) {
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private fun friendlyError(error: PlaybackException, soundName: String): String {
        val prefix = if (soundName.isBlank()) "" else "\"$soundName\": "
        return prefix + when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            -> context.getString(R.string.error_io)
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            -> context.getString(R.string.error_unsupported_format)
            else -> context.getString(R.string.error_playback)
        }
    }
}
