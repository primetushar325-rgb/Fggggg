package com.gamesoundpro.app

import com.gamesoundpro.app.overlay.OverlayStateMachine
import com.gamesoundpro.app.overlay.OverlayUiState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression tests for the floating-sidebar toggle bug: first tap opens, second closes,
 * third opens again — and the state machine always stays consistent.
 */
class OverlayStateMachineTest {

    @Test
    fun `first tap opens sidebar`() {
        val machine = OverlayStateMachine()
        assertEquals(OverlayUiState.BUBBLE_ONLY, machine.state)
        machine.toggle()
        assertEquals(OverlayUiState.SIDEBAR_OPEN, machine.state)
    }

    @Test
    fun `second tap closes sidebar`() {
        val machine = OverlayStateMachine()
        machine.toggle()
        machine.toggle()
        assertEquals(OverlayUiState.BUBBLE_ONLY, machine.state)
    }

    @Test
    fun `third tap opens sidebar again`() {
        val machine = OverlayStateMachine()
        machine.toggle(); machine.toggle(); machine.toggle()
        assertEquals(OverlayUiState.SIDEBAR_OPEN, machine.state)
    }

    @Test
    fun `50 rapid toggles alternate correctly`() {
        val machine = OverlayStateMachine()
        repeat(50) { index ->
            val expected = if (index % 2 == 0) OverlayUiState.SIDEBAR_OPEN else OverlayUiState.BUBBLE_ONLY
            assertEquals("toggle #$index", expected, machine.toggle())
        }
        assertEquals(50, machine.toggleCount)
    }

    @Test
    fun `set is idempotent and reports changes`() {
        val machine = OverlayStateMachine()
        assertEquals(true, machine.set(true))
        assertEquals(false, machine.set(true)) // already open — no change, no toggle counted
        assertEquals(true, machine.set(false))
        assertEquals(2, machine.toggleCount)
        assertEquals(OverlayUiState.BUBBLE_ONLY, machine.state)
    }

    @Test
    fun `fresh machine after process death starts closed`() {
        val machine = OverlayStateMachine()
        assertEquals(OverlayUiState.BUBBLE_ONLY, machine.state)
        assertEquals(false, machine.sidebarOpen)
    }
}
