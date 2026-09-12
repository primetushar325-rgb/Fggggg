@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

package android.webkit

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.view.View

class WebView(context: Context) : android.view.ViewGroup(context) {
    val settings: WebSettings = WebSettings()
    var title: String? = null
    var url: String? = null
    val originalUrl: String? = null
    val progress: Int = 0

    var webViewClient: WebViewClient? = null
    var webChromeClient: WebChromeClient? = null
    fun setDownloadListener(listener: DownloadListener) {}
    fun loadUrl(url: String) {}
    fun loadUrl(url: String, additionalHttpHeaders: Map<String, String>) {}
    fun loadDataWithBaseURL(
        baseUrl: String?,
        data: String,
        mimeType: String?,
        encoding: String?,
        historyUrl: String?,
    ) {}

    fun goBack() {}
    fun goForward() {}
    fun canGoBack(): Boolean = false
    fun canGoForward(): Boolean = false
    fun reload() {}
    fun stopLoading() {}
    fun evaluateJavascript(script: String, callback: ValueCallback<String>?) {}
    fun addJavascriptInterface(obj: Any, name: String) {}
    fun clearHistory() {}
    fun clearSslPreferences() {}
    fun clearCache(includeDiskFiles: Boolean) {}
    fun clearFormData() {}
    fun destroy() {}
    fun onPause() {}
    fun onResume() {}
    fun getFavicon(): Bitmap? = null
    fun copyBackForwardList(): WebBackForwardList = WebBackForwardList()

    fun interface ValueCallback<T> {
        fun onReceiveValue(value: T)
    }

    companion object {
        @JvmStatic
        fun setWebContentsDebuggingEnabled(enabled: Boolean) {}
    }
}

class WebSettings {
    companion object {
        const val MIXED_CONTENT_ALWAYS_ALLOW = 0
        const val MIXED_CONTENT_NEVER_ALLOW = 1
        const val MIXED_CONTENT_COMPATIBILITY_MODE = 2
        const val LOAD_DEFAULT = -1
        const val LOAD_CACHE_ELSE_NETWORK = 1
    }

    var javaScriptEnabled: Boolean = false
    var domStorageEnabled: Boolean = false
    var databaseEnabled: Boolean = false
    var allowFileAccess: Boolean = false
    var allowContentAccess: Boolean = false
    var allowFileAccessFromFileURLs: Boolean = false
    var allowUniversalAccessFromFileURLs: Boolean = false
    var javaScriptCanOpenWindowsAutomatically: Boolean = false
    var builtInZoomControls: Boolean = true
    var displayZoomControls: Boolean = false
    var loadsImagesAutomatically: Boolean = true
    var mediaPlaybackRequiresUserGesture: Boolean = true
    var userAgentString: String = ""

    var loadWithOverviewMode: Boolean = false
    var useWideViewPort: Boolean = false
    var cacheMode: Int = 0
    var mixedContentMode: Int = 0
    var safeBrowsingEnabled: Boolean = false

    fun setSupportMultipleWindows(support: Boolean) {}
    /** The real getter is named `supportZoom()`, not `getSupportZoom()` - so Kotlin sees a
     *  function pair and `supportZoom = true` must not compile. */
    fun supportZoom(): Boolean = true
    fun setSupportZoom(zoom: Boolean) {}
}

open class WebViewClient {
    open fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
    open fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {}
    open fun onPageFinished(view: WebView, url: String) {}
    open fun onLoadResource(view: WebView, url: String) {}
    open fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {}
    open fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {}
    open fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) {}
    open fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {}

    companion object {
        const val ERROR_UNKNOWN = -1
        const val ERROR_HOST_LOOKUP = -2
        const val ERROR_UNSUPPORTED_AUTH_SCHEME = -3
        const val ERROR_AUTHENTICATION = -4
        const val ERROR_CONNECT = -6
        const val ERROR_IO = -7
        const val ERROR_TIMEOUT = -8
        const val ERROR_TOO_MANY_REQUESTS = -9
        const val ERROR_FILE = -10
        const val ERROR_FAILED_SSL_HANDSHAKE = -11
        const val ERROR_BAD_URL = -12
    }
}

