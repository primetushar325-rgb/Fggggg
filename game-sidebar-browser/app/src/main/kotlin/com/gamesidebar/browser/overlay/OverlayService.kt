package com.gamesidebar.browser.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.IBinder
import android.widget.Toast
import com.gamesidebar.browser.R
import com.gamesidebar.browser.browser.WebViewFactory
import com.gamesidebar.browser.util.OverlayNotification
import com.gamesidebar.core.service.OverlayCommand

/**
 * Foreground service that owns the overlay.
 *
 * Why a foreground service: an overlay window added from a background process is torn down as soon
 * as the process leaves the foreground on modern Android, and the user has no visible way to stop it.
 * The notification is persistent, honest and carries Open / Hide / Stop - never hidden, never
 * downgraded below low importance.
 *
 * Every command arrives as an [OverlayCommand]; there is no other way to drive it.
 */
class OverlayService : Service() {

    private var manager: OverlayManager? = null
    private var webviewAvailable = true

    override fun onCreate() {
        super.onCreate()
        publishState(running = true, panelOpen = false)
        OverlayNotification.ensureChannel(this)
        webviewAvailable = WebViewFactory.isWebViewAvailable(this)
        if (webviewAvailable) {
            manager = OverlayManager(this, this).also { it.start() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Foreground first: on Android 12+ startForeground must happen promptly, and the service can
        // be started from a notification action or the app UI.
        startForegroundWithState()

        when (OverlayCommand.fromAction(intent?.action)) {
            OverlayCommand.SHOW_HANDLE -> manager?.showHandle()
            OverlayCommand.SHOW_PANEL -> openPanel()
            OverlayCommand.HIDE_PANEL -> manager?.hidePanel()
            OverlayCommand.TOGGLE_PANEL -> manager?.togglePanel()
            OverlayCommand.OPEN_URL -> {
                openPanel()
                intent?.getStringExtra(EXTRA_URL)?.let { url -> manager?.let { m -> m.openUrl(url) } }
            }

            OverlayCommand.OPEN_APP -> launchApp()
            OverlayCommand.TOGGLE_GAMING_MODE -> manager?.toggleGamingMode()
            OverlayCommand.STOP_SERVICE -> stopSelf()
            null -> manager?.showHandle()
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotation, split screen, foldables and display changes all land here: the panel and handle
        // are re-measured against the new insets instead of being left in the old geometry.
        manager?.onScreenChanged()
    }

    override fun onDestroy() {
        manager?.stop()
        manager = null
        publishState(running = false, panelOpen = false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------------------------------------------------------------- state

    fun onPanelStateChanged(panelOpen: Boolean) {
        publishState(running = true, panelOpen = panelOpen)
        startForegroundWithState(panelOpen)
    }

    /** The overlay could not be attached - permission revoked while running, or no WebView. */
    fun onOverlayFailed() {
        Toast.makeText(this, R.string.error_overlay_permission, Toast.LENGTH_LONG).show()
        stopSelf()
    }

    private fun startForegroundWithState(panelOpen: Boolean = manager?.isPanelOpen == true) {
        val notification = if (webviewAvailable) {
            OverlayNotification.build(this, panelOpen)
        } else {
            OverlayNotification.buildUnavailable(this)
        }
        runCatching { startForeground(OverlayNotification.NOTIFICATION_ID, notification) }
            .onFailure {
                // ForegroundServiceStartNotAllowedException: the system refused the service, so
                // there is nothing to keep running.
                stopSelf()
            }
    }

    private fun openPanel() {
        if (!webviewAvailable) {
            Toast.makeText(this, R.string.error_webview_missing, Toast.LENGTH_LONG).show()
            return
        }
        if (manager == null) {
            manager = OverlayManager(this, this).also { it.start() }
        }
        manager?.showPanel()
    }

    private fun launchApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
    }

    companion object {
        const val EXTRA_URL = "com.gamesidebar.browser.extra.URL"

        /**
         * Cheap "is it running" flag for the home screen. A bound service would be the textbook
         * answer, but the UI only needs a yes/no on resume, and a volatile costs nothing.
         */
        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var isPanelOpen: Boolean = false
            private set

        internal fun publishState(running: Boolean, panelOpen: Boolean) {
            isRunning = running
            isPanelOpen = panelOpen
        }

        fun start(context: Context, command: OverlayCommand = OverlayCommand.SHOW_HANDLE, url: String? = null) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = command.action
                url?.let { putExtra(EXTRA_URL, it) }
            }
            runCatching {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, OverlayService::class.java)) }
        }
    }
}
