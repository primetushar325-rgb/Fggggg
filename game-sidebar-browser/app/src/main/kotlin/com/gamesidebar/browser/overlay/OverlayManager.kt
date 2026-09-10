package com.gamesidebar.browser.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.gamesidebar.browser.browser.BrowserController
import com.gamesidebar.browser.data.ServiceLocator
import com.gamesidebar.browser.util.ScreenMetrics
import com.gamesidebar.core.download.DownloadRequest
import com.gamesidebar.core.geometry.Edge
import com.gamesidebar.core.geometry.OverlayGeometry
import com.gamesidebar.core.geometry.Point
import com.gamesidebar.core.geometry.PxSize
import com.gamesidebar.core.geometry.Screen
import com.gamesidebar.core.model.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Owns the two overlay windows - the handle and the panel - and every placement decision about them.
 *
 * Window flags, and why:
 *  - the handle is `FLAG_NOT_FOCUSABLE`, so a floating pill never steals input focus from a game;
 *  - the panel is focusable (it has a text field) but `FLAG_NOT_TOUCH_MODAL`, so touches outside it
 *    still reach the game, and `FLAG_WATCH_OUTSIDE_TOUCH` gives us the tap that closes it;
 *  - both are `TYPE_APPLICATION_OVERLAY`, which is exactly what SYSTEM_ALERT_WINDOW grants. There is
 *    no attempt to sit above the system UI or to hide the notification.
 *
 * All geometry comes from `:core` ([OverlayGeometry]), which is unit-tested; this class only talks to
 * WindowManager.
 */
