package com.gamesidebar.browser.overlay

import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.WindowManager

/**
 * BUG #2 FIX: Double-tap OUTSIDE sidebar to collapse — safety first.
 *
 * - No full-screen transparent view (would block Free Fire touch)
 * - Uses FLAG_WATCH_OUTSIDE_TOUCH which delivers ACTION_OUTSIDE without consuming game touch
 * - Single tap outside => NOTHING (returns false, game receives it)
 * - Double tap outside (both taps outside bounds, within doubleTapTimeout) => collapse
 * - Double tap inside sidebar / WebView => normal webpage interaction
 * - Uses short timeout (300ms = ViewConfiguration.getDoubleTapTimeout()) — not extremely long
 * - Does NOT intercept long press / swipe / drag
 */
class OutsideDoubleTapDetector(
    private val onDoubleTapOutside: () -> Unit,
) {
    private var lastOutsideTapTime = 0L
    private val doubleTapTimeoutMs = 300L // ViewConfiguration.getDoubleTapTimeout()
    private val sidebarBounds = Rect()

    fun updateSidebarBounds(x: Int, y: Int, w: Int, h: Int) {
        sidebarBounds.set(x, y, x + w, y + h)
    }

    /**
     * Called when window receives ACTION_OUTSIDE (FLAG_WATCH_OUTSIDE_TOUCH).
     * ACTION_OUTSIDE has no reliable coordinates on some devices, so we treat any ACTION_OUTSIDE
     * as an outside tap and do double-tap timing check.
     * Returns true if collapse was triggered (second tap within timeout).
     */
    fun onOutsideTouch(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_OUTSIDE) return false
        val now = SystemClock.uptimeMillis()
        return if (now - lastOutsideTapTime < doubleTapTimeoutMs) {
            lastOutsideTapTime = 0L
            onDoubleTapOutside()
            true
        } else {
            lastOutsideTapTime = now
            false // single outside tap => do nothing, game still gets it
        }
    }

    /**
     * Alternative when raw coordinates are available: explicitly check outside bounds.
     * Used as fallback if ACTION_OUTSIDE not delivered.
     */
    fun onTouchMaybeOutside(rawX: Float, rawY: Float): Boolean {
        val outside = !sidebarBounds.contains(rawX.toInt(), rawY.toInt())
        if (!outside) return false
        val now = SystemClock.uptimeMillis()
        return if (now - lastOutsideTapTime < doubleTapTimeoutMs) {
            lastOutsideTapTime = 0L
            onDoubleTapOutside()
            true
        } else {
            lastOutsideTapTime = now
            false
        }
    }

    companion object {
        fun applyOutsideParams(p: WindowManager.LayoutParams) {
            p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        }
    }
}
