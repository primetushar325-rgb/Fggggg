package com.gamesoundpro.app.overlay

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.content.pm.ServiceInfo
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.gamesoundpro.app.GameSoundProApp
import com.gamesoundpro.app.MainActivity
import com.gamesoundpro.app.R
import com.gamesoundpro.app.audio.AudioEngine
import com.gamesoundpro.app.database.entity.SoundEntity
import com.gamesoundpro.app.permissions.Permissions
import com.gamesoundpro.app.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Floating gaming overlay: a small draggable bubble above other apps that expands into a
 * compact soundboard (favorites + recents, stop-all, volume). Uses the officially supported
 * TYPE_APPLICATION_OVERLAY mechanism and only ever runs after the user has explicitly
 * granted "Display over other apps" and enabled Gaming Mode. The window is not focusable so
 * the game underneath keeps full input; it is fully movable and never blocks touch outside
 * the bubble/panel.
 *
 * The overlay is a plain overlay renderer — it never reads or interacts with any other app.
 */
class OverlayService : Service() {

    private val container by lazy { (application as GameSoundProApp).container }
    private val engine: AudioEngine by lazy { container.audioEngine }
    private val settings: SettingsRepository by lazy { container.settingsRepository }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var windowManager: WindowManager? = null
    private var bubble: FrameLayout? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panel: LinearLayout? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var statusView: TextView? = null
    private var grid: GridLayout? = null
    private var volumeBar: SeekBar? = null

    private var quickSounds: List<SoundEntity> = emptyList()
    private var playingId: String? = null

