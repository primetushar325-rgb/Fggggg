package com.gamesoundpro.app

import com.gamesoundpro.app.overlay.FloatingIconTouchController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V3 regression tests for the floating-icon drag/tap mixing bug: a drag must NEVER toggle
 * the sidebar, a tap must NEVER move the icon, and the classification must be mutualLy
 * exclusive per gesture.
 */
class FloatingIconGestureTest {

    private class RecordingCallbacks : FloatingIconTouchController.Callbacks {
        var taps = 0
        var dragStarts = 0
        var dragMoves = 0
        var dragEnds = 0
        var lastX = 0
        var lastY = 0

        override fun onDragStart(x: Int, y: Int) {
            dragStarts++; lastX = x; lastY = y
        }

        override fun onDragMoved(x: Int, y: Int) {
            dragMoves++; lastX = x; lastY = y
        }

        override fun onDragEnd() {
            dragEnds++
        }

        override fun onTap() {
            taps++
        }
    }

    private fun controller(slop: Int = 24, cb: RecordingCallbacks = RecordingCallbacks()) =
        cb to FloatingIconTouchController(slop, cb)

    @Test
    fun `tap below slop toggles sidebar exactly once`() {
        val (cb, c) = controller(slop = 24)
        c.feed(FloatingIconTouchController.ACTION_DOWN, 100f, 100f)
        c.feed(FloatingIconTouchController.ACTION_MOVE, 105f, 103f)   // 5px jitter < 24
        c.feed(FloatingIconTouchController.ACTION_UP, 105f, 103f)
        assertEquals(1, cb.taps)
        assertEquals(0, cb.dragStarts)
        assertEquals(0, cb.dragEnds)
        assertFalse(c.isDragging)
    }

    @Test
    fun `drag above slop never triggers tap`() {
        val (cb, c) = controller(slop = 24)
        c.feed(FloatingIconTouchController.ACTION_DOWN, 100f, 100f)
        c.feed(FloatingIconTouchController.ACTION_MOVE, 200f, 260f)   // way past slop
        c.feed(FloatingIconTouchController.ACTION_MOVE, 210f, 270f)
        c.feed(FloatingIconTouchController.ACTION_UP, 210f, 270f)
        assertEquals(0, cb.taps)
        assertEquals(1, cb.dragStarts)
        assertTrue(cb.dragMoves >= 2)
        assertEquals(1, cb.dragEnds)
        assertEquals(210, cb.lastX)
        assertEquals(270, cb.lastY)
    }

    @Test
    fun `drag that returns to origin still is a drag (no phantom tap)`() {
        val (cb, c) = controller(slop = 24)
        c.feed(FloatingIconTouchController.ACTION_DOWN, 100f, 100f)
        c.feed(FloatingIconTouchController.ACTION_MOVE, 400f, 400f)   // becomes a drag
        c.feed(FloatingIconTouchController.ACTION_MOVE, 100f, 100f)   // ...and back
        c.feed(FloatingIconTouchController.ACTION_UP, 100f, 100f)
        assertEquals(0, cb.taps)
        assertEquals(1, cb.dragEnds)
    }

    @Test
    fun `cancel discards gesture without tap or drag end`() {
        val (cb, c) = controller()
        c.feed(FloatingIconTouchController.ACTION_DOWN, 0f, 0f)
        c.feed(FloatingIconTouchController.ACTION_MOVE, 300f, 300f)
        c.feed(FloatingIconTouchController.ACTION_CANCEL, 300f, 300f)
        assertEquals(0, cb.taps)
        assertEquals(0, cb.dragEnds)
        assertFalse(c.isDragging)
    }

    @Test
    fun `100 rapid gestures keep tap and drag counts exact`() {
        val (cb, c) = controller()
        var expectedTaps = 0
        var expectedDrags = 0
        repeat(100) { i ->
            c.feed(FloatingIconTouchController.ACTION_DOWN, 50f, 50f)
            if (i % 2 == 0) {
                c.feed(FloatingIconTouchController.ACTION_UP, 52f, 51f)
                expectedTaps++
            } else {
                c.feed(FloatingIconTouchController.ACTION_MOVE, 250f, 90f)
                c.feed(FloatingIconTouchController.ACTION_UP, 250f, 90f)
                expectedDrags++
            }
        }
        assertEquals(expectedTaps, cb.taps)
        assertEquals(expectedDrags, cb.dragEnds)
        assertEquals(0, cb.dragStarts - expectedDrags) // a drag start per drag only
        assertFalse(c.isDragging)
    }
}
