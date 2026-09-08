package com.gamesoundpro.app

import com.gamesoundpro.app.utils.Format
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun `durations render as minutes and seconds`() {
        assertEquals("0:00", Format.duration(0))
        assertEquals("0:07", Format.duration(7_000))
        assertEquals("1:05", Format.duration(65_000))
        assertEquals("12:59", Format.duration(779_000))
        assertEquals("0:00", Format.duration(-5))
    }

    @Test
    fun `clock pads minutes`() {
        assertEquals("00:07", Format.clock(7_000))
        assertEquals("02:05", Format.clock(125_000))
    }

    @Test
    fun `bytes are human readable`() {
        assertEquals("900 B", Format.bytes(900))
        assertEquals("5.2 KB", Format.bytes(5_300))
        assertEquals("1.0 MB", Format.bytes(1_048_576))
    }

    @Test
    fun `percent clamps and rounds`() {
        assertEquals("0%", Format.percent(0f))
        assertEquals("72%", Format.percent(0.72f))
        assertEquals("100%", Format.percent(1.5f))
    }
}
