package com.gamesidebar.util;

import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

/**
 * Handle status bar, navigation bar, cutout correctly.
 * Do not create giant blank top area. Do not place controls under system bars.
 */
public class InsetsHelper {
    public static void applySafeInsets(View header, View browserToolbar) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            header.setOnApplyWindowInsetsListener((v, insets) -> {
                int top = insets.getInsets(WindowInsets.Type.statusBars() | WindowInsets.Type.displayCutout()).top;
                // Only pad if needed, small compact padding
                v.setPadding(v.getPaddingLeft(), Math.min(top, 12), v.getPaddingRight(), v.getPaddingBottom());
                return WindowInsets.CONSUMED;
            });
        }
    }
}
