package com.gamesoundpro.app.audio

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.gamesoundpro.app.domain.Category
import com.gamesoundpro.app.domain.MixerVolumes
import com.gamesoundpro.app.domain.MusicState
import com.gamesoundpro.app.repository.SoundRepository
import com.gamesoundpro.app.service.PlaybackNotificationService
import com.gamesoundpro.app.settings.SettingsRepository
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Central audio engine built exclusively on Android's public Media3/ExoPlayer APIs.
 *
 * - A small pool of effect players lets short sounds overlap, each with per-category channel
 *   volume, per-sound gain and trim (playback window) applied via ClippingMediaSource.
 * - A dedicated music player handles the mini music player (play/pause/seek/loop/shuffle) and
 *   owns audio focus so system events (calls, other media apps) pause it properly.
 * - Optional ducking lowers music while a sound effect plays.
 *
 * Everything here plays through the normal system audio output, like any media app. The
 * engine deliberately never tries to route audio into another app's voice chat — that is not
 * possible with public APIs and is out of scope by design.
 *
 * Threading: all public functions must be called from the main thread (UI/service callbacks).
 */
class AudioEngine(
    private val context: Context,
    private val settings: SettingsRepository,
    private val repository: SoundRepository,
) {

    companion object {
        private const val EFFECT_POOL_SIZE = 4
        private const val DUCK_FACTOR = 0.35f
        private val MEDIA_ATTRS = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val dataSourceFactory = DefaultDataSource.Factory(context)
    private val progressiveFactory = ProgressiveMediaSource.Factory(dataSourceFactory)

    // ---- Effects pool ---------------------------------------------------------------

    private class EffectSlot(val player: ExoPlayer) {
        var soundId: String? = null
        var name: String = ""
        var icon: String = "🔊"
        var category: String = Category.EFFECTS.key
        var baseVolume: Float = 1f

        val isActive: Boolean get() = soundId != null
    }

    private val slots: List<EffectSlot> by lazy {
        List(EFFECT_POOL_SIZE) {
            val player = ExoPlayer.Builder(context).build().apply {
                setAudioAttributes(MEDIA_ATTRS, /* handleAudioFocus = */ false)
                setHandleAudioBecomingNoisy(true)
            }
            EffectSlot(player).also { slot ->
                slot.player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                            deactivate(slot)
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val label = slot.name.ifBlank { context.getString(R.string.app_name) }
                        deactivate(slot)
                        _errors.tryEmit(friendlyError(error, label))
                    }
                })
            }
        }
    }

    private var poolCursor = 0

    private val _activeSounds = MutableStateFlow<List<ActiveSound>>(emptyList())
    val activeSounds: StateFlow<List<ActiveSound>> = _activeSounds.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    @Volatile private var volumes: MixerVolumes = settings.snapshot.mixer
    @Volatile private var duckingEnabled: Boolean = settings.snapshot.ducking

    // ---- Music player ----------------------------------------------------------------

    private var musicPlayerField: ExoPlayer? = null
    private val musicPlayer: ExoPlayer
        get() = musicPlayerField ?: createMusicPlayer().also { musicPlayerField = it }

    private var playlist: List<SoundEntity> = emptyList()

    private val _musicState = MutableStateFlow(MusicState())
    val musicState: StateFlow<MusicState> = _musicState.asStateFlow()

    // Dedicated player for previews inside the add/edit sheets (no state, no DB writes).
    private var previewPlayerField: ExoPlayer? = null

    init {
        scope.launch {
            settings.settings.collect { s ->
                volumes = s.mixer
                duckingEnabled = s.ducking
                applyVolumesToSlots()
                applyMusicVolume()
            }
        }
        scope.launch {
            // Progress ticker for the music UI (cheap: only while playing).
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
    }

    // ---- Sound effects ---------------------------------------------------------------

    fun playSound(sound: SoundEntity) {
        val file = File(sound.filePath)
        if (!file.exists()) {
            _errors.tryEmit(context.getString(R.string.error_file_missing, sound.name))
            return
        }
        val slot = pickSlot(sound.id)
        try {
            slot.player.setMediaSource(buildSource(sound.filePath, sound.trimStartMs, sound.trimEndMs))
            slot.soundId = sound.id
            slot.name = sound.name
            slot.icon = sound.icon
            slot.category = sound.category
            slot.baseVolume = sound.volume
            slot.player.volume = slotVolume(slot)
            slot.player.prepare()
            slot.player.play()
        } catch (e: Exception) {
            deactivate(slot)
            _errors.tryEmit(context.getString(R.string.error_playback))
            return
        }
        refreshActiveSounds()
        scope.launch(Dispatchers.IO) { repository.onSoundPlayed(sound.id) }
        updateDucking()
    }

    /** Sheet preview: plays a raw file without touching the library or play counts. */
    fun playPreview(filePath: String, volume: Float, trimStartMs: Long, trimEndMs: Long) {
        if (!File(filePath).exists()) {
            _errors.tryEmit(context.getString(R.string.error_file_missing_generic))
            return
        }
        val player = previewPlayerField ?: ExoPlayer.Builder(context).build()
            .also { it.setAudioAttributes(MEDIA_ATTRS, false); previewPlayerField = it }
        try {
            player.setMediaSource(buildSource(filePath, trimStartMs, trimEndMs))
            player.volume = (volume.coerceIn(0.1f, 1.5f) * volumes.master).coerceAtMost(1f)
            player.prepare()
            player.play()
        } catch (_: Exception) {
            _errors.tryEmit(context.getString(R.string.error_playback))
        }
    }

    fun stopPreview() {
        previewPlayerField?.stop()
    }

    fun stopSound(soundId: String) {
        slots.firstOrNull { it.soundId == soundId }?.let {
            it.player.stop()
            deactivate(it)
        }
    }

    fun stopAllEffects() {
        slots.forEach { slot ->
            if (slot.isActive) {
                slot.player.stop()
                deactivate(slot)
            }
        }
        previewPlayerField?.stop()
    }

    /** Global Stop All: every effect stops and music pauses. */
    fun stopAll() {
        stopAllEffects()
        musicPlayerField?.let { if (it.isPlaying) it.pause() }
        refreshMusicState()
    }

    private fun pickSlot(soundId: String): EffectSlot {
        // Retriggering the same sound reuses its slot (restarts it).
        slots.firstOrNull { it.soundId == soundId }?.let { return it }
        slots.firstOrNull { !it.isActive }?.let { return it }
        val slot = slots[poolCursor % slots.size]
        poolCursor++
        return slot
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

    private fun updateDucking() {
        val music = musicPlayerField ?: return
        if (!duckingEnabled) return
        // Idempotent: duck while any effect is active, restore the full music volume after.
        if (slots.any { it.isActive }) {
            music.volume = (volumes.effectiveMusic() * DUCK_FACTOR).coerceAtMost(1f)
        } else {
            applyMusicVolume()
        }
    }

    // ---- Music -----------------------------------------------------------------------

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
                _errors.tryEmit(friendlyError(error, playlist.getOrNull(currentMediaItemIndexSafe())?.name ?: ""))
                refreshMusicState()
            }
        })
    }

    private fun currentMediaItemIndexSafe(): Int =
        musicPlayerField?.currentMediaItemIndex ?: 0

    fun playMusic(tracks: List<SoundEntity>, startIndex: Int) {
        val available = tracks.filter { File(it.filePath).exists() }
        if (available.isEmpty()) {
            _errors.tryEmit(context.getString(R.string.error_file_missing_generic))
            return
        }
        playlist = available
        val index = startIndex.coerceIn(0, available.lastIndex)
        val items = available.map { MediaItem.fromUri(Uri.fromFile(File(it.filePath))) }
        musicPlayer.setMediaItems(items, index, 0L)
        musicPlayer.prepare()
        musicPlayer.play()
        refreshMusicState()
    }

    fun playPauseMusic() {
        val player = musicPlayerField ?: return
        if (player.isPlaying) player.pause() else if (playlist.isNotEmpty()) player.play()
    }

    fun nextTrack() {
        val player = musicPlayerField ?: return
        if (player.hasNextMediaItem()) player.seekToNextMediaItem() else player.seekTo(0)
        player.play()
    }

    fun previousTrack() {
        val player = musicPlayerField ?: return
        if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() else player.seekTo(0)
        player.play()
    }

    fun seekMusicTo(positionMs: Long) {
        musicPlayerField?.seekTo(positionMs.coerceAtLeast(0L))
        refreshMusicState()
    }

    fun setMusicLoop(enabled: Boolean) {
        musicPlayer.repeatMode = if (enabled) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        refreshMusicState()
    }

    fun setMusicShuffle(enabled: Boolean) {
        musicPlayer.shuffleModeEnabled = enabled
        refreshMusicState()
    }

    fun stopMusic() {
        musicPlayerField?.stop()
        musicPlayerField?.clearMediaItems()
        playlist = emptyList()
        refreshMusicState()
        stopPlaybackService()
    }

    private fun refreshMusicState() {
        val player = musicPlayerField
        if (player == null || playlist.isEmpty()) {
            _musicState.value = MusicState(loopEnabled = _musicState.value.loopEnabled, shuffleEnabled = _musicState.value.shuffleEnabled)
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
        val player = musicPlayerField ?: return
        player.volume = volumes.effectiveMusic().coerceIn(0f, 1f)
    }

    /** Live music volume (mixer slider). Persisting is done by the caller. */
    fun setMusicVolumeLive(value: Float) {
        volumes = volumes.copy(music = value.coerceIn(0f, 1f))
        applyMusicVolume()
    }

    /** Live master volume (mixer slider + overlay). Persisting is done by the caller. */
    fun setMasterVolumeLive(value: Float) {
        volumes = volumes.copy(master = value.coerceIn(0f, 1f))
        applyVolumesToSlots()
        applyMusicVolume()
    }

    private fun startPlaybackService() {
        try {
            context.startForegroundService(Intent(context, PlaybackNotificationService::class.java))
        } catch (_: Exception) {
            // Starting a service can fail if the app is in a restricted state; playback in the
            // foreground is unaffected.
        }
    }

    private fun stopPlaybackService() {
        try {
            context.stopService(Intent(context, PlaybackNotificationService::class.java))
        } catch (_: Exception) {
        }
    }

    // ---- Errors ----------------------------------------------------------------------

    private fun friendlyError(error: PlaybackException, soundName: String): String {
        val prefix = if (soundName.isBlank()) "" else "\"$soundName\": "
        return prefix + when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> context.getString(R.string.error_io)
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED -> context.getString(R.string.error_unsupported_format)
            else -> context.getString(R.string.error_playback)
        }
    }
}
