package com.gamesidebar.core.tests

import com.gamesidebar.core.geometry.Edge
import com.gamesidebar.core.geometry.OverlayGeometry
import com.gamesidebar.core.geometry.Point
import com.gamesidebar.core.geometry.PanelPlacement
import com.gamesidebar.core.geometry.PxSize
import com.gamesidebar.core.geometry.Screen
import com.gamesidebar.core.harness.Assert
import com.gamesidebar.core.harness.TestCase
import com.gamesidebar.core.model.HandleSize
import com.gamesidebar.core.model.HorizontalAnchor
import com.gamesidebar.core.model.PanelSize
import com.gamesidebar.core.model.VerticalAnchor

object GeometryTests {

    private const val DENSITY = 3f // xxhdpi: 1dp = 3px, so dp values below read directly as px/3

    /** 1080x2400 phone with a 90px status bar, a 132px gesture nav bar and no cutout on the sides. */
    private val phone = Screen(
        widthPx = 1080,
        heightPx = 2400,
        insetTopPx = 90,
        insetBottomPx = 132,
    )

    /** Landscape tablet with a 60px display cutout on the left and nav bar on the right. */
    private val landscape = Screen(
        widthPx = 2560,
        heightPx = 1600,
        insetLeftPx = 60,
        insetRightPx = 96,
        insetTopPx = 0,
        insetBottomPx = 0,
    )

