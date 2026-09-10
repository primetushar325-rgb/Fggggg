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
        fun onPanelResize(widthPx: Int, heightPx: Int)
        fun onOpenExternalAuth(url: String)
        fun onApplyWindowBrightness(brightness: Float)
    }

    private val density = resources.displayMetrics.density

    private val panelRoot: View
    private val panelHeader: View
    private val panelTitle: TextView
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

    private val iconCache = HashMap<String, Int>()
    private var shortcuts: ShortcutList = ShortcutList()
    private var settings: AppSettings = AppSettings.DEFAULT
    private var toolsOpen = false
    private var updatingUrlField = false
    private var pendingDownload: DownloadRequest? = null
    private var loading = false

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
        wireResizeGrip()
        wireTools()

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

        // Tapping the product mark goes home, which is where a home button would sit on a full
        // browser toolbar; the panel keeps the more-menu entry too.
        panelTitle.setOnClickListener { controller.home() }
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
                controller.submit(urlField.text.toString())
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

    private fun showMoreMenu(anchor: View) {
        val menu = PopupMenu(context, anchor, Gravity.END)
        val items = menu.menu
        items.add(MENU_GROUP, MENU_HOME, 0, R.string.action_home).setIcon(R.drawable.ic_home)
        items.add(MENU_GROUP, MENU_NEW_TAB, 1, R.string.panel_new_tab).setIcon(R.drawable.ic_add)
        items.add(MENU_GROUP, MENU_CLOSE_TAB, 2, R.string.panel_close_tab).setIcon(R.drawable.ic_close)
        items.add(MENU_GROUP, MENU_DESKTOP, 3, R.string.action_desktop_mode)
            .setIcon(R.drawable.ic_desktop)
            .setCheckable(true)
            .setChecked(controller.tabs.active?.isDesktopMode == true)
        items.add(MENU_GROUP, MENU_INCOGNITO, 4, R.string.settings_incognito)
            .setIcon(R.drawable.ic_incognito)
            .setCheckable(true)
            .setChecked(settings.incognito)
        items.add(MENU_GROUP, MENU_EXTERNAL, 5, R.string.action_open_external).setIcon(R.drawable.ic_external)
        items.add(MENU_GROUP, MENU_SHARE, 6, R.string.action_share).setIcon(R.drawable.ic_external)
        items.add(MENU_GROUP, MENU_CLEAR_CACHE, 7, R.string.settings_clear_cache).setIcon(R.drawable.ic_delete)
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_HOME -> controller.home()
                MENU_NEW_TAB -> controller.newTab()
                MENU_CLOSE_TAB -> controller.tabs.activeId?.let { controller.closeTab(it) }
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
                text = (tab.faviconKey ?: UrlResolver.hostOf(tab.url)?.take(1)?.uppercase()) ?: "•"
                textColor = if (active) Color.parseColor("#4C8DFF") else Color.parseColor("#B3FFFFFF")
                textSize = 11f
                setPadding(0, 0, dp(6f), 0)
            })

            chip.addView(TextView(context).apply {
                text = Tabs.shortenTitle(tab.displayTitle, 14)
                textColor = if (active) Color.WHITE else Color.parseColor("#B3FFFFFF")
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
                text = shortcut.normalizedTitle.take(10)
                textColor = Color.parseColor("#B3FFFFFF")
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
            text = existing?.title.orEmpty()
            textColor = Color.WHITE
            hintTextColor = Color.parseColor("#80FFFFFF")
            maxLines = 1
        }
        val urlFieldEditor = EditText(context).apply {
            hint = context.getString(R.string.shortcuts_url)
            text = existing?.url.orEmpty()
            textColor = Color.WHITE
            hintTextColor = Color.parseColor("#80FFFFFF")
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
            val error = ShortcutList.validate(titleField.text.toString(), urlFieldEditor.text.toString())
            if (error != null) {
                PanelTools.toast(context, context.getString(stringForShortcutError(error)))
                return@showOverlayDialog
            }
            uiScope.launch {
                val updated = if (existing == null) {
                    shortcuts.add(
                        com.gamesidebar.core.browser.Shortcut(
                            id = ShortcutList.newId(shortcuts.items),
                            title = titleField.text.toString(),
                            url = urlFieldEditor.text.toString(),
                        ),
                    )
                } else {
                    shortcuts.update(
                        existing.id,
                        titleField.text.toString(),
                        urlFieldEditor.text.toString(),
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
        buttonExitFullscreen.setOnClickListener { exitFullscreen() }
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
            textColor = Color.WHITE
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
            textColor = Color.parseColor("#4C8DFF")
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

    @SuppressLint("ClickableViewAccessibility")
    private fun wireResizeGrip() {
        var startX = 0f
        var startY = 0f
        var startWidth = 0
        var startHeight = 0

        resizeGrip.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    startWidth = width
                    startHeight = height
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startX).toInt()
                    val dy = (event.rawY - startY).toInt()
                    host.onPanelResize(startWidth + dx, startHeight + dy)
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    host.onPanelDragEnd()
                    true
                }

                else -> false
            }
        }
    }

    // ---------------------------------------------------------------- controller callbacks

    fun onTabsChanged(tabs: Tabs) {
        renderTabs(tabs)
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
            urlField.text = UrlResolver.displayUrl(url)
            updatingUrlField = false
        }
        panelTitle.text = title.ifBlank { context.getString(R.string.panel_title) }
    }

    fun onNavigationChanged(canGoBack: Boolean, canGoForward: Boolean, isLoading: Boolean) {
        buttonBack.alpha = if (canGoBack) 1f else 0.35f
        buttonForward.alpha = if (canGoForward) 1f else 0.35f
        buttonBack.isEnabled = canGoBack
        buttonForward.isEnabled = canGoForward
        updateReloadButton(isLoading)
    }

    fun onBookmarkChanged(isBookmarked: Boolean) {
        buttonBookmark.setImageResource(if (isBookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark)
        buttonBookmark.setColorFilter(
            if (isBookmarked) Color.parseColor("#4C8DFF") else Color.parseColor("#B3FFFFFF"),
        )
    }

    fun onDownloadRequested(request: DownloadRequest) {
        pendingDownload = request
        val message = TextView(context).apply {
            text = "${request.fileName}\n${request.sizeLabel} -> ${request.targetSubdir}"
            textColor = Color.WHITE
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

    fun enterFullscreen(view: View) {
        if (view.parent != null) (view.parent as? ViewGroup)?.removeView(view)
        fullscreenContainer.addView(
            view,
            0,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        fullscreenContainer.visibility = View.VISIBLE
        host.onApplyWindowBrightness(1f)
    }

    fun exitFullscreen() {
        val child = fullscreenContainer.getChildAt(0)
        if (child != null && child !== buttonExitFullscreen) {
            fullscreenContainer.removeView(child)
        }
        fullscreenContainer.visibility = View.GONE
    }

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
        if (isExternalAuthUrl(url)) {
            errorAction.setText(R.string.error_action_open_login)
            errorAction.setOnClickListener { host.onOpenExternalAuth(url) }
        }
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
        const val MENU_SHORTCUT_EDIT = 20
        const val MENU_SHORTCUT_DELETE = 21
    }
}
