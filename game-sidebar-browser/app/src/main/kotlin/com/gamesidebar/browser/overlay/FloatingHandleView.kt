package com.gamesidebar.browser.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import com.gamesidebar.core.model.GlowIntensity

/**
 * The floating handle: a small white pill that sits over games until it is tapped.
 *
 * Drawn by hand rather than composed from nested views: one View, one draw pass, no layout tree.
 * The glow is a handful of translucent rounded rects rather than a blur filter, because a real blur
 * on a hardware-accelerated overlay window costs frames on exactly the mid-range devices this app
 * targets.
 *
 * Gestures: tap opens, double tap toggles, long press enters reposition mode, drag moves the window,
 * fling down opens and fling up minimises.
 */
class FloatingHandleView(context: Context) : View(context) {

    interface Listener {
        fun onTap()
        fun onDoubleTap()
        fun onLongPress()
        fun onDrag(dx: Float, dy: Float)
        fun onDragEnd()
        fun onFlingDown()
        fun onFlingUp()
    }

    data class Appearance(
        val glowEnabled: Boolean = true,
        val glowColor: Int = Color.parseColor("#4C8DFF"),
        val glowIntensity: GlowIntensity = GlowIntensity.MEDIUM,
        val alpha: Float = 0.95f,
        val repositionMode: Boolean = false,
    )

    var listener: Listener? = null

    var appearance: Appearance = Appearance()
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = Color.parseColor("#804C8DFF")
    }
    private val pillRect = RectF()

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private var velocityTracker: VelocityTracker? = null
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var dragging = false

    /** Extra invisible margin so the pill stays comfortably tappable during a match. */
    private val touchPadding = 14f * density

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                listener?.onTap()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                listener?.onDoubleTap()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                listener?.onLongPress()
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float,
            ): Boolean {
                if (kotlin.math.abs(velocityY) < minFlingVelocity * 2) return false
                if (kotlin.math.abs(velocityY) < kotlin.math.abs(velocityX)) return false
                return if (velocityY > 0) {
                    listener?.onFlingDown()
                    true
                } else {
                    listener?.onFlingUp()
                    true
                }
            }
        },
    )

    init {
        // The handle must not swallow gestures meant for the game: it is a small window and only
        // reacts to touches inside it.
        isClickable = true
        isFocusable = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(suggestedMinimumHeight, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        val left = touchPadding
        val top = touchPadding
        val right = (width - touchPadding).coerceAtLeast(left + 1f)
        val bottom = (height - touchPadding).coerceAtLeast(top + 1f)
        pillRect.set(left, top, right, bottom)
        val radius = (bottom - top) / 2f

        if (appearance.glowEnabled) {
            drawGlow(canvas, radius)
        }

        pillPaint.alpha = (appearance.alpha.coerceIn(0.2f, 1f) * 255).toInt()
        canvas.drawRoundRect(pillRect, radius, radius, pillPaint)

        if (appearance.repositionMode) {
            val expanded = RectF(pillRect)
            expanded.inset(-3f * density, -3f * density)
            canvas.drawRoundRect(expanded, radius + 3f * density, radius + 3f * density, ringPaint)
        }
    }

    /**
     * Cheap "glow": three concentric translucent pills. Reads as a soft halo at handle size and
     * costs three round-rect draws, so Gaming Mode can simply turn it off.
     */
    private fun drawGlow(canvas: Canvas, radius: Float) {
        val intensity = appearance.glowIntensity
        val layers = 3
        for (index in layers downTo 1) {
            val spread = intensity.radiusDp * density * (index / layers.toFloat())
            val halo = RectF(pillRect)
            halo.inset(-spread, -spread)
            val alpha = (intensity.alpha * 255 * (1f - (index - 1) / (layers + 1f))).toInt()
                .coerceIn(0, 255)
            glowPaint.color = appearance.glowColor
            glowPaint.alpha = alpha
            canvas.drawRoundRect(halo, radius + spread, radius + spread, glowPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        velocityTracker?.addMovement(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.rawX
                lastTouchY = event.rawY
                dragging = false
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastTouchX
                val dy = event.rawY - lastTouchY
                if (!dragging && (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop)) {
                    dragging = true
                }
                if (dragging) {
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    listener?.onDrag(dx, dy)
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) listener?.onDragEnd()
                dragging = false
                velocityTracker?.recycle()
                velocityTracker = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** Called by the overlay manager when the handle is parked at an edge and partly hidden. */
    fun setEdgeReveal(visibleFraction: Float) {
        alpha = (0.55f + 0.45f * visibleFraction.coerceIn(0f, 1f))
    }

    fun restoreAlpha() {
        alpha = 1f
    }

    @Suppress("unused")
    private fun supportsBlurNatively(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}
