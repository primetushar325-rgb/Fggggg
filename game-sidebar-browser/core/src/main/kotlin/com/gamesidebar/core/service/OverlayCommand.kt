package com.gamesidebar.core.service

/**
 * The complete set of things that can be asked of the overlay service, from a notification action,
 * a shortcut, a broadcast or the app UI.
 *
 * Everything the service does is one of these; adding behaviour means adding a command, which keeps
 * the service's `onStartCommand` a flat, auditable switch.
 */
enum class OverlayCommand(val action: String) {
    /** Ensure the service is running and show only the small handle. */
    SHOW_HANDLE("com.gamesidebar.browser.action.SHOW_HANDLE"),

    /** Open the floating panel over whatever is on screen. */
    SHOW_PANEL("com.gamesidebar.browser.action.SHOW_PANEL"),

    /** Collapse the panel back into the handle, keeping the service alive. */
    HIDE_PANEL("com.gamesidebar.browser.action.HIDE_PANEL"),

    /** Tap-the-handle behaviour: open if closed, minimise if open. */
    TOGGLE_PANEL("com.gamesidebar.browser.action.TOGGLE_PANEL"),

    /** Open a URL in the panel (used by quick shortcuts and the notification). */
    OPEN_URL("com.gamesidebar.browser.action.OPEN_URL"),

    /** Bring the app UI to the front. */
    OPEN_APP("com.gamesidebar.browser.action.OPEN_APP"),

    /** Flip Gaming Mode without opening the app. */
    TOGGLE_GAMING_MODE("com.gamesidebar.browser.action.TOGGLE_GAMING_MODE"),

    /** Tear everything down: remove the overlay windows, stop the foreground service. */
    STOP_SERVICE("com.gamesidebar.browser.action.STOP_SERVICE"),
    ;

    companion object {
        private val byAction = entries.associateBy { it.action }
        fun fromAction(action: String?): OverlayCommand? = action?.let { byAction[it] }
    }
}

/** Why the panel is not available - drives the error surface instead of a silent no-op. */
enum class OverlayUnavailableReason(val messageKey: String) {
    PERMISSION_MISSING("error_overlay_permission"),
    WEBVIEW_MISSING("error_webview_missing"),
    SERVICE_RESTRICTED("error_service_restricted"),
    NO_NETWORK("error_no_network"),
}
