package com.gamesidebar.core.geometry

import com.gamesidebar.core.model.HandleSize
import com.gamesidebar.core.model.HorizontalAnchor
import com.gamesidebar.core.model.PanelSize
import com.gamesidebar.core.model.VerticalAnchor
import kotlin.math.roundToInt

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

    /**
     * Orientation is decided from the *usable* area, not the raw display: a landscape phone whose
     * navigation bar sits on the side can still have more usable width than height, and that is the
     * space the panel actually has to live in.
     */
    val isLandscape: Boolean get() = usableWidth > usableHeight
}

data class PxSize(val widthPx: Int, val heightPx: Int)

data class Point(val x: Int, val y: Int)

enum class Edge { NONE, LEFT, RIGHT }

data class SnapResult(val point: Point, val edge: Edge, val visibleWidthPx: Int)

data class PanelPlacement(val origin: Point, val size: PxSize)

/**
 * Which parts of the panel chrome fit at a given panel height, and what is left for the web content.
 *
 * The chrome is a fixed dp cost (see `res/values/dimens.xml` + `res/layout/overlay_panel.xml`), so on
 * a short landscape panel it can eat the entire window and leave the WebView with zero height - which
 * is what makes page scrolling "unreliable" in games. Dropping the least important rows first keeps
 * the content area usable without removing any feature: the shortcut row and the tab strip are hidden,
 * never deleted, and both come back as soon as the panel is tall enough again.
 */
data class ChromeLayout(
    val showTabStrip: Boolean,
    val showShortcutRow: Boolean,
    val showSecondaryToolbarButtons: Boolean,
    val chromeHeightPx: Int,
    val contentHeightPx: Int,
)

/**
 * The panel rectangle as fractions of the usable screen, which is what gets persisted.
 *
 * Fractions rather than pixels for the same reason the handle position is stored as fractions: a
 * portrait pixel value is meaningless (and can be entirely off-screen) once the device rotates.
 */
data class PanelFrame(
    val xFraction: Float,
    val yFraction: Float,
    val widthFraction: Float,
    val heightFraction: Float,
)

/**
 * One resize step, expressed as absolute deltas from the start of the gesture.
 *
 * [originDx]/[originDy] are non-zero only when the dragged edge is the left or the top one - those
 * are the two edges whose movement has to move the window as well, since a WindowManager overlay is
 * positioned by its top-left corner.
 */