    private val density: Float get() = resources.displayMetrics.density
    private fun dp(value: Int): Int = (value * density).toInt()
    private fun dpf(value: Int): Float = value * density

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        GamingModeManager.overlayActive.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Foreground first (required promptly after startForegroundService), then validate.
        startAsForeground()
        if (!Permissions.canDrawOverlays(this)) {
            // Never keep a windowless service alive; the UI explains how to grant the permission.
            stopSelf()
            return START_NOT_STICKY
        }
        if (bubble == null) addBubble()
        observeState()
        return START_STICKY
    }

    override fun onDestroy() {
        GamingModeManager.overlayActive.value = false
        removeAllViews()
        scope.cancel()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Re-anchor after rotation so the bubble never ends up off-screen.
        removePanel()
        bubble?.let { removeBubble(); addBubble() }
    }

    // ---- Foreground plumbing ----------------------------------------------------------

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

    // ---- Bubble -----------------------------------------------------------------------

    private fun addBubble() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm
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
            x = savedOrDefault(settings.snapshot.overlayX) { dp(12) }
            y = savedOrDefault(settings.snapshot.overlayY) { dp(320) }
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
        attachDragAndClick(content, params) { togglePanel() }

        val frame = FrameLayout(this)
        frame.addView(content)
        bubble = frame
        bubbleParams = params
        try {
            wm.addView(frame, params)
        } catch (_: Exception) {
            bubble = null
        }
    }

    private fun savedOrDefault(saved: Int, fallback: () -> Int): Int =
        if (saved == Int.MIN_VALUE) fallback() else saved

    /**
     * Shared drag + click behavior: dragging moves the window (persisting the last position),
     * a tap without movement runs [onClick].
     */
    private fun attachDragAndClick(view: View, params: WindowManager.LayoutParams, onClick: () -> Unit) {
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (abs(dx) > TOUCH_SLOP_PX || abs(dy) > TOUCH_SLOP_PX) moved = true
                    if (moved) {
                        params.x = (startX + dx).toInt().coerceAtLeast(0)
                        params.y = (startY + dy).toInt().coerceAtLeast(0)
                        runCatching { windowManager?.updateViewLayout(v, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        scope.launch { settings.setOverlayPosition(params.x, params.y) }
                    } else {
                        onClick()
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    // ---- Expanded panel ---------------------------------------------------------------

    private fun togglePanel() {
        if (panel != null) removePanel() else showPanel()
    }

    private fun showPanel() {
        val frame = bubble ?: return
        val params = bubbleParams ?: return
        removePanel()

        val scale = settings.snapshot.overlayScale
        val panelWidth = dp((292 * scale).toInt())
        val panelHeight = dp((352 * scale).toInt())
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
            x = (params.x + dp(64)).coerceIn(0, (screenW - panelWidth).coerceAtLeast(0))
            y = (params.y - dp(120)).coerceIn(0, (screenH - panelHeight).coerceAtLeast(0))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dpf(24)
                setColor(0xF50D1122.toInt())
                setStroke(dp(1), 0x4D8B5CF6)
            }
            elevation = dpf(14)
        }

        // Header (draggable) with title + collapse.
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "🎵 GAMING MODE"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
            setTextColor(0xFFA5B0E0.toInt())
        })
        header.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        header.addView(textButton("OPEN APP", 0xFF22D3EE.toInt()) { openApp() })
        header.addView(textButton("✕", 0xFF8B94C8.toInt()) { removePanel() })
        root.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        statusView = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFF7E88B8.toInt())
            maxLines = 1
        }
        root.addView(statusView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })

        grid = GridLayout(this).apply { columnCount = 3 }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(grid, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(8) })

        // Volume row.
        volumeBar = SeekBar(this).apply {
            max = 100
            progress = (settings.snapshot.mixer.master * 100).toInt()
            val tick = GradientDrawable().apply { setColor(0xFF8B5CF6.toInt()); setSize(dp(4), dp(4)) }
            thumb = tick
            progressTintList = android.content.res.ColorStateList.valueOf(0xFF8B5CF6.toInt())
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0xFF2A3150.toInt())
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) engine.setMasterVolumeLive(progress / 100f)
                }

                override fun onStartTrackingTouch(bar: SeekBar?) {}

                override fun onStopTrackingTouch(bar: SeekBar?) {
                    scope.launch { settings.setMasterVolume((bar?.progress ?: 100) / 100f) }
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
        root.addView(volumeRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })

        // Bottom actions.
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(actionButton("⏹ STOP ALL", 0xFFF87171.toInt()) { engine.stopAll() })
            addView(actionButton("⏻ EXIT MODE", 0xFF8B94C8.toInt()) { exitGamingMode() })
        }
        root.addView(actions, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })

        panel = root
        panelParams = pParams
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(root, pParams)
        } catch (_: Exception) {
            panel = null
            return
        }
        rebuildGrid()
        updateStatus()
    }

    private fun textButton(label: String, tint: Int, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tint)
            setPadding(dp(10), dp(6), dp(2), dp(6))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
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
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) }
            layoutParams = lp
            minHeight = dp(40)
            minWidth = dp(72)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    private fun soundButton(sound: SoundEntity): TextView =
        TextView(this).apply {
            val playing = sound.id == playingId
            text = "${sound.icon}\n${sound.name}"
            textSize = 11f
            maxLines = 2
            gravity = Gravity.CENTER
            setTextColor(if (playing) 0xFFFFFFFF.toInt() else 0xFFC9D0F2.toInt())
            typeface = if (playing) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            background = GradientDrawable().apply {
                cornerRadius = dpf(16)
                setColor(if (playing) 0x4D8B5CF6 else 0x14FFFFFF)
                setStroke(dp(1), if (playing) 0xFF22D3EE.toInt() else 0x26FFFFFF)
            }
            setPadding(dp(6), dp(10), dp(6), dp(10))
            minHeight = dp(64)
            isClickable = true
            isFocusable = true
            setOnClickListener { engine.playSound(sound) }
            setOnLongClickListener {
                openApp()
                true
            }
        }

    private fun rebuildGrid() {
        val g = grid ?: return
        g.removeAllViews()
        quickSounds.take(9).forEach { sound ->
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
    }

    private fun updateStatus() {
        statusView?.text = when (val id = playingId) {
            null -> "Ready — tap a sound"
            else -> "▶ ${quickSounds.firstOrNull { it.id == id }?.name ?: "Playing"}"
        }
    }

    private fun openApp() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    private fun exitGamingMode() {
        scope.launch { settings.setGamingMode(false) }
        stopSelf()
    }

    // ---- Teardown ---------------------------------------------------------------------

    private fun removePanel() {
        panel?.let {
            try {
                (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it)
            } catch (_: Exception) {
            }
        }
        panel = null
        panelParams = null
        statusView = null
        grid = null
        volumeBar = null
    }

    private fun removeBubble() {
        bubble?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
        }
        bubble = null
        bubbleParams = null
    }

    private fun removeAllViews() {
        removePanel()
        removeBubble()
    }

    private fun observeState() {
        scope.launch {
            container.soundRepository.quickOverlaySounds.collectLatest { sounds ->
                quickSounds = sounds
                rebuildGrid()
            }
        }
        scope.launch {
            engine.activeSounds.collectLatest { active ->
                playingId = active.firstOrNull()?.id
                updateStatus()
                rebuildGrid()
            }
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 41
        private const val TOUCH_SLOP_PX = 12
    }
}
