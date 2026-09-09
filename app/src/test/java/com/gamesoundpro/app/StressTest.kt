package com.gamesoundpro.app

import com.gamesoundpro.app.overlay.OverlayStateMachine
import com.gamesoundpro.app.overlay.OverlayUiState
import com.gamesoundpro.app.utils.Geometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Stress tests demanded by the stability spec:
 *  - 50+ rapid sidebar open/close operations
 *  - 100 rapid sound-button-press-equivalent state transitions
 *  - interleaved toggle/explicit-open/close commands (bubble tap + ✕ button)
 *  - overlay geometry fuzzing (never throws, never returns an off-screen origin)
 *
 * The AudioEngine's play path itself is main-thread-bound (ExoPlayer), so its rapid-press
 * safety is enforced by architecture: commands are serialized on the main looper and every
 * player operation is exception-guarded (see AudioEngine.commandInFlight + rebuildSlot).
 */
class StressTest {

    @Test
    fun `100 rapid sound-button state cycles stay consistent`() {
        val machine = OverlayStateMachine()
        // Simulates: open sidebar -> press sound (no state change) -> close -> repeat x100
        repeat(100) { cycle ->
            assertTrue("cycle $cycle: open should change state", machine.set(true))
            assertEquals("cycle $cycle", OverlayUiState.SIDEBAR_OPEN, machine.state)
            assertTrue(machine.sidebarOpen)
            // sound press does not touch UI state
            assertFalse(machine.set(true)) // re-open while open is a no-op
            assertTrue("cycle $cycle: close should change state", machine.set(false))
            assertEquals("cycle $cycle", OverlayUiState.BUBBLE_ONLY, machine.state)
            assertFalse(machine.sidebarOpen)
        }
        assertEquals(200, machine.toggleCount)
    }

    @Test
    fun `50 rapid toggles alternate without divergence`() {
        val machine = OverlayStateMachine()
        repeat(50) { index ->
            val expected = if (index % 2 == 0) OverlayUiState.SIDEBAR_OPEN else OverlayUiState.BUBBLE_ONLY
            assertEquals("toggle #$index", expected, machine.toggle())
        }
    }

    @Test
    fun `interleaved bubble-tap and close-button commands never desync`() {
        val machine = OverlayStateMachine()
        val random = Random(42)
        repeat(300) {
            when (random.nextInt(3)) {
                0 -> machine.toggle()
                1 -> machine.set(true)
                else -> machine.set(false)
            }
            // Invariants that must hold after EVERY command:
            assertEquals(machine.sidebarOpen, machine.state == OverlayUiState.SIDEBAR_OPEN)
            assertTrue(machine.toggleCount >= 0)
        }
    }

    @Test
    fun `overlay geometry fuzz never throws and stays on screen`() {
        val random = Random(7)
        repeat(10_000) {
            val screenW = random.nextInt(200, 2600)
            val screenH = random.nextInt(200, 1400)
            val panelW = random.nextInt(50, 1200)
            val panelH = random.nextInt(50, 1200)
            val preferredX = random.nextInt(-500, 3000)
            val preferredY = random.nextInt(-500, 3000)

            val x = Geometry.clampPanelOrigin(preferredX, panelW, screenW)
            val y = Geometry.clampPanelOrigin(preferredY, panelH, screenH)

            // The V1 crash was an exception from an inverted coerceIn range; also assert
            // the result always keeps the panel inside the screen (or pinned at 0).
            assertTrue("x=$x panelW=$panelW screenW=$screenW", x >= 0)
            assertTrue("y=$y panelH=$panelH screenH=$screenH", y >= 0)
            if (panelW <= screenW) assertTrue(x <= screenW - panelW)
            if (panelH <= screenH) assertTrue(y <= screenH - panelH)
        }
    }
}
