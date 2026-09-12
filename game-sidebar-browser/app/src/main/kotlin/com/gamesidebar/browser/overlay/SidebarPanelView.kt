package com.gamesidebar.browser.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import com.gamesidebar.browser.R
import com.gamesidebar.browser.browser.BrowserController
import com.gamesidebar.browser.data.BrowserDataRepository
import com.gamesidebar.browser.data.prefs.SettingsRepository
import com.gamesidebar.core.browser.ShortcutIcon
import com.gamesidebar.core.browser.ShortcutList
import com.gamesidebar.core.browser.Tabs
import com.gamesidebar.core.browser.UrlResolver
import com.gamesidebar.core.download.DownloadRequest
import com.gamesidebar.core.geometry.ChromeLayout
import com.gamesidebar.core.geometry.PanelResize
import com.gamesidebar.core.model.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The floating sidebar panel: tabs, address bar, quick shortcuts, web content, tools and error
 * states, all inside one WindowManager overlay window.
 *
 * Layout is plain framework views (see `res/layout/overlay_panel.xml`); behaviour lives here and in
 * [BrowserController]. The panel never holds an Activity reference - it is owned by the overlay
 * service, which is what keeps the game's activity graph untouched.
 */
class SidebarPanelView(
    context: Context,
    private val controller: BrowserController,
    private val dataRepository: BrowserDataRepository,
    private val settingsRepository: SettingsRepository,
    private val uiScope: CoroutineScope,
    private val host: Host,
) : FrameLayout(context) {

    interface Host {
        fun onMinimize()
        fun onClose()
        fun onPanelDrag(dx: Float, dy: Float)
        fun onPanelDragEnd()

        /** First resize event of a gesture: lets the host snapshot the origin the deltas are relative to. */
        fun onPanelResizeStart()
        fun onPanelResize(resize: PanelResize)
        fun onOpenExternalAuth(url: String)
        fun onApplyWindowBrightness(brightness: Float)
    }

    private val density = resources.displayMetrics.density

    private val panelRoot: View
    private val panelHeader: View
    private val panelTitle: TextView
    private val buttonHome: ImageView
    private val tabStrip: LinearLayout
    private val tabScroll: HorizontalScrollView
    private val urlField: EditText
    private val buttonBack: ImageView
    private val buttonForward: ImageView
    private val buttonReload: ImageView
    private val buttonBookmark: ImageView
    private val buttonMore: ImageView
    private val buttonTools: ImageView
    private val buttonMinimize: ImageView
    private val buttonClose: ImageView
    private val pageProgress: ProgressBar
    private val shortcutRow: LinearLayout
    private val shortcutScroll: HorizontalScrollView
    private val webContainer: FrameLayout
    private val toolsContainer: FrameLayout
    private val errorView: LinearLayout
    private val errorIcon: ImageView
    private val errorTitle: TextView
    private val errorBody: TextView
    private val errorAction: TextView
    private val fullscreenContainer: FrameLayout
    private val buttonExitFullscreen: ImageView
    private val resizeGrip: View
    // V4 FIX: Compact video controls — tiny border bar 28-40dp, overlayed, only visible in VIDEO_FOCUS_MODE
    private val compactVideoControls: LinearLayout
    private val buttonVideoBack: ImageView
    private val buttonVideoMinimize: ImageView
    private val buttonVideoClose: ImageView

    private val iconCache = HashMap<String, Int>()
    private var shortcuts: ShortcutList = ShortcutList()
    private var settings: AppSettings = AppSettings.DEFAULT
    private var toolsOpen = false
    private var updatingUrlField = false
    private var pendingDownload: DownloadRequest? = null
    private var loading = false

    /** Chrome rows currently shown; re-applied only when the bucket changes, never per pixel. */
    private var chrome: ChromeLayout? = null

    /** Tab count the current chrome visibility was computed for. */
    private var chromeTabCount = -1
    private var tabCount = 0

    /** Last reported bookmark state, so the overflow menu can show it while the button is hidden. */
    private var bookmarked = false

    /** True between `onShowCustomView` and `onHideCustomView`: the video owns the whole panel. */
    private var videoFullscreen = false

    // V4 FIX: VIDEO_FOCUS_MODE — tiny controls, WebView 85-95% area, no toolbar recreation
    private var videoFocusMode = false

    // Live resize gesture. Deltas are absolute from ACTION_DOWN, so the start values are captured
    // once and every MOVE is a pure function of them - no accumulating drift on a fast drag.
    private var resizing = false
    private var resizeEdgeX = 0
    private var resizeEdgeY = 0
    private var resizeStartRawX = 0f
    private var resizeStartRawY = 0f
    private var resizeStartWidth = 0
    private var resizeStartHeight = 0

    /**
     * Width of the border ring that resizes the panel, taken from the layout's own padding.
     *
     * Every row of the panel is inset by `panel_padding`, so this ring contains no children at all:
     * intercepting a touch there cannot take a gesture away from the page, the tab strip or the
     * toolbar. Reading it from the dimen (rather than hardcoding 10dp) keeps the touch target and the
     * layout from drifting apart.
     */
    private val resizeBandPx: Int = resources.getDimensionPixelSize(R.dimen.panel_padding)

    /** The visible corner grip stays the generous resize target it always was. */
    private val resizeGripPx: Int = resources.getDimensionPixelSize(R.dimen.panel_resize_grip)

    private val swipeDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (!settings.swipeBetweenTabs) return false
                if (kotlin.math.abs(velocityX) < kotlin.math.abs(velocityY) * 1.5f) return false
                if (kotlin.math.abs(velocityX) < 600 * density) return false
                if (velocityX < 0) controller.nextTab() else controller.previousTab()
                return true
            }
        },
    )

    init {
        LayoutInflater.from(context).inflate(R.layout.overlay_panel, this, true)
        panelRoot = requireViewById(R.id.panelRoot)
        panelHeader = requireViewById(R.id.panelHeader)
        panelTitle = requireViewById(R.id.panelTitle)
        buttonHome = requireViewById(R.id.buttonHome)
        tabStrip = requireViewById(R.id.tabStrip)
        tabScroll = requireViewById(R.id.tabScroll)
        urlField = requireViewById(R.id.urlField)
        buttonBack = requireViewById(R.id.buttonBack)
        buttonForward = requireViewById(R.id.buttonForward)
        buttonReload = requireViewById(R.id.buttonReload)
        buttonBookmark = requireViewById(R.id.buttonBookmark)
        buttonMore = requireViewById(R.id.buttonMore)
        buttonTools = requireViewById(R.id.buttonTools)
        buttonMinimize = requireViewById(R.id.buttonMinimize)
        buttonClose = requireViewById(R.id.buttonClose)
        pageProgress = requireViewById(R.id.pageProgress)
        shortcutRow = requireViewById(R.id.shortcutRow)
        shortcutScroll = requireViewById(R.id.shortcutScroll)
        webContainer = requireViewById(R.id.webContainer)
        compactVideoControls = requireViewById(R.id.compactVideoControls)
        buttonVideoBack = requireViewById(R.id.buttonVideoBack)
        buttonVideoMinimize = requireViewById(R.id.buttonVideoMinimize)
        buttonVideoClose = requireViewById(R.id.buttonVideoClose)
        toolsContainer = requireViewById(R.id.toolsContainer)
        errorView = requireViewById(R.id.errorView)
        errorIcon = requireViewById(R.id.errorIcon)
        errorTitle = requireViewById(R.id.errorTitle)
        errorBody = requireViewById(R.id.errorBody)
        errorAction = requireViewById(R.id.errorAction)
        fullscreenContainer = requireViewById(R.id.fullscreenContainer)
        buttonExitFullscreen = requireViewById(R.id.buttonExitFullscreen)
        resizeGrip = requireViewById(R.id.resizeGrip)

        wireHeader()
        wireToolbar()
        wireResize()
        wireTools()
        wireVideoFocusControls()

        controller.attachContainer(webContainer)
        controller.attachDownloadListener(object : com.gamesidebar.browser.browser.DownloadCoordinator.Listener {
            override fun onDownloadStarted(fileName: String, location: String) {
                PanelTools.toast(context, context.getString(R.string.download_started))
            }

            override fun onDownloadFailed(fileName: String, reason: String) {
                showError(R.string.error_download_failed, R.string.error_no_network_body, R.string.action_cancel) {}
            }
        })
    }

    // ---------------------------------------------------------------- public API

    /** Applies settings: opacity, gaming mode, shortcuts, and WebView configuration. */
    fun applySettings(newSettings: AppSettings) {
        settings = newSettings
        (panelRoot.background?.mutate() as? GradientDrawable)?.alpha =
            (newSettings.opacityAlpha.coerceIn(0.2f, 1f) * 255).toInt()
        resizeGrip.visibility = if (newSettings.gamingMode) View.GONE else View.VISIBLE
        controller.applySettings(newSettings)
    }

    fun applyShortcuts(list: ShortcutList) {
        shortcuts = list
        renderShortcuts()
    }

    fun onPanelShown() {
        controller.refreshBookmarkState()
        renderTabs(controller.tabs)
    }

    fun onPanelHidden() {
        hideError()
    }

    fun teardown() {
        // A video still in fullscreen has to be released before the panel view goes away: the custom
        // view is parented inside this layout, and leaving it there means the chrome client keeps
        // holding a stale view and refuses the next onShowCustomView for the rest of the session.
        // The WebView itself is untouched - it belongs to the controller and survives the teardown.
        if (videoFullscreen) controller.exitFullscreen()
        controller.detachContainer()
    }

    // ---------------------------------------------------------------- header

    @SuppressLint("ClickableViewAccessibility")
    private fun wireHeader() {
        var lastX = 0f
        var lastY = 0f
        var totalDy = 0f
        var velocityTracker: VelocityTracker? = null
        val dismissThreshold = 90 * density

        panelHeader.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX
                    lastY = event.rawY
                    totalDy = 0f
                    velocityTracker?.recycle()
                    velocityTracker = VelocityTracker.obtain()
                    velocityTracker?.addMovement(event)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - lastX
                    val dy = event.rawY - lastY
                    lastX = event.rawX
                    lastY = event.rawY
                    totalDy += dy
                    velocityTracker?.addMovement(event)
                    host.onPanelDrag(dx, dy)
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    velocityTracker?.computeCurrentVelocity(1000)
                    val vy = velocityTracker?.yVelocity ?: 0f
                    velocityTracker?.recycle()
                    velocityTracker = null
                    host.onPanelDragEnd()
                    // Swipe down on the header minimises the panel back into the handle.
                    if (totalDy > dismissThreshold && vy > 400 * density) {
                        host.onMinimize()
                    }
                    true
                }

                else -> false
            }
        }

        // Swiping across the tab strip moves between tabs; the page itself keeps its own scrolling.
        tabScroll.setOnTouchListener { _, event ->
            swipeDetector.onTouchEvent(event)
            false
        }

        // The product mark is the home button, which is where a home button sits on a full browser
        // toolbar; the more-menu keeps a second entry so nothing depends on hitting it.
        //
        // The title text is deliberately NOT clickable. A clickable child swallows ACTION_DOWN, so the
        // header's drag listener only ever saw the small gaps between buttons - the widest part of the
        // header could not be used to move the panel. Leaving the title inert makes it part of the drag
        // surface, which is the gesture the header is for.
        buttonHome.setOnClickListener { controller.home() }
        buttonMinimize.setOnClickListener { host.onMinimize() }
        buttonClose.setOnClickListener { host.onClose() }
    }

    // ---------------------------------------------------------------- toolbar

    private fun wireToolbar() {
        buttonBack.setOnClickListener { controller.back() }
        buttonForward.setOnClickListener { controller.forward() }
        buttonBookmark.setOnClickListener { controller.toggleBookmark() }
        buttonMore.setOnClickListener { showMoreMenu(it) }
        buttonReload.setOnClickListener { if (loading) controller.stop() else controller.reload() }

        urlField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                controller.submit(urlField.getText().toString())
                urlField.clearFocus()
                true
            } else {
                false
            }
        }
        urlField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (updatingUrlField) return
                // Typing clears the error state so a retry is one keystroke away.
                hideError()
            }
        })
        urlField.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) urlField.selectAll()
        }
    }

    /**
     * The overflow menu.
     *
     * It also carries Forward and Bookmark, which the compact toolbar hides to keep the address bar
     * wide enough to read on a short landscape panel. Hiding a button must not hide a feature, so both
     * stay reachable here whatever the chrome layout is.
     */
    private fun showMoreMenu(anchor: View) {
        val menu = PopupMenu(context, anchor, Gravity.END)
        val items = menu.menu
        items.add(MENU_GROUP, MENU_HOME, 0, R.string.action_home).setIcon(R.drawable.ic_home)
        items.add(MENU_GROUP, MENU_NEW_TAB, 1, R.string.panel_new_tab).setIcon(R.drawable.ic_add)
        items.add(MENU_GROUP, MENU_CLOSE_TAB, 2, R.string.panel_close_tab).setIcon(R.drawable.ic_close)
        items.add(MENU_GROUP, MENU_FORWARD, 3, R.string.action_forward)
            .setIcon(R.drawable.ic_forward)
            .setEnabled(controller.canGoForward())
        items.add(MENU_GROUP, MENU_BOOKMARK, 4, R.string.action_bookmark)
            .setIcon(if (bookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark)
        items.add(MENU_GROUP, MENU_DESKTOP, 5, R.string.action_desktop_mode)
            .setIcon(R.drawable.ic_desktop)
            .setCheckable(true)
            .setChecked(controller.tabs.active?.isDesktopMode == true)
        items.add(MENU_GROUP, MENU_INCOGNITO, 6, R.string.settings_incognito)
            .setIcon(R.drawable.ic_incognito)
            .setCheckable(true)
            .setChecked(settings.incognito)
        items.add(MENU_GROUP, MENU_EXTERNAL, 7, R.string.action_open_external).setIcon(R.drawable.ic_external)
        items.add(MENU_GROUP, MENU_SHARE, 8, R.string.action_share).setIcon(R.drawable.ic_external)
        items.add(MENU_GROUP, MENU_CLEAR_CACHE, 9, R.string.settings_clear_cache).setIcon(R.drawable.ic_delete)
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_HOME -> controller.home()
                MENU_NEW_TAB -> controller.newTab()
                MENU_CLOSE_TAB -> controller.tabs.activeId?.let { controller.closeTab(it) }
                MENU_FORWARD -> controller.forward()
                MENU_BOOKMARK -> controller.toggleBookmark()
                MENU_DESKTOP -> controller.toggleDesktopMode()
                MENU_INCOGNITO -> uiScope.launch {
                    settingsRepository.update { it.copy(incognito = !it.incognito) }
                }

                MENU_EXTERNAL -> controller.openExternally()
                MENU_SHARE -> controller.shareCurrentPage()
                MENU_CLEAR_CACHE -> {
                    uiScope.launch {
                        com.gamesidebar.browser.browser.WebViewFactory.clearCacheOnly(context)
                        PanelTools.toast(context, context.getString(R.string.settings_cleared))
                    }
                }
            }
            true
        }
        runCatching { menu.show() }
    }

    // ---------------------------------------------------------------- tabs & shortcuts

    private fun renderTabs(tabs: Tabs) {
        tabStrip.removeAllViews()
        tabs.tabs.forEach { tab ->
            val active = tab.id == tabs.activeId
            val chip = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(if (active) R.drawable.bg_tab_active else R.drawable.bg_tab)
                setPadding(dp(10f), dp(6f), dp(6f), dp(6f))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ).apply { setMargins(0, 0, dp(6f), 0) }
                setOnClickListener { controller.selectTab(tab.id) }
            }

            chip.addView(TextView(context).apply {
                setText((tab.faviconKey ?: UrlResolver.hostOf(tab.url)?.take(1)?.uppercase()) ?: "•")
                setTextColor(if (active) Color.parseColor("#4C8DFF") else Color.parseColor("#B3FFFFFF"))
                textSize = 11f
                setPadding(0, 0, dp(6f), 0)
            })

            chip.addView(TextView(context).apply {
                setText(Tabs.shortenTitle(tab.displayTitle, 14))
                setTextColor(if (active) Color.WHITE else Color.parseColor("#B3FFFFFF"))
                textSize = 12f
                maxLines = 1
            })

            chip.addView(ImageView(context).apply {
                setImageResource(if (tab.isLoading) R.drawable.ic_reload else R.drawable.ic_close)
                setColorFilter(Color.parseColor("#80FFFFFF"))
                contentDescription = context.getString(R.string.panel_close_tab)
                setPadding(dp(6f), dp(6f), dp(2f), dp(6f))
                layoutParams = LinearLayout.LayoutParams(dp(24f), dp(24f))
                setOnClickListener { controller.closeTab(tab.id) }
            })
            tabStrip.addView(chip)
        }

        val addTab = ImageView(context).apply {
            setImageResource(R.drawable.ic_add)
            setColorFilter(Color.parseColor("#B3FFFFFF"))
            contentDescription = context.getString(R.string.panel_new_tab)
            setBackgroundResource(R.drawable.bg_icon_button)
            setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
            layoutParams = LinearLayout.LayoutParams(dp(32f), dp(32f))
            setOnClickListener { controller.newTab() }
        }
        tabStrip.addView(addTab)
    }

    private fun renderShortcuts() {
        shortcutRow.removeAllViews()
        shortcuts.items.forEach { shortcut ->
            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(dp(62f), ViewGroup.LayoutParams.MATCH_PARENT)
                setOnClickListener { controller.openShortcut(shortcut.url) }
                setOnLongClickListener {
                    showShortcutMenu(it, shortcut.id)
                    true
                }
            }
            column.addView(ImageView(context).apply {
                setImageResource(iconFor(shortcut.iconKey))
                setColorFilter(Color.WHITE)
                setBackgroundResource(R.drawable.bg_shortcut)
                setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
                layoutParams = LinearLayout.LayoutParams(dp(34f), dp(34f))
            })
            column.addView(TextView(context).apply {
                setText(shortcut.normalizedTitle.take(10))
                setTextColor(Color.parseColor("#B3FFFFFF"))
                textSize = 9f
                maxLines = 1
                gravity = Gravity.CENTER
            })
            shortcutRow.addView(column)
        }
    }

    private fun showShortcutMenu(anchor: View, shortcutId: String) {
        val menu = PopupMenu(context, anchor)
        menu.menu.add(MENU_GROUP, MENU_SHORTCUT_EDIT, 0, R.string.shortcuts_edit)
        menu.menu.add(MENU_GROUP, MENU_SHORTCUT_DELETE, 1, R.string.action_delete)
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_SHORTCUT_DELETE -> uiScope.launch {
                    val updated = shortcuts.remove(shortcutId)
                    settingsRepository.saveShortcuts(updated)
                    applyShortcuts(updated)
                }

                MENU_SHORTCUT_EDIT -> showShortcutEditor(shortcutId)
            }
            true
        }
        runCatching { menu.show() }
    }

    private fun showShortcutEditor(shortcutId: String?) {
        val existing = shortcutId?.let { id -> shortcuts.items.firstOrNull { it.id == id } }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(8f), dp(20f), 0)
        }
        val titleField = EditText(context).apply {
            hint = context.getString(R.string.shortcuts_name)
            setText(existing?.title.orEmpty())
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#80FFFFFF"))
            maxLines = 1
        }
        val urlFieldEditor = EditText(context).apply {
            hint = context.getString(R.string.shortcuts_url)
            setText(existing?.url.orEmpty())
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#80FFFFFF"))
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            maxLines = 1
        }
        container.addView(titleField)
        container.addView(urlFieldEditor)

        PanelTools.showOverlayDialog(
            context,
            context.getString(if (existing == null) R.string.shortcuts_add else R.string.shortcuts_edit),
            container,
        ) {
            val error = ShortcutList.validate(titleField.getText().toString(), urlFieldEditor.getText().toString())
            if (error != null) {
                PanelTools.toast(context, context.getString(stringForShortcutError(error)))
                return@showOverlayDialog
            }
            uiScope.launch {
                val updated = if (existing == null) {
                    shortcuts.add(
                        com.gamesidebar.core.browser.Shortcut(
                            id = ShortcutList.newId(shortcuts.items),
                            title = titleField.getText().toString(),
                            url = urlFieldEditor.getText().toString(),
                        ),
                    )
                } else {
                    shortcuts.update(
                        existing.id,
                        titleField.getText().toString(),
                        urlFieldEditor.getText().toString(),
                        existing.iconKey,
                    )
                }
                settingsRepository.saveShortcuts(updated)
                applyShortcuts(updated)
            }
        }
    }

    private fun stringForShortcutError(key: String): Int = when (key) {
        "shortcut_error_url_required" -> R.string.shortcut_error_url_required
        "shortcut_error_title_long" -> R.string.shortcut_error_title_long
        else -> R.string.shortcut_error_invalid_url
    }

    private fun iconFor(iconKey: String): Int {
        val icon = ShortcutIcon.fromKey(iconKey)
        return iconCache.getOrPut(icon.drawableName) {
            val resolved = resources.getIdentifier(icon.drawableName, "drawable", context.packageName)
            if (resolved != 0) resolved else R.drawable.ic_browser
        }
    }

    // ---------------------------------------------------------------- tools

    private fun wireTools() {
        buttonTools.setOnClickListener { toggleTools() }
        // Through the controller, not straight at this view: releasing the page's custom view is what
        // tells YouTube its fullscreen ended and restores the window geometry. Calling exitFullscreen()
        // here alone left the overlay window full-screen with the normal panel drawn inside it.
        buttonExitFullscreen.setOnClickListener { controller.exitFullscreen() }
    }

    private fun toggleTools() {
        toolsOpen = !toolsOpen
        if (toolsOpen) {
            toolsContainer.removeAllViews()
            toolsContainer.addView(buildToolsView())
            toolsContainer.visibility = View.VISIBLE
        } else {
            toolsContainer.removeAllViews()
            toolsContainer.visibility = View.GONE
        }
    }

    private fun buildToolsView(): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
        }

        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val content = FrameLayout(context)

        fun show(tool: View) {
            content.removeAllViews()
            content.addView(tool)
        }

        fun toolChip(labelRes: Int, factory: () -> View): TextView = TextView(context).apply {
            setText(labelRes)
            setTextColor(Color.WHITE)
            textSize = 11f
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_chip)
            setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(dp(3f), 0, dp(3f), 0) }
            setOnClickListener { show(factory()) }
        }

        val chipScroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        val chips = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        chips.addView(toolChip(R.string.tool_calculator) { PanelTools.calculatorView(context) })
        chips.addView(toolChip(R.string.tool_notes) { PanelTools.notesView(context, uiScope, dataRepository) })
        chips.addView(toolChip(R.string.tool_timer) { PanelTools.timerView(context) })
        chips.addView(toolChip(R.string.tool_clipboard) { PanelTools.clipboardView(context) })
        chips.addView(toolChip(R.string.tool_brightness) {
            PanelTools.brightnessControl(context) { brightness -> host.onApplyWindowBrightness(brightness) }
        })
        chips.addView(toolChip(R.string.tool_volume) { PanelTools.volumeControl(context) })
        chipScroll.addView(chips)

        val close = TextView(context).apply {
            setText(R.string.panel_browser)
            setTextColor(Color.parseColor("#4C8DFF"))
            textSize = 11f
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_chip)
            setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
            setOnClickListener { toggleTools() }
        }

        bar.addView(chipScroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(close)
        root.addView(bar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        show(PanelTools.calculatorView(context))
        return root
    }

    // ---------------------------------------------------------------- resize

    /**
     * Four-side and corner resize, driven by touch interception instead of eight extra views.
     *
     * The grip keeps its old job - the visible affordance in the bottom-right corner, hidden in Gaming
     * Mode - but it no longer owns a touch listener, because a listener there and interception here
     * would both claim the same corner and fight over which delta wins.
     */
    private fun wireResize() {
        resizeGrip.setOnTouchListener(null)
        resizeGrip.isClickable = false
    }

    /**
     * Which edges a touch grabs: -1 = left/top, +1 = right/bottom, 0 = nothing.
     *
     * Two zones, and neither contains an interactive child:
     *  - the `panel_padding` ring around the panel, which gives four edges and four 10dp corners;
     *  - the visible grip's own rect, which stays the comfortable bottom-right corner target.
     *
     * Resizing is switched off in Gaming Mode (the existing "no resize grip" rule) and while a video
     * owns the panel, so a fullscreen gesture is never mistaken for a resize.
     */
    private fun resizeEdgesAt(x: Float, y: Float): Pair<Int, Int> {
        if (videoFullscreen || settings.gamingMode || width <= 0 || height <= 0) return 0 to 0
        val band = resizeBandPx.toFloat()
        val gripLeft = width - resizeGripPx - band
        val gripTop = height - resizeGripPx - band
        val inGrip = x >= gripLeft && y >= gripTop
        val edgeX = when {
            x <= band -> -1
            x >= width - band || inGrip -> 1
            else -> 0
        }
        val edgeY = when {
            y <= band -> -1
            y >= height - band || inGrip -> 1
            else -> 0
        }
        return edgeX to edgeY
    }

    /**
     * Decided once per gesture, on ACTION_DOWN.
     *
     * Returning false is what keeps WebView scrolling intact: from then on the panel does not look at
     * another event of that gesture, so a vertical swipe inside a page belongs to the page alone and
     * the sidebar does not move. Returning true - possible only inside the border ring - claims the
     * gesture for resizing. Nothing is disabled globally and no transparent layer is added.
     */
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            val (edgeX, edgeY) = resizeEdgesAt(event.x, event.y)
            edgeX != 0 || edgeY != 0
        }

        MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> resizing
        else -> false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val (edgeX, edgeY) = resizeEdgesAt(event.x, event.y)
                if (edgeX == 0 && edgeY == 0) return super.onTouchEvent(event)
                beginResize(edgeX, edgeY, event)
                return true
            }

            MotionEvent.ACTION_MOVE -> if (resizing) {
                publishResize(event)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (resizing) {
                endResize()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun beginResize(edgeX: Int, edgeY: Int, event: MotionEvent) {
        resizing = true
        resizeEdgeX = edgeX
        resizeEdgeY = edgeY
        resizeStartRawX = event.rawX
        resizeStartRawY = event.rawY
        resizeStartWidth = width
        resizeStartHeight = height
        host.onPanelResizeStart()
    }

    /** Deltas stay absolute from ACTION_DOWN, so no MOVE can accumulate into a drifting panel. */
    private fun publishResize(event: MotionEvent) {
        val dx = (event.rawX - resizeStartRawX).toInt()
        val dy = (event.rawY - resizeStartRawY).toInt()
        host.onPanelResize(
            PanelResize(
                widthPx = resizeStartWidth + resizeEdgeX * dx,
                heightPx = resizeStartHeight + resizeEdgeY * dy,
                originDx = if (resizeEdgeX < 0) dx else 0,
                originDy = if (resizeEdgeY < 0) dy else 0,
                movesLeftEdge = resizeEdgeX < 0,
                movesTopEdge = resizeEdgeY < 0,
            ),
        )
    }

    private fun endResize() {
        resizing = false
        resizeEdgeX = 0
        resizeEdgeY = 0
        // The same end-of-gesture hook the drag uses: clamp once more and persist the new rectangle.
        host.onPanelDragEnd()
    }

    /**
     * Applies the chrome layout that fits the current panel height.
     *
     * Rows are hidden, never removed, and every control that disappears from the toolbar keeps an
     * entry in the more-menu, so a compact landscape panel loses pixels and nothing else. The early
     * return matters for smoothness: a resize crosses a bucket boundary at most a couple of times,
     * while it reports a new height on every move event.
     */
    fun applyChrome(layout: ChromeLayout) {
        // CRITICAL VIDEO FIX: When VIDEO_FOCUS_MODE is active, do NOT re-apply chrome that would unhide
        // header/tabs/URL/shortcuts and shrink WebView — video must continue when sidebar gets smaller
        if (videoFocusMode) return
        if (videoFullscreen) return
        // A second tab is a hard reason to keep the tab strip: hiding it would leave no way to switch
        // tabs at all, and losing a feature is worse than losing 38dp of page. With a single tab - the
        // normal case while gaming - the shortest chrome applies as computed.
        val showTabStrip = layout.showTabStrip || tabCount > 1
        if (chrome == layout && chromeTabCount == tabCount) return
        chrome = layout
        chromeTabCount = tabCount
        tabScroll.visibility = if (showTabStrip) View.VISIBLE else View.GONE
        shortcutScroll.visibility = if (layout.showShortcutRow) View.VISIBLE else View.GONE
        buttonForward.visibility = if (layout.showSecondaryToolbarButtons) View.VISIBLE else View.GONE
        buttonBookmark.visibility = if (layout.showSecondaryToolbarButtons) View.VISIBLE else View.GONE
    }

    // ---------------------------------------------------------------- controller callbacks

    fun onTabsChanged(tabs: Tabs) {
        tabCount = tabs.count
        renderTabs(tabs)
        // Going from one tab to two can bring the tab strip back even in the shortest chrome layout.
        chrome?.let { applyChrome(it) }
    }

    fun onProgress(progress: Int) {
        pageProgress.progress = progress
        pageProgress.visibility = if (progress in 1..99) View.VISIBLE else View.INVISIBLE
        updateReloadButton(isLoading = progress in 1..99)
    }

    /** One place decides whether the button stops or reloads; the listener is installed once. */
    private fun updateReloadButton(isLoading: Boolean) {
        if (loading == isLoading) return
        loading = isLoading
        buttonReload.setImageResource(if (isLoading) R.drawable.ic_stop else R.drawable.ic_reload)
        buttonReload.contentDescription = context.getString(
            if (isLoading) R.string.action_stop else R.string.action_reload,
        )
    }

    fun onUrlChanged(url: String, title: String) {
        if (!urlField.hasFocus()) {
            updatingUrlField = true
            urlField.setText(UrlResolver.displayUrl(url))
            updatingUrlField = false
        }
        panelTitle.setText(title.ifBlank { context.getString(R.string.panel_title) })
    }

    fun onNavigationChanged(canGoBack: Boolean, canGoForward: Boolean, isLoading: Boolean) {
        buttonBack.alpha = if (canGoBack) 1f else 0.35f
        buttonForward.alpha = if (canGoForward) 1f else 0.35f
        buttonBack.isEnabled = canGoBack
        buttonForward.isEnabled = canGoForward
        updateReloadButton(isLoading)
    }

    fun onBookmarkChanged(isBookmarked: Boolean) {
        // Kept for the overflow menu, which shows the bookmark state while the compact toolbar hides
        // the button itself.
        bookmarked = isBookmarked
        buttonBookmark.setImageResource(if (isBookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark)
        buttonBookmark.setColorFilter(
            if (isBookmarked) Color.parseColor("#4C8DFF") else Color.parseColor("#B3FFFFFF"),
        )
    }

    fun onDownloadRequested(request: DownloadRequest) {
        pendingDownload = request
        val message = TextView(context).apply {
            setText("${request.fileName}\n${request.sizeLabel} -> ${request.targetSubdir}")
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(dp(20f), dp(12f), dp(20f), dp(4f))
        }
        val dialog = android.app.AlertDialog.Builder(context)
            .setTitle(R.string.download_title)
            .setView(message)
            .setPositiveButton(R.string.action_download) { _, _ ->
                pendingDownload?.let { controller.downloadListener().enqueue(it) }
                pendingDownload = null
            }
            .setNegativeButton(R.string.action_cancel) { _, _ -> pendingDownload = null }
            .create()
        dialog.window?.setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        runCatching { dialog.show() }
    }

    // ---------------------------------------------------------------- fullscreen

    /**
     * VIDEO_FULLSCREEN: the page's own video surface covers the panel, so the header, tab strip,
     * address bar and shortcuts are all behind it and the video gets the whole window.
     *
     * The WebView stays exactly where it is - only the custom view the page handed us is re-parented.
     * Resize and drag are suspended for the duration so a video gesture cannot move the sidebar.
     */
    fun enterFullscreen(view: View) {
        // A resize in flight has to land before the video takes over, or its rectangle would never be
        // clamped or persisted and the panel would reopen at a half-finished size.
        if (resizing) endResize()
        if (view.parent != null) (view.parent as? ViewGroup)?.removeView(view)
        fullscreenContainer.addView(
            view,
            0,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        fullscreenContainer.visibility = View.VISIBLE
        videoFullscreen = true
        resizing = false
        host.onApplyWindowBrightness(1f)
    }

    /**
     * Back to NORMAL: the video surface goes home, the chrome is revealed again and the panel keeps
     * the size and position it had before the video started (restored by the overlay manager).
     */
    fun exitFullscreen() {
        val child = fullscreenContainer.getChildAt(0)
        if (child != null && child !== buttonExitFullscreen) {
            fullscreenContainer.removeView(child)
        }
        fullscreenContainer.visibility = View.GONE
        videoFullscreen = false
        // Brightness goes back to "no override" (-1) so the panel does not keep the window pinned at
        // full brightness after the video ends.
        host.onApplyWindowBrightness(-1f)
        // Also exit video focus mode when fullscreen ends — return to normal browser mode without recreating WebView
        if (videoFocusMode) exitVideoFocusMode()
    }

    // V4 FIX: VIDEO_FOCUS_MODE — tiny 32dp border, WebView 85-95% area, no toolbar recreation
    private fun wireVideoFocusControls() {
        buttonVideoBack.setOnClickListener { controller.back() }
        buttonVideoMinimize.setOnClickListener { host.onMinimize() }
        buttonVideoClose.setOnClickListener { host.onClose() }
        // Compact controls only consume touch inside tiny 32dp bar, never fullscreen — game/WebView gestures untouched
        compactVideoControls.setOnTouchListener { _, event ->
            // Only handle touches inside the tiny bar, do not create fullscreen transparent layer
            event.actionMasked == MotionEvent.ACTION_DOWN && compactVideoControls.visibility == View.VISIBLE
        }
    }

    fun enterVideoFocusMode() {
        if (videoFocusMode) return
        videoFocusMode = true
        // CRITICAL VIDEO FIX: Hide everything that consumes layout so WebView keeps playing when sidebar becomes smaller (350x500 -> 280x400)
        // Outer container SidebarRoot (FrameLayout) will be resized via WindowManager.updateViewLayout; WebView stays SAME INSTANCE with MATCH_PARENT
        // Resizing must ONLY update outer dimensions, never WebView lifecycle.
        panelHeader.visibility = View.GONE
        tabScroll.visibility = View.GONE
        findViewById<View>(R.id.toolbar)?.visibility = View.GONE
        shortcutScroll.visibility = View.GONE
        pageProgress.visibility = View.GONE
        // Keep ONLY tiny 32dp border bar overlayed — WebView occupies almost entire sidebar (85-95%)
        compactVideoControls.visibility = View.VISIBLE
        // Ensure WebView itself is untouched — same instance, MATCH_PARENT, no removeView/addView, no loadUrl/reload
        (webContainer.layoutParams as ViewGroup.LayoutParams).apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        // Do NOT recreate WebView — same instance, just visibility change; requestLayout via invalidate (stub-compatible)
        invalidate()
    }

    fun exitVideoFocusMode() {
        if (!videoFocusMode) return
        videoFocusMode = false
        // Restore NORMAL_BROWSER_MODE: header, tabs, address, navigation, shortcuts — without recreating WebView
        panelHeader.visibility = View.VISIBLE
        // Let applyChrome decide visibility, but ensure not forced GONE
        tabScroll.visibility = View.VISIBLE
        findViewById<View>(R.id.toolbar)?.visibility = View.VISIBLE
        shortcutScroll.visibility = View.VISIBLE
        pageProgress.visibility = View.INVISIBLE
        compactVideoControls.visibility = View.GONE
        invalidate()
    }

    fun isVideoFocusMode(): Boolean = videoFocusMode

    // ---------------------------------------------------------------- errors

    fun showError(titleRes: Int, bodyRes: Int, actionRes: Int, action: () -> Unit) {
        errorIcon.setImageResource(R.drawable.ic_warning)
        errorTitle.setText(titleRes)
        errorBody.setText(bodyRes)
        errorAction.setText(actionRes)
        errorAction.setOnClickListener {
            hideError()
            action()
        }
        errorView.visibility = View.VISIBLE
    }

    fun showErrorWithUrl(titleRes: Int, bodyRes: Int, url: String) {
        errorIcon.setImageResource(R.drawable.ic_wifi_off)
        errorTitle.setText(titleRes)
        errorBody.setText(bodyRes)
        errorAction.setText(R.string.action_retry)
        errorAction.setOnClickListener {
            hideError()
            controller.reload()
        }
        errorView.visibility = View.VISIBLE
        // CRITICAL LOGIN FIX: Do NOT show "Secure Login" blocking overlay for external auth URLs.
        // Previous code replaced retry with "Secure Login" when isExternalAuthUrl(url) true, which
        // blocked YouTube "Sign in" with Game SideBar warning. Now we keep normal retry; the
        // Custom Tab path is handled directly in shouldOverrideUrlLoading / onExternalAuthRequired
        // without showing blocking warning, preserving normal form login in WebView.
    }

    fun hideError() {
        errorView.visibility = View.GONE
    }

    /** Sign-in refusals get their own recovery path rather than a generic retry. */
    private fun isExternalAuthUrl(url: String): Boolean =
        com.gamesidebar.core.security.UrlSafety.requiresExternalAuth(url)

    private fun dp(value: Float): Int = (value * density + 0.5f).toInt()

    private companion object {
        const val MENU_GROUP = 0
        const val MENU_HOME = 1
        const val MENU_NEW_TAB = 2
        const val MENU_CLOSE_TAB = 3
        const val MENU_DESKTOP = 4
        const val MENU_INCOGNITO = 5
        const val MENU_EXTERNAL = 6
        const val MENU_SHARE = 7
        const val MENU_CLEAR_CACHE = 8
        const val MENU_FORWARD = 9
        const val MENU_BOOKMARK = 10
        const val MENU_SHORTCUT_EDIT = 20
        const val MENU_SHORTCUT_DELETE = 21
    }
}
