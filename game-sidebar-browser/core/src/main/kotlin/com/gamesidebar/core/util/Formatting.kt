package com.gamesidebar.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Display formatting shared by the overlay panel and the in-app screens.
 *
 * Uses `java.time`, which is platform-supported from API 26 - the app's minSdk - so no
 * desugaring dependency is required.
 */
object Formatting {

    private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy")

    fun timeOfDay(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    fun date(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DATE_FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    /** "Today" / "Yesterday" / "12 Mar 2026" - the section headers in the history list. */
    fun dayLabel(epochMillis: Long, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val day = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return when (day) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> DATE_FORMAT.format(day)
        }
    }

    fun relativeTime(epochMillis: Long, nowMillis: Long): String {
        val delta = (nowMillis - epochMillis).coerceAtLeast(0L)
        val minutes = delta / 60_000L
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 60 * 24 -> "${minutes / 60}h ago"
            minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)}d ago"
            else -> date(epochMillis)
        }
    }

    /** Groups history entries into day sections without allocating a map per row. */
    fun <T> groupByDay(
        items: List<T>,
        nowMillis: Long,
        timestampOf: (T) -> Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Pair<String, List<T>>> {
        val out = mutableListOf<Pair<String, List<T>>>()
        var currentLabel: String? = null
        var bucket = mutableListOf<T>()
        for (item in items) {
            val label = dayLabel(timestampOf(item), nowMillis, zone)
            if (label != currentLabel) {
                if (currentLabel != null) out += currentLabel to bucket.toList()
                currentLabel = label
                bucket = mutableListOf()
            }
            bucket += item
        }
        if (currentLabel != null) out += currentLabel to bucket.toList()
        return out
    }

    fun isSameDay(a: Long, b: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val dayA: LocalDate = Instant.ofEpochMilli(a).atZone(zone).toLocalDate()
        val dayB: LocalDate = Instant.ofEpochMilli(b).atZone(zone).toLocalDate()
        return dayA == dayB
    }
}

/**
 * Clipboard history entries.
 *
 * The app only records what the user copies from inside Game SideBar, plus clipboard content the
 * user explicitly captures from the Clipboard tool while the overlay is focused. Entries the system
 * marks sensitive are never stored ([sensitive] items are dropped on capture).
 */
data class ClipboardItem(
    val id: Long,
    val text: String,
    val label: String,
    val createdAt: Long,
    val sensitive: Boolean = false,
)

object ClipboardOps {

    const val MAX_ITEMS = 50
    const val PREVIEW_CHARS = 120

    fun record(items: List<ClipboardItem>, text: String, label: String, now: Long, idFactory: () -> Long): List<ClipboardItem> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return items
        val deduped = items.filterNot { it.text == trimmed }
        val entry = ClipboardItem(idFactory(), trimmed, label.trim().ifBlank { "Copied text" }, now)
        return (listOf(entry) + deduped).take(MAX_ITEMS)
    }

    fun remove(items: List<ClipboardItem>, id: Long): List<ClipboardItem> = items.filterNot { it.id == id }

    fun preview(text: String): String {
        val single = text.replace(Regex("\\s+"), " ").trim()
        return if (single.length <= PREVIEW_CHARS) single else single.take(PREVIEW_CHARS) + "…"
    }
}
