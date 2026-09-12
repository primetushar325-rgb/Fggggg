package com.gamesidebar.overlay;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.gamesidebar.R;
import com.gamesidebar.util.LandscapeHelper;
import com.gamesidebar.util.PrefsManager;

/**
 * Small unobtrusive floating handle when collapsed.
 * Stay within bounds, not covering important game controls unnecessarily.
 * Tap -> reopen sidebar restoring previous state.
 */
public class CollapseHandleView {

    private final Context ctx;
    private final WindowManager wm;
    private final PrefsManager prefs;
    private final OverlayManager overlayManager;
    private View handleView;
    private WindowManager.LayoutParams params;
    private boolean isShowing = false;

    private int touchDownX, touchDownY;
    private int initialX, initialY;
    private boolean isDragging = false;

    public CollapseHandleView(Context ctx, WindowManager wm, PrefsManager prefs, OverlayManager overlayManager) {
        this.ctx = ctx;
        this.wm = wm;
        this.prefs = prefs;
        this.overlayManager = overlayManager;
    }

    public void show() {
        if (isShowing) return;
        handleView = View.inflate(ctx, R.layout.view_collapse_handle, null);
        int size = ctx.getResources().getDimensionPixelSize(R.dimen.handle_size);
        params = new WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        // Restore or default to right edge, center vertical
        LandscapeHelper.SafeBounds bounds = LandscapeHelper.getSafeBounds(ctx);
        int defaultX = bounds.insetLeft + bounds.availW - size - 8;
        int defaultY = bounds.insetTop + bounds.availH / 2;
        params.x = prefs.getX(defaultX); // reuse X but for handle we clamp separately
        params.y = prefs.getY(defaultY);
        // Clamp handle within screen
        clampHandle(bounds);

        handleView.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    touchDownX = (int) event.getRawX();
                    touchDownY = (int) event.getRawY();
                    initialX = params.x;
                    initialY = params.y;
                    isDragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int) event.getRawX() - touchDownX;
                    int dy = (int) event.getRawY() - touchDownY;
                    if (!isDragging && (Math.abs(dx) > 10 || Math.abs(dy) > 10)) {
                        isDragging = true;
                    }
                    if (isDragging) {
                        params.x = initialX + dx;
                        params.y = initialY + dy;
                        clampHandle(LandscapeHelper.getSafeBounds(ctx));
                        wm.updateViewLayout(handleView, params);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!isDragging) {
                        // Tap -> reopen
                        overlayManager.expand();
                    } else {
                        // Save handle position in prefs via overlay manager
                        prefs.saveBounds(params.x, params.y, 0, 0); // only position matters for handle
                    }
                    return true;
            }
            return false;
        });

        try {
            wm.addView(handleView, params);
            isShowing = true;
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void clampHandle(LandscapeHelper.SafeBounds bounds) {
        int size = ctx.getResources().getDimensionPixelSize(R.dimen.handle_size);
        params.x = Math.max(bounds.insetLeft, Math.min(params.x, bounds.insetLeft + bounds.availW - size));
        params.y = Math.max(bounds.insetTop, Math.min(params.y, bounds.insetTop + bounds.availH - size));
    }

    public void hide() {
        if (!isShowing || handleView == null) return;
        try { wm.removeView(handleView); } catch (Exception ignored) {}
        isShowing = false;
        handleView = null;
    }

    public boolean isShowing() { return isShowing; }
}
