package com.gamesoundpro.app.utils

/**
 * Window-geometry helpers. [Int.coerceIn] throws IllegalArgumentException when the range is
 * inverted — which happened in the V1 overlay when the sidebar panel was wider/taller than
 * the screen (landscape games, high overlay scale) and crashed the service mid-toggle.
 * Every overlay geometry computation must go through these safe variants.
 */
object Geometry {

    /** Coerces [value] into [lo]..[hi], tolerating lo > hi (uses the smaller as lower bound). */
    fun safeCoerce(value: Int, lo: Int, hi: Int): Int {
        val min = minOf(lo, hi)
        val max = maxOf(lo, hi)
        return value.coerceIn(min, max)
    }

    /** Coerces into 0..[hi] with hi clamped to be >= 0, so the result is never negative. */
    fun coerceNonNegative(value: Int, hi: Int): Int =
        safeCoerce(value, 0, hi.coerceAtLeast(0))

    /** Clamps a panel origin so a [size] panel stays fully on a [screen] long edge. */
    fun clampPanelOrigin(preferred: Int, size: Int, screen: Int): Int {
        if (size >= screen) return 0
        return preferred.coerceIn(0, screen - size)
    }

    // ---------------------------------------------------------------------------------
    // Floating-icon position: snap-to-edge + orientation-safe normalized persistence
    // ---------------------------------------------------------------------------------

    /**
     * Nearest horizontal edge for an icon at [x] (icon width [iconW]) — used to snap the
     * icon to the left or right screen edge (with [margin]) after a drag, like the
     * standard assistant bubbles.
     */
    fun snapToNearestEdgeX(x: Int, iconW: Int, screenW: Int, margin: Int): Int {
        val maxX = (screenW - iconW - margin).coerceAtLeast(margin)
        val left = margin
        val right = maxX
        return if (Math.abs(x - left) <= Math.abs(right - x)) left else right
    }

    /**
     * Converts an absolute position into normalized fractions of the movable range
     * (0..1 on each axis). Fractions survive portrait/landscape switches and different
     * aspect ratios; absolute pixels do not (the V1 restore bug).
     */
    fun normalizePosition(x: Int, y: Int, availW: Int, availH: Int): Pair<Float, Float> {
        val fx = if (availW <= 0) 0f else (x.toFloat() / availW).coerceIn(0f, 1f)
        val fy = if (availH <= 0) 0f else (y.toFloat() / availH).coerceIn(0f, 1f)
        return fx to fy
    }

    /** Inverse of [normalizePosition]; result is always clamped inside 0..avail. */
    fun denormalizePosition(fx: Float, fy: Float, availW: Int, availH: Int): Pair<Int, Int> {
        val x = ((fx.coerceIn(0f, 1f)) * availW).toInt().coerceIn(0, availW.coerceAtLeast(0))
        val y = ((fy.coerceIn(0f, 1f)) * availH).toInt().coerceIn(0, availH.coerceAtLeast(0))
        return x to y
    }
}
