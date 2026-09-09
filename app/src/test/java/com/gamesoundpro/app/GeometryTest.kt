package com.gamesoundpro.app

import com.gamesoundpro.app.utils.Geometry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression tests for the overlay geometry crash: coercing into an inverted range used to
 * throw IllegalArgumentException and kill the overlay service mid-toggle (landscape games,
 * large overlay scale).
 */
class GeometryTest {

    @Test
    fun `safeCoerce tolerates inverted ranges`() {
        // panel (360) wider than screen (320) used to crash: coerceIn(0, -40)
        assertEquals(-40, Geometry.safeCoerce(-40, 0, -40))
        // inverted range is flattened: max becomes the upper bound
        assertEquals(0, Geometry.safeCoerce(320, -40, 0))
        assertEquals(5, Geometry.safeCoerce(5, 0, 10))
        assertEquals(10, Geometry.safeCoerce(99, 0, 10))
        assertEquals(0, Geometry.safeCoerce(-5, 0, 10))
    }

    @Test
    fun `coerceNonNegative never returns negative`() {
        assertEquals(0, Geometry.coerceNonNegative(-10, 0))
        assertEquals(0, Geometry.coerceNonNegative(50, 0))
        assertEquals(50, Geometry.coerceNonNegative(50, 500))
        assertEquals(500, Geometry.coerceNonNegative(50_000, 500))
    }

    @Test
    fun `clampPanelOrigin keeps panel fully on screen`() {
        // Panel fits: origin clamped to screen - size.
        assertEquals(240, Geometry.clampPanelOrigin(900, 80, 320))
        // Panel bigger than screen: pinned to 0, never negative, never throws.
        assertEquals(0, Geometry.clampPanelOrigin(400, 360, 320))
        assertEquals(0, Geometry.clampPanelOrigin(-999, 360, 320))
    }

    @Test
    fun `snapToNearestEdgeX picks the closer edge`() {
        // Screen 1080, icon 160, margin 8: left=8, right=912.
        assertEquals(8, Geometry.snapToNearestEdgeX(100, 160, 1080, 8))
        assertEquals(912, Geometry.snapToNearestEdgeX(900, 160, 1080, 8))
        // Midpoint rounds to the left edge (<=).
        assertEquals(8, Geometry.snapToNearestEdgeX(460, 160, 1080, 8))
        // Results are always valid positions.
        assertEquals(912, Geometry.snapToNearestEdgeX(5000, 160, 1080, 8))
    }

    @Test
    fun `normalized position survives portrait-landscape round trips`() {
        // Save in portrait (1080x2280 usable), restore in landscape (2280x1080 usable).
        val (fx, fy) = Geometry.normalizePosition(1000, 1500, 1080 - 160, 2280 - 160)
        val (lx, ly) = Geometry.denormalizePosition(fx, fy, 2280 - 160, 1080 - 160)
        // Result must be inside the landscape movable area, never off-screen.
        assertTrue(lx in 0..(2280 - 160))
        assertTrue(ly in 0..(1080 - 160))
    }

    @Test
    fun `denormalize clamps out-of-range fractions`() {
        assertEquals(0 to 0, Geometry.denormalizePosition(-2f, -0.5f, 1000, 1000))
        assertEquals(1000 to 1000, Geometry.denormalizePosition(3f, 1.5f, 1000, 1000))
    }

    @Test
    fun `normalize tolerates zero-sized ranges`() {
        val (fx, fy) = Geometry.normalizePosition(50, 50, 0, 0)
        assertEquals(0f, fx)
        assertEquals(0f, fy)
    }
}
