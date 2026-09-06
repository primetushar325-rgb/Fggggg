package com.rigstudio.app.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import com.rigstudio.core.anim.AnimationEngine
import com.rigstudio.core.anim.PlaybackClock
import com.rigstudio.core.rig.Pose

/**
 * The editor's live viewport: a plain [View] that redraws the puppet on every display frame.
 *
 * Playback is driven by [Choreographer] inside the view rather than by recomposing a Compose tree
 * at 60 Hz. That keeps the hot path (sample pose → compose draw list → paint bitmaps) free of
 * composition overhead, and it means the playhead can never drift from the frame actually on
 * screen. Compose owns the *controls*; this view owns the *clock and the pixels*, and reports the
 * playhead back at ~15 Hz so the timeline readout can follow along without thrashing.
 *
 * The stage is prepared at the view's own pixel size, so what the user sees is framed exactly as a
 * 1280×720 or 1920×1080 export of the same clip will be framed (see [StageRenderer]).
 */
class StageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr), Choreographer.FrameCallback {

    /**
     * What to draw. Assigning a new source re-solves the camera for the current size; assigning
     * null clears the stage (the view then paints only its background).
     */
    var stageSource: StageSource? = null
        set(value) {
            if (field == value) return
            // V5 §41: keep the outgoing stage for a short pose-space crossfade.
            prepared?.let { outgoing ->
                outgoingStage = outgoing
                outgoingPlayhead = clock.normalizedTime
                outgoingSwitchNanos = System.nanoTime()
            }
            field = value
            prepare()
            scheduleFrames()
            invalidate()
        }

    /** Show a subtle checkerboard behind transparent artwork. Editor only — never export. */
    var drawChecker: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    /** Playhead position as normalised clip time (0..1). Writing seeks. */
    var normalizedTime: Float
        get() = clock.normalizedTime
        set(value) {
            clock.seekNormalized(value)
            publishFrame(force = true)
            invalidate()
        }

    /** Playback speed multiplier (0.25×–3×). */
    var speed: Float
        get() = clock.speed
        set(value) {
            clock.speed = value
        }

    /** Whether the clip repeats. Set from the clip's own `loop` flag. */
    var loop: Boolean
        get() = clock.loop
        set(value) {
            clock.loop = value
            invalidate()
        }

    val isPlaying: Boolean get() = clock.isPlaying

    /** Length of one cycle in wall-clock seconds at the current speed. */
    val cycleSeconds: Float get() = clock.cycleSeconds

    /** The pose drawn on the most recent frame, for the live expression/mouth readout. */
    var lastPose: Pose? = null
        private set

    /** Called at ~15 Hz (and on every seek/finish) with the current playhead. */
    var onFrame: ((normalizedTime: Float, isPlaying: Boolean) -> Unit)? = null

    /** Called once when a non-looping clip reaches its final frame. */
    var onFinished: (() -> Unit)? = null

    /** Called on a single tap on the stage (toggle playback). Double-tap resets the camera. */
    var onTap: (() -> Unit)? = null

    private val clock = PlaybackClock()
    private var prepared: PreparedStage? = null
    private var callbackPosted = false
    private var lastPublishMillis = 0L

    // --- V5 crossfade state (clip-to-clip transitions) ---------------------------------------
    private var outgoingStage: PreparedStage? = null
    private var outgoingPlayhead = 0f
    private var outgoingSwitchNanos = 0L

    init {
        // The view is interactive: pinch/drag/double-tap drive the user camera (V4 §32).
        isClickable = true
        setWillNotDraw(false)
    }

    fun play() {
        clock.play()
        scheduleFrames()
        publishFrame(force = true)
    }

    fun pause() {
        clock.pause()
        publishFrame(force = true)
    }

    fun toggle() {
        if (clock.isPlaying) pause() else play()
    }

    fun restart() {
        clock.restart()
        scheduleFrames()
        publishFrame(force = true)
        invalidate()
    }

    /** Pause and rewind to the first frame (V4 §29 transport: stop). */
    fun stop() {
        normalizedTime = 0f
        clock.pause()
        publishFrame(force = true)
        invalidate()
    }

