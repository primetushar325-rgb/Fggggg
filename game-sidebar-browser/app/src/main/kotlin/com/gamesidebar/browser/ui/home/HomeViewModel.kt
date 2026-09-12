package com.gamesidebar.browser.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gamesidebar.browser.browser.WebViewFactory
import com.gamesidebar.browser.data.ServiceLocator
import com.gamesidebar.browser.overlay.OverlayService
import com.gamesidebar.browser.util.OverlayPermission
import com.gamesidebar.core.model.AppSettings
import com.gamesidebar.core.service.OverlayCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Home screen state.
 *
 * The screen is the control panel for the service: it starts and stops the overlay, and it is where
 * the permission and capability problems are explained before the user hits a wall inside a game.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val locator = ServiceLocator.get(application)

    data class UiState(
        val settings: AppSettings = AppSettings.DEFAULT,
        val sidebarRunning: Boolean = false,
        val panelOpen: Boolean = false,
        val overlayGranted: Boolean = false,
        val webviewAvailable: Boolean = true,
        val notificationPermissionNeeded: Boolean = false,
        val batteryOptimised: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            locator.settingsRepository.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
            }
        }
        refresh()
    }

    /** Re-read everything that can change outside the app (permissions, service, WebView). */
    fun refresh() {
        val context = getApplication<Application>()
        _state.update {
            it.copy(
                sidebarRunning = OverlayService.isRunning,
                panelOpen = OverlayService.isPanelOpen,
                overlayGranted = OverlayPermission.canDrawOverlays(context),
                webviewAvailable = WebViewFactory.isWebViewAvailable(context),
                notificationPermissionNeeded = OverlayPermission.needsNotificationPermission(context),
                batteryOptimised = !OverlayPermission.isIgnoringBatteryOptimizations(context),
            )
        }
    }

    fun startSidebar() {
        val context = getApplication<Application>()
        if (!OverlayPermission.canDrawOverlays(context)) return
        OverlayService.start(context, OverlayCommand.SHOW_HANDLE)
        _state.update { it.copy(sidebarRunning = true) }
    }

    fun stopSidebar() {
        OverlayService.stop(getApplication<Application>())
        _state.update { it.copy(sidebarRunning = false, panelOpen = false) }
    }

    fun openPanel() {
        val context = getApplication<Application>()
        if (!OverlayPermission.canDrawOverlays(context)) return
        OverlayService.start(context, OverlayCommand.SHOW_PANEL)
        _state.update { it.copy(sidebarRunning = true, panelOpen = true) }
    }

    fun setAutoStart(enabled: Boolean) {
        viewModelScope.launch { locator.settingsRepository.update { it.copy(autoStartSidebar = enabled) } }
    }

    fun setGamingMode(enabled: Boolean) {
        viewModelScope.launch { locator.settingsRepository.update { it.copy(gamingMode = enabled) } }
    }
}
