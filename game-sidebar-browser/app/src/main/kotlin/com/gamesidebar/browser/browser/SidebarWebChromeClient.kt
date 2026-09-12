package com.gamesidebar.browser.browser

import android.graphics.Bitmap
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView

/**
 * Progress, titles, favicons and HTML5 fullscreen.
 *
 * Device capabilities are refused on purpose: the app holds no camera, microphone or location
 * permission, so a page asking for them gets a clean denial rather than a broken half-grant.
 */
class SidebarWebChromeClient(
    private val callbacks: Callbacks,
) : WebChromeClient() {

    interface Callbacks {
        fun onProgress(progress: Int)
        fun onTitleReceived(url: String?, title: String?)
        fun onFaviconReceived(url: String?, icon: Bitmap?)
        fun onEnterFullscreen(view: View)
        fun onExitFullscreen()
    }

    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        callbacks.onProgress(newProgress)
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        callbacks.onTitleReceived(view.url, title)
    }

    override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
        callbacks.onFaviconReceived(view.url, icon)
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }
        customView = view
        customViewCallback = callback
        callbacks.onEnterFullscreen(view)
    }

    override fun onHideCustomView() {
        val callback = customViewCallback
        customView = null
        customViewCallback = null
        callbacks.onExitFullscreen()
        // Telling the page its view is gone is what lets YouTube leave its own fullscreen state.
        callback?.onCustomViewHidden()
    }

    fun currentCustomView(): View? = customView

    /** Called when the panel is torn down while a video is still fullscreen. */
    fun releaseCustomView() {
        if (customView == null) return
        onHideCustomView()
    }

    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, message: android.os.Message?): Boolean {
        // Pop-ups are not supported in a floating panel; the link stays in the current tab.
        return false
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        // Camera/microphone/protected-media: the app declares none of these permissions.
        request.deny()
    }

    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
        callback.invoke(origin, false, false)
    }

    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        // Page console output is not surfaced: it is the site's business, not the user's.
        return true
    }
}
