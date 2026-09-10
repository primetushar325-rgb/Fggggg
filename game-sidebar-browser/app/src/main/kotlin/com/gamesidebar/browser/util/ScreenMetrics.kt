package com.gamesidebar.browser.util

import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import android.os.Build
import android.view.WindowManager
import com.gamesidebar.core.geometry.Screen

/**
 * Screen metrics and system-window insets, converted into the core [Screen] value the geometry
 * maths expects.
 *
 * Overlay windows are laid out by WindowManager against the whole display, so the panel has to know
 * where the status bar, navigation bar, gesture pill and display cutout are - otherwise controls end
 * up underneath them in landscape or on a punch-hole device.
 */
object ScreenMetrics {

    fun density(context: Context): Float = context.resources.displayMetrics.density

    fun screen(context: Context): Screen {
        val windowManager = context.getSystemService(WindowManager::class.java)
            ?: return fallback(context)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout(),
            )
            Screen(
                widthPx = bounds.width(),
                heightPx = bounds.height(),
                insetLeftPx = insets.left,
                insetTopPx = insets.top,
                insetRightPx = insets.right,
                insetBottomPx = insets.bottom,
            )
        } else {
            legacyScreen(windowManager, context.resources)
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyScreen(windowManager: WindowManager, resources: Resources): Screen {
        val realSize = android.graphics.Point()
        windowManager.defaultDisplay.getRealSize(realSize)
        val usable = Rect()
        windowManager.defaultDisplay.getRectSize(usable)
        // getRectSize gives the app-usable area; the difference from the real size is the system
        // chrome. Split it by orientation so landscape nav bars land on the correct edge.
        val horizontalDelta = (realSize.x - usable.width()).coerceAtLeast(0)
        val verticalDelta = (realSize.y - usable.height()).coerceAtLeast(0)
        val landscape = realSize.x > realSize.y
        val statusBar = systemBarHeight(resources, "status_bar_height", fallbackPx = dp(24f, resources))
        val navBar = systemBarHeight(resources, "navigation_bar_height", fallbackPx = dp(48f, resources))
        return if (landscape) {
            Screen(
                widthPx = realSize.x,
                heightPx = realSize.y,
                insetLeftPx = horizontalDelta / 2,
                insetTopPx = 0,
                insetRightPx = horizontalDelta - horizontalDelta / 2,
                insetBottomPx = (verticalDelta - statusBar).coerceAtLeast(0),
            )
        } else {
            Screen(
                widthPx = realSize.x,
                heightPx = realSize.y,
                insetTopPx = statusBar.coerceAtMost(verticalDelta),
                insetBottomPx = (verticalDelta - statusBar).coerceAtLeast(0).let {
                    if (it == 0) navBar.coerceAtMost(verticalDelta) else it
                },
            )
        }
    }

    private fun systemBarHeight(resources: Resources, name: String, fallbackPx: Int): Int {
        val id = resources.getIdentifier(name, "dimen", "android")
        return if (id != 0) resources.getDimensionPixelSize(id) else fallbackPx
    }

    private fun dp(value: Float, resources: Resources): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun fallback(context: Context): Screen {
        val metrics = context.resources.displayMetrics
        return Screen(widthPx = metrics.widthPixels, heightPx = metrics.heightPixels)
    }
}
