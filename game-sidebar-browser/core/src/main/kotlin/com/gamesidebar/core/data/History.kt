package com.gamesidebar.core.data

import com.gamesidebar.core.browser.UrlResolver

data class HistoryEntry(
    val id: Long = 0L,
    val url: String,
    val title: String,
    val visitedAt: Long,
    val visits: Int = 1,
) {
    val host: String? get() = UrlResolver.hostOf(url)
    val displayTitle: String get() = title.ifBlank { host ?: url }
}

/**
 * Whether a navigation should be written to history at all.
 *
 * Incognito keeps nothing; the "Don't save browsing history" switch keeps nothing either. Both are
 * checked at write time by the repository, so a page opened before the switch was flipped is never
 * retroactively recorded.
 */
data class HistoryPolicy(val saveHistory: Boolean, val incognito: Boolean) {
    val recordingEnabled: Boolean get() = saveHistory && !incognito

    fun shouldRecord(url: String): Boolean {
        if (!recordingEnabled) return false
        if (url.isBlank()) return false
        val scheme = UrlResolver.schemeOf(url)
        // about:blank and internal pages are noise in a history list.
        if (scheme == "about" || scheme == "data") return false
        return true
    }
}

object HistoryOps {

    const val MAX_ENTRIES = 500

    /** Consecutive hits on the same page within this window update the row instead of adding one. */
    const val DEDUPE_WINDOW_MS = 5 * 60 * 1000L

    fun record(
        items: List<HistoryEntry>,
        url: String,
        title: String,
        now: Long,
        idFactory: () -> Long,
    ): List<HistoryEntry> {
        val key = UrlResolver.canonicalKey(url)
        val recent = items.firstOrNull {
            UrlResolver.canonicalKey(it.url) == key && now - it.visitedAt <= DEDUPE_WINDOW_MS
        }
        val updated = if (recent != null) {
            items.map {
                if (it.id == recent.id) {
                    it.copy(
                        title = title.ifBlank { it.title },
                        visitedAt = now,
                        visits = it.visits + 1,
                    )
                } else {
                    it
                }
            }
        } else {
            val entry = HistoryEntry(
                id = idFactory(),
                url = url,
                title = title.trim().ifBlank { UrlResolver.hostOf(url) ?: url },
                visitedAt = now,
            )
            listOf(entry) + items
        }
        return trim(updated)
    }

    fun trim(items: List<HistoryEntry>): List<HistoryEntry> =
        if (items.size > MAX_ENTRIES) items.take(MAX_ENTRIES) else items

    fun remove(items: List<HistoryEntry>, id: Long): List<HistoryEntry> = items.filterNot { it.id == id }

    fun search(items: List<HistoryEntry>, query: String): List<HistoryEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return items
        return items.filter { it.title.lowercase().contains(q) || it.url.lowercase().contains(q) }
    }

    fun sortedNewestFirst(items: List<HistoryEntry>): List<HistoryEntry> =
        items.sortedWith(compareByDescending<HistoryEntry> { it.visitedAt }.thenByDescending { it.id })
}