open class WebChromeClient {
    open fun onProgressChanged(view: WebView, newProgress: Int) {}
    open fun onReceivedTitle(view: WebView, title: String?) {}
    open fun onReceivedIcon(view: WebView, icon: Bitmap?) {}
    open fun onShowCustomView(view: View, callback: CustomViewCallback) {}
    open fun onHideCustomView() {}
    open fun onPermissionRequest(request: PermissionRequest) {}
    open fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {}
    open fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean = false
    open fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: android.os.Message?,
    ): Boolean = false

    interface CustomViewCallback {
        fun onCustomViewHidden()
    }
}

class WebResourceRequest {
    val url: Uri = Uri.parse("")
    val method: String = "GET"
    val isForMainFrame: Boolean = true
    val requestHeaders: Map<String, String> = emptyMap()
}

class WebResourceError {
    val errorCode: Int = 0
    val description: CharSequence = ""

    companion object {
        const val ERROR_HOST_LOOKUP = -2
        const val ERROR_UNKNOWN = -1
    }
}

class WebResourceResponse(
    val mimeType: String?,
    val encoding: String?,
    val data: java.io.InputStream?,
) {
    constructor(mimeType: String?, encoding: String?, statusCode: Int, reasonPhrase: String, headers: Map<String, String>?, data: java.io.InputStream?) :
        this(mimeType, encoding, data)

    val statusCode: Int = 0
    val reasonPhrase: String = ""
    val responseHeaders: Map<String, String> = emptyMap()
}

class SslErrorHandler {
    fun proceed() {}
    fun cancel() {}
}

class PermissionRequest {
    fun grant(resources: Array<String>) {}
    fun deny() {}

    companion object {
        const val RESOURCE_VIDEO_CAPTURE = "android.webkit.resource.VIDEO_CAPTURE"
        const val RESOURCE_AUDIO_CAPTURE = "android.webkit.resource.AUDIO_CAPTURE"
    }
}

class GeolocationPermissions {
    interface Callback {
        fun invoke(origin: String, allow: Boolean, retain: Boolean)
    }
}

fun interface DownloadListener {
    fun onDownloadStart(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long,
    )
}

class WebBackForwardList {
    val currentIndex: Int = 0
    val size: Int = 0
    fun getItemAtIndex(index: Int): WebHistoryItem = WebHistoryItem()
}

class WebHistoryItem {
    val url: String? = null
    val title: String? = null
}

class CookieManager {
    fun setAcceptCookie(accept: Boolean) {}
    fun setAcceptThirdPartyCookies(webView: WebView, accept: Boolean) {}
    fun removeAllCookies(callback: WebView.ValueCallback<Boolean>?) {}
    fun getCookie(url: String): String? = null
    fun removeSessionCookies(callback: WebView.ValueCallback<Boolean>?) {}
    fun flush() {}

    companion object {
        @JvmStatic
        fun getInstance(): CookieManager = CookieManager()
    }
}

class WebStorage {
    fun deleteAllData() {}

    companion object {
        @JvmStatic
        fun getInstance(): WebStorage = WebStorage()
    }

}

class ConsoleMessage {
    val messageLevel: MessageLevel = MessageLevel.TIP

    enum class MessageLevel { TIP, LOG, ERROR, DEBUG, WARNING }
}

object URLUtil {
    @JvmStatic
    fun guessFileName(url: String, contentDisposition: String?, mimeType: String?): String = "download.bin"

    @JvmStatic
    fun isNetworkUrl(url: String): Boolean = true
}

object WebViewFactoryProbe {
    @JvmStatic
    fun probe(context: Context): Boolean = true
}