    /**
     * Points the clock at a different clip while keeping the proportional playhead position, which
     * is what makes flipping between Idle and Walk feel continuous instead of jarring.
     */
    fun retarget(durationSeconds: Float, speed: Float = clock.speed, loop: Boolean = clock.loop) {
        val wasPlaying = clock.isPlaying
        clock.retarget(durationSeconds, speed, loop)
        if (wasPlaying) scheduleFrames()
        publishFrame(force = true)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        prepare()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val stage = prepared
        if (stage == null) {
            canvas.drawColor(FALLBACK_BACKGROUND)
            return
        }

        // V5 §41: for ~220 ms after a clip switch, blend the outgoing clip (still advancing)
        // into the new one in pose space — no snapping between animations, ever.
        var pose = stage.samplePose(clock.normalizedTime)
        val outgoing = outgoingStage
        if (outgoing != null) {
            val elapsed = (System.nanoTime() - outgoingSwitchNanos) / 1_000_000_000f
            if (elapsed >= CROSSFADE_SECONDS) {
                outgoingStage = null
            } else {
                val outgoingT = (outgoingPlayhead + elapsed / outgoing.cycleSeconds
                    .coerceAtLeast(0.01f)).mod(1f)
                val alpha = (elapsed / CROSSFADE_SECONDS).coerceIn(0f, 1f)
                pose = AnimationEngine.blend(outgoing.samplePose(outgoingT), pose, alpha)
            }
        }

        val saveCount = canvas.save()
        if (userZoom != 1f || userPanX != 0f || userPanY != 0f) {
            // Screen-space camera on top of the solved framing (V4 §32). Pivot-scale so the
            // zoom is centred on the viewport; the rig's coordinates are never touched.
            canvas.translate(width * 0.5f, height * 0.5f)
            canvas.scale(userZoom, userZoom)
            canvas.translate(-width * 0.5f + userPanX, -height * 0.5f + userPanY)
        }
        lastPose = pose
        stage.paintPose(canvas, pose, drawChecker)
        canvas.restoreToCount(saveCount)
        if (debugOverlay) {
            drawDebugOverlay(canvas, stage)
        }
    }

    // --- user camera (V4 §32): pinch to zoom, drag to pan, double-tap to reset ---------------

    /** Zoom multiplier applied on top of the auto framing (1 = solved camera, unchanged). */
    var userZoom: Float = 1f
        private set

    /** Pan offset in view pixels applied after the zoom. */
    var userPanX: Float = 0f
        private set
    var userPanY: Float = 0f
        private set

    /** True once the user has moved the camera away from the auto framing. */
    val cameraMoved: Boolean get() = userZoom != 1f || userPanX != 0f || userPanY != 0f

    /** Resets zoom and pan back to the solved auto-framing (V4 §32 "reset / center"). */
    fun resetCamera() {
        userZoom = 1f
        userPanX = 0f
        userPanY = 0f
        activePointerId = -1
        invalidate()
    }

