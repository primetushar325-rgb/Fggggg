package com.gamesoundpro.app.overlay

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.gamesoundpro.app.GameSoundProApp
import com.gamesoundpro.app.MainActivity
import com.gamesoundpro.app.R
import com.gamesoundpro.app.audio.AudioState
import com.gamesoundpro.app.database.entity.SoundEntity
import com.gamesoundpro.app.domain.Category
import com.gamesoundpro.app.permissions.Permissions
import com.gamesoundpro.app.settings.SettingsRepository
import com.gamesoundpro.app.utils.DebugLog
import com.gamesoundpro.app.utils.Geometry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Floating gaming overlay: a small draggable bubble above other apps that expands into a
 * compact sidebar soundboard.
 *
 * V2 STABILITY ARCHITECTURE
 * -------------------------
 * The service is a thin *renderer* over three fully decoupled layers:
 *
 *   OverlayStateMachine (pure UI state: BUBBLE_ONLY <-> SIDEBAR_OPEN)
 *        |
 *        v
 *   OverlayService.reconcile()  — attaches/detaches ONLY the sidebar window to match state
 *        |                        (the bubble, the service itself and Gaming Mode are never
 *        |                         touched by opening/closing the sidebar)
 *        v
 *   AudioEngine (Application-scoped singleton — owns every player)
 *
 * Root causes of the V1 toggle bug that are fixed here:
 *  1. Window geometry could raise IllegalArgumentException (coerceIn with an inverted range
 *     when the panel exceeded the screen — landscape games, large overlay scale) which killed
 *     the service mid-toggle. All geometry now goes through Geometry.safeCoerce*.
 *  2. State fields and attached windows could desync after any swallowed exception. A single
 *     reconcile() pass is now the ONLY place windows are added/removed, and it re-syncs both
 *     directions (stale view attached -> detach; state wants view -> attach).
 *  3. observeState() added duplicate collectors on every service restart. Now guarded.
 *  4. Rapid double taps could interleave. Toggle is atomic (single main-thread path) with a
 *     small 220 ms debounce that never blocks a normal tap.
 */
class OverlayService : Service() {

    private val container by lazy { (application as GameSoundProApp).container }
    private val engine by lazy { container.audioEngine }
    private val settings: SettingsRepository by lazy { container.settingsRepository }
    private val stateMachine = OverlayStateMachine()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var windowManager: WindowManager? = null

    // Bubble (always attached while the service runs)
    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var iconSizePx: Int = 0
    private var iconGesture: FloatingIconTouchController? = null

    // Sidebar (attached only in SIDEBAR_OPEN — attach/detach NEVER touches the service)
    private var sidebarView: View? = null
    private var sidebarParams: WindowManager.LayoutParams? = null
    private var statusView: TextView? = null
    private var grid: GridLayout? = null
    private var searchField: EditText? = null
    private var chipRow: LinearLayout? = null

    // Sidebar content state (survives sidebar close/open — only process death resets it)
    private var searchQuery: String = ""
    private var activeFilter: String = "all"
    private var allSounds: List<SoundEntity> = emptyList()
    private var recentSounds: List<SoundEntity> = emptyList()
    private var keyboardActive: Boolean = false

    /** Monotonic content revision; collectors bump it and one posted pass re-renders. */
    private var contentRevision = 0
    private var renderScheduled = false
    private var reconciling = false
    private var observing = false
    private var lastToggleAt = 0L

    private val density: Float get() = resources.displayMetrics.density
    private fun dp(value: Int): Int = (value * density).toInt()
    private fun dpf(value: Int): Float = value * density

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        DebugLog.d(TAG, "service onCreate pid=${android.os.Process.myPid()}")
        GamingModeManager.overlayActive.value = true
        activeFilter = settings.snapshot.overlayFilter
        engine.warmUp()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DebugLog.d(TAG, "onStartCommand startId=$startId")
        // Foreground first (required promptly after startForegroundService), then validate.
        startAsForeground()
        if (!Permissions.canDrawOverlays(this)) {
            DebugLog.w("Permission", "overlay permission missing — stopping service")
            stopSelf()
            return START_NOT_STICKY
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        DebugLog.d("Service", "OVERLAY_SERVICE_RUNNING")
        reconcile()
        startObserving()
        return START_STICKY
    }

