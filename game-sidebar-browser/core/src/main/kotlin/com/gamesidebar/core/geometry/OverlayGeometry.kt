package com.gamesidebar.core.geometry

import com.gamesidebar.core.model.HandleSize
import com.gamesidebar.core.model.HorizontalAnchor
import com.gamesidebar.core.model.PanelSize
import com.gamesidebar.core.model.VerticalAnchor

/**
 * All overlay placement maths, in pure pixels.
 *
 * The Android layer converts dp -> px and collects window insets (status bar, navigation bar,
 * display cutout, gesture pill), then hands a [Screen] here. Nothing is decided in the View layer,
 * which keeps the panel out of system areas on every device and every navigation mode.
 */
data class Screen(
    val widthPx: Int,
    val heightPx: Int,
    val insetLeftPx: Int = 0,
    val insetTopPx: Int = 0,
    val insetRightPx: Int = 0,
    val insetBottomPx: Int = 0,
) {
    val usableLeft: Int get() = insetLeftPx.coerceAtLeast(0)
    val usableTop: Int get() = insetTopPx.coerceAtLeast(0)
    val usableRight: Int get() = (widthPx - insetRightPx).coerceAtLeast(usableLeft)
    val usableBottom: Int get() = (heightPx - insetBottomPx).coerceAtLeast(usableTop)
    val usableWidth: Int get() = (usableRight - usableLeft).coerceAtLeast(1)
    val usableHeight: Int get() = (usableBottom - usableTop).coerceAtLeast(1)
    val centerX: Int get() = usableLeft + usableWidth / 2
    val centerY: Int get() = usableTop + usableHeight / 2
}

data class PxSize(val widthPx: Int, val heightPx: Int)

data class Point(val x: Int, val y: Int)

enum class Edge { NONE, LEFT, RIGHT }

data class SnapResult(val point: Point, val edge: Edge, val visibleWidthPx: Int)

data class PanelPlacement(val origin: Point, val size: PxSize)

object OverlayGeometry {

    /**
     * Floor for a usable panel: below this the URL bar and tab strip stop being reachable.
     * Deliberately modest so the Small preset is still reachable on a 360dp-wide phone.
     */
    const val MIN_PANEL_WIDTH_DP = 200
    const val MIN_PANEL_HEIGHT_DP = 140

    /** Gap kept between the panel and the screen edge / system bars. */
    const val PANEL_MARGIN_DP = 8

    /** Distance from an edge that triggers snapping, in dp. */
    const val SNAP_THRESHOLD_DP = 24

    /** Fraction of the handle left visible when edge-hide mode is on. */
    const val EDGE_HIDDEN_VISIBLE_FRACTION = 0.42f

    fun dp(dp: Float, density: Float): Int = (dp * density + 0.5f).toInt()

    fun panelSize(
        panelSize: PanelSize,
        customWidthFraction: Float,
        customHeightFraction: Float,
        screen: Screen,
        density: Float,
    ): PxSize {
        val (wFraction, hFraction) = if (panelSize.isCustom) {
            customWidthFraction to customHeightFraction
        } else {
            panelSize.widthFraction to panelSize.heightFraction
        }
        val margin = dp(PANEL_MARGIN_DP.toFloat(), density) * 2
        // The maximum is computed first so that a screen smaller than the floor degrades to
        // "fill the usable area" instead of producing an inverted (empty) coerce range.
        val maxWidth = (screen.usableWidth - margin).coerceAtLeast(1)
        val maxHeight = (screen.usableHeight - margin).coerceAtLeast(1)
        val minWidth = dp(MIN_PANEL_WIDTH_DP.toFloat(), density).coerceAtMost(maxWidth)
        val minHeight = dp(MIN_PANEL_HEIGHT_DP.toFloat(), density).coerceAtMost(maxHeight)
        val width = (screen.usableWidth * wFraction).toInt().coerceIn(minWidth, maxWidth)
        val height = (screen.usableHeight * hFraction).toInt().coerceIn(minHeight, maxHeight)
        return PxSize(width, height)
    }

    fun handleSize(handleSize: HandleSize, density: Float): PxSize =
        PxSize(dp(handleSize.widthDp.toFloat(), density), dp(handleSize.heightDp.toFloat(), density))

    /**
     * Where the handle starts before the user moves it: horizontally centred, in the upper third of
     * the usable area, which is out of the way of both thumb reach and typical HUD elements.
     */
    fun defaultHandlePoint(handle: PxSize, screen: Screen): Point {
        val x = screen.centerX - handle.widthPx / 2
        val y = screen.usableTop + (screen.usableHeight * 0.18f).toInt()
        return clampHandle(Point(x, y), handle, screen)
    }

