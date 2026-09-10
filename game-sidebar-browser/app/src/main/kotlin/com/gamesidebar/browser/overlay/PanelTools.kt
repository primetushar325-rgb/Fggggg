package com.gamesidebar.browser.overlay

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.gamesidebar.browser.R
import com.gamesidebar.browser.data.BrowserDataRepository
import com.gamesidebar.core.calc.Calculator
import com.gamesidebar.core.data.Note
import com.gamesidebar.core.data.NoteOps
import com.gamesidebar.core.timer.TimerState
import com.gamesidebar.core.util.ClipboardItem
import com.gamesidebar.core.util.ClipboardOps
import com.gamesidebar.core.util.Formatting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The panel's Tools section: calculator, notes, timer and clipboard.
 *
 * Built programmatically rather than from XML because these views only ever exist inside the overlay
 * window, and keeping them in one file makes it obvious that none of them reaches outside the app:
 * no sensors, no accessibility hooks, no game interaction.
 *
 * Not implemented on purpose: a screenshot shortcut (Android requires MediaProjection plus a consent
 * prompt per capture - neither quick nor appropriate for a browser overlay) and system-wide
 * brightness (needs WRITE_SETTINGS). The brightness control here dims the overlay window itself,
 * which needs no permission at all.
 */
object PanelTools {

    // ---------------------------------------------------------------- calculator