    override fun onDestroy() {
        DebugLog.d(TAG, "service onDestroy (Gaming Mode turned off or system kill)")
        GamingModeManager.overlayActive.value = false
        detachSidebar()
        detachBubble()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        DebugLog.d("Overlay", "configuration changed -> re-clamping overlay for new bounds")
        mainHandler.post {
            // Sidebar was laid out for the old bounds — rebuild it for the new orientation.
            detachSidebar()
            reClampBubble()
            reconcile()
        }
    }

    /**
     * Keeps the icon inside the current screen bounds (rotation, foldables, split screen).
     * Positions are stored as fractions of the movable area, so a portrait-saved position
     * lands somewhere sensible in landscape instead of off-screen (V1 restore bug).
     */
    private fun reClampBubble() {
        val view = bubbleView ?: return
        val params = bubbleParams ?: return
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        params.x = Geometry.coerceNonNegative(params.x, screenW - iconSizePx)
        params.y = Geometry.coerceNonNegative(params.y, screenH - iconSizePx)
        try {
            if (view.isAttachedToWindow) windowManager?.updateViewLayout(view, params)
            saveIconPosition(params)
        } catch (t: Throwable) {
            DebugLog.w("Overlay", "re-clamp failed", t)
        }
    }

    /** Persists the icon position as orientation-safe fractions (0..1 of movable area). */
    private fun saveIconPosition(params: WindowManager.LayoutParams) {
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val availW = (screenW - iconSizePx).coerceAtLeast(1)
        val availH = (screenH - iconSizePx).coerceAtLeast(1)
        val (fx, fy) = Geometry.normalizePosition(params.x, params.y, availW, availH)
        serviceScope.launch { settings.setOverlayPositionNormalized(fx, fy) }
        DebugLog.d("Overlay", "icon position saved (${("%.2f".format(fx))}, ${("%.2f".format(fy))})")
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, GameSoundProApp.CHANNEL_GAMING)
            .setSmallIcon(R.drawable.ic_stat_soundboard)
            .setContentTitle(getString(R.string.notif_gaming_title))
            .setContentText(getString(R.string.notif_gaming_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    // =====================================================================================
    // Window reconciliation — the ONLY place windows are added/removed
    // =====================================================================================

    /** Brings the actual windows in line with the state machine, whatever happened before. */
    private fun reconcile() {
        if (reconciling) return
        reconciling = true
        try {
            ensureBubble()
            val wantSidebar = stateMachine.sidebarOpen
            DebugLog.d("OverlayState", "reconcile state=${stateMachine.state} sidebarAttached=${sidebarView != null}")
            if (wantSidebar && sidebarView == null) {
                buildSidebar()
            } else if (!wantSidebar && sidebarView != null) {
                detachSidebar()
            } else if (wantSidebar && sidebarView != null && sidebarView?.isAttachedToWindow == false) {
                // Window got dropped by the system (rare) — rebuild it.
                detachSidebar()
                buildSidebar()
            }
        } catch (t: Throwable) {
            DebugLog.e(TAG, "reconcile failed — self-healing", t)
            selfHeal()
        } finally {
            reconciling = false
        }
    }

    /** Last-resort recovery: drop every window, rebuild exactly what the state machine says. */
    private fun selfHeal() {
        try {
            detachSidebar()
            detachBubble()
        } catch (t: Throwable) {
            DebugLog.e(TAG, "selfHeal detach failed", t)
        }
        try {
            ensureBubble()
            if (stateMachine.sidebarOpen) buildSidebar()
            DebugLog.d(TAG, "selfHeal complete state=${stateMachine.state}")
        } catch (t: Throwable) {
            DebugLog.e(TAG, "selfHeal rebuild failed", t)
        }
    }

    // =====================================================================================
    // Bubble
    // =====================================================================================

    private fun ensureBubble() {
        val wm = windowManager ?: return
        bubbleView?.let { view ->
            if (view.isAttachedToWindow) return
            // Stale/detached view object — rebuild from scratch.
            detachBubble()
        }
        val scale = settings.snapshot.overlayScale
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val screenW = resources.displayMetrics.widthPixels
            val screenH = resources.displayMetrics.heightPixels
            val sidePx = dp((56 * scale).toInt())
            val availW = (screenW - sidePx).coerceAtLeast(0)
            val availH = (screenH - sidePx).coerceAtLeast(0)
            // Restore: normalized fractions first (orientation-safe); legacy absolute px as
            // fallback; defaults when never saved. Everything is clamped into safe bounds.
            val nx = settings.snapshot.overlayNormX
            val ny = settings.snapshot.overlayNormY
            val (x0, y0) = if (!nx.isNaN() && !ny.isNaN()) {
                Geometry.denormalizePosition(nx, ny, availW, availH)
            } else if (settings.snapshot.overlayX != Int.MIN_VALUE && settings.snapshot.overlayY != Int.MIN_VALUE) {
                Geometry.denormalizePosition(
                    settings.snapshot.overlayX / 10_000f,
                    settings.snapshot.overlayY / 10_000f,
                    availW, availH,
                )
            } else {
                dp(12) to (availH * 2 / 3)
            }
            x = Geometry.coerceNonNegative(x0, availW)
            y = Geometry.coerceNonNegative(y0, availH)
            DebugLog.d("Overlay", "bubble restore ($x,$y) of ${availW}x$availH")
        }

        val side = (56 * scale).toInt()
        val content = TextView(this).apply {
            text = "🎵"
            textSize = 22f * scale
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            contentDescription = getString(R.string.cd_overlay_bubble)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                orientation = GradientDrawable.Orientation.TL_BR
                colors = intArrayOf(0xFF7C6CF6.toInt(), 0xFF22D3EE.toInt())
                setStroke(dp(1), 0x66FFFFFF)
            }
            layoutParams = FrameLayout.LayoutParams(dp(side), dp(side))
        }
        iconSizePx = dp(side)
        attachDragAndClick(content, params) { toggleSidebar() }

        val frame = FrameLayout(this)
        frame.addView(content)
        try {
            wm.addView(frame, params)
            bubbleView = frame
            bubbleParams = params
            DebugLog.d(TAG, "bubble attached at ${params.x},${params.y}")
        } catch (t: Throwable) {
            DebugLog.e(TAG, "bubble attach failed", t)
            bubbleView = null
            bubbleParams = null
        }
    }

