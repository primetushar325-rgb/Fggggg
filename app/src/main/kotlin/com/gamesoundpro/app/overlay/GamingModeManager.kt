package com.gamesoundpro.app.overlay

import android.content.Context
import android.content.Intent
import com.gamesoundpro.app.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Turns Gaming Mode on/off: persists the preference and starts/stops the floating overlay
 * foreground service. The service itself is the only component that adds windows.
 */
class GamingModeManager(
    private val context: Context,
    private val settings: SettingsRepository,
) {

    companion object {
        /** Live flag read by MainActivity (auto-stop decision) and the UI banner. */
        val overlayActive = kotlinx.coroutines.flow.MutableStateFlow(false)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val enabled: StateFlow<Boolean> = settings.settings
        .map { it.gamingMode }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), settings.snapshot.gamingMode)

    fun setEnabled(on: Boolean) {
        scope.launch { settings.setGamingMode(on) }
        // Idempotence: enabling twice must not create a second service (V3 requirement).
        if (on && overlayActive.value) {
            com.gamesoundpro.app.utils.DebugLog.d("Overlay", "service already running — not duplicating")
            return
        }
        try {
            if (on) {
                context.startForegroundService(Intent(context, OverlayService::class.java))
            } else {
                context.stopService(Intent(context, OverlayService::class.java))
            }
        } catch (_: Exception) {
            // Starting the service requires the app to be in the foreground; all callers are.
        }
    }
}