data class PanelResize(
    val widthPx: Int,
    val heightPx: Int,
    val originDx: Int = 0,
    val originDy: Int = 0,
    val movesLeftEdge: Boolean = false,
    val movesTopEdge: Boolean = false,
)

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

    /**
     * Fixed cost of the panel chrome, in dp, for each of the three layouts.
     *
     * FULL     = panel padding (10 + 10) + header 40 + tab row (4 + 34) + toolbar (4 + 44)
     *            + progress (4 + 2) + shortcut row (4 + 52) + body margin 4                  = 212
     * COMPACT  = FULL without the shortcut row (4 + 52)                                       = 156
     * MINIMAL  = COMPACT without the tab row (4 + 34)                                         = 118
     *
     * These mirror `res/values/dimens.xml`; the geometry tests assert the relationship between them
     * so a layout change that forgets to update one of the two is caught before it ships.
     */
    const val CHROME_FULL_DP = 212
    const val CHROME_COMPACT_DP = 156
    const val CHROME_MINIMAL_DP = 118

    /** Web area the panel must be able to offer before a chrome layout is considered acceptable. */
    const val MIN_WEB_CONTENT_DP = 120

    /**
     * Landscape width ceiling, as a fraction of the usable width.
     *
     * The portrait fractions read as "a sidebar" because the screen is narrow. Applied unchanged in
     * landscape they span the whole game, which is the "sidebar occupies an excessive area" report.
     * Capping here keeps a compact floating browser and leaves the game visible on both sides.
     */
    const val LANDSCAPE_MAX_WIDTH_FRACTION = 0.68f

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
        return sizeForFractions(wFraction, hFraction, screen, density)
    }

    /**
     * The one place a width/height fraction of the usable area becomes a panel size in pixels.
     *
     * Both the size presets and [carryPanelSize] go through here, so a panel that is carried across a
     * rotation is limited by exactly the same rules as one that was just chosen from settings - there
     * is no second, drifting copy of the orientation logic.
     */
    fun sizeForFractions(
        widthFraction: Float,
        heightFraction: Float,
        screen: Screen,
        density: Float,
    ): PxSize {
        val margin = dp(PANEL_MARGIN_DP.toFloat(), density) * 2
        // The maximum is computed first so that a screen smaller than the floor degrades to
        // "fill the usable area" instead of producing an inverted (empty) coerce range.
        val maxWidth = (screen.usableWidth - margin).coerceAtLeast(1)
        val maxHeight = (screen.usableHeight - margin).coerceAtLeast(1)
        val minWidth = dp(MIN_PANEL_WIDTH_DP.toFloat(), density).coerceAtMost(maxWidth)
        val minHeight = dp(MIN_PANEL_HEIGHT_DP.toFloat(), density).coerceAtMost(maxHeight)

        // Landscape gets a width ceiling so the panel stays a sidebar instead of covering the game.
        val cappedWidthFraction = if (screen.isLandscape) {
            widthFraction.coerceAtMost(LANDSCAPE_MAX_WIDTH_FRACTION)
        } else {
            widthFraction
        }
        val width = (screen.usableWidth * cappedWidthFraction).toInt().coerceIn(minWidth, maxWidth)

        // In landscape the height fraction alone can produce a panel shorter than its own chrome, which
        // leaves the WebView with no room and is what makes page scrolling unreliable in a game. Raise
        // it to the compact chrome plus a scrollable content area - but never past what the screen can
        // give, so a tiny or split-screen window still degrades to "fill the usable area".
        val requestedHeight = (screen.usableHeight * heightFraction).toInt()
        val landscapeFloor = if (screen.isLandscape) {
            (dp(CHROME_COMPACT_DP.toFloat(), density) + dp(MIN_WEB_CONTENT_DP.toFloat(), density))
                .coerceAtMost(maxHeight)
        } else {
            0
        }
        val height = maxOf(requestedHeight, landscapeFloor).coerceIn(minHeight, maxHeight)
        return PxSize(width, height)
    }

    /**
     * The chrome layout that fits a panel of [panelHeightPx], plus the content height it leaves.
     *
     * Rows are dropped from the least important up - shortcut row first, then the tab strip - and only
     * when keeping them would squeeze the web content below [MIN_WEB_CONTENT_DP]. Nothing is removed
     * from the panel permanently: the same call with a taller panel returns them.
     */
    fun chromeLayout(panelHeightPx: Int, density: Float): ChromeLayout {
        val minContent = dp(MIN_WEB_CONTENT_DP.toFloat(), density)
        val full = dp(CHROME_FULL_DP.toFloat(), density)
        val compact = dp(CHROME_COMPACT_DP.toFloat(), density)
        val minimal = dp(CHROME_MINIMAL_DP.toFloat(), density)
        return when {
            panelHeightPx - full >= minContent ->
                ChromeLayout(
                    showTabStrip = true,
                    showShortcutRow = true,
                    showSecondaryToolbarButtons = true,
                    chromeHeightPx = full,
                    contentHeightPx = panelHeightPx - full,
                )

            panelHeightPx - compact >= minContent ->
                ChromeLayout(
                    showTabStrip = true,
                    showShortcutRow = false,
                    showSecondaryToolbarButtons = true,
                    chromeHeightPx = compact,
                    contentHeightPx = panelHeightPx - compact,
                )

            else ->
                ChromeLayout(
                    showTabStrip = false,
                    showShortcutRow = false,
                    showSecondaryToolbarButtons = false,
                    chromeHeightPx = minimal,
                    contentHeightPx = (panelHeightPx - minimal).coerceAtLeast(0),
                )
        }
    }

    /** Clamps an existing pixel size into the range the given screen can actually host. */
    fun clampPanelSize(size: PxSize, screen: Screen, density: Float): PxSize {
        val margin = dp(PANEL_MARGIN_DP.toFloat(), density) * 2
        val maxWidth = (screen.usableWidth - margin).coerceAtLeast(1)
        val maxHeight = (screen.usableHeight - margin).coerceAtLeast(1)
        val minWidth = dp(MIN_PANEL_WIDTH_DP.toFloat(), density).coerceAtMost(maxWidth)
        val minHeight = dp(MIN_PANEL_HEIGHT_DP.toFloat(), density).coerceAtMost(maxHeight)
        return PxSize(
            widthPx = size.widthPx.coerceIn(minWidth, maxWidth),
            heightPx = size.heightPx.coerceIn(minHeight, maxHeight),
        )
    }

    /** Current rectangle as persistable fractions of the usable area. */
    fun frameOf(origin: Point, size: PxSize, screen: Screen): PanelFrame = PanelFrame(
        xFraction = fractionOf(origin.x - screen.usableLeft, screen.usableWidth),
        yFraction = fractionOf(origin.y - screen.usableTop, screen.usableHeight),
        widthFraction = fractionOf(size.widthPx, screen.usableWidth),
        heightFraction = fractionOf(size.heightPx, screen.usableHeight),
    )

    /**
     * Back to pixels against the *current* screen, size clamped first so the origin is clamped
     * against the size that will really be used. Round-trips [frameOf] on an unchanged screen.
     */
    fun placementOf(frame: PanelFrame, screen: Screen, density: Float): PanelPlacement {
        // Rounded rather than truncated: these fractions came from the same rectangle on the same
        // screen, so rounding makes frameOf -> placementOf an exact round trip instead of losing a
        // pixel on every reopen.
        val size = clampPanelSize(
            PxSize(
                widthPx = (screen.usableWidth * frame.widthFraction.coerceIn(0f, 1f)).roundToInt(),
                heightPx = (screen.usableHeight * frame.heightFraction.coerceIn(0f, 1f)).roundToInt(),
            ),
            screen,
            density,
        )
        val origin = Point(
            x = screen.usableLeft + (screen.usableWidth * frame.xFraction.coerceIn(0f, 1f)).roundToInt(),
            y = screen.usableTop + (screen.usableHeight * frame.yFraction.coerceIn(0f, 1f)).roundToInt(),
        )
        return PanelPlacement(clampPanel(origin, size, screen), size)
    }

    /**
     * Resolves one resize step against the real screen limits.
     *
     * The size is clamped first and the moving edge then follows the *clamped* size. Getting that
     * order wrong is what makes a panel slide sideways when you push its left edge past the minimum
     * width: the origin would keep travelling while the width had already stopped shrinking.
     */
    fun resizePanel(origin: Point, resize: PanelResize, screen: Screen, density: Float): PanelPlacement {
        val size = clampPanelSize(PxSize(resize.widthPx, resize.heightPx), screen, density)
        val dx = if (resize.movesLeftEdge) resize.originDx + (resize.widthPx - size.widthPx) else 0
        val dy = if (resize.movesTopEdge) resize.originDy + (resize.heightPx - size.heightPx) else 0
        return PanelPlacement(clampPanel(Point(origin.x + dx, origin.y + dy), size, screen), size)
    }

    /**
     * Carries a panel across a screen change (rotation, split screen, fold) by keeping its relative
     * position instead of its pixel position, then clamping.
     *
     * Reusing portrait pixels in landscape is what puts the panel mostly off-screen; reusing the
     * *ratio* keeps it where the user left it in the only sense that survives a rotation.
     */
    fun remapPanel(origin: Point, size: PxSize, from: Screen, to: Screen): PanelPlacement {
        val remapped = if (from.usableWidth > 0 && from.usableHeight > 0) {
            Point(
                x = to.usableLeft + (to.usableWidth * fractionOf(origin.x - from.usableLeft, from.usableWidth)).toInt(),
                y = to.usableTop + (to.usableHeight * fractionOf(origin.y - from.usableTop, from.usableHeight)).toInt(),
            )
        } else {
            origin
        }
        return PanelPlacement(clampPanel(remapped, size, to), size)
    }

    /**
     * Carries a panel *size* across a screen change.
     *
     * The fractions the size represents are preserved and then put back through [sizeForFractions], so
     * a panel sized in portrait returns as a compact floating browser in landscape instead of spanning
     * the whole game - and a preset-sized panel returns as exactly the preset for the new orientation.
     */
    fun carryPanelSize(size: PxSize, from: Screen, to: Screen, density: Float): PxSize {
        if (from.usableWidth <= 0 || from.usableHeight <= 0) return clampPanelSize(size, to, density)
        return sizeForFractions(
            widthFraction = fractionOf(size.widthPx, from.usableWidth),
            heightFraction = fractionOf(size.heightPx, from.usableHeight),
            screen = to,
            density = density,
        )
    }

    /**
     * Carries the handle across a screen change, with the handle's own clamping rules - which are
     * looser horizontally than the panel's, because edge-hide mode parks it partly off-screen on
     * purpose and that has to survive a rotation.
     */
    fun remapHandle(origin: Point, handle: PxSize, from: Screen, to: Screen): Point {
        if (from.usableWidth <= 0 || from.usableHeight <= 0) return clampHandle(origin, handle, to)
        return clampHandle(
            Point(
                x = to.usableLeft + (to.usableWidth * fractionOf(origin.x - from.usableLeft, from.usableWidth)).toInt(),
                y = to.usableTop + (to.usableHeight * fractionOf(origin.y - from.usableTop, from.usableHeight)).toInt(),
            ),
            handle,
            to,
        )
    }

    private fun fractionOf(valuePx: Int, totalPx: Int): Float =
        if (totalPx <= 0) 0f else (valuePx.toFloat() / totalPx).coerceIn(0f, 1f)

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
