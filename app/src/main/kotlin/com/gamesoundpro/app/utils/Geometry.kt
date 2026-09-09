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
}
