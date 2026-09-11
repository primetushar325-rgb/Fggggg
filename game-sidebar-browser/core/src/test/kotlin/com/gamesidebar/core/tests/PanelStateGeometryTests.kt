package com.gamesidebar.core.tests

import com.gamesidebar.core.geometry.OverlayGeometry
import com.gamesidebar.core.geometry.PanelResize
import com.gamesidebar.core.geometry.Point
import com.gamesidebar.core.geometry.PxSize
import com.gamesidebar.core.geometry.Screen
import com.gamesidebar.core.harness.Assert
import com.gamesidebar.core.harness.TestCase
import com.gamesidebar.core.model.HandleSize
import com.gamesidebar.core.model.PanelSize
import kotlin.math.abs

/**
 * The geometry that decides how the sidebar behaves over a landscape game.
 *
 * These are the cases that used to be device-only complaints - "the sidebar is the wrong size in Free
 * Fire", "the page will not scroll", "it moved after I rotated" - expressed as numbers. Everything here
 * is the pure pixel maths from [OverlayGeometry]; the Android layer only feeds it the current screen.
 */
object PanelStateGeometryTests {

    private const val DENSITY = 3f // xxhdpi: 1dp = 3px, so the dp constants below read as px/3

    /** 1080x2400 phone, portrait: 90px status bar, 132px gesture nav bar. */
    private val phonePortrait = Screen(
        widthPx = 1080,
        heightPx = 2400,
        insetTopPx = 90,
        insetBottomPx = 132,
    )

    /** The same phone turned sideways for a game: nav bar on the right, no status bar (immersive). */
    private val phoneLandscape = Screen(
        widthPx = 2400,
        heightPx = 1080,
        insetRightPx = 132,
    )

    private val mediumPortrait = OverlayGeometry.panelSize(PanelSize.MEDIUM, 0.8f, 0.52f, phonePortrait, DENSITY)
    private val mediumLandscape = OverlayGeometry.panelSize(PanelSize.MEDIUM, 0.8f, 0.52f, phoneLandscape, DENSITY)