    fun anchorPoint(
        vertical: VerticalAnchor,
        horizontal: HorizontalAnchor,
        size: PxSize,
        screen: Screen,
    ): Point {
        val margin = dp(PANEL_MARGIN_DP.toFloat(), 1f)
        val x = when (horizontal) {
            HorizontalAnchor.LEFT -> screen.usableLeft + margin
            HorizontalAnchor.RIGHT -> screen.usableRight - size.widthPx - margin
            HorizontalAnchor.CENTER -> screen.centerX - size.widthPx / 2
        }
        val y = when (vertical) {
            VerticalAnchor.TOP -> screen.usableTop + margin
            VerticalAnchor.BOTTOM -> screen.usableBottom - size.heightPx - margin
            VerticalAnchor.MIDDLE -> screen.centerY - size.heightPx / 2
        }
        return Point(x, y)
    }

    fun clampPanel(origin: Point, size: PxSize, screen: Screen): Point {
        val margin = dp(PANEL_MARGIN_DP.toFloat(), 1f)
        val maxX = (screen.usableRight - size.widthPx - margin).coerceAtLeast(screen.usableLeft + margin)
        val maxY = (screen.usableBottom - size.heightPx - margin).coerceAtLeast(screen.usableTop + margin)
        return Point(
            x = origin.x.coerceIn(screen.usableLeft + margin, maxX),
            y = origin.y.coerceIn(screen.usableTop + margin, maxY),
        )
    }

    fun clampHandle(origin: Point, handle: PxSize, screen: Screen): Point {
        val maxY = (screen.usableBottom - handle.heightPx).coerceAtLeast(screen.usableTop)
        // Horizontal clamping is loose on purpose: edge-hide mode parks the handle partly
        // off-screen, so the same visible-width formula as snap() has to be used here or the two
        // disagree by a rounding error and the handle visibly jumps.
        val visibleAtEdge = (handle.widthPx * EDGE_HIDDEN_VISIBLE_FRACTION).toInt().coerceAtLeast(1)
        val minX = screen.usableLeft - (handle.widthPx - visibleAtEdge)
        val maxX = (screen.usableRight - visibleAtEdge).coerceAtLeast(minX)
        return Point(
            x = origin.x.coerceIn(minX, maxX),
            y = origin.y.coerceIn(screen.usableTop, maxY),
        )
    }

    /**
     * Edge snapping. When [autoSnap] is on and the handle centre is within [SNAP_THRESHOLD_DP] of
     * the usable left/right edge, the handle is parked flush against that edge - and with
     * [edgeHideMode] only [EDGE_HIDDEN_VISIBLE_FRACTION] of it stays on screen.
     */
    fun snap(
        origin: Point,
        handle: PxSize,
        screen: Screen,
        autoSnap: Boolean,
        edgeHideMode: Boolean,
        density: Float,
    ): SnapResult {
        if (!autoSnap) {
            val clamped = clampHandle(origin, handle, screen)
            return SnapResult(clamped, Edge.NONE, handle.widthPx)
        }
        val centreX = origin.x + handle.widthPx / 2
        val threshold = dp(SNAP_THRESHOLD_DP.toFloat(), density) + handle.widthPx / 2
        val distanceToLeft = centreX - screen.usableLeft
        val distanceToRight = screen.usableRight - centreX
        val side = when {
            distanceToLeft <= threshold && distanceToLeft <= distanceToRight -> Edge.LEFT
            distanceToRight <= threshold -> Edge.RIGHT
            else -> Edge.NONE
        }
        val visible = if (edgeHideMode) {
            (handle.widthPx * EDGE_HIDDEN_VISIBLE_FRACTION).toInt().coerceAtLeast(dp(16f, density))
        } else {
            handle.widthPx
        }
        val x = when (side) {
            Edge.LEFT -> screen.usableLeft - (handle.widthPx - visible)
            Edge.RIGHT -> screen.usableRight - visible
            Edge.NONE -> origin.x
        }
        return SnapResult(clampHandle(Point(x, origin.y), handle, screen), side, visible)
    }

    /**
     * The panel grows out of the handle, so the animation start point is the handle's centre
     * projected onto where the panel will end up. Returned as the panel's top-left origin.
     */
    fun panelOriginFromHandle(handleCentre: Point, panel: PxSize, screen: Screen): Point = clampPanel(
        Point(handleCentre.x - panel.widthPx / 2, handleCentre.y - panel.heightPx / 2),
        panel,
        screen,
    )

    /** Panel placement for an anchor pair, clamped into the safe area. */
    fun placement(
        vertical: VerticalAnchor,
        horizontal: HorizontalAnchor,
        size: PxSize,
        screen: Screen,
    ): PanelPlacement {
        val origin = anchorPoint(vertical, horizontal, size, screen)
        return PanelPlacement(clampPanel(origin, size, screen), size)
    }

    /** True when the panel still covers the handle, so the handle stays hidden while open. */
    fun overlaps(panel: PanelPlacement, handleOrigin: Point, handle: PxSize): Boolean {
        val hRight = handleOrigin.x + handle.widthPx
        val hBottom = handleOrigin.y + handle.heightPx
        val pRight = panel.origin.x + panel.size.widthPx
        val pBottom = panel.origin.y + panel.size.heightPx
        return handleOrigin.x < pRight && hRight > panel.origin.x &&
            handleOrigin.y < pBottom && hBottom > panel.origin.y
    }
}
