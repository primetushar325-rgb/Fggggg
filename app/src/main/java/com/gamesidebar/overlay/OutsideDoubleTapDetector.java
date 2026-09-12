package com.gamesidebar.overlay;

import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * Double-tap outside sidebar to collapse.
 * IMPORTANT: Must NOT block normal game interaction.
 * Use very short double-tap timeout, only when BOTH taps outside bounds.
 * Do NOT intercept single taps. Do NOT interfere with Free Fire controls.
 * If unsafe, prioritize game passthrough and fallback to handle.
 *
 * Implementation: We do NOT create full-screen touch layer.
 * Instead, we rely on WindowManager outside touch detection via FLAG_WATCH_OUTSIDE_TOUCH
 * and verify taps are outside sidebar bounds with timing.
 */
public class OutsideDoubleTapDetector {

    private static final long DOUBLE_TAP_TIMEOUT_MS = 300; // very short
    private long lastOutsideTapTime = 0;
    private float lastX, lastY;

    private final Rect sidebarBounds = new Rect();
    private final Runnable onDoubleTapOutside;

    public OutsideDoubleTapDetector(Runnable onDoubleTapOutside) {
        this.onDoubleTapOutside = onDoubleTapOutside;
    }

    public void updateSidebarBounds(int x, int y, int w, int h) {
        sidebarBounds.set(x, y, x + w, y + h);
    }

    /**
     * Called when we receive an outside touch (ACTION_OUTSIDE).
     * Only triggers collapse when second tap occurs within timeout and outside bounds.
     */
    public boolean onOutsideTouch(MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_OUTSIDE) return false;
        // ACTION_OUTSIDE has no coordinates on some devices, so we use raw if available
        // For safety, we treat any ACTION_OUTSIDE as potential outside tap
        long now = SystemClock.uptimeMillis();
        // Verify both taps outside: since this event is outside, we count it
        if (now - lastOutsideTapTime < DOUBLE_TAP_TIMEOUT_MS) {
            // Double tap outside -> collapse
            lastOutsideTapTime = 0;
            if (onDoubleTapOutside != null) onDoubleTapOutside.run();
            return true;
        } else {
            lastOutsideTapTime = now;
        }
        return false;
    }

    /**
     * Alternative for devices where we get raw coordinates: check explicitly outside bounds.
     */
    public boolean onTouchMaybeOutside(float rawX, float rawY) {
        boolean outside = !sidebarBounds.contains((int) rawX, (int) rawY);
        if (!outside) return false;
        long now = SystemClock.uptimeMillis();
        if (now - lastOutsideTapTime < DOUBLE_TAP_TIMEOUT_MS) {
            lastOutsideTapTime = 0;
            if (onDoubleTapOutside != null) onDoubleTapOutside.run();
            return true;
        }
        lastOutsideTapTime = now;
        return false;
    }

    /**
     * Setup window to allow outside detection WITHOUT blocking game.
     * FLAG_NOT_TOUCH_MODAL ensures outside touches go to game.
     * FLAG_WATCH_OUTSIDE_TOUCH ensures we get ACTION_OUTSIDE without consuming.
     */
    public static void applyOutsideParams(WindowManager.LayoutParams p) {
        p.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        // Ensure NOT using FLAG_NOT_FOCUSABLE alone that would block; keep touch modal off
        // Do NOT add FLAG_NOT_TOUCHABLE
    }
}
