package com.gamesidebar.util;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;
import android.view.WindowMetrics;

/**
 * FIX landscape/Free Fire mode: Use dynamic window width/height, insets, orientation.
 * Never hardcoded dimensions. Clamp sidebar to safe bounds.
 * Portrait behavior unchanged.
 */
public class LandscapeHelper {

    public static class SafeBounds {
        public int availW, availH;
        public int insetLeft, insetTop, insetRight, insetBottom;
        public boolean isLandscape;
    }

    public static SafeBounds getSafeBounds(Context ctx) {
        SafeBounds b = new SafeBounds();
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        Rect maxBounds = new Rect();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics m = wm.getCurrentWindowMetrics();
            maxBounds.set(m.getBounds());
            // insets from WindowMetrics#getWindowInsets not needed for TYPE_APPLICATION_OVERLAY
            // we clamp with a small margin to avoid cutout/nav bar
            android.graphics.Insets insets = m.getWindowInsets().getInsetsIgnoringVisibility(
                    android.view.WindowInsets.Type.statusBars() | android.view.WindowInsets.Type.navigationBars() | android.view.WindowInsets.Type.displayCutout());
            b.insetLeft = insets.left;
            b.insetTop = insets.top;
            b.insetRight = insets.right;
            b.insetBottom = insets.bottom;
            b.availW = maxBounds.width() - b.insetLeft - b.insetRight;
            b.availH = maxBounds.height() - b.insetTop - b.insetBottom;
        } else {
            Display d = wm.getDefaultDisplay();
            Point size = new Point();
            d.getRealSize(size);
            b.availW = size.x;
            b.availH = size.y;
            // Legacy insets approximation
            DisplayMetrics dm = new DisplayMetrics();
            d.getMetrics(dm);
            b.insetLeft = 0; b.insetRight = 0;
            b.insetTop = getStatusBarHeight(ctx);
            b.insetBottom = b.availH - dm.heightPixels;
            if (b.insetBottom < 0) b.insetBottom = 0;
        }
        b.isLandscape = b.availW > b.availH;
        return b;
    }

    private static int getStatusBarHeight(Context ctx) {
        int id = ctx.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? ctx.getResources().getDimensionPixelSize(id) : 0;
    }

    /**
     * Clamp sidebar to safe bounds with min/max.
     */
    public static int[] clamp(int x, int y, int w, int h, SafeBounds bounds, int minW, int minH) {
        // Max = current available screen width/height
        int maxW = bounds.availW - 16; // 8dp margin each side
        int maxH = bounds.availH - 16;
        w = Math.max(minW, Math.min(w, maxW));
        h = Math.max(minH, Math.min(h, maxH));

        // Ensure not mostly off-screen: clamp position so at least 80% visible
        int minVisible = 40; // px
        x = Math.max(bounds.insetLeft - w + minVisible, Math.min(x, bounds.insetLeft + bounds.availW - minVisible));
        y = Math.max(bounds.insetTop - h + minVisible, Math.min(y, bounds.insetTop + bounds.availH - minVisible));

        // Additional: ensure fully within safe area if possible (prefer fully visible)
        if (w <= bounds.availW) {
            x = Math.max(bounds.insetLeft, Math.min(x, bounds.insetLeft + bounds.availW - w));
        }
        if (h <= bounds.availH) {
            y = Math.max(bounds.insetTop, Math.min(y, bounds.insetTop + bounds.availH - h));
        }
        return new int[]{x, y, w, h};
    }

    public static boolean isLandscape(Context ctx) {
        return getSafeBounds(ctx).isLandscape;
    }
}
