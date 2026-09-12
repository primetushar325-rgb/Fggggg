package com.gamesidebar.core.tests

import com.gamesidebar.core.browser.Shortcut
import com.gamesidebar.core.browser.ShortcutCatalog
import com.gamesidebar.core.browser.ShortcutCodec
import com.gamesidebar.core.harness.Assert
import com.gamesidebar.core.harness.TestCase

object ShortcutCodecTests {

    val cases: List<TestCase> = listOf(
        TestCase("round-trips the default bar") {
            val encoded = ShortcutCodec.encode(ShortcutCatalog.defaults)
            val decoded = ShortcutCodec.decode(encoded)
            Assert.equals(ShortcutCatalog.defaults, decoded)
        },
        TestCase("an empty store falls back to the defaults") {
            Assert.equals(ShortcutCatalog.defaults, ShortcutCodec.decode(null))
            Assert.equals(ShortcutCatalog.defaults, ShortcutCodec.decode("   "))
        },
        TestCase("malformed lines are skipped, not fatal") {
            val encoded = ShortcutCodec.encode(listOf(Shortcut("a", "A", "https://a.test")))
            val decoded = ShortcutCodec.decode("garbage\n\n$encoded\nalso-bad")
            Assert.equals(1, decoded.size)
            Assert.equals("https://a.test", decoded.first().url)
        },
        TestCase("an all-garbage store falls back") {
            Assert.equals(ShortcutCatalog.defaults, ShortcutCodec.decode("garbage\nmore-garbage"))
        },
        TestCase("control characters in a title cannot break the format") {
            val items = listOf(Shortcut("a", "Room\u0001ID\nsecond", "https://a.test"))
            val decoded = ShortcutCodec.decode(ShortcutCodec.encode(items))
            Assert.equals(1, decoded.size)
            Assert.equals("Room ID second", decoded.first().title)
        },
        TestCase("titles keep unicode and spaces") {
            val items = listOf(Shortcut("a", "Комната 42 — mid", "https://a.test/?q=а б"))
            val decoded = ShortcutCodec.decode(ShortcutCodec.encode(items))
            Assert.equals(items, decoded)
        },
        TestCase("a missing icon key decodes to the generic globe") {
            val decoded = ShortcutCodec.decode("a\u0001A\u0001https://a.test")
            Assert.equals("globe", decoded.first().iconKey)
        },
    )
}
