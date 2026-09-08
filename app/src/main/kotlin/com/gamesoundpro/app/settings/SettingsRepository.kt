package com.gamesoundpro.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gamesoundpro.app.domain.AppSettings
import com.gamesoundpro.app.domain.MixerVolumes
import com.gamesoundpro.app.domain.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gamesound_settings")

/**
 * Single source of truth for user preferences (DataStore). Also keeps a main-thread snapshot
 * so non-suspend call sites (e.g. lifecycle callbacks) can read the current values cheaply.
 */
class SettingsRepository(context: Context) {

    private object Keys {
        val THEME_MODE = intPreferencesKey("theme_mode")
        val ACCENT = intPreferencesKey("accent_index")
        val HAPTICS = booleanPreferencesKey("haptics")
        val DEFAULT_VOLUME = floatPreferencesKey("default_volume")
        val AUTO_STOP = booleanPreferencesKey("auto_stop")
        val DUCKING = booleanPreferencesKey("audio_ducking")
        val GAMING_MODE = booleanPreferencesKey("gaming_mode")
        val OVERLAY_SCALE = floatPreferencesKey("overlay_scale")
        val OVERLAY_X = intPreferencesKey("overlay_x")
        val OVERLAY_Y = intPreferencesKey("overlay_y")
        val SEED_DONE = booleanPreferencesKey("seed_done")
        val VOL_EFFECTS = floatPreferencesKey("vol_effects")
        val VOL_MUSIC = floatPreferencesKey("vol_music")
        val VOL_VOICE = floatPreferencesKey("vol_voice")
        val VOL_MASTER = floatPreferencesKey("vol_master")
        val VOL_MUTED = booleanPreferencesKey("vol_muted")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var snapshot: AppSettings = AppSettings.DEFAULT
        private set

    val settings: Flow<AppSettings> = context.dataStore.data.map { p -> p.toSettings() }

    init {
        scope.launch { settings.collect { snapshot = it } }
    }

    private fun Preferences.toSettings(): AppSettings {
        val theme = when (this[Keys.THEME_MODE] ?: ThemeMode.DARK.ordinal) {
            ThemeMode.SYSTEM.ordinal -> ThemeMode.SYSTEM
            ThemeMode.LIGHT.ordinal -> ThemeMode.LIGHT
            else -> ThemeMode.DARK
        }
        return AppSettings(
            themeMode = theme,
            accentIndex = this[Keys.ACCENT] ?: 0,
            haptics = this[Keys.HAPTICS] ?: true,
            defaultVolume = this[Keys.DEFAULT_VOLUME] ?: 1f,
            autoStop = this[Keys.AUTO_STOP] ?: false,
            ducking = this[Keys.DUCKING] ?: true,
            gamingMode = this[Keys.GAMING_MODE] ?: false,
            overlayScale = this[Keys.OVERLAY_SCALE] ?: 1f,
            overlayX = this[Keys.OVERLAY_X] ?: Int.MIN_VALUE,
            overlayY = this[Keys.OVERLAY_Y] ?: Int.MIN_VALUE,
            seedDone = this[Keys.SEED_DONE] ?: false,
            mixer = MixerVolumes(
                effects = this[Keys.VOL_EFFECTS] ?: 0.9f,
                music = this[Keys.VOL_MUSIC] ?: 0.6f,
                voice = this[Keys.VOL_VOICE] ?: 1f,
                master = this[Keys.VOL_MASTER] ?: 1f,
                muted = this[Keys.VOL_MUTED] ?: false,
            ),
        )
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { p ->
            block(p)
            snapshot = p.toSettings()
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME_MODE] = mode.ordinal }
    suspend fun setAccentIndex(index: Int) = edit { it[Keys.ACCENT] = index }
    suspend fun setHaptics(enabled: Boolean) = edit { it[Keys.HAPTICS] = enabled }
    suspend fun setDefaultVolume(value: Float) = edit { it[Keys.DEFAULT_VOLUME] = value.coerceIn(0f, 1f) }
    suspend fun setAutoStop(enabled: Boolean) = edit { it[Keys.AUTO_STOP] = enabled }
    suspend fun setDucking(enabled: Boolean) = edit { it[Keys.DUCKING] = enabled }
    suspend fun setGamingMode(enabled: Boolean) = edit { it[Keys.GAMING_MODE] = enabled }
    suspend fun setOverlayScale(scale: Float) = edit { it[Keys.OVERLAY_SCALE] = scale.coerceIn(0.7f, 1.6f) }
    suspend fun setSeedDone(done: Boolean) = edit { it[Keys.SEED_DONE] = done }

    suspend fun setOverlayPosition(x: Int, y: Int) = edit {
        it[Keys.OVERLAY_X] = x
        it[Keys.OVERLAY_Y] = y
    }

    suspend fun resetOverlayPosition() = edit {
        it[Keys.OVERLAY_X] = Int.MIN_VALUE
        it[Keys.OVERLAY_Y] = Int.MIN_VALUE
    }

    suspend fun setMixerVolumes(volumes: MixerVolumes) = edit {
        it[Keys.VOL_EFFECTS] = volumes.effects
        it[Keys.VOL_MUSIC] = volumes.music
        it[Keys.VOL_VOICE] = volumes.voice
        it[Keys.VOL_MASTER] = volumes.master
        it[Keys.VOL_MUTED] = volumes.muted
    }

    suspend fun setMasterVolume(value: Float) {
        val current = snapshot.mixer
        setMixerVolumes(current.copy(master = (value * 100).roundToInt() / 100f))
    }

    suspend fun toggleMuted() {
        val current = snapshot.mixer
        setMixerVolumes(current.copy(muted = !current.muted))
    }
}
