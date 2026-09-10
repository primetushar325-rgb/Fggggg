package com.gamesidebar.core.tests

import com.gamesidebar.core.harness.Assert
import com.gamesidebar.core.harness.TestCase
import com.gamesidebar.core.model.AppSettings
import com.gamesidebar.core.model.GlowColor
import com.gamesidebar.core.model.GlowIntensity
import com.gamesidebar.core.model.PanelSize
import com.gamesidebar.core.model.SearchEngine
import com.gamesidebar.core.service.OverlayCommand
import com.gamesidebar.core.util.Formatting
import java.time.ZoneOffset

object SettingsAndCommandTests {

    val cases: List<TestCase> = listOf(
        TestCase("defaults match the spec") {
            val s = AppSettings.DEFAULT
            Assert.equals(SearchEngine.GOOGLE, s.searchEngine)
            Assert.equals(90, s.panelOpacity)
            Assert.equals(PanelSize.MEDIUM, s.panelSize)
            Assert.that(s.javaScriptEnabled) { "javascript should be on by default" }
            Assert.that(s.cookiesEnabled) { "cookies should be on by default" }
            Assert.that(s.saveHistory) { "history should be saved by default" }
            Assert.that(!s.incognito) { "incognito should be off by default" }
            Assert.that(s.tapOutsideToClose) { "tap outside to close should default on" }
            Assert.that(s.autoSnap) { "auto snap should default on" }
        },
        TestCase("opacity is clamped into the allowed range") {
            Assert.equals(20, AppSettings.DEFAULT.withOpacity(0).panelOpacity)
            Assert.equals(100, AppSettings.DEFAULT.withOpacity(999).panelOpacity)
            Assert.equals(60, AppSettings.DEFAULT.withOpacity(60).panelOpacity)
            Assert.equals(0.6f, AppSettings.DEFAULT.withOpacity(60).opacityAlpha)
        },
        TestCase("the slider offers 20/40/60/80/100 plus the 90 default") {
            Assert.contains(AppSettings.OPACITY_STEPS, 20)
            Assert.contains(AppSettings.OPACITY_STEPS, 40)
            Assert.contains(AppSettings.OPACITY_STEPS, 60)
            Assert.contains(AppSettings.OPACITY_STEPS, 80)
            Assert.contains(AppSettings.OPACITY_STEPS, 100)
            Assert.contains(AppSettings.OPACITY_STEPS, 90)
        },
        TestCase("out of range opacity is rejected at construction") {
            Assert.throws<IllegalArgumentException> { AppSettings(panelOpacity = 5) }
            Assert.throws<IllegalArgumentException> { AppSettings(customPanelWidthFraction = 2f) }
        },
        TestCase("search engines expose home and search urls") {
            for (engine in SearchEngine.entries) {
                Assert.that(engine.homeUrl.startsWith("https://")) { "${engine.name} home is not https" }
                val url = engine.searchUrl("abc")
                Assert.that(url.contains("abc")) { "${engine.name} search url lost the query" }
                Assert.that(!url.contains("%s")) { "${engine.name} template was not filled" }
            }
        },
        TestCase("glow presets stay subtle") {
            for (glow in GlowIntensity.entries) {
                Assert.that(glow.alpha <= 0.6f) { "${glow.name} glow is too strong" }
                Assert.that(glow.radiusDp <= 20f) { "${glow.name} glow radius too large" }
            }
            Assert.equals(5, GlowColor.entries.size)
            for (colour in GlowColor.entries) {
                val alpha = (colour.argb ushr 24) and 0xFF
                Assert.equals(255, alpha, "${colour.name} should be fully opaque")
            }
        },
        TestCase("every overlay command round-trips through its action string") {
            for (command in OverlayCommand.entries) {
                Assert.equals(command, OverlayCommand.fromAction(command.action))
            }
            Assert.equals(null, OverlayCommand.fromAction("nonsense"))
            Assert.equals(null, OverlayCommand.fromAction(null))
        },
        TestCase("notification actions cover open, hide and stop") {
            val actions = OverlayCommand.entries.map { it.action }
            Assert.contains(actions, OverlayCommand.SHOW_PANEL.action)
            Assert.contains(actions, OverlayCommand.HIDE_PANEL.action)
            Assert.contains(actions, OverlayCommand.STOP_SERVICE.action)
        },
        TestCase("every command action is namespaced to the app") {
            for (command in OverlayCommand.entries) {
                Assert.that(command.action.startsWith("com.gamesidebar.browser.action.")) {
                    "unexpected action ${command.action}"
                }
            }
        },
        TestCase("relative time reads naturally") {
            val now = 1_700_000_000_000L
            Assert.equals("just now", Formatting.relativeTime(now - 10_000, now))
            Assert.equals("5m ago", Formatting.relativeTime(now - 5 * 60_000, now))
            Assert.equals("3h ago", Formatting.relativeTime(now - 3 * 3_600_000, now))
            Assert.equals("2d ago", Formatting.relativeTime(now - 2 * 86_400_000, now))
        },
        TestCase("day labels split today, yesterday and older") {
            val zone = ZoneOffset.UTC
            val now = 1_700_000_000_000L // 2023-11-14T22:13:20Z
            Assert.equals("Today", Formatting.dayLabel(now - 3_600_000, now, zone))
            Assert.equals("Yesterday", Formatting.dayLabel(now - 30 * 3_600_000, now, zone))
            Assert.equals("11 Nov 2023", Formatting.dayLabel(now - 3 * 86_400_000, now, zone))
        },
        TestCase("history is grouped into day sections in order") {
            val zone = ZoneOffset.UTC
            val now = 1_700_000_000_000L
            val items = listOf(now - 1000, now - 2000, now - 30 * 3_600_000, now - 31 * 3_600_000)
            val groups = Formatting.groupByDay(items, now, { it }, zone)
            Assert.equals(2, groups.size)
            Assert.equals("Today", groups[0].first)
            Assert.equals(2, groups[0].second.size)
            Assert.equals("Yesterday", groups[1].first)
            Assert.equals(2, groups[1].second.size)
        },
        TestCase("clock and date formatters do not crash on the epoch") {
            Assert.equals("00:00", Formatting.timeOfDay(0, ZoneOffset.UTC))
            Assert.equals("1 Jan 1970", Formatting.date(0, ZoneOffset.UTC))
        },
    )
}
