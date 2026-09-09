package com.gamesoundpro.app.audio

import com.gamesoundpro.app.domain.ActiveSound

/**
 * Central audio state machine exposed by [AudioEngine]. The UI (app screens AND the
 * floating sidebar) observes these states — it never touches the low-level player
 * lifecycle directly.
 */
enum class AudioState {
    /** No playback requested yet. */
    IDLE,

    /** A sound is preparing (short; ExoPlayer prepares synchronously for local files). */
    LOADING,

    /** At least one sound is playing. */
    PLAYING,

    /** Music is paused (effects are never paused). */
    PAUSED,

    /** Everything stopped. */
    STOPPED,

    /** The last command failed; the engine stays alive and recoverable. */
    ERROR,
}

/** Our audio-focus holder state for sound effects. */
enum class FocusState {
    /** We never requested focus (game-friendly default) or abandoned it. */
    NONE,

    /** We hold transient-may-duck focus (only in the "duck others" focus behavior). */
    HELD,

    /** Focus was taken away by another app while we held it. */
    LOST,
}

/** Immutable snapshot of the whole audio engine, for the UI + Audio Diagnostics screen. */
data class AudioSnapshot(
    val state: AudioState = AudioState.IDLE,
    val currentSound: ActiveSound? = null,
    val focus: FocusState = FocusState.NONE,
    val effectPlayersAlive: Int = 0,
    val musicPlayerCreated: Boolean = false,
    val mediaVolumePercent: Int = 0,
    val audioRoute: String = "DEVICE OUTPUT",
    val lastError: String? = null,
)

/** Result of the "TEST SOUND" button: success flag plus an optional user-facing hint. */
data class TestSoundResult(
    val success: Boolean,
    val hint: String? = null,
    val failureReason: String? = null,
)
