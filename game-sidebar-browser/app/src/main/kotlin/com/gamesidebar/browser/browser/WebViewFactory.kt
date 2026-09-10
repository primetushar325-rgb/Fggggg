package com.gamesidebar.browser.browser

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import com.gamesidebar.core.model.AppSettings

/**
 * Creates and configures WebViews.
 *
 * Security posture, in one place:
 *  - only http(s) content is ever loaded (enforced by [com.gamesidebar.core.security.UrlSafety]);
 *  - file and content access are off, so a page cannot read local files;
 *  - no `addJavascriptInterface` bridge is ever registered - web pages get no Android APIs;
 *  - certificate errors are surfaced to the user, never auto-accepted;
 *  - safe browsing stays enabled where the platform provides it;
 *  - remote debugging is off outside debug builds.
 */
object WebViewFactory {

    private const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/122.0.0.0 Safari/537.36"

    /** True when Android System WebView is present and usable. */
    fun isWebViewAvailable(context: Context): Boolean = try {
        WebView(context).destroy()
        true
    } catch (unavailable: Throwable) {
        // Missing provider, disabled package, or a broken multi-process setup.
        false
    }

    @SuppressLint("SetJavaScriptEnabled") // Toggled from the user's own setting, default on.
    fun create(context: Context, settings: AppSettings): WebView {
        val webView = WebView(context.applicationContext)
        configure(webView, settings)
        return webView
    }

    fun configure(webView: WebView, settings: AppSettings) {
        webView.settings.apply {
            javaScriptEnabled = settings.javaScriptEnabled
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)

            // Local storage / IndexedDB, which YouTube and most modern sites require.
            domStorageEnabled = true
            databaseEnabled = true

            // Never let a page reach the filesystem.
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false

            loadWithOverviewMode = true
            useWideViewPort = true
            supportZoom = true
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = if (settings.desktopMode) DESKTOP_USER_AGENT else defaultMobileAgent(webView)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
        }

        webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        webView.isVerticalScrollBarEnabled = true
        webView.overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(settings.cookiesEnabled)
        cookieManager.setAcceptThirdPartyCookies(webView, settings.cookiesEnabled)

        if (!isDebuggable(webView.context)) {
            WebView.setWebContentsDebuggingEnabled(false)
        }
    }

    fun applyDesktopMode(webView: WebView, desktopMode: Boolean) {
        webView.settings.userAgentString = if (desktopMode) {
            DESKTOP_USER_AGENT
        } else {
            defaultMobileAgent(webView)
        }
    }

    private fun defaultMobileAgent(webView: WebView): String {
        val current = webView.settings.userAgentString
        if (current != DESKTOP_USER_AGENT) return current
        // We are leaving desktop mode: read the platform's own agent from a throwaway WebView
        // instead of trying to patch a desktop string back into a mobile one.
        val probe = WebView(webView.context)
        val agent = probe.settings.userAgentString
        probe.destroy()
        return agent
    }

    /** Removes cache, cookies, storage and form data - "Clear browsing data". */
    fun clearBrowsingData(context: Context) {
        runCatching { CookieManager.getInstance().removeAllCookies(null) }
        runCatching { CookieManager.getInstance().flush() }
        runCatching { WebStorage.getInstance().deleteAllData() }
        runCatching {
            val webView = WebView(context.applicationContext)
            webView.clearCache(true)
            webView.clearFormData()
            webView.clearHistory()
            webView.clearSslPreferences()
            webView.destroy()
        }
    }

    fun clearCacheOnly(context: Context) {
        runCatching {
            val webView = WebView(context.applicationContext)
            webView.clearCache(true)
            webView.destroy()
        }
    }

    private fun isDebuggable(context: Context): Boolean =
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