    val cases: List<TestCase> = listOf(
        TestCase("orientation is read from the usable area, not the raw display") {
            Assert.that(phoneLandscape.isLandscape) { "a game in landscape was not detected" }
            Assert.that(!phonePortrait.isLandscape) { "portrait was read as landscape" }
            // A landscape display with huge side insets (a foldable's outer screen, a deep cutout plus a
            // side nav bar) leaves a portrait-shaped area, and that is what the panel has to fit in.
            val narrow = Screen(widthPx = 2400, heightPx = 1080, insetLeftPx = 700, insetRightPx = 700)
            Assert.that(!narrow.isLandscape) { "usable area is 1000x1080, i.e. portrait-shaped" }
        },

        TestCase("a landscape panel is narrow enough to leave the game visible") {
            val cap = (phoneLandscape.usableWidth * OverlayGeometry.LANDSCAPE_MAX_WIDTH_FRACTION).toInt()
            Assert.that(mediumLandscape.widthPx <= cap) { "width ${mediumLandscape.widthPx} exceeds the cap $cap" }
            Assert.that(phoneLandscape.usableWidth - mediumLandscape.widthPx >= 500) {
                "the panel covers the whole game: ${mediumLandscape.widthPx} of ${phoneLandscape.usableWidth}"
            }
        },

        TestCase("a landscape panel is tall enough for the page to scroll") {
            // The regression this pins down: at the raw height fraction the panel is shorter than its
            // own full chrome, so the WebView is laid out with no height and cannot scroll.
            val rawHeight = (phoneLandscape.usableHeight * 0.52f).toInt()
            val fullChrome = OverlayGeometry.dp(OverlayGeometry.CHROME_FULL_DP.toFloat(), DENSITY)
            Assert.that(fullChrome > rawHeight) { "test premise: $fullChrome chrome should exceed $rawHeight" }

            Assert.that(mediumLandscape.heightPx > rawHeight) { "landscape height was not raised above $rawHeight" }
            val chrome = OverlayGeometry.chromeLayout(mediumLandscape.heightPx, DENSITY)
            val minContent = OverlayGeometry.dp(OverlayGeometry.MIN_WEB_CONTENT_DP.toFloat(), DENSITY)
            Assert.that(chrome.contentHeightPx >= minContent) {
                "only ${chrome.contentHeightPx}px left for the page, needs $minContent"
            }
            Assert.equals(mediumLandscape.heightPx, chrome.chromeHeightPx + chrome.contentHeightPx)
        },

        TestCase("portrait sizing is untouched by the landscape rules") {
            // No width cap and no height floor in portrait: the preset fractions apply verbatim.
            Assert.equals((phonePortrait.usableWidth * 0.8f).toInt(), mediumPortrait.widthPx)
            Assert.equals((phonePortrait.usableHeight * 0.52f).toInt(), mediumPortrait.heightPx)
            val chrome = OverlayGeometry.chromeLayout(mediumPortrait.heightPx, DENSITY)
            Assert.that(chrome.showTabStrip) { "portrait lost the tab strip" }
            Assert.that(chrome.showShortcutRow) { "portrait lost the shortcut row" }
            Assert.that(chrome.showSecondaryToolbarButtons) { "portrait lost forward/bookmark" }
        },

        TestCase("chrome drops the least important rows first") {
            val minContent = OverlayGeometry.dp(OverlayGeometry.MIN_WEB_CONTENT_DP.toFloat(), DENSITY)

            val full = OverlayGeometry.chromeLayout(
                OverlayGeometry.dp(OverlayGeometry.CHROME_FULL_DP.toFloat(), DENSITY) + minContent,
                DENSITY,
            )
            Assert.that(full.showTabStrip && full.showShortcutRow && full.showSecondaryToolbarButtons) {
                "a tall panel should keep every row"
            }

            val compact = OverlayGeometry.chromeLayout(
                OverlayGeometry.dp(OverlayGeometry.CHROME_COMPACT_DP.toFloat(), DENSITY) + minContent,
                DENSITY,
            )
            Assert.that(compact.showTabStrip) { "the tab strip went before the shortcut row" }
            Assert.that(!compact.showShortcutRow) { "the shortcut row should be the first to go" }
            Assert.that(compact.showSecondaryToolbarButtons)

            val minimal = OverlayGeometry.chromeLayout(
                OverlayGeometry.dp(OverlayGeometry.CHROME_MINIMAL_DP.toFloat(), DENSITY) + minContent - 1,
                DENSITY,
            )
            Assert.that(!minimal.showTabStrip && !minimal.showShortcutRow && !minimal.showSecondaryToolbarButtons)
        },

        TestCase("chrome heights agree with the panel layout constants") {
            // 52dp shortcut row + its 4dp margin, and 34dp tab row + its 4dp margin. If the layout
            // changes and these do not, the panel mis-measures itself and the page loses its height.
            Assert.equals(56, OverlayGeometry.CHROME_FULL_DP - OverlayGeometry.CHROME_COMPACT_DP)
            Assert.equals(38, OverlayGeometry.CHROME_COMPACT_DP - OverlayGeometry.CHROME_MINIMAL_DP)
            Assert.that(
                OverlayGeometry.CHROME_FULL_DP > OverlayGeometry.CHROME_COMPACT_DP &&
                    OverlayGeometry.CHROME_COMPACT_DP > OverlayGeometry.CHROME_MINIMAL_DP,
            ) { "chrome layouts are not ordered" }
        },

        TestCase("a degenerate panel height never yields a negative content area") {
            Assert.equals(0, OverlayGeometry.chromeLayout(0, DENSITY).contentHeightPx)
            Assert.equals(0, OverlayGeometry.chromeLayout(-400, DENSITY).contentHeightPx)
            // A panel that is nothing but chrome still reports a usable, non-negative body.
            val onlyChrome = OverlayGeometry.chromeLayout(
                OverlayGeometry.dp(OverlayGeometry.CHROME_MINIMAL_DP.toFloat(), DENSITY),
                DENSITY,
            )
            Assert.equals(0, onlyChrome.contentHeightPx)
        },

        TestCase("the landscape floor degrades to filling the screen when the screen is short") {
            val short = Screen(widthPx = 2000, heightPx = 600)
            val size = OverlayGeometry.panelSize(PanelSize.LARGE, 0.95f, 0.95f, short, DENSITY)
            Assert.that(size.widthPx in 1..short.usableWidth) { "width ${size.widthPx} off-screen" }
            Assert.that(size.heightPx in 1..short.usableHeight) { "height ${size.heightPx} off-screen" }
        },

        TestCase("a panel rectangle survives the trip through persisted fractions") {
            val origin = Point(100, 500)
            val size = PxSize(800, 900)
            val frame = OverlayGeometry.frameOf(origin, size, phonePortrait)
            val restored = OverlayGeometry.placementOf(frame, phonePortrait, DENSITY)
            Assert.equals(origin, restored.origin, "reopen moved the sidebar")
            Assert.equals(size, restored.size, "reopen resized the sidebar")
        },

        TestCase("a frame from an off-screen rectangle is stored clamped and restores on screen") {
            val frame = OverlayGeometry.frameOf(Point(-500, 5000), PxSize(4000, 4000), phonePortrait)
            Assert.that(frame.xFraction in 0f..1f) { "xFraction ${frame.xFraction}" }
            Assert.that(frame.yFraction in 0f..1f) { "yFraction ${frame.yFraction}" }
            Assert.that(frame.widthFraction in 0f..1f) { "widthFraction ${frame.widthFraction}" }
            Assert.that(frame.heightFraction in 0f..1f) { "heightFraction ${frame.heightFraction}" }
            val restored = OverlayGeometry.placementOf(frame, phonePortrait, DENSITY)
            Assert.that(restored.origin.x >= phonePortrait.usableLeft) { "restored left of the screen" }
            Assert.that(restored.origin.y >= phonePortrait.usableTop) { "restored under the status bar" }
            Assert.that(restored.origin.x + restored.size.widthPx <= phonePortrait.usableRight)
            Assert.that(restored.origin.y + restored.size.heightPx <= phonePortrait.usableBottom)
        },

        TestCase("a portrait frame restores inside the landscape safe area") {
            val frame = OverlayGeometry.frameOf(Point(700, 1900), PxSize(864, 1132), phonePortrait)
            val restored = OverlayGeometry.placementOf(frame, phoneLandscape, DENSITY)
            Assert.that(restored.origin.x >= phoneLandscape.usableLeft) { "left of the screen" }
            Assert.that(restored.origin.y >= phoneLandscape.usableTop) { "above the screen" }
            Assert.that(restored.origin.x + restored.size.widthPx <= phoneLandscape.usableRight) {
                "crosses the nav bar: ${restored.origin.x} + ${restored.size.widthPx}"
            }
            Assert.that(restored.origin.y + restored.size.heightPx <= phoneLandscape.usableBottom) {
                "crosses the bottom edge"
            }
            Assert.that(restored.size.widthPx > 0 && restored.size.heightPx > 0) { "panel collapsed to nothing" }
        },

        TestCase("rotation never leaves the panel mostly off-screen") {
            val size = PxSize(800, 700)
            val corners = listOf(
                Point(8, 98),
                Point(272, 98),
                Point(8, 1360),
                Point(272, 1360),
                Point(140, 800),
            )
            corners.forEach { origin ->
                val carried = OverlayGeometry.remapPanel(origin, size, phonePortrait, phoneLandscape)
                Assert.that(carried.origin.x >= phoneLandscape.usableLeft) { "left of screen from $origin" }
                Assert.that(carried.origin.y >= phoneLandscape.usableTop) { "above screen from $origin" }
                Assert.that(carried.origin.x + carried.size.widthPx <= phoneLandscape.usableRight) {
                    "crosses the nav bar from $origin"
                }
                Assert.that(carried.origin.y + carried.size.heightPx <= phoneLandscape.usableBottom) {
                    "crosses the bottom from $origin"
                }
            }
        },

        TestCase("carrying a preset across rotation applies the new orientation's limits") {
            val carried = OverlayGeometry.carryPanelSize(mediumPortrait, phonePortrait, phoneLandscape, DENSITY)
            // Same fractions, so a preset-sized panel must land exactly on the preset for the new
            // orientation - otherwise rotating would silently resize the sidebar.
            Assert.equals(mediumLandscape.widthPx, carried.widthPx)
            Assert.that(abs(mediumLandscape.heightPx - carried.heightPx) <= 4) {
                "carried height ${carried.heightPx} drifted from ${mediumLandscape.heightPx}"
            }
        },

        TestCase("carrying back to portrait returns a portrait-shaped panel") {
            val carried = OverlayGeometry.carryPanelSize(mediumLandscape, phoneLandscape, phonePortrait, DENSITY)
            Assert.that(carried.widthPx <= phonePortrait.usableWidth) { "wider than the screen" }
            Assert.that(carried.heightPx <= phonePortrait.usableHeight) { "taller than the screen" }
            Assert.that(carried.widthPx >= OverlayGeometry.dp(OverlayGeometry.MIN_PANEL_WIDTH_DP.toFloat(), DENSITY)) {
                "narrower than the usable floor"
            }
        },

        TestCase("dragging the right edge resizes without moving the left one") {
            val result = OverlayGeometry.resizePanel(
                Point(100, 500),
                PanelResize(widthPx = 900, heightPx = 900, originDx = 0, movesLeftEdge = false),
                phonePortrait,
                DENSITY,
            )
            Assert.equals(100, result.origin.x, "the anchored edge moved")
            Assert.equals(900, result.size.widthPx)
        },

        TestCase("dragging the left edge moves the origin with it") {
            val result = OverlayGeometry.resizePanel(
                Point(100, 500),
                PanelResize(widthPx = 700, heightPx = 900, originDx = 100, movesLeftEdge = true),
                phonePortrait,
                DENSITY,
            )
            Assert.equals(200, result.origin.x, "the left edge did not follow the finger")
            // The right edge is the anchored one here, so it must not move at all.
            Assert.equals(900, result.origin.x + result.size.widthPx, "the right edge drifted")
        },

        TestCase("pushing the left edge past the minimum stops the edge, not the panel") {
            // Ask for 300px (below the 200dp floor) by dragging the left edge 500px to the right.
            val result = OverlayGeometry.resizePanel(
                Point(100, 500),
                PanelResize(widthPx = 300, heightPx = 900, originDx = 500, movesLeftEdge = true),
                phonePortrait,
                DENSITY,
            )
            Assert.equals(OverlayGeometry.dp(OverlayGeometry.MIN_PANEL_WIDTH_DP.toFloat(), DENSITY), result.size.widthPx)
            Assert.equals(900, result.origin.x + result.size.widthPx, "hitting the minimum slid the panel sideways")
        },

        TestCase("dragging the top edge keeps the bottom edge fixed") {
            val result = OverlayGeometry.resizePanel(
                Point(100, 500),
                PanelResize(widthPx = 800, heightPx = 600, originDy = 300, movesTopEdge = true),
                phonePortrait,
                DENSITY,
            )
            Assert.equals(800, result.origin.y)
            Assert.equals(1400, result.origin.y + result.size.heightPx, "the bottom edge drifted")
        },

        TestCase("dragging the bottom edge keeps the top edge fixed") {
            val result = OverlayGeometry.resizePanel(
                Point(100, 500),
                PanelResize(widthPx = 800, heightPx = 1100, originDy = -200, movesTopEdge = false),
                phonePortrait,
                DENSITY,
            )
            Assert.equals(500, result.origin.y, "the anchored top edge moved")
            Assert.equals(1100, result.size.heightPx)
        },

        TestCase("a corner drag resizes both axes and anchors the opposite corner") {
            val result = OverlayGeometry.resizePanel(
                Point(100, 500),
                PanelResize(
                    widthPx = 600,
                    heightPx = 700,
                    originDx = 200,
                    originDy = 200,
                    movesLeftEdge = true,
                    movesTopEdge = true,
                ),
                phonePortrait,
                DENSITY,
            )
            Assert.equals(900, result.origin.x + result.size.widthPx, "the right edge drifted")
            Assert.equals(1400, result.origin.y + result.size.heightPx, "the bottom edge drifted")
        },

        TestCase("resize cannot exceed the screen or escape it") {
            val huge = OverlayGeometry.resizePanel(
                Point(100, 500),
                PanelResize(widthPx = 100_000, heightPx = 100_000),
                phonePortrait,
                DENSITY,
            )
            val margin = OverlayGeometry.dp(OverlayGeometry.PANEL_MARGIN_DP.toFloat(), DENSITY) * 2
            Assert.equals(phonePortrait.usableWidth - margin, huge.size.widthPx)
            Assert.equals(phonePortrait.usableHeight - margin, huge.size.heightPx)

            val flung = OverlayGeometry.resizePanel(
                Point(100, 500),
                PanelResize(
                    widthPx = 800,
                    heightPx = 900,
                    originDx = -5_000,
                    originDy = -5_000,
                    movesLeftEdge = true,
                    movesTopEdge = true,
                ),
                phonePortrait,
                DENSITY,
            )
            Assert.that(flung.origin.x >= phonePortrait.usableLeft) { "flung off the left edge" }
            Assert.that(flung.origin.y >= phonePortrait.usableTop) { "flung above the status bar" }
        },

        TestCase("the handle is carried across rotation by its own looser rules") {
            val handle = OverlayGeometry.handleSize(HandleSize.MEDIUM, DENSITY)
            val carried = OverlayGeometry.remapHandle(Point(0, 1000), handle, phonePortrait, phoneLandscape)
            // Edge-hide mode parks the handle partly off-screen on purpose, so the horizontal clamp has
            // to stay loose or a rotation would pull it back and change what the user sees.
            Assert.that(carried.x >= phoneLandscape.usableLeft - handle.widthPx) { "handle lost off-screen" }
            Assert.that(carried.x <= phoneLandscape.usableRight) { "handle past the right edge" }
            Assert.that(carried.y >= phoneLandscape.usableTop) { "handle above the screen" }
            Assert.that(carried.y + handle.heightPx <= phoneLandscape.usableBottom) { "handle below the screen" }
        },

        TestCase("the handle keeps its relative height across rotation") {
            val handle = OverlayGeometry.handleSize(HandleSize.MEDIUM, DENSITY)
            val upperArea = Point(0, phonePortrait.usableTop + phonePortrait.usableHeight / 5)
            val carried = OverlayGeometry.remapHandle(upperArea, handle, phonePortrait, phoneLandscape)
            Assert.that(carried.y < phoneLandscape.usableHeight / 2) {
                "a handle in the upper area landed at ${carried.y} of ${phoneLandscape.usableHeight}"
            }
        },
    )
}
