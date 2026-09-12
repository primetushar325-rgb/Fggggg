package com.gamesidebar.core.tests

import com.gamesidebar.core.data.Bookmark
import com.gamesidebar.core.data.BookmarkOps
import com.gamesidebar.core.data.HistoryEntry
import com.gamesidebar.core.data.HistoryOps
import com.gamesidebar.core.data.HistoryPolicy
import com.gamesidebar.core.data.Note
import com.gamesidebar.core.data.NoteOps
import com.gamesidebar.core.harness.Assert
import com.gamesidebar.core.harness.TestCase
import com.gamesidebar.core.util.ClipboardOps

object DataStoreTests {

    private var ids = 0L
    private fun nextId() = ++ids

    val cases: List<TestCase> = listOf(
        TestCase("bookmark toggle adds then removes") {
            var items = emptyList<Bookmark>()
            items = BookmarkOps.toggle(items, "https://youtube.com/", "YouTube", 1000) { nextId() }
            Assert.equals(1, items.size)
            Assert.equals("YouTube", items.first().title)
            Assert.that(BookmarkOps.isBookmarked(items, "https://youtube.com/")) { "should be bookmarked" }
            items = BookmarkOps.toggle(items, "https://youtube.com", "YouTube", 2000) { nextId() }
            Assert.that(items.isEmpty()) { "toggle should remove" }
        },
        TestCase("same page with different casing or trailing slash is one bookmark") {
            var items = emptyList<Bookmark>()
            items = BookmarkOps.toggle(items, "https://Example.com/a/", "A", 1000) { nextId() }
            items = BookmarkOps.toggle(items, "http://example.com/a", "A renamed", 2000) { nextId() }
            Assert.that(items.isEmpty()) { "second toggle should have removed the existing entry" }
        },
        TestCase("bookmark list keeps newest first and can be renamed and moved") {
            var items = listOf(
                Bookmark(1, "Old", "https://old.test", 100, 100),
                Bookmark(2, "New", "https://new.test", 200, 200),
            )
            Assert.equals(2L, BookmarkOps.sortedNewestFirst(items).first().id)
            items = BookmarkOps.rename(items, 1, "Renamed", 300)
            Assert.equals("Renamed", items.first { it.id == 1L }.title)
            items = BookmarkOps.moveUrl(items, 1, "https://moved.test", 400)
            Assert.equals("https://moved.test", items.first { it.id == 1L }.url)
            items = BookmarkOps.remove(items, 1)
            Assert.equals(1, items.size)
        },
        TestCase("bookmark search matches title and url") {
            val items = listOf(
                Bookmark(1, "YouTube", "https://youtube.com", 1),
                Bookmark(2, "News", "https://news.test", 2),
            )
            Assert.equals(1, BookmarkOps.search(items, "you").size)
            Assert.equals(1, BookmarkOps.search(items, "NEWS.TEST").size)
            Assert.equals(2, BookmarkOps.search(items, "  ").size)
        },
        TestCase("history policy honours the switch and incognito") {
            Assert.that(HistoryPolicy(saveHistory = true, incognito = false).shouldRecord("https://a.test"))
            Assert.that(!HistoryPolicy(saveHistory = false, incognito = false).shouldRecord("https://a.test"))
            Assert.that(!HistoryPolicy(saveHistory = true, incognito = true).shouldRecord("https://a.test"))
            Assert.that(!HistoryPolicy(true, false).shouldRecord("about:blank"))
            Assert.that(!HistoryPolicy(true, false).shouldRecord(""))
        },
        TestCase("repeat visits inside the window update the row instead of duplicating") {
            var items = emptyList<HistoryEntry>()
            items = HistoryOps.record(items, "https://a.test", "A", 1_000) { nextId() }
            items = HistoryOps.record(items, "https://a.test", "A", 60_000) { nextId() }
            Assert.equals(1, items.size)
            Assert.equals(2, items.first().visits)
            Assert.equals(60_000L, items.first().visitedAt)
        },
        TestCase("visits outside the window create a new row") {
            var items = emptyList<HistoryEntry>()
            items = HistoryOps.record(items, "https://a.test", "A", 1_000) { nextId() }
            items = HistoryOps.record(items, "https://a.test", "A", 1_000 + HistoryOps.DEDUPE_WINDOW_MS + 1) { nextId() }
            Assert.equals(2, items.size)
        },
        TestCase("a better title replaces an empty one") {
            var items = emptyList<HistoryEntry>()
            items = HistoryOps.record(items, "https://a.test", "", 1_000) { nextId() }
            items = HistoryOps.record(items, "https://a.test", "Real title", 2_000) { nextId() }
            Assert.equals("Real title", items.first().title)
        },
        TestCase("history is capped") {
            var items = emptyList<HistoryEntry>()
            repeat(HistoryOps.MAX_ENTRIES + 50) { i ->
                items = HistoryOps.record(items, "https://a.test/$i", "P$i", i * 600_000L) { nextId() }
            }
            Assert.equals(HistoryOps.MAX_ENTRIES, items.size)
            Assert.that(items.first().url.endsWith("/${HistoryOps.MAX_ENTRIES + 49}")) { "newest entry was trimmed" }
        },
        TestCase("history can be filtered and deleted") {
            var items = listOf(
                HistoryEntry(1, "https://youtube.com", "YouTube", 1),
                HistoryEntry(2, "https://news.test", "News", 2),
            )
            Assert.equals(1, HistoryOps.search(items, "youtube").size)
            items = HistoryOps.remove(items, 1)
            Assert.equals(1, items.size)
        },
        TestCase("notes need a title or a body") {
            Assert.equals(null, NoteOps.create("  ", "  ", 1) { nextId() })
            val note = NoteOps.create("Room ID", "12345", 1) { nextId() }
            Assert.that(note != null) { "note should be created" }
            Assert.equals("12345", note?.preview)
        },
        TestCase("notes are pinned first, then most recently edited") {
            val items = listOf(
                Note(1, "A", "a", 100, 300),
                Note(2, "B", "b", 100, 200, pinned = true),
                Note(3, "C", "c", 100, 400),
            )
            val sorted = NoteOps.sorted(items)
            Assert.equals(2L, sorted.first().id)
            Assert.equals(3L, sorted[1].id)
            Assert.equals(1L, sorted[2].id)
        },
        TestCase("notes search covers title and body and ignores case") {
            val items = listOf(Note(1, "Enemy location", "mid lane", 1), Note(2, "Code", "AB12", 2))
            Assert.equals(1, NoteOps.search(items, "MID").size)
            Assert.equals(1, NoteOps.search(items, "code").size)
            Assert.equals(2, NoteOps.search(items, "").size)
        },
        TestCase("note edits are truncated and delete removes the row") {
            var items = listOf(NoteOps.create("T", "body", 1) { nextId() }!!)
            items = NoteOps.update(items, items.first().id, "New", "x".repeat(NoteOps.BODY_MAX_CHARS + 500), 2)
            Assert.equals(NoteOps.BODY_MAX_CHARS, items.first().body.length)
            items = NoteOps.togglePin(items, items.first().id)
            Assert.that(items.first().pinned) { "pin should toggle on" }
            items = NoteOps.remove(items, items.first().id)
            Assert.that(items.isEmpty()) { "note should be gone" }
        },
        TestCase("clipboard dedupes, caps and previews") {
            var items = emptyList<com.gamesidebar.core.util.ClipboardItem>()
            items = ClipboardOps.record(items, "room 42", "Notes", 1) { nextId() }
            items = ClipboardOps.record(items, "room 42", "Notes", 2) { nextId() }
            Assert.equals(1, items.size)
            Assert.equals(2L, items.first().createdAt)
            repeat(60) { i -> items = ClipboardOps.record(items, "text $i", "Notes", i.toLong()) { nextId() } }
            Assert.equals(ClipboardOps.MAX_ITEMS, items.size)
            Assert.that(items.first().text == "text 59") { "newest should be first" }
        },
        TestCase("clipboard ignores blank captures and previews long text") {
            val items = emptyList<com.gamesidebar.core.util.ClipboardItem>()
            Assert.that(items === ClipboardOps.record(items, "   ", "Notes", 1) { nextId() }) { "blank capture stored" }
            val long = "w".repeat(400)
            Assert.equals(ClipboardOps.PREVIEW_CHARS + 1, ClipboardOps.preview(long).length)
        },
    )
}