    val cases: List<TestCase> = listOf(
        TestCase("usable area excludes system bars") {
            Assert.equals(0, phone.usableLeft)
            Assert.equals(90, phone.usableTop)
            Assert.equals(1080, phone.usableRight)
            Assert.equals(2268, phone.usableBottom)
            Assert.equals(2178, phone.usableHeight)
        },
        TestCase("landscape cutout shrinks the usable width from both sides") {
            Assert.equals(60, landscape.usableLeft)
            Assert.equals(2464, landscape.usableRight)
            Assert.equals(2404, landscape.usableWidth)
        },
        TestCase("dp conversion rounds half up") {
            Assert.equals(3, OverlayGeometry.dp(1f, 3f))
            Assert.equals(2, OverlayGeometry.dp(0.5f, 3f), "0.5dp at 3x rounds half up to 2px")
            Assert.equals(0, OverlayGeometry.dp(0.1f, 3f))
        },
        TestCase("medium panel is roughly 80% x 52% of the usable area") {
            val size = OverlayGeometry.panelSize(PanelSize.MEDIUM, 0.8f, 0.52f, phone, DENSITY)
            Assert.inRange(size.widthPx, 850, 875)
            Assert.inRange(size.heightPx, 1120, 1145)
        },
        TestCase("panel presets grow small -> medium -> large") {
            val small = OverlayGeometry.panelSize(PanelSize.SMALL, 0.8f, 0.52f, phone, DENSITY)
            val medium = OverlayGeometry.panelSize(PanelSize.MEDIUM, 0.8f, 0.52f, phone, DENSITY)
            val large = OverlayGeometry.panelSize(PanelSize.LARGE, 0.8f, 0.52f, phone, DENSITY)
            Assert.that(small.widthPx < medium.widthPx) { "small should be narrower" }
            Assert.that(medium.widthPx < large.widthPx) { "medium should be narrower than large" }
            Assert.that(small.heightPx < medium.heightPx) { "small should be shorter" }
        },
        TestCase("custom fractions drive the custom preset") {
            val size = OverlayGeometry.panelSize(PanelSize.CUSTOM, 0.65f, 0.45f, phone, DENSITY)
            Assert.inRange(size.widthPx, 695, 710)
            Assert.inRange(size.heightPx, 975, 985)
        },
        TestCase("a too-small custom fraction is raised to the usable floor") {
            val size = OverlayGeometry.panelSize(PanelSize.CUSTOM, 0.35f, 0.35f, phone, DENSITY)
            Assert.equals(OverlayGeometry.MIN_PANEL_WIDTH_DP * DENSITY.toInt(), size.widthPx)
        },
        TestCase("panels never shrink below the usable floor") {
            val tiny = Screen(widthPx = 320, heightPx = 320, insetTopPx = 24, insetBottomPx = 24)
            val size = OverlayGeometry.panelSize(PanelSize.LARGE, 0.95f, 0.95f, tiny, DENSITY)
            Assert.that(size.widthPx <= tiny.usableWidth) { "panel wider than screen" }
            Assert.that(size.heightPx <= tiny.usableHeight) { "panel taller than screen" }
            Assert.that(size.widthPx > 0 && size.heightPx > 0) { "panel must stay visible" }
        },
        TestCase("handle sizes match the spec range of 55-90dp by 5-10dp") {
            for (handle in HandleSize.entries) {
                Assert.inRange(handle.widthDp, 55, 90)
                Assert.inRange(handle.heightDp, 5, 10)
                Assert.that(handle.widthDp > handle.heightDp * 5) { "handle is not a pill" }
            }
        },
        TestCase("default handle sits centred in the upper area, clear of the status bar") {
            val handle = OverlayGeometry.handleSize(HandleSize.MEDIUM, DENSITY)
            val point = OverlayGeometry.defaultHandlePoint(handle, phone)
            Assert.equals(phone.centerX - handle.widthPx / 2, point.x)
            Assert.that(point.y > phone.usableTop) { "handle under the status bar" }
            Assert.that(point.y < phone.centerY) { "handle should start in the upper area" }
        },
        TestCase("anchoring covers all nine positions") {
            val size = PxSize(400, 300)
            val topLeft = OverlayGeometry.anchorPoint(VerticalAnchor.TOP, HorizontalAnchor.LEFT, size, phone)
            val bottomRight = OverlayGeometry.anchorPoint(VerticalAnchor.BOTTOM, HorizontalAnchor.RIGHT, size, phone)
            val centre = OverlayGeometry.anchorPoint(VerticalAnchor.MIDDLE, HorizontalAnchor.CENTER, size, phone)
            Assert.equals(8, topLeft.x)
            Assert.equals(98, topLeft.y)
            Assert.equals(1080 - 400 - 8, bottomRight.x)
            Assert.equals(2268 - 300 - 8, bottomRight.y)
            Assert.equals(phone.centerX - 200, centre.x)
            Assert.equals(phone.centerY - 150, centre.y)
        },
        TestCase("panel placement is clamped into the safe area") {
            val size = PxSize(900, 900)
            val placement = OverlayGeometry.placement(VerticalAnchor.TOP, HorizontalAnchor.LEFT, size, landscape)
            Assert.that(placement.origin.x >= landscape.usableLeft) { "panel starts left of the cutout" }
            Assert.that(placement.origin.y >= landscape.usableTop) { "panel starts above the screen" }
            Assert.that(placement.origin.x + size.widthPx <= landscape.usableRight) { "panel crosses the nav bar" }
        },
        TestCase("dragging far off-screen is clamped back") {
            val size = PxSize(600, 400)
            val clamped = OverlayGeometry.clampPanel(Point(-500, 5000), size, phone)
            Assert.that(clamped.x >= 8) { "x not clamped: $clamped" }
            Assert.that(clamped.y + size.heightPx <= phone.usableBottom - 8) { "y not clamped: $clamped" }
        },
        TestCase("auto snap parks the handle on the near edge") {
            val handle = OverlayGeometry.handleSize(HandleSize.MEDIUM, DENSITY)
            val left = OverlayGeometry.snap(Point(4, 500), handle, phone, autoSnap = true, edgeHideMode = false, density = DENSITY)
            Assert.equals(Edge.LEFT, left.edge)
            Assert.equals(phone.usableLeft, left.point.x)
            Assert.equals(handle.widthPx, left.visibleWidthPx)

            val right = OverlayGeometry.snap(Point(phone.usableRight - handle.widthPx + 2, 500), handle, phone, true, false, DENSITY)
            Assert.equals(Edge.RIGHT, right.edge)
            Assert.equals(phone.usableRight - handle.widthPx, right.point.x)
        },
        TestCase("no snap in the middle of the screen") {
            val handle = OverlayGeometry.handleSize(HandleSize.MEDIUM, DENSITY)
            val result = OverlayGeometry.snap(Point(phone.centerX - handle.widthPx / 2, 800), handle, phone, true, false, DENSITY)
            Assert.equals(Edge.NONE, result.edge)
        },
        TestCase("auto snap off leaves the handle where it was dropped") {
            val handle = OverlayGeometry.handleSize(HandleSize.MEDIUM, DENSITY)
            val result = OverlayGeometry.snap(Point(4, 500), handle, phone, autoSnap = false, edgeHideMode = false, density = DENSITY)
            Assert.equals(Edge.NONE, result.edge)
            Assert.equals(4, result.point.x)
        },
        TestCase("edge hide mode leaves part of the handle off-screen") {
            val handle = OverlayGeometry.handleSize(HandleSize.MEDIUM, DENSITY)
            val result = OverlayGeometry.snap(Point(0, 500), handle, phone, true, edgeHideMode = true, density = DENSITY)
            Assert.equals(Edge.LEFT, result.edge)
            Assert.that(result.point.x < 0) { "handle should hang off the left edge, x=${result.point.x}" }
            Assert.that(result.visibleWidthPx < handle.widthPx) { "nothing was hidden" }
            Assert.that(result.visibleWidthPx > 0) { "handle fully hidden" }
            Assert.equals(result.point.x + handle.widthPx, phone.usableLeft + result.visibleWidthPx)
        },
        TestCase("edge hide mode works on the right edge too") {
            val handle = OverlayGeometry.handleSize(HandleSize.MEDIUM, DENSITY)
            val result = OverlayGeometry.snap(
                Point(phone.usableRight - handle.widthPx, 500), handle, phone, true, edgeHideMode = true, density = DENSITY,
            )
            Assert.equals(Edge.RIGHT, result.edge)
            Assert.equals(phone.usableRight - result.visibleWidthPx, result.point.x)
        },
        TestCase("panel opens out of the handle and stays on screen") {
            val handle = OverlayGeometry.handleSize(HandleSize.MEDIUM, DENSITY)
            val handlePoint = OverlayGeometry.defaultHandlePoint(handle, phone)
            val panel = OverlayGeometry.panelSize(PanelSize.MEDIUM, 0.8f, 0.52f, phone, DENSITY)
            val origin = OverlayGeometry.panelOriginFromHandle(
                Point(handlePoint.x + handle.widthPx / 2, handlePoint.y + handle.heightPx / 2),
                panel,
                phone,
            )
            Assert.that(origin.x >= 8) { "panel starts off-screen left" }
            Assert.that(origin.y >= phone.usableTop + 8) { "panel starts under the status bar" }
            Assert.that(origin.x + panel.widthPx <= phone.usableRight - 8) { "panel crosses the right edge" }
            Assert.that(origin.y + panel.heightPx <= phone.usableBottom - 8) { "panel crosses the nav bar" }
        },
        TestCase("overlap detection hides the handle behind an open panel") {
            val handle = PxSize(200, 20)
            val placement = PanelPlacement(Point(100, 400), PxSize(800, 600))
            Assert.that(OverlayGeometry.overlaps(placement, Point(300, 410), handle)) { "should overlap" }
            Assert.that(!OverlayGeometry.overlaps(placement, Point(100, 100), handle)) { "should not overlap" }
        },
    )
}
