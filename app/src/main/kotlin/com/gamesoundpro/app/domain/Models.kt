package com.gamesoundpro.app.domain

import com.gamesoundpro.app.database.entity.SoundEntity

/** Sound categories shown as filter chips across the app. */
enum class Category(val key: String, val label: String, val emoji: String) {
    MEMES("memes", "Memes", "😂"),
    REACTIONS("reactions", "Reactions", "😱"),
    FUNNY("funny", "Funny", "🤣"),
    MUSIC("music", "Music", "🎵"),
    GAMING("gaming", "Gaming", "🎮"),
    VOICE("voice", "Voice", "🎙️"),
    EFFECTS("effects", "Effects", "💥"),
    CUSTOM("custom", "Custom", "✨");

    companion object {
        val DEFAULT = MEMES
        fun fromKey(key: String): Category = entries.firstOrNull { it.key == key } ?: CUSTOM
    }
}

/** Sort orders available in the soundboard / library screens. */
enum class SortOption(val label: String) {
    RECENTLY_ADDED("Recently added"),
    RECENTLY_PLAYED("Recently played"),
    MOST_PLAYED("Most played"),
    FAVORITES("Favorites"),
    A_Z("A-Z");

    fun sort(sounds: List<SoundEntity>): List<SoundEntity> = when (this) {
        RECENTLY_ADDED -> sounds.sortedByDescending { it.createdAt }
        RECENTLY_PLAYED -> sounds.sortedWith(
            compareByDescending<SoundEntity> { it.lastPlayedAt ?: 0L }.thenByDescending { it.createdAt }
        )
        MOST_PLAYED -> sounds.sortedWith(
            compareByDescending<SoundEntity> { it.playCount }.thenByDescending { it.createdAt }
        )
        FAVORITES -> sounds.sortedWith(
            compareByDescending<SoundEntity> { it.isFavorite }.thenByDescending { it.lastPlayedAt ?: 0L }
        )
        A_Z -> sounds.sortedBy { it.name.lowercase() }
    }
}

/** Per-channel mixer volumes. All values are 0..1. */
data class MixerVolumes(
    val effects: Float = 0.9f,
    val music: Float = 0.6f,
    val voice: Float = 1f,
    val master: Float = 1f,
    val muted: Boolean = false,
) {
    private fun channel(c: Float): Float = if (muted) 0f else c * master
    fun effectiveEffects(): Float = channel(effects)
    fun effectiveMusic(): Float = channel(music)
    fun effectiveVoice(): Float = channel(voice)
}

/** A sound currently being played by the effects engine. */
data class ActiveSound(
    val id: String,
    val name: String,
    val icon: String,
)

/** Snapshot of the music player, observed by the mini player / full player UI. */
data class MusicState(
    val track: SoundEntity? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val loopEnabled: Boolean = true,
    val shuffleEnabled: Boolean = false,
)

/** Aggregated storage numbers for the Settings → Storage section. */
data class StorageStats(
    val totalSounds: Int,
    val totalPacks: Int,
    val audioBytes: Long,
    val cacheBytes: Long,
)

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    DARK("Dark"),
    LIGHT("Light");
}

/**
 * How the soundboard treats audio focus while sound effects play.
 * NONE never touches focus (fully independent playback — game-friendly default).
 * DUCK_OTHERS takes a transient-may-duck focus around effect playback.
 */
enum class AudioFocusBehavior(val label: String, val hint: String) {
    NONE("Independent", "Never requests audio focus — game audio is untouched"),
    DUCK_OTHERS("Duck others", "Requests transient-may-duck focus while effects play"),
}

/** Shared search/filter/sort UI state for library-style screens. */
data class BoardFilterState(
    val query: String = "",
    val category: Category? = null,
    val sort: SortOption = SortOption.RECENTLY_ADDED,
)

/** Full persisted app settings. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentIndex: Int = 0,
    val haptics: Boolean = true,
    val defaultVolume: Float = 1f,
    val autoStop: Boolean = false,
    val ducking: Boolean = true,
    val gamingMode: Boolean = false,
    val overlayScale: Float = 1f,
    val overlayX: Int = Int.MIN_VALUE,
    val overlayY: Int = Int.MIN_VALUE,
    val overlayNormX: Float = Float.NaN,
    val overlayNormY: Float = Float.NaN,
    val mixer: MixerVolumes = MixerVolumes(),
    val audioFocusBehavior: AudioFocusBehavior = AudioFocusBehavior.NONE,
    val overlayKeyboard: Boolean = true,
    val overlayFilter: String = "all",
    val seedDone: Boolean = false,
) {
    companion object {
        val DEFAULT = AppSettings()
    }
}
