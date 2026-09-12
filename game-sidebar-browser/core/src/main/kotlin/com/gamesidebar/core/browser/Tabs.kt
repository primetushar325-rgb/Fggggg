package com.gamesidebar.core.browser

/**
 * Immutable tab strip state.
 *
 * Kept as a plain value type so the tab strip is testable without a WebView: the Android layer
 * holds one [Tabs] instance and a WebView per tab id, and every mutation goes through here.
 */
data class Tabs(
    val tabs: List<TabSnapshot> = emptyList(),
    val activeId: String? = null,
) {
    val active: TabSnapshot? get() = tabs.firstOrNull { it.id == activeId }
    val activeIndex: Int get() = tabs.indexOfFirst { it.id == activeId }
    val count: Int get() = tabs.size
    val isEmpty: Boolean get() = tabs.isEmpty()

    fun indexOf(id: String?): Int = tabs.indexOfFirst { it.id == id }

    fun tab(id: String?): TabSnapshot? = tabs.firstOrNull { it.id == id }

    fun add(tab: TabSnapshot, select: Boolean = true): Tabs {
        val next = tabs + tab
        return copy(tabs = next, activeId = if (select) tab.id else activeId)
    }

    /**
     * Closes a tab and picks the neighbour to select. Closing the last tab leaves an empty strip;
     * the caller decides whether to close the panel or open a fresh tab.
     */
    fun close(id: String): Tabs {
        val index = indexOf(id)
        if (index < 0) return this
        val next = tabs.toMutableList().also { it.removeAt(index) }
        val nextActive = when {
            activeId != id -> activeId
            next.isEmpty() -> null
            else -> next[index.coerceAtMost(next.lastIndex)].id
        }
        return copy(tabs = next, activeId = nextActive)
    }

    fun select(id: String): Tabs = if (indexOf(id) < 0) this else copy(activeId = id)

    fun selectRelative(offset: Int): Tabs {
        if (tabs.isEmpty()) return this
        val from = activeIndex.coerceAtLeast(0)
        val target = ((from + offset) % tabs.size + tabs.size) % tabs.size
        return copy(activeId = tabs[target].id)
    }

    fun next(): Tabs = selectRelative(1)
    fun previous(): Tabs = selectRelative(-1)

    fun update(id: String, transform: (TabSnapshot) -> TabSnapshot): Tabs =
        copy(tabs = tabs.map { if (it.id == id) transform(it) else it })

    /** Drag-to-reorder for tabs and shortcuts alike: `from` is clamped, invalid moves are no-ops. */
    fun move(from: Int, to: Int): Tabs {
        if (from !in tabs.indices || to !in tabs.indices || from == to) return this
        val next = tabs.toMutableList()
        next.add(to, next.removeAt(from))
        return copy(tabs = next)
    }

    companion object {
        const val MAX_TABS = 8
        const val TITLE_MAX_CHARS = 18

        fun start(homeUrl: String, idFactory: () -> String = ::randomId): Tabs {
            val tab = TabSnapshot(id = idFactory(), url = homeUrl, title = "")
            return Tabs(listOf(tab), tab.id)
        }

        fun randomId(): String = java.util.UUID.randomUUID().toString().take(8)

        /** Shortens a page title for the tab strip without breaking words mid-way when possible. */
        fun shortenTitle(title: String, maxChars: Int = TITLE_MAX_CHARS): String {
            val clean = title.replace('\n', ' ').replace('\r', ' ').trim()
                .replace(Regex("\\s+"), " ")
            if (clean.length <= maxChars) return clean
            val cut = clean.substring(0, maxChars - 1)
            val lastSpace = cut.lastIndexOf(' ')
            val trimmed = if (lastSpace >= maxChars / 2) cut.substring(0, lastSpace) else cut
            return "$trimmed…"
        }
    }
}

data class TabSnapshot(
    val id: String,
    val url: String,
    val title: String = "",
    val isDesktopMode: Boolean = false,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val faviconKey: String? = null,
) {
    val displayTitle: String
        get() = when {
            title.isNotBlank() -> title
            url.isBlank() -> "New tab"
            else -> UrlResolver.hostOf(url) ?: url
        }
}