class OverlayManager(
    private val context: Context,
    private val service: OverlayService,
) : BrowserController.Listener, SidebarPanelView.Host, FloatingHandleView.Listener {

    private val windowManager: WindowManager =
        context.getSystemService(WindowManager::class.java)
            ?: throw IllegalStateException("WindowManager unavailable")
    private val locator = ServiceLocator.get(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var handleView: FloatingHandleView? = null
    private var panelView: SidebarPanelView? = null
    private var handleParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private var settings: AppSettings = AppSettings.DEFAULT
    private var screen: Screen = Screen(1080, 1920)
    private var density: Float = 3f

    private var handleOrigin = Point(0, 0)
    private var panelOrigin = Point(0, 0)
    private var panelSize = PxSize(0, 0)
    private var handleSizePx = PxSize(0, 0)

    private var panelOpen = false
    private var handleVisible = false
    private var repositionMode = false
    private var started = false

    private val controller: BrowserController by lazy {
        BrowserController(
            context = context,
            dataRepository = locator.browserDataRepository,
            settings = { settings },
            listener = this,
        )
    }

    val isPanelOpen: Boolean get() = panelOpen
    val isHandleVisible: Boolean get() = handleVisible

    // ---------------------------------------------------------------- lifecycle

    fun start() {
        if (started) return
        started = true
        refreshScreen()
        applyInitialSettings()

        scope.launch {
            locator.settingsRepository.settings.collectLatest { latest ->
                val previous = settings
                settings = latest
                onSettingsChanged(previous, latest)
            }
        }
        scope.launch {
            locator.settingsRepository.shortcuts.collectLatest { list ->
                panelView?.applyShortcuts(list)
            }
        }
        scope.launch {
            controller.start(settings.searchEngine.homeUrl)
            panelView?.applyShortcuts(locator.settingsRepository.currentShortcuts())
        }
        showHandle()
    }

    fun stop() {
        hidePanel(immediate = true)
        removeHandle()
        controller.destroy()
        scope.cancel()
        started = false
    }

    /** Screen size, insets and density can change on rotation or when a display folds. */
    fun refreshScreen() {
        density = ScreenMetrics.density(context)
        screen = ScreenMetrics.screen(context)
    }

    // ---------------------------------------------------------------- handle

    fun showHandle() {
        if (handleVisible) return
        refreshScreen()
        applyInitialSettings()

        val view = FloatingHandleView(context).apply { listener = this@OverlayManager }
        val touchPadding = OverlayGeometry.dp(HANDLE_TOUCH_PADDING_DP, density)
        val params = WindowManager.LayoutParams(
            handleSizePx.widthPx + touchPadding * 2,
            handleSizePx.heightPx + touchPadding * 2,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = handleOrigin.x - touchPadding
            y = handleOrigin.y - touchPadding
        }

        runCatching { windowManager.addView(view, params) }.onSuccess {
            handleView = view
            handleParams = params
            handleVisible = true
            applyHandleAppearance()
        }
    }

    fun removeHandle() {
        val view = handleView ?: return
        runCatching { windowManager.removeViewImmediate(view) }
        handleView = null
        handleParams = null
        handleVisible = false
    }

    // ---------------------------------------------------------------- panel

    fun showPanel() {
        if (panelOpen) return
        refreshScreen()

        val view = panelView ?: SidebarPanelView(
            context = context,
            controller = controller,
            dataRepository = locator.browserDataRepository,
            settingsRepository = locator.settingsRepository,
            uiScope = scope,
            host = this,
        ).also { created ->
            panelView = created
            created.applySettings(settings)
            scope.launch { created.applyShortcuts(locator.settingsRepository.currentShortcuts()) }
        }

        val params = WindowManager.LayoutParams(
            panelSize.widthPx,
            panelSize.heightPx,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = panelOrigin.x
            y = panelOrigin.y
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        runCatching { windowManager.addView(view, params) }.onSuccess {
            panelParams = params
            panelOpen = true
            animatePanelIn(view)
            view.onPanelShown()
            service.onPanelStateChanged(true)
        }.onFailure {
            service.onOverlayFailed()
        }
    }

    fun hidePanel(immediate: Boolean = false) {
        val view = panelView ?: return
        if (!panelOpen) return
        panelOpen = false
        view.onPanelHidden()
        service.onPanelStateChanged(false)

        val finish = {
            view.teardown()
            runCatching { windowManager.removeViewImmediate(view) }
            // The panel is torn down on minimise, which is what releases WebView memory while the
            // game is being played. Tabs and their state live in the controller, not the view.
            panelView = null
            panelParams = null
        }

        if (immediate || settings.gamingMode || settings.reducedAnimations) {
            finish()
        } else {
            view.animate()
                .alpha(0f)
                .scaleX(0.35f)
                .scaleY(0.35f)
                .setDuration(CLOSE_ANIMATION_MS)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { finish() }
                .start()
        }
    }

    fun togglePanel() {
        if (panelOpen) hidePanel() else showPanel()
    }

    /** Opens the panel straight onto a URL - used by the notification and quick shortcuts. */
    fun openUrl(url: String) {
        showPanel()
        controller.submit(url)
    }

    /** Gaming Mode without opening the app: fewer animations, no glow, no resize grip. */
    fun toggleGamingMode() {
        scope.launch { locator.settingsRepository.update { it.copy(gamingMode = !it.gamingMode) } }
    }

    // ---------------------------------------------------------------- settings

    private fun applyInitialSettings() {
        scope.launch {
            settings = locator.settingsRepository.currentSettings()
            handleSizePx = OverlayGeometry.handleSize(settings.handleSize, density)
            panelSize = OverlayGeometry.panelSize(
                settings.panelSize,
                settings.customPanelWidthFraction,
                settings.customPanelHeightFraction,
                screen,
                density,
            )
            restoreHandlePosition()
            applyPanelAnchor()
        }
    }

    private fun onSettingsChanged(previous: AppSettings, latest: AppSettings) {
        handleSizePx = OverlayGeometry.handleSize(latest.handleSize, density)
        panelSize = OverlayGeometry.panelSize(
            latest.panelSize,
            latest.customPanelWidthFraction,
            latest.customPanelHeightFraction,
            screen,
            density,
        )
        applyHandleAppearance()
        panelView?.applySettings(latest)

        if (!latest.rememberPosition) {
            placeHandleAtAnchor()
        } else if (previous.handleSize != latest.handleSize ||
            previous.handleVerticalAnchor != latest.handleVerticalAnchor ||
            previous.handleHorizontalAnchor != latest.handleHorizontalAnchor
        ) {
            placeHandleAtAnchor()
        }

        if (panelOpen) {
            applyPanelAnchor()
        }
    }

    private fun restoreHandlePosition() {
        scope.launch {
            val saved = locator.settingsRepository.handlePosition.first()
            if (settings.rememberPosition && saved != null) {
                val x = screen.usableLeft + (screen.usableWidth * saved.first).toInt()
                val y = screen.usableTop + (screen.usableHeight * saved.second).toInt()
                handleOrigin = OverlayGeometry.clampHandle(Point(x, y), handleSizePx, screen)
            } else {
                handleOrigin = OverlayGeometry.defaultHandlePoint(handleSizePx, screen)
            }
            updateHandleLayout()
        }
    }

    private fun placeHandleAtAnchor() {
        val anchor = OverlayGeometry.anchorPoint(
            settings.handleVerticalAnchor,
            settings.handleHorizontalAnchor,
            handleSizePx,
            screen,
        )
        handleOrigin = OverlayGeometry.clampHandle(anchor, handleSizePx, screen)
        updateHandleLayout()
    }

    private fun applyPanelAnchor() {
        val placement = OverlayGeometry.placement(
            settings.panelVerticalAnchor,
            settings.panelHorizontalAnchor,
            panelSize,
            screen,
        )
        panelOrigin = placement.origin
        panelSize = placement.size
        val params = panelParams ?: return
        params.width = panelSize.widthPx
        params.height = panelSize.heightPx
        params.x = panelOrigin.x
        params.y = panelOrigin.y
        val panel = panelView ?: return
        runCatching { windowManager.updateViewLayout(panel, params) }
    }

    private fun applyHandleAppearance() {
        val view = handleView ?: return
        view.appearance = FloatingHandleView.Appearance(
            glowEnabled = settings.glowEnabled && !settings.gamingMode,
            glowColor = settings.glowColor.argb,
            glowIntensity = settings.glowIntensity,
            alpha = 0.95f,
            repositionMode = repositionMode,
        )
        updateHandleLayout()
    }

    private fun updateHandleLayout() {
        val view = handleView ?: return
        val params = handleParams ?: return
        val touchPadding = OverlayGeometry.dp(HANDLE_TOUCH_PADDING_DP, density)
        params.width = handleSizePx.widthPx + touchPadding * 2
        params.height = handleSizePx.heightPx + touchPadding * 2
        params.x = handleOrigin.x - touchPadding
        params.y = handleOrigin.y - touchPadding
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun animatePanelIn(view: View) {
        if (settings.gamingMode || settings.reducedAnimations) {
            view.alpha = 1f
            view.scaleX = 1f
            view.scaleY = 1f
            view.translationX = 0f
            view.translationY = 0f
            return
        }
        // Grow out of the handle so the panel visibly comes from where the user tapped.
        val handleCentreX = handleOrigin.x + handleSizePx.widthPx / 2f
        val handleCentreY = handleOrigin.y + handleSizePx.heightPx / 2f
        val startDx = handleCentreX - (panelOrigin.x + panelSize.widthPx / 2f)
        val startDy = handleCentreY - (panelOrigin.y + panelSize.heightPx / 2f)

        view.alpha = 0f
        view.scaleX = 0.25f
        view.scaleY = 0.25f
        view.translationX = startDx
        view.translationY = startDy
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationX(0f)
            .translationY(0f)
            .setDuration(OPEN_ANIMATION_MS)
            .setInterpolator(OvershootInterpolator(0.9f))
            .start()
    }

    /** minSdk is 26, so TYPE_APPLICATION_OVERLAY is always available. */
    private fun overlayType(): Int = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    // ---------------------------------------------------------------- FloatingHandleView.Listener

    override fun onTap() = showPanel()

    override fun onDoubleTap() = togglePanel()

    override fun onLongPress() {
        repositionMode = !repositionMode
        applyHandleAppearance()
    }

    override fun onDrag(dx: Float, dy: Float) {
        val params = handleParams ?: return
        params.x += dx.toInt()
        params.y += dy.toInt()
        handleOrigin = Point(params.x + OverlayGeometry.dp(HANDLE_TOUCH_PADDING_DP, density), params.y + OverlayGeometry.dp(HANDLE_TOUCH_PADDING_DP, density))
        handleView?.let { view -> runCatching { windowManager.updateViewLayout(view, params) } }
    }

    override fun onDragEnd() {
        val snapped = OverlayGeometry.snap(
            origin = handleOrigin,
            handle = handleSizePx,
            screen = screen,
            autoSnap = settings.autoSnap,
            edgeHideMode = settings.edgeHideMode,
            density = density,
        )
        handleOrigin = snapped.point
        updateHandleLayout()
        handleView?.let { view ->
            if (snapped.edge != Edge.NONE && settings.edgeHideMode) {
                view.setEdgeReveal(snapped.visibleWidthPx.toFloat() / handleSizePx.widthPx)
            } else {
                view.restoreAlpha()
            }
        }
        if (settings.rememberPosition) {
            val xFraction = (handleOrigin.x - screen.usableLeft).toFloat() / screen.usableWidth
            val yFraction = (handleOrigin.y - screen.usableTop).toFloat() / screen.usableHeight
            scope.launch { locator.settingsRepository.saveHandlePosition(xFraction, yFraction) }
        }
    }

    override fun onFlingDown() = showPanel()

    override fun onFlingUp() = hidePanel()

    // ---------------------------------------------------------------- SidebarPanelView.Host

    override fun onMinimize() = hidePanel()

    override fun onClose() = hidePanel()

    override fun onPanelDrag(dx: Float, dy: Float) {
        val params = panelParams ?: return
        params.x += dx.toInt()
        params.y += dy.toInt()
        panelView?.let { view -> runCatching { windowManager.updateViewLayout(view, params) } }
    }

    override fun onPanelDragEnd() {
        val params = panelParams ?: return
        val clamped = OverlayGeometry.clampPanel(Point(params.x, params.y), panelSize, screen)
        panelOrigin = clamped
        params.x = clamped.x
        params.y = clamped.y
        panelView?.let { view -> runCatching { windowManager.updateViewLayout(view, params) } }
    }

    override fun onPanelResize(widthPx: Int, heightPx: Int) {
        val params = panelParams ?: return
        val floor = PxSize(
            OverlayGeometry.dp(OverlayGeometry.MIN_PANEL_WIDTH_DP.toFloat(), density).coerceAtMost(screen.usableWidth),
            OverlayGeometry.dp(OverlayGeometry.MIN_PANEL_HEIGHT_DP.toFloat(), density).coerceAtMost(screen.usableHeight),
        )
        val maxSize = PxSize(
            (screen.usableWidth - OverlayGeometry.dp(OverlayGeometry.PANEL_MARGIN_DP.toFloat(), density) * 2)
                .coerceAtLeast(floor.widthPx),
            (screen.usableHeight - OverlayGeometry.dp(OverlayGeometry.PANEL_MARGIN_DP.toFloat(), density) * 2)
                .coerceAtLeast(floor.heightPx),
        )
        panelSize = PxSize(
            widthPx.coerceIn(floor.widthPx, maxSize.widthPx),
            heightPx.coerceIn(floor.heightPx, maxSize.heightPx),
        )
        params.width = panelSize.widthPx
        params.height = panelSize.heightPx
        panelView?.let { view -> runCatching { windowManager.updateViewLayout(view, params) } }
    }

    override fun onOpenExternalAuth(url: String) {
        com.gamesidebar.browser.util.ExternalBrowser.open(context, url)
    }

    override fun onApplyWindowBrightness(brightness: Float) {
        val params = panelParams ?: return
        params.screenBrightness = brightness.coerceIn(0.01f, 1f)
        panelView?.let { view -> runCatching { windowManager.updateViewLayout(view, params) } }
    }

    // ---------------------------------------------------------------- BrowserController.Listener

    override fun onTabsChanged(tabs: com.gamesidebar.core.browser.Tabs) {
        panelView?.onTabsChanged(tabs)
    }

    override fun onProgress(progress: Int) {
        panelView?.onProgress(progress)
    }

    override fun onUrlChanged(url: String, title: String) {
        panelView?.onUrlChanged(url, title)
    }

    override fun onNavigationChanged(canGoBack: Boolean, canGoForward: Boolean, isLoading: Boolean) {
        panelView?.onNavigationChanged(canGoBack, canGoForward, isLoading)
    }

    override fun onBookmarkChanged(isBookmarked: Boolean) {
        panelView?.onBookmarkChanged(isBookmarked)
    }

    override fun onLoadError(messageKey: String, url: String) {
        panelView?.showErrorWithUrl(titleFor(messageKey), bodyFor(messageKey), url)
    }

    override fun onLoadErrorCleared() {
        panelView?.hideError()
    }

    override fun onExternalAuthRequired(url: String) {
        panelView?.showError(
            com.gamesidebar.browser.R.string.error_login_blocked,
            com.gamesidebar.browser.R.string.error_login_body,
            com.gamesidebar.browser.R.string.error_action_open_login,
        ) { onOpenExternalAuth(url) }
    }

    override fun onEnterFullscreen(view: View) {
        // HTML5 fullscreen: the panel window grows to the whole usable screen, the video fills it,
        // and the service (plus its notification) keeps running untouched.
        panelView?.enterFullscreen(view)
        val params = panelParams ?: return
        fullscreenParams = PxSize(params.width, params.height) to Point(params.x, params.y)
        params.width = screen.usableWidth
        params.height = screen.usableHeight
        params.x = screen.usableLeft
        params.y = screen.usableTop
        panelView?.let { view -> runCatching { windowManager.updateViewLayout(view, params) } }
    }

    override fun onExitFullscreen() {
        panelView?.exitFullscreen()
        val params = panelParams ?: return
        val restore = fullscreenParams
        fullscreenParams = null
        params.width = restore?.first?.widthPx ?: panelSize.widthPx
        params.height = restore?.first?.heightPx ?: panelSize.heightPx
        params.x = restore?.second?.x ?: panelOrigin.x
        params.y = restore?.second?.y ?: panelOrigin.y
        panelView?.let { view -> runCatching { windowManager.updateViewLayout(view, params) } }
    }

    override fun onDownloadRequested(request: DownloadRequest) {
        panelView?.onDownloadRequested(request)
    }

    private var fullscreenParams: Pair<PxSize, Point>? = null

    private fun titleFor(messageKey: String): Int = when (messageKey) {
        "error_no_network" -> com.gamesidebar.browser.R.string.error_no_network
        "error_blocked_scheme" -> com.gamesidebar.browser.R.string.error_blocked_scheme
        "error_blocked_file" -> com.gamesidebar.browser.R.string.error_blocked_file
        "error_blocked_script" -> com.gamesidebar.browser.R.string.error_blocked_script
        "error_empty_url" -> com.gamesidebar.browser.R.string.error_empty_url
        "error_ssl_not_yet_valid" -> com.gamesidebar.browser.R.string.error_ssl_not_yet_valid
        "error_ssl_expired" -> com.gamesidebar.browser.R.string.error_ssl_expired
        "error_ssl_id_mismatch" -> com.gamesidebar.browser.R.string.error_ssl_id_mismatch
        "error_ssl_untrusted" -> com.gamesidebar.browser.R.string.error_ssl_untrusted
        "error_ssl_generic" -> com.gamesidebar.browser.R.string.error_ssl_generic
        "error_webview_missing" -> com.gamesidebar.browser.R.string.error_webview_missing
        else -> com.gamesidebar.browser.R.string.error_page_failed
    }

    private fun bodyFor(messageKey: String): Int = when (messageKey) {
        "error_no_network" -> com.gamesidebar.browser.R.string.error_no_network_body
        "error_ssl_not_yet_valid", "error_ssl_expired", "error_ssl_id_mismatch",
        "error_ssl_untrusted", "error_ssl_generic",
        -> com.gamesidebar.browser.R.string.error_ssl_body

        "error_webview_missing" -> com.gamesidebar.browser.R.string.error_webview_body
        else -> com.gamesidebar.browser.R.string.error_no_network_body
    }

    /** Called by the service on configuration change (rotation, split screen, fold). */
    fun onScreenChanged() {
        refreshScreen()
        applyInitialSettings()
        if (panelOpen) applyPanelAnchor()
    }

    private companion object {
        const val OPEN_ANIMATION_MS = 260L
        const val CLOSE_ANIMATION_MS = 160L
        const val HANDLE_TOUCH_PADDING_DP = 14f
    }
}

