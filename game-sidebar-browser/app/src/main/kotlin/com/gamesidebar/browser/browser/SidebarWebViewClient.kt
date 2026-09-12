package com.gamesidebar.browser.browser

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.gamesidebar.core.security.UrlSafety

/**
 * Navigation gatekeeper for every tab.
 *
 * Responsibilities: keep non-web schemes out of the WebView, hand sign-in flows that Google refuses
 * to the system browser, and report failures (network, HTTP, SSL) to the UI as recoverable states.
 * SSL errors always cancel the load - there is no "proceed anyway" path in this app.
 */
class SidebarWebViewClient(
    private val context: Context,
    private val callbacks: Callbacks,
) : WebViewClient() {

    interface Callbacks {
        fun onPageStarted(url: String)
        fun onPageFinished(url: String, title: String?)
        fun onLoadError(url: String, messageKey: String)
        fun onSslError(url: String, messageKey: String)
        /** Google refused the embedded sign-in: offer a Custom Tab instead. */
        fun onExternalAuthRequired(url: String)
        /** A non-web scheme (mailto:, tel:, intent:) was handed to the system, or blocked. */
        fun onExternalIntentHandled(description: String)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url?.toString() ?: return false

        // CRITICAL LOGIN FIX: Do NOT block YouTube "Sign in" with "Secure login required" overlay.
        // OAuth hosts (accounts.google.com etc.) must use secure Custom Tab, but WITHOUT showing the
        // blanket "floating browser" warning. Directly open Custom Tab and return to existing sidebar.
        // Normal <form> login stays in WebView; only OAuth uses Custom Tab, never captures passwords.
        if (UrlSafety.requiresExternalAuth(url)) {
            try {
                com.gamesidebar.browser.util.ExternalBrowser.open(context, url)
            } catch (_: Exception) {
                callbacks.onExternalAuthRequired(url)
            }
            return true
        }

        val scheme = com.gamesidebar.core.browser.UrlResolver.schemeOf(url)
        return when (scheme) {
            null, "http", "https" -> false
            "intent" -> handleIntentUrl(url)
            "mailto", "tel", "sms", "market" -> handleViewUri(url)
            else -> {
                callbacks.onLoadError(url, "error_blocked_scheme")
                true
            }
        }
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        callbacks.onPageStarted(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        callbacks.onPageFinished(url, view.title)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        // Fired for in-page navigation too, which onPageFinished misses on SPA sites.
        callbacks.onPageFinished(url, view.title)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        // Only main-frame failures are worth an error screen; sub-resource errors are noise.
        if (request.isForMainFrame) {
            val key = when (error.errorCode) {
                ERROR_HOST_LOOKUP -> "error_no_network"
                ERROR_TIMEOUT -> "error_page_failed"
                ERROR_CONNECT, ERROR_IO -> "error_no_network"
                else -> "error_page_failed"
            }
            callbacks.onLoadError(request.url?.toString().orEmpty(), key)
        }
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
        // Never handler.proceed(): a certificate problem stops the load and is explained.
        handler.cancel()
        view.loadUrl("about:blank")
        callbacks.onSslError(error.url ?: "", UrlSafety.sslErrorKey(error.primaryError))
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: android.webkit.WebResourceResponse,
    ) {
        if (request.isForMainFrame && errorResponse.statusCode in 400..599) {
            callbacks.onLoadError(request.url?.toString().orEmpty(), "error_page_failed")
        }
    }

    private fun handleIntentUrl(url: String): Boolean {
        return try {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.component = null
            intent.setPackage(null)
            val fallback = intent.getStringExtra("browser_fallback_url")
            if (fallback != null) {
                callbacks.onExternalIntentHandled(fallback)
            } else {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                callbacks.onExternalIntentHandled(url)
            }
            true
        } catch (parseFailure: Exception) {
            callbacks.onLoadError(url, "error_blocked_scheme")
            true
        } catch (notFound: ActivityNotFoundException) {
            callbacks.onLoadError(url, "error_blocked_scheme")
            true
        }
    }

    private fun handleViewUri(url: String): Boolean {
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            callbacks.onExternalIntentHandled(url)
            true
        } catch (notFound: ActivityNotFoundException) {
            callbacks.onLoadError(url, "error_blocked_scheme")
            true
        }
    }

    private companion object {
        // WebViewClient error constants are compile-time ints; referenced by name for clarity.
        const val ERROR_HOST_LOOKUP = WebViewClient.ERROR_HOST_LOOKUP
        const val ERROR_TIMEOUT = WebViewClient.ERROR_TIMEOUT
        const val ERROR_CONNECT = WebViewClient.ERROR_CONNECT
        const val ERROR_IO = WebViewClient.ERROR_IO
    }
}
