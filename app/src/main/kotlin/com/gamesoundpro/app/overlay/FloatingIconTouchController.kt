package com.gamesoundpro.app.overlay

import com.gamesoundpro.app.utils.DebugLog

/**
 * Tap-vs-drag gesture classifier for the floating icon (V3 BUG #1 fix).
 *
 * V1 used a fixed 10 px threshold that ignored screen density and the platform touch slop,
 * so taps were misclassified as drags (or the reverse) depending on the device. This
 * controller:
 *  - is constructed with ViewConfiguration's scaledTouchSlop (density + vendor correct),
 *  - runs an explicit state machine: IDLE -> TRACKING (finger down) -> DRAGGING (threshold
 *    crossed) -> terminal (finger up/cancel),
 *  - guarantees mutual exclusion: once DRAGGING, the tap callback can NEVER fire for that
 *    gesture, and while still TRACKING below the threshold nothing moves,
 *  - exposes [feed] with plain primitives (constants mirror MotionEvent actions) so the
 *    classification rules are unit-testable on the JVM.
 */
class FloatingIconTouchController(
    private val touchSlopPx: Int,
    private val callbacks: Callbacks,
) {

    interface Callbacks {
        /** Finger went down on the icon (every gesture — capture the window origin here). */
        fun onGestureDown(x: Int, y: Int)

        /** Icon should move to the given raw coordinates (threshold already passed). */
        fun onDragMoved(x: Int, y: Int)

        /** A DRAG finished (never fired for taps); position is final (snap/save in service). */
        fun onDragEnd()

        /** A clean tap (never exceeded the slop on either axis); mutually exclusive with drags. */
        fun onTap()
    }

    enum class GestureState { IDLE, TRACKING, DRAGGING }

    companion object {
        // MotionEvent action constants (duplicated so the classifier needs no Android classes).
        const val ACTION_DOWN = 0
        const val ACTION_MOVE = 2
        const val ACTION_UP = 1
        const val ACTION_CANCEL = 3
    }

    var state: GestureState = GestureState.IDLE
        private set

    private var downRawX = 0f
    private var downRawY = 0f
    private var maxMovement = 0f

    val isDragging: Boolean get() = state == GestureState.DRAGGING

    /**
     * Feeds one touch event. [action] is a MotionEvent actionMasked value; [rawX]/[rawY]
     * are screen-absolute coordinates. Returns true when the event belongs to this gesture.
     */
    fun feed(action: Int, rawX: Float, rawY: Float): Boolean {
        when (action) {
            ACTION_DOWN -> {
                downRawX = rawX
                downRawY = rawY
                maxMovement = 0f
                state = GestureState.TRACKING
                DebugLog.d("Drag", "START at ${rawX.toInt()},${rawY.toInt()}")
                callbacks.onGestureDown(rawX.toInt(), rawY.toInt())
                return true
            }

            ACTION_MOVE -> {
                if (state == GestureState.IDLE) return false
                val dx = rawX - downRawX
                val dy = rawY - downRawY
                val movement = maxOf(Math.abs(dx), Math.abs(dy))
                if (state == GestureState.TRACKING && movement > touchSlopPx) {
                    state = GestureState.DRAGGING
                    DebugLog.d("Drag", "threshold crossed (${movement.toInt()}px > ${touchSlopPx}px) — gesture is a DRAG")
                }
                if (state == GestureState.DRAGGING) {
                    maxMovement = maxOf(maxMovement, movement)
                    callbacks.onDragMoved(rawX.toInt(), rawY.toInt())
                    DebugLog.d("Drag", "MOVING x=${rawX.toInt()} y=${rawY.toInt()}")
                }
                return true
            }

            ACTION_UP -> {
                when (state) {
                    GestureState.DRAGGING -> {
                        DebugLog.d("Drag", "END (moved up to ${maxMovement.toInt()}px) — no click")
                        state = GestureState.IDLE
                        callbacks.onDragEnd()
                    }
                    GestureState.TRACKING -> {
                        DebugLog.d("Click", "TAP detected (movement ${maxMovement.toInt()}px <= slop ${touchSlopPx}px)")
                        state = GestureState.IDLE
                        callbacks.onTap()
                    }
                    GestureState.IDLE -> return false
                }
                return true
            }

            ACTION_CANCEL -> {
                if (state != GestureState.IDLE) {
                    DebugLog.d("Drag", "CANCEL — gesture discarded (no click, no drag end)")
                    state = GestureState.IDLE
                }
                return true
            }

            else -> return false
        }
    }
}
