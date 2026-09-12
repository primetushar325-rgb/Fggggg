package com.gamesidebar.core.data

import com.gamesidebar.core.browser.UrlResolver

data class Bookmark(
    val id: Long = 0L,
    val title: String,
    val url: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
) {
    val host: String? get() = UrlResolver.hostOf(url)
    val displayTitle: String get() = title.ifBlank { host ?: url }
}

/** List-level rules for bookmarks: dedupe by canonical URL, newest first, tolerant search. */
object BookmarkOps {

    fun isBookmarked(items: List<Bookmark>, url: String): Boolean =
        items.any { UrlResolver.canonicalKey(it.url) == UrlResolver.canonicalKey(url) }

    /** Adds, or refreshes the title of an existing entry for the same canonical URL. */
    fun toggle(items: List<Bookmark>, url: String, title: String, now: Long, idFactory: () -> Long): List<Bookmark> {
        val key = UrlResolver.canonicalKey(url)
        val existing = items.firstOrNull { UrlResolver.canonicalKey(it.url) == key }
        return if (existing != null) {
            items.filterNot { it.id == existing.id }
        } else {
            val entry = Bookmark(
                id = idFactory(),
                title = title.trim().ifBlank { UrlResolver.hostOf(url) ?: url },
                url = url,
                createdAt = now,
                updatedAt = now,
            )
            (listOf(entry) + items)
        }
    }

    fun remove(items: List<Bookmark>, id: Long): List<Bookmark> = items.filterNot { it.id == id }

    fun rename(items: List<Bookmark>, id: Long, title: String, now: Long): List<Bookmark> =
        items.map { if (it.id == id) it.copy(title = title.trim(), updatedAt = now) else it }

    fun moveUrl(items: List<Bookmark>, id: Long, url: String, now: Long): List<Bookmark> =
        items.map { if (it.id == id) it.copy(url = url.trim(), updatedAt = now) else it }

    fun search(items: List<Bookmark>, query: String): List<Bookmark> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return items
        return items.filter {
            it.title.lowercase().contains(q) || it.url.lowercase().contains(q)
        }
    }

    fun sortedNewestFirst(items: List<Bookmark>): List<Bookmark> =
        items.sortedWith(compareByDescending<Bookmark> { it.updatedAt }.thenByDescending { it.id })
}
