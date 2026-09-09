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
        assertEquals(320, Geometry.safeCoerce(320, -40, 0))
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
}
