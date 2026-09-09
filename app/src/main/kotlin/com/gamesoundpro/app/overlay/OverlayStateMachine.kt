package com.gamesoundpro.app.overlay

/**
 * Pure overlay UI state machine, completely decoupled from the Service, the windows and the
 * audio engine.
 *
 * The Service renders whichever state this machine is in (a reconciliation loop), so:
 *  - first tap opens the sidebar, second tap closes it, third opens it again — forever,
 *  - closing the sidebar only flips state; the service, the bubble, Gaming Mode and the
 *    AudioEngine are untouched,
 *  - if the system kills and restarts the service, a fresh machine starts at BUBBLE_ONLY —
 *    a clean, known state (no half-attached windows).
 *
 * Kept free of Android imports so the toggle logic is unit-testable (rapid-tap sequences).
 */
enum class OverlayUiState {
    /** Floating bubble visible, sidebar closed. */
    BUBBLE_ONLY,

    /** Floating bubble visible, compact sidebar open. */
    SIDEBAR_OPEN,
}

class OverlayStateMachine(initial: OverlayUiState = OverlayUiState.BUBBLE_ONLY) {

    var state: OverlayUiState = initial
        private set

    /** Total toggles performed — exposed for tests/diagnostics. */
    var toggleCount: Int = 0
        private set

    /** Flips BUBBLE_ONLY ↔ SIDEBAR_OPEN atomically and returns the new state. */
    fun toggle(): OverlayUiState {
        state = if (state == OverlayUiState.SIDEBAR_OPEN) OverlayUiState.BUBBLE_ONLY else OverlayUiState.SIDEBAR_OPEN
        toggleCount++
        return state
    }

    /**
     * Sets an explicit target. Returns true when the state actually changed (so the caller
     * can skip redundant window work).
     */
    fun set(open: Boolean): Boolean {
        val target = if (open) OverlayUiState.SIDEBAR_OPEN else OverlayUiState.BUBBLE_ONLY
        if (state == target) return false
        state = target
        toggleCount++
        return true
    }

    val sidebarOpen: Boolean get() = state == OverlayUiState.SIDEBAR_OPEN
}
