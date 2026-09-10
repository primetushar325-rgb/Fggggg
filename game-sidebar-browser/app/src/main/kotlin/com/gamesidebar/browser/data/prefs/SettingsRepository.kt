package com.gamesidebar.browser.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gamesidebar.core.browser.Shortcut
import com.gamesidebar.core.browser.ShortcutCatalog
import com.gamesidebar.core.browser.ShortcutCodec
import com.gamesidebar.core.browser.ShortcutList
import com.gamesidebar.core.model.AppSettings
import com.gamesidebar.core.model.GlowColor
import com.gamesidebar.core.model.GlowIntensity
import com.gamesidebar.core.model.HandleSize
import com.gamesidebar.core.model.HorizontalAnchor
import com.gamesidebar.core.model.PanelSize
import com.gamesidebar.core.model.SearchEngine
import com.gamesidebar.core.model.VerticalAnchor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "gamesidebar_settings")

/**
 * Every user preference and the last-known overlay geometry, in DataStore.
 *
 * Exposed as one [Flow] of [AppSettings] so the overlay never applies a half-updated configuration:
 * a settings change arrives as a whole new immutable value.
 */
class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.settingsStore.data
        .catch { cause ->
            // A corrupt preferences file must not brick the app: fall back to defaults.
            if (cause is java.io.IOException) emit(emptyPreferences()) else throw cause
        }
        .map { it.toSettings() }

    val shortcuts: Flow<ShortcutList> = context.settingsStore.data
        .catch { cause -> if (cause is java.io.IOException) emit(emptyPreferences()) else throw cause }
        .map { prefs -> ShortcutList(ShortcutCodec.decode(prefs[Keys.SHORTCUTS])) }

    /** Handle position as fractions of the usable screen, so it survives rotation and resizes. */
    val handlePosition: Flow<Pair<Float, Float>?> = context.settingsStore.data.map { prefs ->
        val x = prefs[Keys.HANDLE_X_FRACTION] ?: return@map null
        val y = prefs[Keys.HANDLE_Y_FRACTION] ?: return@map null
        x to y
    }

    suspend fun currentSettings(): AppSettings = settings.first()

    suspend fun currentShortcuts(): ShortcutList = shortcuts.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(currentSettings())
        context.settingsStore.edit {
            this[Keys.AUTO_START] = updated.autoStartSidebar
            this[Keys.REMEMBER_POSITION] = updated.rememberPosition
            this[Keys.TAP_OUTSIDE] = updated.tapOutsideToClose
            this[Keys.SEARCH_ENGINE] = updated.searchEngine.name
            this[Keys.DESKTOP_MODE] = updated.desktopMode
            this[Keys.JAVASCRIPT] = updated.javaScriptEnabled
            this[Keys.COOKIES] = updated.cookiesEnabled
            this[Keys.SWIPE_TABS] = updated.swipeBetweenTabs
            this[Keys.HTTPS_PREFERRED] = updated.httpsPreferred
            this[Keys.PANEL_SIZE] = updated.panelSize.name
            this[Keys.PANEL_WIDTH_FRACTION] = updated.customPanelWidthFraction
            this[Keys.PANEL_HEIGHT_FRACTION] = updated.customPanelHeightFraction
            this[Keys.PANEL_V_ANCHOR] = updated.panelVerticalAnchor.name
            this[Keys.PANEL_H_ANCHOR] = updated.panelHorizontalAnchor.name
            this[Keys.AUTO_SNAP] = updated.autoSnap
            this[Keys.EDGE_HIDE] = updated.edgeHideMode
            this[Keys.HANDLE_SIZE] = updated.handleSize.name
            this[Keys.HANDLE_V_ANCHOR] = updated.handleVerticalAnchor.name
            this[Keys.HANDLE_H_ANCHOR] = updated.handleHorizontalAnchor.name
            this[Keys.GAMING_MODE] = updated.gamingMode
            this[Keys.REDUCED_ANIMATIONS] = updated.reducedAnimations
            this[Keys.OPACITY] = updated.panelOpacity
            this[Keys.GLOW_ENABLED] = updated.glowEnabled
            this[Keys.GLOW_INTENSITY] = updated.glowIntensity.name
            this[Keys.GLOW_COLOR] = updated.glowColor.name
            this[Keys.SAVE_HISTORY] = updated.saveHistory
            this[Keys.INCOGNITO] = updated.incognito
        }
    }

    suspend fun saveShortcuts(list: ShortcutList) {
        context.settingsStore.edit { this[Keys.SHORTCUTS] = ShortcutCodec.encode(list.items) }
    }

    suspend fun resetShortcuts() {
        context.settingsStore.edit { this[Keys.SHORTCUTS] = ShortcutCodec.encode(ShortcutCatalog.defaults) }
    }

    /** Stores fractions (0..1) rather than pixels so rotation and screen changes stay sane. */
    suspend fun saveHandlePosition(xFraction: Float, yFraction: Float) {
        context.settingsStore.edit {
            this[Keys.HANDLE_X_FRACTION] = xFraction.coerceIn(0f, 1f)
            this[Keys.HANDLE_Y_FRACTION] = yFraction.coerceIn(0f, 1f)
        }
    }

    suspend fun clearHandlePosition() {
        context.settingsStore.edit {
            remove(Keys.HANDLE_X_FRACTION)
            remove(Keys.HANDLE_Y_FRACTION)
        }
    }

    private fun emptyPreferences(): Preferences =
        androidx.datastore.preferences.core.emptyPreferences()

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings.DEFAULT
        val opacity = (this[Keys.OPACITY] ?: defaults.panelOpacity).coerceIn(AppSettings.OPACITY_RANGE)
        val widthFraction = (this[Keys.PANEL_WIDTH_FRACTION] ?: defaults.customPanelWidthFraction)
            .coerceIn(AppSettings.FRACTION_RANGE)
        val heightFraction = (this[Keys.PANEL_HEIGHT_FRACTION] ?: defaults.customPanelHeightFraction)
            .coerceIn(AppSettings.FRACTION_RANGE)
        return AppSettings(
            autoStartSidebar = this[Keys.AUTO_START] ?: defaults.autoStartSidebar,
            rememberPosition = this[Keys.REMEMBER_POSITION] ?: defaults.rememberPosition,
            tapOutsideToClose = this[Keys.TAP_OUTSIDE] ?: defaults.tapOutsideToClose,
            searchEngine = enumOrDefault(this[Keys.SEARCH_ENGINE], defaults.searchEngine),
            desktopMode = this[Keys.DESKTOP_MODE] ?: defaults.desktopMode,
            javaScriptEnabled = this[Keys.JAVASCRIPT] ?: defaults.javaScriptEnabled,
            cookiesEnabled = this[Keys.COOKIES] ?: defaults.cookiesEnabled,
            swipeBetweenTabs = this[Keys.SWIPE_TABS] ?: defaults.swipeBetweenTabs,
            httpsPreferred = this[Keys.HTTPS_PREFERRED] ?: defaults.httpsPreferred,
            panelSize = enumOrDefault(this[Keys.PANEL_SIZE], defaults.panelSize),
            customPanelWidthFraction = widthFraction,
            customPanelHeightFraction = heightFraction,
            panelVerticalAnchor = enumOrDefault(this[Keys.PANEL_V_ANCHOR], defaults.panelVerticalAnchor),
            panelHorizontalAnchor = enumOrDefault(this[Keys.PANEL_H_ANCHOR], defaults.panelHorizontalAnchor),
            autoSnap = this[Keys.AUTO_SNAP] ?: defaults.autoSnap,
            edgeHideMode = this[Keys.EDGE_HIDE] ?: defaults.edgeHideMode,
            handleSize = enumOrDefault(this[Keys.HANDLE_SIZE], defaults.handleSize),
            handleVerticalAnchor = enumOrDefault(this[Keys.HANDLE_V_ANCHOR], defaults.handleVerticalAnchor),
            handleHorizontalAnchor = enumOrDefault(this[Keys.HANDLE_H_ANCHOR], defaults.handleHorizontalAnchor),
            gamingMode = this[Keys.GAMING_MODE] ?: defaults.gamingMode,
            reducedAnimations = this[Keys.REDUCED_ANIMATIONS] ?: defaults.reducedAnimations,
            panelOpacity = opacity,
            glowEnabled = this[Keys.GLOW_ENABLED] ?: defaults.glowEnabled,
            glowIntensity = enumOrDefault(this[Keys.GLOW_INTENSITY], defaults.glowIntensity),
            glowColor = enumOrDefault(this[Keys.GLOW_COLOR], defaults.glowColor),
            saveHistory = this[Keys.SAVE_HISTORY] ?: defaults.saveHistory,
            incognito = this[Keys.INCOGNITO] ?: defaults.incognito,
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, default: T): T =
        raw?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default

    private object Keys {
        val AUTO_START = booleanPreferencesKey("auto_start_sidebar")
        val REMEMBER_POSITION = booleanPreferencesKey("remember_position")
        val TAP_OUTSIDE = booleanPreferencesKey("tap_outside_to_close")
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val DESKTOP_MODE = booleanPreferencesKey("desktop_mode")
        val JAVASCRIPT = booleanPreferencesKey("javascript_enabled")
        val COOKIES = booleanPreferencesKey("cookies_enabled")
        val SWIPE_TABS = booleanPreferencesKey("swipe_between_tabs")
        val HTTPS_PREFERRED = booleanPreferencesKey("https_preferred")
        val PANEL_SIZE = stringPreferencesKey("panel_size")
        val PANEL_WIDTH_FRACTION = floatPreferencesKey("panel_width_fraction")
        val PANEL_HEIGHT_FRACTION = floatPreferencesKey("panel_height_fraction")
        val PANEL_V_ANCHOR = stringPreferencesKey("panel_vertical_anchor")
        val PANEL_H_ANCHOR = stringPreferencesKey("panel_horizontal_anchor")
        val AUTO_SNAP = booleanPreferencesKey("auto_snap")
        val EDGE_HIDE = booleanPreferencesKey("edge_hide_mode")
        val HANDLE_SIZE = stringPreferencesKey("handle_size")
        val HANDLE_V_ANCHOR = stringPreferencesKey("handle_vertical_anchor")
        val HANDLE_H_ANCHOR = stringPreferencesKey("handle_horizontal_anchor")
        val GAMING_MODE = booleanPreferencesKey("gaming_mode")
        val REDUCED_ANIMATIONS = booleanPreferencesKey("reduced_animations")
        val OPACITY = intPreferencesKey("panel_opacity")
        val GLOW_ENABLED = booleanPreferencesKey("glow_enabled")
        val GLOW_INTENSITY = stringPreferencesKey("glow_intensity")
        val GLOW_COLOR = stringPreferencesKey("glow_color")
        val SAVE_HISTORY = booleanPreferencesKey("save_history")
        val INCOGNITO = booleanPreferencesKey("incognito")
        val SHORTCUTS = stringPreferencesKey("shortcuts")
        val HANDLE_X_FRACTION = floatPreferencesKey("handle_x_fraction")
        val HANDLE_Y_FRACTION = floatPreferencesKey("handle_y_fraction")
    }
}

/** Convenience used by the settings screen: resolve an icon key to its resource-independent label. */
fun Shortcut.iconLabel(): String = com.gamesidebar.core.browser.ShortcutIcon.fromKey(iconKey).label
