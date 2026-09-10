package com.gamesidebar.core.model

/**
 * Every persisted user preference, in one immutable value object.
 *
 * The Android layer maps this 1:1 onto DataStore keys ([com.gamesidebar.browser.data.prefs]), and
 * the overlay reads it as a whole so a settings change can never be observed half-applied.
 */
data class AppSettings(
    // General
    val autoStartSidebar: Boolean = false,
    val rememberPosition: Boolean = true,
    val tapOutsideToClose: Boolean = true,

    // Browser
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val desktopMode: Boolean = false,
    val javaScriptEnabled: Boolean = true,
    val cookiesEnabled: Boolean = true,
    val swipeBetweenTabs: Boolean = true,
    val httpsPreferred: Boolean = true,

    // Overlay
    val panelSize: PanelSize = PanelSize.MEDIUM,
    val customPanelWidthFraction: Float = 0.80f,
    val customPanelHeightFraction: Float = 0.52f,
    val panelVerticalAnchor: VerticalAnchor = VerticalAnchor.MIDDLE,
    val panelHorizontalAnchor: HorizontalAnchor = HorizontalAnchor.CENTER,
    val autoSnap: Boolean = true,
    val edgeHideMode: Boolean = false,
    val handleSize: HandleSize = HandleSize.MEDIUM,
    val handleVerticalAnchor: VerticalAnchor = VerticalAnchor.TOP,
    val handleHorizontalAnchor: HorizontalAnchor = HorizontalAnchor.CENTER,

    // Gaming
    val gamingMode: Boolean = false,
    val reducedAnimations: Boolean = false,

    // Appearance
    val panelOpacity: Int = 90,
    val glowEnabled: Boolean = true,
    val glowIntensity: GlowIntensity = GlowIntensity.MEDIUM,
    val glowColor: GlowColor = GlowColor.BLUE,

    // Privacy
    val saveHistory: Boolean = true,
    val incognito: Boolean = false,
) {
    init {
        require(panelOpacity in OPACITY_RANGE) { "panelOpacity must be within $OPACITY_RANGE" }
        require(customPanelWidthFraction in FRACTION_RANGE) { "panel width fraction out of range" }
        require(customPanelHeightFraction in FRACTION_RANGE) { "panel height fraction out of range" }
    }

    fun withOpacity(value: Int): AppSettings = copy(panelOpacity = value.coerceIn(OPACITY_RANGE))

    /** Opacity as the 0..1 alpha the panel background actually uses. */
    val opacityAlpha: Float get() = panelOpacity / 100f

    companion object {
        // Order matters: the instance init block reads these ranges, so DEFAULT has to be declared
        // last or the constructor would observe half-initialised constants.
        val OPACITY_RANGE: IntRange = 20..100
        val FRACTION_RANGE: ClosedFloatingPointRange<Float> = 0.35f..0.95f

        /** Values the opacity slider offers; the default sits between 80 and 100. */
        val OPACITY_STEPS: List<Int> = listOf(20, 40, 60, 80, 90, 100)

        val DEFAULT: AppSettings = AppSettings()
    }
}

enum class SearchEngine(
    val displayName: String,
    val homeUrl: String,
    private val searchTemplate: String,
) {
    GOOGLE("Google", "https://www.google.com/", "https://www.google.com/search?q=%s"),
    BING("Bing", "https://www.bing.com/", "https://www.bing.com/search?q=%s"),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/", "https://duckduckgo.com/?q=%s"),
    ;

    /** Builds the search URL for free text. Encoding is the URL layer's job. */
    fun searchUrl(query: String): String = searchTemplate.replace("%s", query)
}

enum class PanelSize(val displayName: String, val widthFraction: Float, val heightFraction: Float) {
    SMALL("Small", 0.62f, 0.38f),
    MEDIUM("Medium", 0.80f, 0.52f),
    LARGE("Large", 0.90f, 0.64f),
    CUSTOM("Custom", 0f, 0f),
    ;

    val isCustom: Boolean get() = this == CUSTOM
}

enum class VerticalAnchor(val displayName: String, val fraction: Float) {
    TOP("Top", 0.04f),
    MIDDLE("Middle", 0.5f),
    BOTTOM("Bottom", 0.96f),
}

enum class HorizontalAnchor(val displayName: String, val fraction: Float) {
    LEFT("Left", 0.04f),
    CENTER("Center", 0.5f),
    RIGHT("Right", 0.96f),
}

enum class HandleSize(val displayName: String, val widthDp: Int, val heightDp: Int) {
    SMALL("Small", 56, 6),
    MEDIUM("Medium", 74, 8),
    LARGE("Large", 90, 10),
}

enum class GlowIntensity(val displayName: String, val radiusDp: Float, val alpha: Float) {
    LOW("Low", 6f, 0.28f),
    MEDIUM("Medium", 12f, 0.42f),
    HIGH("High", 20f, 0.60f),
}

enum class GlowColor(val displayName: String, val argb: Int) {
    WHITE("White", 0xFFFFFFFF.toInt()),
    BLUE("Blue", 0xFF4C8DFF.toInt()),
    PURPLE("Purple", 0xFF9A6BFF.toInt()),
    CYAN("Cyan", 0xFF37E2D5.toInt()),
    RED("Red", 0xFFFF5C6C.toInt()),
}
