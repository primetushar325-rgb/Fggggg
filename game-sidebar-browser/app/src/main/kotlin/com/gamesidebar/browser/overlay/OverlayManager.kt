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
import com.gamesidebar.core.geometry.ChromeLayout
import com.gamesidebar.core.geometry.Edge
import com.gamesidebar.core.geometry.OverlayGeometry
import com.gamesidebar.core.geometry.PanelPlacement
import com.gamesidebar.core.geometry.PanelResize
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

    /**
     * The three states the overlay can be in, and the only transitions between them:
     *
     *   COLLAPSED (!panelOpen)  -> NORMAL (panelOpen)                open / tap the handle
     *   NORMAL                  -> VIDEO_FULLSCREEN (videoFullscreen) page asks for fullscreen
     *   VIDEO_FULLSCREEN        -> NORMAL                             page or user leaves fullscreen
     *   NORMAL                  -> COLLAPSED                          minimise / close
     *
     * Keeping fullscreen as an explicit flag rather than as "a big panelSize" is what stops the two
     * from being mixed: a rotation or a settings write while a video plays must re-apply the
     * fullscreen bounds, not re-anchor a panel that is temporarily the whole screen.
     */
    private var videoFullscreen = false

    /** Panel rectangle to put back when fullscreen ends. */
    private var fullscreenRestore: Pair<PxSize, Point>? = null

    /** Panel origin at the start of a resize gesture; resize deltas are absolute against it. */
    private var resizeAnchorOrigin = Point(0, 0)

    /** True once the handle has been given a starting point, so seeding never overwrites a saved one. */
    private var geometrySeeded = false

    /** True while the open panel covers the handle and its window is therefore touch-transparent. */
    private var handleCovered = false

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
        seedGeometry()
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
        // hidePanel already persisted the rectangle; this covers the case where the service is stopped
        // while the panel was open through a path that did not go through it.
        if (panelOpen) persistPanelFrame()
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

    /**
     * Gives both overlay windows a usable rectangle before any asynchronous read has answered.
     *
     * The service can be started and asked to open the panel in the same call, and the settings live in
     * DataStore - so without this the window would be added at 0x0 and refined a moment later, which is
     * indistinguishable from "the sidebar did not appear". [applyInitialSettings] then replaces these
     * values with the persisted ones as soon as they are read.
     */
    private fun seedGeometry() {
        handleSizePx = OverlayGeometry.handleSize(settings.handleSize, density)
        if (!geometrySeeded) {
            handleOrigin = OverlayGeometry.defaultHandlePoint(handleSizePx, screen)
            geometrySeeded = true
        }
        val placement = OverlayGeometry.placement(
            settings.panelVerticalAnchor,
            settings.panelHorizontalAnchor,
            OverlayGeometry.panelSize(
                settings.panelSize,
                settings.customPanelWidthFraction,
                settings.customPanelHeightFraction,
                screen,
                density,
            ),
            screen,
        )
        panelSize = placement.size
        panelOrigin = placement.origin
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
            updateHandleCoverage()
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
            applyChromeToPanel()
            updateHandleCoverage()
            service.onPanelStateChanged(true)
        }.onFailure {
            service.onOverlayFailed()
        }
    }

    /**
     * Tells the panel which chrome rows fit the height it currently has.
     *
     * Called whenever the rectangle changes - open, resize end, rotation, leaving fullscreen - and not
     * during a drag, so a resize reports a new height on every move event without the toolbar
     * flickering. `SidebarPanelView.applyChrome` ignores a repeat of the same layout.
     */
    private fun applyChromeToPanel() {
        if (videoFullscreen) return
        val layout: ChromeLayout = OverlayGeometry.chromeLayout(panelSize.heightPx, density)
        panelView?.applyChrome(layout)
    }

    /**
     * Writes the live panel rectangle as fractions of the usable screen.
     *
     * Called at the end of a gesture and on collapse only - never during a move - so dragging or
     * resizing costs one small DataStore write each instead of one per frame.
     */
    private fun persistPanelFrame() {
        if (!settings.rememberPosition) return
        if (panelSize.widthPx <= 0 || panelSize.heightPx <= 0) return
        val frame = OverlayGeometry.frameOf(panelOrigin, panelSize, screen)
        scope.launch { locator.settingsRepository.savePanelFrame(frame) }
    }

    fun hidePanel(immediate: Boolean = false) {
        val view = panelView ?: return
        if (!panelOpen) return
        panelOpen = false
        // Collapse is one of the "sensible points" to persist: reopening has to bring back exactly this
        // rectangle, and the service may not survive until the next write.
        persistPanelFrame()
        view.onPanelHidden()
        service.onPanelStateChanged(false)

        val finish = {
            view.teardown()
            runCatching { windowManager.removeViewImmediate(view) }
            // Only the panel *view* goes away. The WebView, its tabs, the current URL and any playing
            // video stay alive inside the controller, which is what makes reopening instant instead of
            // a reload - collapsing the sidebar must not cost the user their browser session.
            panelView = null
            panelParams = null
            updateHandleCoverage()
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
            restoreHandlePosition()
            resolvePanelGeometry()
            if (panelOpen) applyPanelWindow()
        }
    }

    /**
     * Decides the panel rectangle when nothing more specific is known.
     *
     * The remembered rectangle wins, because it is the size and position the user actually left the
     * sidebar at - reopening must not reset it to a preset. Only when there is none (first run,
     * "Remember position" off, or a size preset just chosen) does the panel fall back to its preset
     * size and anchor. Everything is resolved against the *current* screen, so a rectangle saved in
     * portrait comes back as the same relative rectangle in landscape.
     */
    private suspend fun resolvePanelGeometry() {
        val saved = if (settings.rememberPosition) locator.settingsRepository.currentPanelFrame() else null
        val placement = if (saved != null) {
            OverlayGeometry.placementOf(saved, screen, density)
        } else {
            OverlayGeometry.placement(
                settings.panelVerticalAnchor,
                settings.panelHorizontalAnchor,
                OverlayGeometry.panelSize(
                    settings.panelSize,
                    settings.customPanelWidthFraction,
                    settings.customPanelHeightFraction,
                    screen,
                    density,
                ),
                screen,
            )
        }
        panelSize = placement.size
        panelOrigin = placement.origin
    }

    private fun onSettingsChanged(previous: AppSettings, latest: AppSettings) {
        // Every write to the settings store re-emits the whole AppSettings - including the writes this
        // class makes itself when it persists a panel or handle rectangle. Only the fields that really
        // drive geometry may move anything, or a resize would snap the panel back to its preset anchor
        // a moment after the user let go of it.
        val handleGeometryChanged = previous.handleSize != latest.handleSize ||
            previous.handleVerticalAnchor != latest.handleVerticalAnchor ||
            previous.handleHorizontalAnchor != latest.handleHorizontalAnchor
        val rememberChanged = previous.rememberPosition != latest.rememberPosition
        val panelPresetChanged = previous.panelSize != latest.panelSize ||
            previous.customPanelWidthFraction != latest.customPanelWidthFraction ||
            previous.customPanelHeightFraction != latest.customPanelHeightFraction
        val panelAnchorChanged = previous.panelVerticalAnchor != latest.panelVerticalAnchor ||
            previous.panelHorizontalAnchor != latest.panelHorizontalAnchor

        handleSizePx = OverlayGeometry.handleSize(latest.handleSize, density)
        applyHandleAppearance()
        panelView?.applySettings(latest)

        if (!latest.rememberPosition || handleGeometryChanged || rememberChanged) {
            placeHandleAtAnchor()
        }

        // A video owns the whole window right now; its rectangle is restored on the way out, so
        // applying panel geometry here would shrink the video mid-playback.
        if (videoFullscreen) return

        if (panelPresetChanged || panelAnchorChanged) {
            // Explicit user intent about the panel rectangle: it beats the remembered one.
            panelSize = OverlayGeometry.panelSize(
                latest.panelSize,
                latest.customPanelWidthFraction,
                latest.customPanelHeightFraction,
                screen,
                density,
            )
            applyPanelAnchor()
            scope.launch { locator.settingsRepository.clearPanelFrame() }
        } else {
            // Nothing about the panel changed: keep the rectangle on screen, just make sure it still
            // fits (the screen may have changed since it was chosen).
            clampPanelIntoScreen()
            if (panelOpen) applyPanelWindow()
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

    /** Puts the panel on its configured anchor - an explicit user choice, never a rotation. */
    private fun applyPanelAnchor() {
        val placement = OverlayGeometry.placement(
            settings.panelVerticalAnchor,
            settings.panelHorizontalAnchor,
            panelSize,
            screen,
        )
        panelOrigin = placement.origin
        panelSize = placement.size
        applyPanelWindow()
    }

    /** Pushes the current rectangle to the window and re-fits the chrome to the new height. */
    private fun applyPanelWindow() {
        val params = panelParams ?: return
        val panel = panelView ?: return
        params.width = panelSize.widthPx
        params.height = panelSize.heightPx
        params.x = panelOrigin.x
        params.y = panelOrigin.y
        runCatching { windowManager.updateViewLayout(panel, params) }
        applyChromeToPanel()
        updateHandleCoverage()
    }

    /**
     * Re-fits the remembered rectangle to the screen it is now on without moving it anywhere else.
     *
     * This is the "safe clamp" half of rotation handling: the size is brought inside the new limits and
     * the origin is then clamped against that size, so the panel can never end up mostly off-screen.
     */
    private fun clampPanelIntoScreen() {
        val size = OverlayGeometry.clampPanelSize(panelSize, screen, density)
        panelSize = size
        panelOrigin = OverlayGeometry.clampPanel(panelOrigin, size, screen)
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

    /**
     * Keeps the handle out of the way - and out of the game's input - while the open panel covers it.
     *
     * The handle window is bigger than the pill (it carries an invisible touch margin), so leaving it
     * up underneath the panel meant a strip of the game stopped receiving touches for no reason. An
     * invisible window still swallows input, which is why this clears `FLAG_NOT_TOUCHABLE` rather than
     * just hiding the view: with the flag set, touches pass straight through to whatever is below.
     *
     * The handle is only covered when the panel really overlaps it - a panel anchored in one corner and
     * a handle parked in the other both stay fully usable.
     */
    private fun updateHandleCoverage() {
        val view = handleView ?: return
        val params = handleParams ?: return
        val covered = panelOpen && OverlayGeometry.overlaps(
            PanelPlacement(panelOrigin, panelSize),
            handleOrigin,
            handleSizePx,
        )
        if (covered == handleCovered) return
        handleCovered = covered
        params.flags = if (covered) {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        view.visibility = if (covered) View.INVISIBLE else View.VISIBLE
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
            persistHandlePosition()
        }
    }

    override fun onFlingDown() = showPanel()

    override fun onFlingUp() = hidePanel()

    // ---------------------------------------------------------------- SidebarPanelView.Host

    override fun onMinimize() = hidePanel()

    override fun onClose() = hidePanel()

    override fun onPanelDrag(dx: Float, dy: Float) {
        val params = panelParams ?: return
        // Clamped while dragging, not only at the end: a panel that can be pulled off-screen can also
        // be lost off-screen, and recovering it means reopening the sidebar and starting over.
        val next = OverlayGeometry.clampPanel(
            Point(params.x + dx.toInt(), params.y + dy.toInt()),
            panelSize,
            screen,
        )
        params.x = next.x
        params.y = next.y
        panelOrigin = next
        panelView?.let { view -> runCatching { windowManager.updateViewLayout(view, params) } }
    }

    override fun onPanelDragEnd() {
        val params = panelParams ?: return
        val clamped = OverlayGeometry.clampPanel(Point(params.x, params.y), panelSize, screen)
        panelOrigin = clamped
        params.x = clamped.x
        params.y = clamped.y
        panelView?.let { view -> runCatching { windowManager.updateViewLayout(view, params) } }
        applyChromeToPanel()
        updateHandleCoverage()
        // End of a drag or of a resize: the rectangle is final, so this is where it gets persisted.
        persistPanelFrame()
    }

    override fun onPanelResizeStart() {
        // Deltas arrive absolute from the gesture's ACTION_DOWN, so the origin they are applied to has
        // to be the one from that same moment - otherwise every move compounds on the previous one.
        resizeAnchorOrigin = panelOrigin
    }

    override fun onPanelResize(resize: PanelResize) {
        if (videoFullscreen) return
        val params = panelParams ?: return
        // All limits (min/max width and height, and staying inside the safe area) are resolved in
        // :core against the current screen, so landscape and portrait cannot disagree about them.
        val placement = OverlayGeometry.resizePanel(resizeAnchorOrigin, resize, screen, density)
        panelSize = placement.size
        panelOrigin = placement.origin
        params.width = placement.size.widthPx
        params.height = placement.size.heightPx
        params.x = placement.origin.x
        params.y = placement.origin.y
        panelView?.let { view -> runCatching { windowManager.updateViewLayout(view, params) } }
        // Cheap and worth it: shrinking the panel past a threshold immediately hands the space back to
        // the page. applyChrome ignores the call unless the layout bucket actually changed, so a resize
        // that reports a new height on every move does not rebuild the toolbar every move.
        applyChromeToPanel()
    }

    override fun onOpenExternalAuth(url: String) {
        com.gamesidebar.browser.util.ExternalBrowser.open(context, url)
    }

    override fun onApplyWindowBrightness(brightness: Float) {
        val params = panelParams ?: return
        // A negative value is BRIGHTNESS_OVERRIDE_NONE - "stop overriding, follow the system". The
        // brightness tool never asks for it; leaving fullscreen does, and clamping that to 0.01 would
        // dim the panel to black for the rest of the session.
        params.screenBrightness = if (brightness < 0f) -1f else brightness.coerceIn(0.01f, 1f)
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
        // HTML5 fullscreen: the panel window grows to the whole usable screen, the video fills it, and
        // the service (plus its notification) keeps running untouched. The rectangle the panel had is
        // remembered here so leaving fullscreen puts it back exactly where it was - the WebView is never
        // recreated and no state is reset on the way in or out.
        val params = panelParams
        if (params == null) {
            // No panel window to grow (the sidebar is collapsed). Refuse cleanly: keeping the custom
            // view would make the chrome client refuse every later onShowCustomView, which is how
            // "video fullscreen stopped working" happens.
            controller.exitFullscreen()
            return
        }
        if (!videoFullscreen) {
            fullscreenRestore = PxSize(params.width, params.height) to Point(params.x, params.y)
            videoFullscreen = true
        }
        panelView?.enterFullscreen(view)
        applyFullscreenWindow(params)
    }

    /** Fullscreen bounds against the *current* screen, so a rotation mid-video stays correct. */
    private fun applyFullscreenWindow(params: WindowManager.LayoutParams) {
        params.width = screen.usableWidth
        params.height = screen.usableHeight
        params.x = screen.usableLeft
        params.y = screen.usableTop
        panelView?.let { view -> runCatching { windowManager.updateViewLayout(view, params) } }
    }

    override fun onExitFullscreen() {
        val restore = fullscreenRestore
        fullscreenRestore = null
        videoFullscreen = false
        // Order matters: the view goes back to normal chrome first (which also releases the brightness
        // override), then the window is put back to the rectangle it had before the video started.
        panelView?.exitFullscreen()
        restore?.let { (size, origin) ->
            panelSize = size
            panelOrigin = origin
        }
        // The state is restored even when the panel is on its way out, so the next open comes back at
        // the pre-video size and position instead of a preset.
        if (!panelOpen) return
        clampPanelIntoScreen()
        applyPanelWindow()
    }

    override fun onDownloadRequested(request: DownloadRequest) {
        panelView?.onDownloadRequested(request)
    }

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

    /**
     * Called by the service on configuration change (rotation, split screen, fold).
     *
     * Nothing is reset to a default here. Both overlay windows are carried across the change
     * proportionally and then clamped into the new safe area, which is the only mapping that means
     * anything once width and height have swapped: a portrait pixel coordinate in landscape is how the
     * panel ends up mostly off-screen, and re-anchoring on every rotation is how it ends up somewhere
     * the user did not put it.
     */
    fun onScreenChanged() {
        val previous = screen
        val previousDensity = density
        refreshScreen()
        if (previous == screen && previousDensity == density) return

        handleSizePx = OverlayGeometry.handleSize(settings.handleSize, density)
        if (settings.rememberPosition) {
            handleOrigin = OverlayGeometry.remapHandle(handleOrigin, handleSizePx, previous, screen)
            updateHandleLayout()
            persistHandlePosition()
        } else {
            placeHandleAtAnchor()
        }

        if (videoFullscreen) {
            // A video is playing: keep filling the new usable area rather than shrinking mid-playback.
            panelParams?.let { applyFullscreenWindow(it) }
            return
        }

        panelSize = OverlayGeometry.carryPanelSize(panelSize, previous, screen, density)
        panelOrigin = OverlayGeometry.remapPanel(panelOrigin, panelSize, previous, screen).origin
        if (panelOpen) applyPanelWindow()
        persistPanelFrame()
    }

    /** Handle position as fractions of the usable screen - the form that survives a rotation. */
    private fun persistHandlePosition() {
        if (!settings.rememberPosition) return
        val xFraction = (handleOrigin.x - screen.usableLeft).toFloat() / screen.usableWidth
        val yFraction = (handleOrigin.y - screen.usableTop).toFloat() / screen.usableHeight
        scope.launch { locator.settingsRepository.saveHandlePosition(xFraction, yFraction) }
    }

    private companion object {
        const val OPEN_ANIMATION_MS = 260L
        const val CLOSE_ANIMATION_MS = 160L
        const val HANDLE_TOUCH_PADDING_DP = 14f
    }
}

