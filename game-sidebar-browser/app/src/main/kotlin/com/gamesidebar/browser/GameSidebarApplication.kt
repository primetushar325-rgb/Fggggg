package com.gamesidebar.browser

import android.app.Application
import com.gamesidebar.browser.data.ServiceLocator
import com.gamesidebar.browser.util.OverlayNotification

/**
 * Application entry point.
 *
 * Everything it does is cheap and local: create the notification channel, warm the singletons. No
 * analytics, no crash reporting SDK, no remote config - there is nothing to initialise because the
 * app sends nothing anywhere.
 */
class GameSidebarApplication : Application() {

    val locator: ServiceLocator by lazy { ServiceLocator.get(this) }

    override fun onCreate() {
        super.onCreate()
        OverlayNotification.ensureChannel(this)
        // Touching the locator here opens the database on a background thread before the first
        // screen needs it, so the first frame of the home screen is not waiting on Room.
        locator.database
        val debuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debuggable) {
            android.webkit.WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}