    fun calculatorView(context: Context): View {
        val root = vertical(context)
        var expression = ""

        val display = TextView(context).apply {
            text = "0"
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            maxLines = 1
            setPadding(context.dpPx(14f), context.dpPx(16f), context.dpPx(14f), context.dpPx(10f))
            typeface = Typeface.create("monospace", Typeface.NORMAL)
        }
        root.addView(display, matchWidth())

        fun render() {
            display.text = if (expression.isEmpty()) "0" else Calculator.toDisplayExpression(expression)
        }

        val keys = GridLayout(context).apply {
            columnCount = 4
            setPadding(context.dpPx(8f), context.dpPx(4f), context.dpPx(8f), context.dpPx(8f))
        }

        fun addKey(label: String, span: Int = 1, accent: Boolean = false, action: () -> Unit) {
            val key = TextView(context).apply {
                text = label
                textSize = 17f
                gravity = Gravity.CENTER
                setTextColor(if (accent) ACCENT else Color.WHITE)
                setBackgroundResource(R.drawable.bg_tool_key)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = context.dpPx(44f)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, span, 1f)
                    setMargins(context.dpPx(4f), context.dpPx(4f), context.dpPx(4f), context.dpPx(4f))
                }
                setOnClickListener { action() }
            }
            keys.addView(key)
        }

        addKey("C", accent = true) { expression = ""; render() }
        addKey("(") { expression += "("; render() }
        addKey(")") { expression += ")"; render() }
        addKey("⌫", accent = true) { expression = expression.dropLast(1); render() }

        listOf("7", "8", "9").forEach { digit -> addKey(digit) { expression += digit; render() } }
        addKey("÷", accent = true) { expression += " ÷ "; render() }

        listOf("4", "5", "6").forEach { digit -> addKey(digit) { expression += digit; render() } }
        addKey("×", accent = true) { expression += " × "; render() }

        listOf("1", "2", "3").forEach { digit -> addKey(digit) { expression += digit; render() } }
        addKey("−", accent = true) { expression += " - "; render() }

        addKey("%") { expression += "%"; render() }
        addKey("0") { expression += "0"; render() }
        addKey(".") { expression += "."; render() }
        addKey("+", accent = true) { expression += " + "; render() }

        addKey("=", span = 4, accent = true) {
            when (val result = Calculator.evaluate(expression)) {
                is Calculator.Result.Value -> expression = result.display
                is Calculator.Result.Error -> display.text = context.stringForReason(result.reasonKey)
                Calculator.Result.Incomplete -> Unit
            }
            render()
        }

        val scroll = ScrollView(context).apply { addView(keys, matchWidth()) }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    // ---------------------------------------------------------------- timer

    fun timerView(context: Context): View {
        val root = vertical(context)
        var state = TimerState()
        val handler = Handler(Looper.getMainLooper())

        val label = TextView(context).apply {
            text = TimerState.formatClock(0, withCentiseconds = true)
            textSize = 34f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.create("monospace", Typeface.BOLD)
            setPadding(0, context.dpPx(18f), 0, context.dpPx(8f))
        }
        root.addView(label, matchWidth())

        val transport = horizontal(context, Gravity.CENTER)
        val startPause = chipButton(context, R.string.timer_start)
        val reset = chipButton(context, R.string.timer_reset)
        transport.addView(startPause)
        transport.addView(reset)
        root.addView(transport, matchWidth())

        // One tick source, and only while running: a stopped timer costs zero CPU.
        val tick = object : Runnable {
            override fun run() {
                val now = SystemClock.elapsedRealtime()
                state = state.tick(now)
                label.text = state.primaryLabel(now)
                if (state.running) {
                    handler.postDelayed(this, TICK_MS)
                } else {
                    startPause.setText(R.string.timer_start)
                }
            }
        }

        fun stopTicking() = handler.removeCallbacks(tick)

        fun refresh() {
            label.text = state.primaryLabel(SystemClock.elapsedRealtime())
            startPause.setText(if (state.running) R.string.timer_pause else R.string.timer_start)
        }

        startPause.setOnClickListener {
            stopTicking()
            state = state.toggle(SystemClock.elapsedRealtime())
            refresh()
            if (state.running) handler.postDelayed(tick, TICK_MS)
        }
        reset.setOnClickListener {
            stopTicking()
            state = state.reset()
            refresh()
        }

        val modeRow = horizontal(context, Gravity.CENTER)
        modeRow.addView(chip(context, context.getString(R.string.timer_stopwatch)) {
            stopTicking()
            state = state.withMode(TimerState.Mode.STOPWATCH)
            refresh()
        })
        modeRow.addView(chip(context, context.getString(R.string.timer_countdown)) {
            stopTicking()
            state = state.withMode(TimerState.Mode.COUNTDOWN)
            refresh()
        })
        root.addView(modeRow, matchWidth())

        val presets = horizontal(context, Gravity.CENTER)
        TimerState.COUNTDOWN_PRESETS_MS.forEach { millis ->
            presets.addView(chip(context, "${millis / 60_000}m") {
                stopTicking()
                state = state.withTarget(millis)
                refresh()
            })
        }
        val presetScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(presets)
        }
        root.addView(presetScroll, matchWidth())

        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) = stopTicking()
        })
        return root
    }

    // ---------------------------------------------------------------- notes

    fun notesView(
        context: Context,
        scope: CoroutineScope,
        repository: BrowserDataRepository,
    ): View {
        val root = vertical(context)
        var query = ""
        var latest: List<Note> = emptyList()

        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(context).apply { addView(list, matchWidth()) }

        fun render() {
            list.removeAllViews()
            val filtered = NoteOps.search(NoteOps.sorted(latest), query)
            if (filtered.isEmpty()) {
                list.addView(emptyHint(context, R.string.notes_empty, R.string.notes_empty_body))
                return
            }
            filtered.forEach { note -> list.addView(noteRow(context, note, scope, repository)) }
        }

        val search = EditText(context).apply {
            hint = context.getString(R.string.notes_search)
            setTextColor(Color.WHITE)
            setHintTextColor(MUTED)
            setBackgroundResource(R.drawable.bg_url_field)
            inputType = InputType.TYPE_CLASS_TEXT
            maxLines = 1
            setPadding(context.dpPx(12f), context.dpPx(8f), context.dpPx(12f), context.dpPx(8f))
            textSize = 13f
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString().orEmpty()
                    render()
                }
            })
        }

        val header = horizontal(context, Gravity.CENTER_VERTICAL)
        header.addView(search, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val addButton = chipButton(context, R.string.notes_new)
        addButton.setOnClickListener {
            val editor = noteEditor(context)
            showOverlayDialog(context, context.getString(R.string.notes_new), editor.first) {
                scope.launch {
                    repository.createNote(editor.second.text.toString(), editor.third.text.toString())
                }
            }
        }
        header.addView(addButton)

        root.addView(header, matchWidth())
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        scope.launch {
            repository.notes.collectLatest { notes ->
                latest = notes
                render()
            }
        }
        return root
    }

    // ---------------------------------------------------------------- clipboard

    fun clipboardView(context: Context): View {
        val root = vertical(context)
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(context).apply { addView(list, matchWidth()) }

        fun render(items: List<ClipboardItem>) {
            list.removeAllViews()
            if (items.isEmpty()) {
                list.addView(emptyHint(context, R.string.clipboard_empty, R.string.notes_empty_body))
                return
            }
            items.forEach { item ->
                val row = horizontal(context, Gravity.CENTER_VERTICAL)
                row.setPadding(context.dpPx(8f), context.dpPx(6f), context.dpPx(8f), context.dpPx(6f))
                val text = TextView(context).apply {
                    text = ClipboardOps.preview(item.text)
                    setTextColor(Color.WHITE)
                    textSize = 12f
                    maxLines = 2
                }
                row.addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(iconAction(context, R.drawable.ic_copy, R.string.clipboard_copied) {
                    copyToClipboard(context, item.text)
                })
                row.addView(iconAction(context, R.drawable.ic_delete, R.string.action_delete) {
                    render(ClipboardHistory.remove(item.id))
                })
                list.addView(row, matchWidth())
            }
        }

        val header = horizontal(context, Gravity.CENTER_VERTICAL)
        val capture = chipButton(context, R.string.clipboard_capture)
        capture.setOnClickListener {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            val clip = clipboard?.primaryClip
            if (clip == null || clip.itemCount == 0) {
                render(ClipboardHistory.snapshot())
                return@setOnClickListener
            }
            val text = clip.getItemAt(0).coerceToText(context).toString()
            val updated = ClipboardHistory.capture(
                text = text,
                label = clip.description?.label?.toString().orEmpty(),
                sensitive = clip.isSensitive(),
            )
            if (updated == null) {
                toast(context, context.getString(R.string.clipboard_empty))
            } else {
                render(updated)
            }
        }
        val clear = chipButton(context, R.string.action_clear_all)
        clear.setOnClickListener { render(ClipboardHistory.clear()) }
        header.addView(capture)
        header.addView(clear)

        root.addView(header, matchWidth())
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        render(ClipboardHistory.snapshot())
        return root
    }

    /** Adjusts the overlay window's own brightness - no WRITE_SETTINGS involved. */
    fun brightnessControl(context: Context, onChange: (Float) -> Unit): View =
        sliderRow(context, R.drawable.ic_brightness, 90) { progress -> onChange(progress / 100f) }

    fun volumeControl(context: Context): View {
        val audio = context.getSystemService(AudioManager::class.java)
        val max = audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
        val current = audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        return sliderRow(context, R.drawable.ic_volume, current * 100 / max) { progress ->
            audio?.setStreamVolume(AudioManager.STREAM_MUSIC, progress * max / 100, 0)
        }
    }

    // ---------------------------------------------------------------- helpers

    private const val TICK_MS = 100L
    private val ACCENT: Int = Color.parseColor("#4C8DFF")
    private val MUTED: Int = Color.parseColor("#80FFFFFF")

    /** Android 13 exposes a "this clip is sensitive" flag; respect it and store nothing. */
    private fun ClipData.isSensitive(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true

    private fun vertical(context: Context) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dpPx(6f), context.dpPx(6f), context.dpPx(6f), context.dpPx(6f))
    }

    private fun horizontal(context: Context, gravity: Int) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        this.gravity = gravity
        setPadding(context.dpPx(4f), context.dpPx(4f), context.dpPx(4f), context.dpPx(4f))
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun chipButton(context: Context, @StringRes labelRes: Int): TextView = TextView(context).apply {
        setText(labelRes)
        setTextColor(Color.WHITE)
        textSize = 12f
        gravity = Gravity.CENTER
        setBackgroundResource(R.drawable.bg_chip)
        setPadding(context.dpPx(12f), context.dpPx(8f), context.dpPx(12f), context.dpPx(8f))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(context.dpPx(4f), context.dpPx(4f), context.dpPx(4f), context.dpPx(4f)) }
    }

    private fun chip(context: Context, label: String, onClick: () -> Unit): TextView = TextView(context).apply {
        text = label
        textSize = 12f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setBackgroundResource(R.drawable.bg_chip)
        setPadding(context.dpPx(12f), context.dpPx(6f), context.dpPx(12f), context.dpPx(6f))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(context.dpPx(4f), 0, context.dpPx(4f), 0) }
        setOnClickListener { onClick() }
    }

    private fun iconAction(
        context: Context,
        @DrawableRes icon: Int,
        @StringRes descriptionRes: Int,
        onClick: () -> Unit,
    ): ImageView = ImageView(context).apply {
        setImageResource(icon)
        setColorFilter(MUTED)
        contentDescription = context.getString(descriptionRes)
        setBackgroundResource(R.drawable.bg_icon_button)
        setPadding(context.dpPx(8f), context.dpPx(8f), context.dpPx(8f), context.dpPx(8f))
        layoutParams = LinearLayout.LayoutParams(context.dpPx(36f), context.dpPx(36f)).apply {
            setMargins(context.dpPx(4f), 0, 0, 0)
        }
        setOnClickListener { onClick() }
    }

    private fun sliderRow(
        context: Context,
        @DrawableRes icon: Int,
        initial: Int,
        onChange: (Int) -> Unit,
    ): View {
        val row = horizontal(context, Gravity.CENTER_VERTICAL)
        row.addView(
            ImageView(context).apply {
                setImageResource(icon)
                setColorFilter(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(context.dpPx(20f), context.dpPx(20f))
            },
        )
        row.addView(
            SeekBar(context).apply {
                max = 100
                progress = initial.coerceIn(0, 100)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) onChange(progress)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                })
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        return row
    }

    private fun emptyHint(context: Context, @StringRes titleRes: Int, @StringRes bodyRes: Int): TextView =
        TextView(context).apply {
            text = context.getString(titleRes) + "\n" + context.getString(bodyRes)
            setTextColor(MUTED)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(context.dpPx(16f), context.dpPx(24f), context.dpPx(16f), context.dpPx(24f))
        }

    private fun noteRow(
        context: Context,
        note: Note,
        scope: CoroutineScope,
        repository: BrowserDataRepository,
    ): View {
        val row = horizontal(context, Gravity.CENTER_VERTICAL)
        row.setPadding(context.dpPx(8f), context.dpPx(8f), context.dpPx(8f), context.dpPx(8f))

        val textColumn = vertical(context)
        textColumn.addView(TextView(context).apply {
            text = note.title.ifBlank { note.preview }
            setTextColor(Color.WHITE)
            textSize = 13f
            maxLines = 1
        })
        textColumn.addView(TextView(context).apply {
            text = Formatting.relativeTime(note.updatedAt, System.currentTimeMillis())
            setTextColor(MUTED)
            textSize = 11f
        })
        row.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        row.addView(
            iconAction(
                context,
                if (note.pinned) R.drawable.ic_bookmark_filled else R.drawable.ic_pin,
                R.string.notes_pin,
            ) { scope.launch { repository.toggleNotePin(note.id) } },
        )
        row.addView(iconAction(context, R.drawable.ic_edit, R.string.action_edit) {
            val editor = noteEditor(context, note.title, note.body)
            showOverlayDialog(context, context.getString(R.string.action_edit), editor.first) {
                scope.launch {
                    repository.updateNote(note.id, editor.second.text.toString(), editor.third.text.toString())
                }
            }
        })
        row.addView(iconAction(context, R.drawable.ic_delete, R.string.action_delete) {
            scope.launch { repository.deleteNote(note.id) }
        })
        return row
    }

    private fun noteEditor(
        context: Context,
        initialTitle: String = "",
        initialBody: String = "",
    ): Triple<ViewGroup, EditText, EditText> {
        val container = vertical(context)
        val title = EditText(context).apply {
            hint = context.getString(R.string.notes_hint_title)
            text = initialTitle
            setTextColor(Color.WHITE)
            setHintTextColor(MUTED)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = 1
            textSize = 14f
        }
        val body = EditText(context).apply {
            hint = context.getString(R.string.notes_hint_body)
            text = initialBody
            setTextColor(Color.WHITE)
            setHintTextColor(MUTED)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            textSize = 14f
        }
        container.addView(title, matchWidth())
        container.addView(body, matchWidth())
        return Triple(container, title, body)
    }

    /**
     * Dialogs opened from an overlay need a window type: the view's context is a Service, which has
     * no activity token. TYPE_APPLICATION_OVERLAY is legal here because the permission that puts the
     * panel on screen also allows its dialogs.
     */
    internal fun showOverlayDialog(
        context: Context,
        title: String,
        content: View,
        onConfirm: () -> Unit,
    ) {
        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(content)
            .setPositiveButton(R.string.action_save) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply {
                setColor(Color.parseColor("#FF101625"))
                cornerRadius = context.dpPx(18f).toFloat()
            },
        )
        runCatching { dialog.show() }
    }

    internal fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.primaryClip = ClipData.newPlainText("Game SideBar", text)
    }

    private fun Context.dpPx(value: Float): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun Context.stringForReason(reasonKey: String): String = when (reasonKey) {
        "calc_error_div_zero" -> getString(R.string.calc_error_div_zero)
        "calc_error_parentheses" -> getString(R.string.calc_error_parentheses)
        "calc_error_overflow" -> getString(R.string.calc_error_overflow)
        else -> getString(R.string.calc_error_syntax)
    }
}