    private fun detachBubble() {
        bubbleView?.let { view ->
            try {
                if (view.isAttachedToWindow) windowManager?.removeView(view)
            } catch (t: Throwable) {
                DebugLog.w(TAG, "bubble detach failed", t)
            }
        }
        bubbleView = null
        bubbleParams = null
    }

    private fun savedOrDefault(saved: Int, fallback: () -> Int): Int =
        if (saved == Int.MIN_VALUE) fallback() else saved

    /**
     * Shared drag + click behavior for the floating icon, backed by
     * [FloatingIconTouchController]: a tap toggles the sidebar, a drag moves the icon and
     * can NEVER trigger the toggle. The platform touch slop (density-correct) decides.
     */
    private fun attachDragAndClick(view: View, params: WindowManager.LayoutParams, onClick: () -> Unit) {
        val touchSlop = android.view.ViewConfiguration.get(this).scaledTouchSlop
        var startPX = 0
        var startPY = 0
        var downX = 0
        var downY = 0

        iconGesture = FloatingIconTouchController(touchSlop, object : FloatingIconTouchController.Callbacks {
            override fun onGestureDown(x: Int, y: Int) {
                startPX = params.x
                startPY = params.y
                downX = x
                downY = y
            }

            override fun onDragMoved(x: Int, y: Int) {
                try {
                    val screenW = resources.displayMetrics.widthPixels
                    val screenH = resources.displayMetrics.heightPixels
                    params.x = Geometry.coerceNonNegative(startPX + (x - downX), screenW - iconSizePx)
                    params.y = Geometry.coerceNonNegative(startPY + (y - downY), screenH - iconSizePx)
                    if (view.isAttachedToWindow) windowManager?.updateViewLayout(view, params)
                } catch (t: Throwable) {
                    DebugLog.e("Drag", "move failed", t)
                }
            }

            override fun onDragEnd() {
                // Snap to the nearest horizontal edge (assistant-bubble behavior), then save.
                try {
                    val screenW = resources.displayMetrics.widthPixels
                    params.x = Geometry.snapToNearestEdgeX(params.x, iconSizePx, screenW, dp(8))
                    if (view.isAttachedToWindow) windowManager?.updateViewLayout(view, params)
                    saveIconPosition(params)
                } catch (t: Throwable) {
                    DebugLog.e("Drag", "end failed", t)
                }
            }

            override fun onTap() {
                try {
                    view.performClick()
                } catch (_: Throwable) {
                }
                onClick()
            }
        })

        view.setOnTouchListener { _, event ->
            try {
                iconGesture?.feed(event.actionMasked, event.rawX, event.rawY) ?: false
            } catch (t: Throwable) {
                // Never let a touch event crash the overlay.
                DebugLog.e(TAG, "touch handler failed", t)
                true
            }
        }
    }

