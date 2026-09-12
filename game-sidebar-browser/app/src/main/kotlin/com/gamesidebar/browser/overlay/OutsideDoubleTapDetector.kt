package com.gamesidebar.browser.overlay

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
    private var boundsLeft = 0
    private var boundsTop = 0
    private var boundsRight = 0
    private var boundsBottom = 0

    fun updateSidebarBounds(x: Int, y: Int, w: Int, h: Int) {
        boundsLeft = x
        boundsTop = y
        boundsRight = x + w
        boundsBottom = y + h
    }

    /**
     * Called when window receives ACTION_OUTSIDE (FLAG_WATCH_OUTSIDE_TOUCH).
     * ACTION_OUTSIDE has no reliable coordinates on some devices, so we treat any ACTION_OUTSIDE
     * as an outside tap and do double-tap timing check.
     * Returns true if collapse was triggered (second tap within timeout).
     */
    fun onOutsideTouch(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_OUTSIDE) return false
        val now = System.currentTimeMillis()
        val isDouble = now - lastOutsideTapTime < doubleTapTimeoutMs
        return if (isDouble) {
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
        val x = rawX.toInt()
        val y = rawY.toInt()
        val inside = x >= boundsLeft && x < boundsRight && y >= boundsTop && y < boundsBottom
        val outside = !inside
        if (!outside) return false
        val now = System.currentTimeMillis()
        val isDouble = now - lastOutsideTapTime < doubleTapTimeoutMs
        return if (isDouble) {
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
