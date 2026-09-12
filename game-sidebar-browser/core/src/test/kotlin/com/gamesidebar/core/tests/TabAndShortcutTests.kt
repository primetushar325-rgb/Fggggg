package com.gamesidebar.core.tests

import com.gamesidebar.core.browser.Shortcut
import com.gamesidebar.core.browser.ShortcutCatalog
import com.gamesidebar.core.browser.ShortcutIcon
import com.gamesidebar.core.browser.ShortcutList
import com.gamesidebar.core.browser.TabSnapshot
import com.gamesidebar.core.browser.Tabs
import com.gamesidebar.core.harness.Assert
import com.gamesidebar.core.harness.TestCase

object TabAndShortcutTests {

    private var counter = 0
    private fun id() = "t${counter++}"

    val cases: List<TestCase> = listOf(
        TestCase("a new strip starts with one tab selected") {
            val tabs = Tabs.start("https://www.google.com/") { id() }
            Assert.equals(1, tabs.count)
            Assert.that(tabs.active != null) { "no active tab" }
            Assert.equals("https://www.google.com/", tabs.active?.url)
        },
        TestCase("adding a tab selects it") {
            val tabs = Tabs.start("https://a.test") { id() }.add(TabSnapshot(id(), "https://b.test"))
            Assert.equals(2, tabs.count)
            Assert.equals("https://b.test", tabs.active?.url)
        },
        TestCase("closing the active tab selects the neighbour") {
            val first = id()
            val second = id()
            val tabs = Tabs(listOf(TabSnapshot(first, "a"), TabSnapshot(second, "b")), second).close(second)
            Assert.equals(1, tabs.count)
            Assert.equals(first, tabs.activeId)
        },
        TestCase("closing the last tab empties the strip") {
            val only = id()
            val tabs = Tabs(listOf(TabSnapshot(only, "a")), only).close(only)
            Assert.that(tabs.isEmpty) { "strip should be empty" }
            Assert.equals(null, tabs.activeId)
        },
        TestCase("closing an unknown id changes nothing") {
            val tabs = Tabs.start("https://a.test") { id() }
            Assert.equals(tabs, tabs.close("nope"))
        },
        TestCase("select wraps around both ways") {
            val ids = List(3) { id() }
            val tabs = Tabs(ids.map { TabSnapshot(it, "u$it") }, ids[0])
            Assert.equals(ids[2], tabs.previous().activeId)
            Assert.equals(ids[1], tabs.next().activeId)
        },
        TestCase("reordering moves a tab and keeps selection") {
            val ids = List(3) { id() }
            val tabs = Tabs(ids.map { TabSnapshot(it, "u$it") }, ids[0])
            val moved = tabs.move(0, 2)
            Assert.equals(listOf(ids[1], ids[2], ids[0]), moved.tabs.map { it.id })
            Assert.equals(ids[0], moved.activeId)
        },
        TestCase("invalid reorder is a no-op") {
            val tabs = Tabs.start("https://a.test") { id() }
            Assert.equals(tabs, tabs.move(0, 5))
        },
        TestCase("titles are shortened without breaking words when possible") {
            Assert.equals("YouTube", Tabs.shortenTitle("YouTube"))
            val short = Tabs.shortenTitle("WhatsApp Web - fast messaging in the browser")
            Assert.that(short.length <= Tabs.TITLE_MAX_CHARS) { "title too long: $short (${short.length})" }
            Assert.that(short.endsWith("…")) { "expected ellipsis: $short" }
            Assert.that(!short.contains("  ")) { "double space in $short" }
        },
        TestCase("newlines are stripped from titles") {
            Assert.equals("A B", Tabs.shortenTitle("A\n\n  B  \r"))
        },
        TestCase("tab display title falls back to host then to New tab") {
            Assert.equals("a.test", TabSnapshot("x", "https://a.test/path").displayTitle)
            Assert.equals("New tab", TabSnapshot("x", "").displayTitle)
            Assert.equals("Named", TabSnapshot("x", "https://a.test", "Named").displayTitle)
        },
        TestCase("default shortcuts are the seven from the spec") {
            Assert.equals(7, ShortcutCatalog.defaults.size)
            val hosts = ShortcutCatalog.defaults.map { it.url }
            Assert.contains(hosts, "https://www.youtube.com/")
            Assert.contains(hosts, "https://web.whatsapp.com/")
            Assert.contains(hosts, "https://drive.google.com/")
        },
        TestCase("every default shortcut has a real vector icon key") {
            for (shortcut in ShortcutCatalog.defaults) {
                val icon = ShortcutIcon.fromKey(shortcut.iconKey)
                Assert.notEquals(ShortcutIcon.GENERIC, icon, "shortcut ${shortcut.title} has no dedicated icon")
                Assert.that(icon.drawableName.startsWith("ic_")) { "bad drawable ${icon.drawableName}" }
            }
        },
        TestCase("unknown icon keys fall back to the generic globe") {
            Assert.equals(ShortcutIcon.GENERIC, ShortcutIcon.fromKey("not-a-key"))
            Assert.equals(ShortcutIcon.GENERIC, ShortcutIcon.fromKey(null))
        },
        TestCase("shortcuts can be added, edited, reordered and deleted") {
            var list = ShortcutList()
            val added = Shortcut("sc-new", "Arena", "https://arena.gg", ShortcutIcon.STAR.key)
            list = list.add(added)
            Assert.equals(8, list.items.size)
            list = list.update("sc-new", "Arena GG", "https://arena.gg/home", ShortcutIcon.SEARCH.key)
            Assert.equals("Arena GG", list.items.last().title)
            Assert.equals("https://arena.gg/home", list.items.last().url)
            list = list.move(7, 0)
            Assert.equals("sc-new", list.items.first().id)
            list = list.remove("sc-new")
            Assert.equals(7, list.items.size)
        },
        TestCase("shortcut cap is enforced") {
            var list = ShortcutList()
            repeat(10) { i -> list = list.add(Shortcut("sc-$i", "S$i", "https://s$i.test")) }
            Assert.equals(ShortcutCatalog.MAX_SHORTCUTS, list.items.size)
            Assert.that(!list.canAdd) { "should not accept more shortcuts" }
        },
        TestCase("shortcut validation rejects junk and accepts urls") {
            Assert.equals("shortcut_error_url_required", ShortcutList.validate("Name", "  "))
            Assert.equals("shortcut_error_invalid_url", ShortcutList.validate("Name", "hello there"))
            Assert.equals(null, ShortcutList.validate("Name", "https://ok.test"))
            Assert.equals(null, ShortcutList.validate("Name", "ok.test"))
        },
        TestCase("generated shortcut ids never collide") {
            val existing = listOf(Shortcut("sc-1", "a", "https://a.test"), Shortcut("sc-2", "b", "https://b.test"))
            val first = ShortcutList.newId(existing)
            val withNew = existing + Shortcut(first, "c", "https://c.test")
            val second = ShortcutList.newId(withNew)
            Assert.notEquals(first, second)
            Assert.that(withNew.none { it.id == second }) { "id collision: $second" }
        },
    )
}