    /** Hidden developer view (V4 §52): sprite bounds, pivots, z-order badges and FPS. */
    var debugOverlay: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private var activePointerId = -1
    private var lastDragX = 0f
    private var lastDragY = 0f
    private var pinchStartDistance = 0f
    private var pinchStartZoom = 1f
    private var lastTapUpMillis = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var pendingTap: Runnable? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                lastDragX = event.x
                lastDragY = event.y
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    pinchStartDistance = pointerDistance(event)
                    pinchStartZoom = userZoom
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2 && pinchStartDistance > 0f) {
                    val distance = pointerDistance(event)
                    if (distance > 0f) {
                        userZoom = (pinchStartZoom * distance / pinchStartDistance)
                            .coerceIn(MIN_USER_ZOOM, MAX_USER_ZOOM)
                        clampPan()
                        invalidate()
                    }
                    return true
                }
                val index = event.findPointerIndex(activePointerId)
                if (index >= 0) {
                    val x = event.getX(index)
                    val y = event.getY(index)
                    userPanX += x - lastDragX
                    userPanY += y - lastDragY
                    lastDragX = x
                    lastDragY = y
                    clampPan()
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                pinchStartDistance = 0f
                // Keep dragging with whichever pointer is still down.
                val remaining = if (event.getPointerId(0) == event.getPointerId(event.pointerCount - 1)) 0
                else event.pointerCount - 1
                activePointerId = event.getPointerId(remaining)
                val idx = remaining.coerceAtMost(event.pointerCount - 1)
                lastDragX = event.getX(idx)
                lastDragY = event.getY(idx)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val now = event.eventTime
                val isDoubleTap = now - lastTapUpMillis <= DOUBLE_TAP_MILLIS &&
                    kotlin.math.abs(event.x - lastTapX) < DOUBLE_TAP_SLOP &&
                    kotlin.math.abs(event.y - lastTapY) < DOUBLE_TAP_SLOP
                lastTapUpMillis = now
                lastTapX = event.x
                lastTapY = event.y
                pinchStartDistance = 0f
                activePointerId = -1
                if (isDoubleTap) {
                    // A double-tap cancels the pending single-tap and resets the camera.
                    pendingTap?.let { removeCallbacks(it) }
                    pendingTap = null
                    if (cameraMoved) resetCamera()
                } else {
                    val tap = Runnable {
                        pendingTap = null
                        performClick()
                        onTap?.invoke()
                    }
                    pendingTap = tap
                    postDelayed(tap, DOUBLE_TAP_MILLIS)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pinchStartDistance = 0f
                activePointerId = -1
                return true
            }
        }
        return false
    }

    private fun pointerDistance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /** Keeps the stage under the user's finger from disappearing off-screen. */
    private fun clampPan() {
        val maxX = width * (userZoom - 1f) * 0.5f + width * 0.25f
        val maxY = height * (userZoom - 1f) * 0.5f + height * 0.25f
        userPanX = userPanX.coerceIn(-maxX, maxX)
        userPanY = userPanY.coerceIn(-maxY, maxY)
    }

    // --- debug overlay painting (V4 §52) ------------------------------------------------------

    /** Smoothed frames-per-second of the preview loop, refreshed every ~500 ms. */
    var debugFps: Float = 0f
        private set

    private var lastFrameNanos = 0L
    private var fpsSinceSample = 0
    private var fpsSampleNanos = 0L

    private val boundsPaint by lazy {
        Paint().apply { style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true }
    }
    private val pivotPaint by lazy {
        Paint().apply { color = 0xFF3DDC84.toInt(); isAntiAlias = true }
    }
    private val badgePaint by lazy {
        Paint().apply { color = 0xCC12161C.toInt(); isAntiAlias = true; textAlign = Paint.Align.CENTER }
    }
    private val badgeTextPaint by lazy {
        Paint().apply { color = 0xFF3DDC84.toInt(); isAntiAlias = true; textSize = 22f; textAlign = Paint.Align.CENTER }
    }
    private val hudPaint by lazy {
        Paint().apply { color = 0xFF3DDC84.toInt(); isAntiAlias = true; textSize = 30f }
    }

    private fun drawDebugOverlay(canvas: Canvas, stage: PreparedStage) {
        val cx = width * 0.5f
        val cy = height * 0.5f
        for (draw in stage.lastDraws) {
            val a = mapThroughUserCamera(draw.world.transform(draw.restRect.left, draw.restRect.top), cx, cy)
            val b = mapThroughUserCamera(draw.world.transform(draw.restRect.right, draw.restRect.top), cx, cy)
            val c = mapThroughUserCamera(draw.world.transform(draw.restRect.right, draw.restRect.bottom), cx, cy)
            val d = mapThroughUserCamera(draw.world.transform(draw.restRect.left, draw.restRect.bottom), cx, cy)
            boundsPaint.color = if (draw.shade < 1f) 0x80FFC107.toInt() else 0x803DDC84.toInt()
            canvas.drawLine(a.x, a.y, b.x, b.y, boundsPaint)
            canvas.drawLine(b.x, b.y, c.x, c.y, boundsPaint)
            canvas.drawLine(c.x, c.y, d.x, d.y, boundsPaint)
            canvas.drawLine(d.x, d.y, a.x, a.y, boundsPaint)

            val pivot = mapThroughUserCamera(
                draw.world.transform(
                    draw.restRect.left + draw.sprite.pivot.x * draw.restRect.width,
                    draw.restRect.top + draw.sprite.pivot.y * draw.restRect.height,
                ),
                cx, cy,
            )
            canvas.drawCircle(pivot.x, pivot.y, 5f, pivotPaint)

            canvas.drawCircle(a.x + 18f, a.y + 18f, 16f, badgePaint)
            canvas.drawText("${draw.z}", a.x + 18f, a.y + 25f, badgeTextPaint)
        }
        canvas.drawText("fps %.1f".format(debugFps), 18f, height - 18f, hudPaint)
    }

    private fun mapThroughUserCamera(point: com.rigstudio.core.geom.Vec2, cx: Float, cy: Float): com.rigstudio.core.geom.Vec2 {
        if (!cameraMoved) return point
        return com.rigstudio.core.geom.Vec2(
            cx + (point.x - cx) * userZoom + userPanX,
            cy + (point.y - cy) * userZoom + userPanY,
        )
    }


    override fun doFrame(frameTimeNanos: Long) {
        callbackPosted = false
        updateFps(frameTimeNanos)
        val moved = clock.tick()
        if (moved) {
            invalidate()
            publishFrame()
        }
        if (!clock.isPlaying) {
            if (clock.isFinished) {
                invalidate()
                publishFrame(force = true)
                onFinished?.invoke()
            }
            // A crossfade still in flight must keep redrawing even while paused (V5 §41).
            if (outgoingStage != null) {
                invalidate()
                scheduleFrames()
            }
            return
        }
        scheduleFrames()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (clock.isPlaying) scheduleFrames()
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(this)
        callbackPosted = false
        pendingTap?.let { removeCallbacks(it) }
        pendingTap = null
        super.onDetachedFromWindow()
    }

    private fun updateFps(frameTimeNanos: Long) {
        if (lastFrameNanos != 0L && frameTimeNanos > lastFrameNanos) {
            fpsSinceSample++
            if (fpsSampleNanos == 0L) fpsSampleNanos = frameTimeNanos
            val elapsed = frameTimeNanos - fpsSampleNanos
            if (elapsed >= FPS_SAMPLE_WINDOW_NANOS && fpsSinceSample > 0) {
                debugFps = fpsSinceSample * 1_000_000_000f / elapsed
                fpsSinceSample = 0
                fpsSampleNanos = frameTimeNanos
                if (debugOverlay) invalidate()
            }
        }
        lastFrameNanos = frameTimeNanos
    }

    private fun scheduleFrames() {
        if (callbackPosted) return
        callbackPosted = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun publishFrame(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPublishMillis < PUBLISH_INTERVAL_MILLIS) return
        lastPublishMillis = now
        onFrame?.invoke(clock.normalizedTime, clock.isPlaying)
    }

    private fun prepare() {
        val source = stageSource
        if (source == null || width <= 0 || height <= 0) {
            prepared = null
            return
        }
        prepared = StageRenderer.DEFAULT.prepare(source, width, height)
        clock.durationSeconds = source.clip.durationSeconds
    }

    companion object {
        /** Throttle for playhead callbacks into Compose (~15 Hz reads as continuous). */
        const val PUBLISH_INTERVAL_MILLIS = 66L

        /** Painted when there is nothing to show yet. */
        const val FALLBACK_BACKGROUND = 0xFF12161C.toInt()

        /** V5 §41: clip-to-clip crossfade length in seconds (150–300 ms window). */
        const val CROSSFADE_SECONDS = AnimationEngine.DEFAULT_BLEND_SECONDS

        /** User camera limits (V4 §32): a screen-space zoom on top of the auto framing. */
        const val MIN_USER_ZOOM = 1f
        const val MAX_USER_ZOOM = 4f
        private const val DOUBLE_TAP_MILLIS = 300L
        private const val DOUBLE_TAP_SLOP = 40f
        private const val FPS_SAMPLE_WINDOW_NANOS = 500_000_000L
    }
}