    // =====================================================================================
    // Sidebar toggle — atomic, debounced, NEVER stops the service or the audio
    // =====================================================================================

    private fun toggleSidebar() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastToggleAt < TOGGLE_DEBOUNCE_MS) {
            DebugLog.d("OverlayState", "toggle debounced (too fast)")
            return
        }
        lastToggleAt = now
        val newState = stateMachine.toggle()
        DebugLog.d("Click", if (newState == OverlayUiState.SIDEBAR_OPEN) "Sidebar OPEN" else "Sidebar CLOSED")
        DebugLog.d("OverlayState", "toggle -> $newState")
        if (newState == OverlayUiState.BUBBLE_ONLY) collapseKeyboard()
        reconcile()
    }

    private fun openSidebar() {
        if (stateMachine.set(true)) {
            DebugLog.d("OverlayState", "SIDEBAR_OPEN")
            reconcile()
        }
    }

    private fun closeSidebar() {
        collapseKeyboard()
        if (stateMachine.set(false)) {
            DebugLog.d("OverlayState", "SIDEBAR_CLOSED")
            reconcile()
        }
    }

    // =====================================================================================
    // Sidebar UI
    // =====================================================================================

    private fun sidebarSize(): Pair<Int, Int> {
        val scale = settings.snapshot.overlayScale
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val width = minOf(dp((300 * scale).toInt()), screenW - dp(16))
        val height = minOf(dp((360 * scale).toInt()), screenH - dp(48))
        return width.coerceAtLeast(dp(220)) to height.coerceAtLeast(dp(240))
    }

    private fun buildSidebar() {
        val wm = windowManager ?: return
        detachSidebar()

        val (panelWidth, panelHeight) = sidebarSize()
        val bubble = bubbleParams
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels

        val pParams = WindowManager.LayoutParams(
            panelWidth,
            panelHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Anchor next to the bubble, clamped fully on-screen (both sides considered).
            val preferredX = bubble?.x?.plus(dp(64)) ?: dp(12)
            val preferredY = bubble?.y?.minus(dp(40)) ?: dp(60)
            x = if (preferredX + panelWidth < screenW) preferredX else Geometry.clampPanelOrigin(
                preferredX - panelWidth - dp(60), panelWidth, screenW,
            )
            y = Geometry.clampPanelOrigin(preferredY, panelHeight, screenH)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dpf(22)
                setColor(0xF50D1122.toInt())
                setStroke(dp(1), 0x4D8B5CF6)
            }
            elevation = dpf(14)
        }

        // ---- Header: 🎮 GAME SOUND PRO  [TEST]  [✕] ----
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "🎮 GAME SOUND PRO"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.1f
            setTextColor(0xFFA5B0E0.toInt())
        })
        header.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        header.addView(textButton("TEST", 0xFF34D399.toInt()) {
            engine.testSound()
        })
        header.addView(textButton("OPEN", 0xFF22D3EE.toInt()) { openApp() })
        header.addView(textButton("✕", 0xFF8B94C8.toInt()) { closeSidebar() })
        root.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        statusView = TextView(this).apply {
            textSize = 11f
            setTextColor(0xFF7E88B8.toInt())
            maxLines = 1
        }
        root.addView(statusView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })

        // ---- Search field ----
        val search = EditText(this).apply {
            hint = "🔍 Search sound..."
            textSize = 13f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
            setTextColor(0xFFE8ECFB.toInt())
            setHintTextColor(0xFF5D668F.toInt())
            background = GradientDrawable().apply {
                cornerRadius = dpf(12)
                setColor(0x14FFFFFF)
                setStroke(dp(1), 0x26FFFFFF)
            }
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString().orEmpty()
                scheduleContentRefresh()
            }
        })
        search.setOnEditorActionListener { field, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) {
                collapseKeyboard()
                field.clearFocus()
                true
            } else {
                false
            }
        }
        search.setOnClickListener {
            if (!settings.snapshot.overlayKeyboard) return@setOnClickListener
            makeSidebarFocusable()
            keyboardActive = true
            search.post {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        searchField = search
        root.addView(search, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(38)).apply { topMargin = dp(8) })

        // ---- Filter chips ----
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        chipRow = chips
        val chipScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        }
        root.addView(chipScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })

        // ---- Sound grid (scrollable) ----
        grid = GridLayout(this).apply { columnCount = 3 }
        val gridScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(grid, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        root.addView(gridScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(6) })

        // ---- Volume + actions ----
        val volumeBar = SeekBar(this).apply {
            max = 100
            progress = (settings.snapshot.mixer.master * 100).toInt()
            progressTintList = android.content.res.ColorStateList.valueOf(0xFF8B5CF6.toInt())
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0xFF2A3150.toInt())
            thumbTintList = android.content.res.ColorStateList.valueOf(0xFFC9D0F2.toInt())
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) engine.setMasterVolumeLive(progress / 100f)
                }

                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {
                    serviceScope.launch { settings.setMasterVolume((bar?.progress ?: 100) / 100f) }
                }
            })
            contentDescription = "Master volume"
        }
        val volumeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@OverlayService).apply {
                text = "🔊"
                textSize = 14f
                setPadding(0, 0, dp(8), 0)
            })
            addView(volumeBar, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(volumeRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(actionButton("⏹ STOP ALL", 0xFFF87171.toInt()) { engine.stopAll() })
        }
        root.addView(actions, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })

        sidebarView = root
        sidebarParams = pParams
        try {
            wm.addView(root, pParams)
            DebugLog.d("OverlayState", "sidebar attached ${pParams.x},${pParams.y} ${panelWidth}x${panelHeight}")
        } catch (t: Throwable) {
            DebugLog.e(TAG, "sidebar attach failed", t)
            sidebarView = null
            sidebarParams = null
            return
        }
        rebuildChips()
        refreshContent()
        updateStatus()
    }

    /** Removes ONLY the sidebar window. Bubble/service/audio keep running. */
    private fun detachSidebar() {
        sidebarView?.let { view ->
            try {
                if (view.isAttachedToWindow) windowManager?.removeView(view)
            } catch (t: Throwable) {
                DebugLog.w(TAG, "sidebar detach failed", t)
            }
        }
        sidebarView = null
        sidebarParams = null
        statusView = null
        grid = null
        searchField = null
        chipRow = null
        keyboardActive = false
    }

    private fun makeSidebarFocusable() {
        val view = sidebarView ?: return
        val params = sidebarParams ?: return
        try {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            windowManager?.updateViewLayout(view, params)
            DebugLog.d(TAG, "sidebar focusable (keyboard)")
        } catch (t: Throwable) {
            DebugLog.w(TAG, "makeFocusable failed", t)
        }
    }

    private fun collapseKeyboard() {
        if (!keyboardActive) return
        try {
            searchField?.clearFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            searchField?.windowToken?.let { token ->
                imm?.hideSoftInputFromWindow(token, 0)
            }
            sidebarParams?.let { params ->
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                sidebarView?.let { view ->
                    if (view.isAttachedToWindow) windowManager?.updateViewLayout(view, params)
                }
            }
        } catch (t: Throwable) {
            DebugLog.w(TAG, "collapseKeyboard failed", t)
        }
        keyboardActive = false
    }

    private fun textButton(label: String, tint: Int, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tint)
            setPadding(dp(10), dp(6), dp(2), dp(6))
            minHeight = dp(36)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                try {
                    onClick()
                } catch (t: Throwable) {
                    DebugLog.e(TAG, "button '$label' failed", t)
                }
            }
        }

    private fun actionButton(label: String, tint: Int, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tint)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dpf(14)
                setColor(0x14FFFFFF)
                setStroke(dp(1), 0x33FFFFFF)
            }
            setPadding(dp(14), dp(9), dp(14), dp(9))
            minHeight = dp(40)
            minWidth = dp(120)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                try {
                    onClick()
                } catch (t: Throwable) {
                    DebugLog.e(TAG, "action '$label' failed", t)
                }
            }
        }

    // =====================================================================================
    // Sidebar content (chips, grid, status) — revision-based single-flight rendering
    // =====================================================================================

    private val chipDefs: List<Pair<String, String>> = buildList {
        add("all" to "ALL")
        Category.entries.forEach { add(it.key to it.label.uppercase()) }
        add("fav" to "🔥")
        add("recent" to "RECENT")
    }

    private fun rebuildChips() {
        val row = chipRow ?: return
        row.removeAllViews()
        chipDefs.forEach { (key, label) ->
            val selected = key == activeFilter
            row.addView(TextView(this).apply {
                text = label
                textSize = 10f
                typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (selected) 0xFF0D1122.toInt() else 0xFF9AA3CC.toInt())
                background = GradientDrawable().apply {
                    cornerRadius = dpf(12)
                    setColor(if (selected) 0xFF22D3EE.toInt() else 0x14FFFFFF)
                }
                setPadding(dp(10), dp(5), dp(10), dp(5))
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { marginEnd = dp(6) }
                layoutParams = lp
                minHeight = dp(28)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    activeFilter = key
                    serviceScope.launch { settings.setOverlayFilter(key) }
                    rebuildChips()
                    refreshContent()
                }
            })
        }
    }

    private fun visibleSounds(): List<SoundEntity> {
        val q = searchQuery.trim()
        val base = when {
            q.isNotBlank() -> allSounds.filter {
                it.name.contains(q, true) || it.category.contains(q, true)
            }
            activeFilter == "fav" -> allSounds.filter { it.isFavorite }
            activeFilter == "recent" -> recentSounds
            activeFilter == "all" -> allSounds
            else -> allSounds.filter { it.category == activeFilter }
        }
        return if (q.isBlank() && activeFilter != "recent") {
            base.sortedWith(compareByDescending<SoundEntity> { it.isFavorite }.thenBy { it.name.lowercase() })
        } else {
            base
        }.take(30)
    }

    private fun soundButton(sound: SoundEntity): TextView =
        TextView(this).apply {
            val playing = engine.activeSounds.value.any { it.id == sound.id }
            text = "${sound.icon} ${sound.name}"
            textSize = 11f
            maxLines = 2
            gravity = Gravity.CENTER
            setTextColor(if (playing) 0xFFFFFFFF.toInt() else 0xFFC9D0F2.toInt())
            typeface = if (playing) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            background = GradientDrawable().apply {
                cornerRadius = dpf(14)
                setColor(if (playing) 0x4D8B5CF6 else 0x14FFFFFF)
                setStroke(dp(1), if (playing) 0xFF22D3EE.toInt() else 0x26FFFFFF)
            }
            setPadding(dp(6), dp(8), dp(6), dp(8))
            minHeight = dp(56)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                try {
                    if (sound.category == com.gamesoundpro.app.domain.Category.MUSIC.key) {
                        // Music taps stream through the music player (loop/shuffle/seek-capable),
                        // not the short-SFX pool.
                        val tracks = allSounds.filter { it.category == com.gamesoundpro.app.domain.Category.MUSIC.key }
                        val index = tracks.indexOfFirst { it.id == sound.id }.coerceAtLeast(0)
                        engine.playMusic(tracks, index)
                    } else {
                        engine.playSound(sound)
                    }
                } catch (t: Throwable) {
                    DebugLog.e(TAG, "play failed sound=${sound.name}", t)
                    updateStatusText("⚠ Playback failed")
                }
            }
        }

    /** Coalesces collector updates into one posted re-render (never during a layout pass). */
    private fun scheduleContentRefresh() {
        contentRevision++
        if (renderScheduled) return
        renderScheduled = true
        val host = sidebarView ?: bubbleView
        if (host == null) {
            renderScheduled = false
            return
        }
        host.post {
            renderScheduled = false
            refreshContent()
        }
    }

    private fun refreshContent() {
        if (stateMachine.sidebarOpen && sidebarView == null) return
        val g = grid ?: return
        val sounds = visibleSounds()
        try {
            g.removeAllViews()
            sounds.forEach { sound ->
                val lp = GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f),
                ).apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                }
                g.addView(soundButton(sound), lp)
            }
            if (sounds.isEmpty()) {
                g.addView(TextView(this).apply {
                    text = if (searchQuery.isNotBlank()) "No matches" else "No sounds here yet"
                    textSize = 11f
                    setTextColor(0xFF7E88B8.toInt())
                    setPadding(dp(8), dp(16), dp(8), dp(16))
                })
            }
        } catch (t: Throwable) {
            DebugLog.e(TAG, "refreshContent failed", t)
        }
    }

    private fun updateStatus() {
        val snap = engine.snapshot.value
        val playing = snap.currentSound
        val text = when {
            snap.state == AudioState.PLAYING && playing != null -> "▶ ${playing.name}"
            snap.state == AudioState.ERROR -> "⚠ ${snap.lastError ?: "Playback error"}"
            else -> "Ready — tap a sound"
        }
        updateStatusText(text)
    }

    private fun updateStatusText(text: String) {
        statusView?.text = text
    }

    private fun openApp() {
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        } catch (t: Throwable) {
            DebugLog.w(TAG, "openApp failed", t)
        }
    }

    private fun exitGamingMode() {
        DebugLog.d(TAG, "exitGamingMode requested")
        serviceScope.launch { settings.setGamingMode(false) }
        stopSelf()
    }

    // =====================================================================================
    // State observation (idempotent — never duplicated on service restart)
    // =====================================================================================

    private fun startObserving() {
        if (observing) {
            DebugLog.d(TAG, "startObserving skipped (already observing)")
            return
        }
        observing = true
        serviceScope.launch {
            container.soundRepository.sounds.collect { sounds ->
                allSounds = sounds
                scheduleContentRefresh()
            }
        }
        serviceScope.launch {
            container.soundRepository.recents.collect { recents ->
                recentSounds = recents
                scheduleContentRefresh()
            }
        }
        serviceScope.launch {
            engine.activeSounds.collect {
                scheduleContentRefresh()
                updateStatus()
            }
        }
        serviceScope.launch {
            engine.snapshot.collect { snap ->
                updateStatus()
                if (snap.state == AudioState.ERROR) updateStatusText("⚠ ${snap.lastError ?: "Playback error"}")
            }
        }
        serviceScope.launch {
            engine.testResult.collect { result ->
                when {
                    result == null -> {}
                    result.success -> {
                        updateStatusText(if (result.hint != null) "✓ Audio Engine Active — ${result.hint}" else "✓ Audio Engine Active")
                    }
                    else -> updateStatusText("⚠ Audio Playback Unavailable — ${result.failureReason ?: "unknown"}")
                }
            }
        }
        DebugLog.d(TAG, "observing started")
    }

    companion object {
        private const val TAG = "OverlayService"
        private const val NOTIFICATION_ID = 41
        private const val TOGGLE_DEBOUNCE_MS = 220L
    }
}
