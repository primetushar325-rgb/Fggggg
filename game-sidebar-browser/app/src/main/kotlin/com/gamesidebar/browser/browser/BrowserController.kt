package com.gamesidebar.browser.browser

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import com.gamesidebar.core.browser.TabSnapshot
import com.gamesidebar.core.browser.Tabs
import com.gamesidebar.core.browser.UrlResolver
import com.gamesidebar.core.download.DownloadRequest
import com.gamesidebar.core.model.AppSettings
import com.gamesidebar.core.security.UrlSafety
import com.gamesidebar.browser.data.BrowserDataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The browser: tab state, one WebView per tab, and every navigation action.
 *
 * Deliberately free of layout code so the exact same engine drives both the floating panel and the
 * in-app browser. It owns its WebViews and destroys them on [destroy] - a leaked WebView keeps a
 * renderer process (and its memory) alive, which is the single worst thing an overlay utility can do
 * on a mid-range phone.
 */
class BrowserController(
    context: Context,
    private val dataRepository: BrowserDataRepository,
    private val settings: () -> AppSettings,
    private val listener: Listener,
) : SidebarWebViewClient.Callbacks, SidebarWebChromeClient.Callbacks {

    interface Listener {
        fun onTabsChanged(tabs: Tabs)
        fun onProgress(progress: Int)
        fun onUrlChanged(url: String, title: String)
        fun onNavigationChanged(canGoBack: Boolean, canGoForward: Boolean, isLoading: Boolean)
        fun onBookmarkChanged(isBookmarked: Boolean)
        fun onLoadError(messageKey: String, url: String)
        fun onLoadErrorCleared()
        fun onExternalAuthRequired(url: String)
        fun onEnterFullscreen(view: View)
        fun onExitFullscreen()
        fun onDownloadRequested(request: DownloadRequest)
    }

    private val appContext: Context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val webViewClient = SidebarWebViewClient(appContext, this)
    private val chromeClient = SidebarWebChromeClient(this)
    private val downloads = DownloadCoordinator(appContext)

    private val webViews = LinkedHashMap<String, WebView>()
    private var container: ViewGroup? = null
    private var activeTabId: String? = null

    var tabs: Tabs = Tabs()
        private set

    val activeWebView: WebView? get() = tabs.activeId?.let { webViews[it] }

    val currentUrl: String get() = activeWebView?.url.orEmpty()
    val currentTitle: String get() = activeWebView?.title.orEmpty()

    fun downloadListener(): DownloadCoordinator = downloads

    fun attachDownloadListener(listener: DownloadCoordinator.Listener) = downloads.attach(listener)

    /** Creates the first tab and loads the search engine home page. */
    fun start(homeUrl: String = settings().searchEngine.homeUrl) {
        if (tabs.isEmpty) {
            val tab = TabSnapshot(id = Tabs.randomId(), url = homeUrl)
            tabs = Tabs(listOf(tab), tab.id)
            ensureWebView(tab.id)
            publishTabs()
            loadUrl(homeUrl)
        }
    }

    /** Binds the WebView container the host layout provides (panel or activity). */
    fun attachContainer(view: ViewGroup) {
        container = view
        showActiveWebView()
    }

    fun detachContainer() {
        val view = container ?: return
        activeWebView?.let { if (it.parent === view) view.removeView(it) }
        container = null
    }

    // ---------------------------------------------------------------- navigation

    /** Address-bar entry point: URL, host or search text, decided by core. */
    fun submit(input: String) {
        when (val resolution = UrlResolver.resolve(input, settings().searchEngine)) {
            is UrlResolver.Resolution.Url -> loadUrl(resolution.url)
            is UrlResolver.Resolution.Search -> loadUrl(
                settings().searchEngine.searchUrl(UrlResolver.encodeQuery(resolution.query)),
            )

            UrlResolver.Resolution.Empty -> listener.onLoadError("error_empty_url", "")
        }
    }

    fun loadUrl(rawUrl: String) {
        val webView = activeWebView ?: return
        val verdict = UrlSafety.verdict(rawUrl, settings().httpsPreferred)
        val target = verdict.urlToLoad
        if (target == null) {
            listener.onLoadError(verdict.reasonKey ?: "error_blocked_scheme", rawUrl)
            return
        }
        listener.onLoadErrorCleared()
        updateTab(tabs.activeId) { it.copy(url = target, isLoading = true) }
        webView.loadUrl(target)
    }

    fun back() {
        activeWebView?.takeIf { it.canGoBack() }?.goBack()
    }

    fun forward() {
        activeWebView?.takeIf { it.canGoForward() }?.goForward()
    }

    fun reload() {
        activeWebView?.reload()
    }

    fun stop() {
        activeWebView?.stopLoading()
        listener.onProgress(100)
    }

    fun home() = loadUrl(settings().searchEngine.homeUrl)

    fun openShortcut(url: String) = loadUrl(url)

    fun canGoBack(): Boolean = activeWebView?.canGoBack() == true
    fun canGoForward(): Boolean = activeWebView?.canGoForward() == true

    // ---------------------------------------------------------------- tabs

    fun newTab(url: String? = null) {
        if (tabs.count >= Tabs.MAX_TABS) return
        val target = url ?: settings().searchEngine.homeUrl
        val tab = TabSnapshot(id = Tabs.randomId(), url = target)
        tabs = tabs.add(tab)
        ensureWebView(tab.id)
        publishTabs()
        showActiveWebView()
        loadUrl(target)
    }

    fun closeTab(id: String) {
        val webView = webViews.remove(id)
        removeWebViewFromContainer(webView)
        destroyWebView(webView)

        val remaining = tabs.close(id)
        tabs = remaining
        if (remaining.isEmpty) {
            activeTabId = null
            publishTabs()
            return
        }
        activeTabId = remaining.activeId
        publishTabs()
        showActiveWebView()
        publishNavigation()
        refreshBookmarkState()
    }

    fun selectTab(id: String) {
        if (tabs.activeId == id || tabs.indexOf(id) < 0) return
        tabs = tabs.select(id)
        activeTabId = id
        publishTabs()
        showActiveWebView()
        publishNavigation()
        refreshBookmarkState()
    }

    fun nextTab() = cycle(1)
    fun previousTab() = cycle(-1)

    private fun cycle(offset: Int) {
        if (tabs.count < 2) return
        val next = tabs.selectRelative(offset)
        selectTab(next.activeId ?: return)
    }

    fun closeAllTabs() {
        webViews.values.forEach { removeWebViewFromContainer(it); destroyWebView(it) }
        webViews.clear()
        tabs = Tabs()
        activeTabId = null
        publishTabs()
    }

    // ---------------------------------------------------------------- page options

    fun toggleDesktopMode() {
        val webView = activeWebView ?: return
        val tab = tabs.active ?: return
        val next = !tab.isDesktopMode
        WebViewFactory.applyDesktopMode(webView, next)
        updateTab(tab.id) { it.copy(isDesktopMode = next) }
        webView.reload()
    }

    fun toggleBookmark() {
        val url = currentUrl.ifBlank { return }
        val title = currentTitle.ifBlank { UrlResolver.hostOf(url) ?: url }
        scope.launch {
            dataRepository.toggleBookmark(url, title)
            refreshBookmarkState()
        }
    }

    fun refreshBookmarkState() {
        val url = currentUrl
        if (url.isBlank()) {
            listener.onBookmarkChanged(false)
            return
        }
        scope.launch { listener.onBookmarkChanged(dataRepository.isBookmarked(url)) }
    }

    /** Sign-in the WebView cannot host: hand the URL to a Custom Tab. */
    fun openExternally(url: String = currentUrl) {
        if (url.isBlank()) return
        com.gamesidebar.browser.util.ExternalBrowser.open(appContext, url)
    }

    fun shareCurrentPage() {
        val url = currentUrl
        if (url.isBlank()) return
        com.gamesidebar.browser.util.ExternalBrowser.share(appContext, url, currentTitle)
    }

    /** Applies a settings change to every live WebView. */
    fun applySettings(settings: AppSettings) {
        webViews.values.forEach { WebViewFactory.configure(it, settings) }
    }

    // ---------------------------------------------------------------- WebViewClient.Callbacks

    override fun onPageStarted(url: String) {
        listener.onLoadErrorCleared()
        updateTab(tabs.activeId) { it.copy(url = url, isLoading = true) }
        publishNavigation()
        listener.onUrlChanged(url, activeWebView?.title.orEmpty())
    }

    override fun onPageFinished(url: String, title: String?) {
        // V4 BUG #5 FIX: Remove blanket restriction — only third-party OAuth hosts trigger CustomTab
        // Normal login forms (<input type=email/password>) must work normally inside WebView
        // DO NOT block just because floating/overlay/small — only when provider explicitly blocks embedded (disallowed_useragent + OAuth hosts)
        if (UrlSafety.requiresExternalAuth(url) || url.lowercase().contains("disallowed_useragent")) {
            // Preserve WebView session — do not destroy/reload — show Secure Login error with CustomTab fallback
            // Only auth flow uses CustomTab; normal browsing stays inside WebView; never store passwords
            if (title?.contains("Secure login", ignoreCase = true) != true) {
                listener.onExternalAuthRequired(url)
                updateTab(tabs.activeId) {
                    it.copy(
                        url = url,
                        title = title ?: it.title,
                        isLoading = false,
                        canGoBack = webViews[activeTabId ?: ""]?.canGoBack() == true,
                        canGoForward = webViews[activeTabId ?: ""]?.canGoForward() == true,
                    )
                }
                publishTabs()
                publishNavigation()
                return
            }
        }
        val webView = activeWebView ?: return
        updateTab(tabs.activeId) {
            it.copy(
                url = url,
                title = title ?: it.title,
                isLoading = false,
                canGoBack = webView.canGoBack(),
                canGoForward = webView.canGoForward(),
            )
        }
        publishTabs()
        publishNavigation()
        listener.onUrlChanged(url, title.orEmpty())
        refreshBookmarkState()

        // History is written off the main thread and only when the privacy policy allows it.
        scope.launch(Dispatchers.Default) { dataRepository.recordVisit(url, title.orEmpty()) }
    }

    override fun onLoadError(url: String, messageKey: String) {
        listener.onLoadError(messageKey, url)
        updateTab(tabs.activeId) { it.copy(isLoading = false) }
        publishNavigation()
    }

    override fun onSslError(url: String, messageKey: String) {
        listener.onLoadError(messageKey, url)
    }

    override fun onExternalAuthRequired(url: String) {
        listener.onExternalAuthRequired(url)
    }

    override fun onExternalIntentHandled(description: String) {
        // Handled by the system; the panel just stays as it was.
    }

    // ---------------------------------------------------------------- WebChromeClient.Callbacks

    override fun onProgress(progress: Int) {
        listener.onProgress(progress)
        if (progress >= 100) {
            updateTab(tabs.activeId) { it.copy(isLoading = false) }
        }
    }

    override fun onTitleReceived(url: String?, title: String?) {
        if (url == null || title.isNullOrBlank()) return
        val tab = tabs.tabs.firstOrNull { it.url == url } ?: tabs.active ?: return
        updateTab(tab.id) { it.copy(title = title) }
        publishTabs()
    }

    override fun onFaviconReceived(url: String?, icon: Bitmap?) {
        // Favicons are not cached to disk: a 16x16 bitmap per tab is not worth the I/O in a
        // low-memory overlay, so the tab strip shows the host's first letter instead.
        if (url == null || icon == null) return
        updateTab(tabs.activeId) { it.copy(faviconKey = UrlResolver.hostOf(url)?.take(1)?.uppercase()) }
        publishTabs()
    }

    override fun onEnterFullscreen(view: View) = listener.onEnterFullscreen(view)

    override fun onExitFullscreen() = listener.onExitFullscreen()

    /** True while a page (typically a YouTube video) owns the fullscreen custom view. */
    val isFullscreen: Boolean get() = chromeClient.currentCustomView() != null

    /**
     * Leaves HTML5 fullscreen from *our* side - the panel's own exit button, or the panel being
     * torn down while a video is still playing.
     *
     * Going through [SidebarWebChromeClient.releaseCustomView] matters: it clears the retained
     * custom view and calls `onCustomViewHidden()`, which is the only thing that tells the page its
     * fullscreen ended. Skipping it leaves the chrome client holding a stale view, so the next
     * `onShowCustomView` is refused and video fullscreen stays broken for the whole session.
     */
    fun exitFullscreen() = chromeClient.releaseCustomView()

    // ---------------------------------------------------------------- internals

    private fun ensureWebView(tabId: String): WebView {
        webViews[tabId]?.let { return it }
        val webView = WebViewFactory.create(appContext, settings())
        webView.webViewClient = webViewClient
        webView.webChromeClient = chromeClient
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            val request = downloads.requestFrom(url, userAgent, contentDisposition, mimeType, contentLength)
            listener.onDownloadRequested(request)
        }
        webViews[tabId] = webView
        return webView
    }

    private fun showActiveWebView() {
        val host = container ?: return
        val id = tabs.activeId ?: return
        val webView = ensureWebView(id)
        activeTabId = id
        if (webView.parent !== host) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            host.removeAllViews()
            host.addView(
                webView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        webView.onResume()
    }

    private fun updateTab(id: String?, transform: (TabSnapshot) -> TabSnapshot) {
        val target = id ?: tabs.activeId ?: return
        tabs = tabs.update(target, transform)
    }

    private fun publishTabs() {
        listener.onTabsChanged(tabs)
    }

    private fun publishNavigation() {
        val webView = activeWebView
        listener.onNavigationChanged(
            canGoBack = webView?.canGoBack() == true,
            canGoForward = webView?.canGoForward() == true,
            isLoading = tabs.active?.isLoading == true,
        )
    }

    private fun removeWebViewFromContainer(webView: WebView?) {
        val view = webView ?: return
        (view.parent as? ViewGroup)?.removeView(view)
    }

    private fun destroyWebView(webView: WebView?) {
        val view = webView ?: return
        runCatching {
            view.onPause()
            view.stopLoading()
            view.loadUrl("about:blank")
            view.webViewClient = SidebarWebViewClient(appContext, NoOpCallbacks)
            view.webChromeClient = null
            view.removeAllViews()
            view.destroy()
        }
    }

    /** Releases every WebView, the download receiver and the coroutine scope. */
    fun destroy() {
        chromeClient.releaseCustomView()
        detachContainer()
        closeAllTabs()
        downloads.detach()
        scope.cancel()
        container = null
    }

    /** Swallows callbacks from a WebView that is one line away from being destroyed. */
    private object NoOpCallbacks : SidebarWebViewClient.Callbacks {
        override fun onPageStarted(url: String) = Unit
        override fun onPageFinished(url: String, title: String?) = Unit
        override fun onLoadError(url: String, messageKey: String) = Unit
        override fun onSslError(url: String, messageKey: String) = Unit
        override fun onExternalAuthRequired(url: String) = Unit
        override fun onExternalIntentHandled(description: String) = Unit
    }
}
