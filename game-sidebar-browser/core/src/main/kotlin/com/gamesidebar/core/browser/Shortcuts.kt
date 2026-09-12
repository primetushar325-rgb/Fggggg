package com.gamesidebar.core.browser

import com.gamesidebar.core.model.SearchEngine

/**
 * Quick-access website shortcuts shown in the panel toolbar.
 *
 * The catalogue ships with the seven defaults from the spec; users can add, edit, delete and
 * reorder freely, and everything is persisted locally (DataStore) - nothing is fetched remotely.
 */
data class Shortcut(
    val id: String,
    val title: String,
    val url: String,
    val iconKey: String = ShortcutIcon.GENERIC.key,
) {
    val normalizedTitle: String get() = title.trim().ifBlank { UrlResolver.hostOf(url) ?: url }
}

/** Icon keys, mapped to real vector drawables in the Android layer (never emoji). */
enum class ShortcutIcon(val key: String, val label: String, val drawableName: String) {
    YOUTUBE("youtube", "YouTube", "ic_sc_youtube"),
    GOOGLE("google", "Google", "ic_sc_google"),
    FACEBOOK("facebook", "Facebook", "ic_sc_facebook"),
    WHATSAPP("whatsapp", "WhatsApp", "ic_sc_whatsapp"),
    TELEGRAM("telegram", "Telegram", "ic_sc_telegram"),
    GMAIL("gmail", "Gmail", "ic_sc_gmail"),
    DRIVE("drive", "Drive", "ic_sc_drive"),
    SEARCH("search", "Search", "ic_search"),
    STAR("star", "Star", "ic_bookmark_filled"),
    GENERIC("globe", "Website", "ic_browser"),
    ;

    companion object {
        private val byKey = entries.associateBy { it.key }
        fun fromKey(key: String?): ShortcutIcon = byKey[key] ?: GENERIC
        val all: List<ShortcutIcon> = entries
    }
}

object ShortcutCatalog {

    val defaults: List<Shortcut> = listOf(
        Shortcut("sc-youtube", "YouTube", "https://www.youtube.com/", ShortcutIcon.YOUTUBE.key),
        Shortcut("sc-google", "Google", "https://www.google.com/", ShortcutIcon.GOOGLE.key),
        Shortcut("sc-facebook", "Facebook", "https://m.facebook.com/", ShortcutIcon.FACEBOOK.key),
        Shortcut("sc-whatsapp", "WhatsApp", "https://web.whatsapp.com/", ShortcutIcon.WHATSAPP.key),
        Shortcut("sc-telegram", "Telegram", "https://web.telegram.org/", ShortcutIcon.TELEGRAM.key),
        Shortcut("sc-gmail", "Gmail", "https://mail.google.com/", ShortcutIcon.GMAIL.key),
        Shortcut("sc-drive", "Drive", "https://drive.google.com/", ShortcutIcon.DRIVE.key),
    )

    const val MAX_SHORTCUTS = 12
    const val TITLE_MAX_CHARS = 14
}

/** Immutable list operations for the shortcut bar. */
data class ShortcutList(val items: List<Shortcut> = ShortcutCatalog.defaults) {

    val canAdd: Boolean get() = items.size < ShortcutCatalog.MAX_SHORTCUTS

    fun add(shortcut: Shortcut): ShortcutList =
        if (!canAdd) this else copy(items = items + shortcut)

    fun update(id: String, title: String, url: String, iconKey: String): ShortcutList =
        copy(
            items = items.map {
                if (it.id == id) it.copy(title = title.trim(), url = url.trim(), iconKey = iconKey) else it
            },
        )

    fun remove(id: String): ShortcutList = copy(items = items.filterNot { it.id == id })

    fun move(from: Int, to: Int): ShortcutList {
        if (from !in items.indices || to !in items.indices || from == to) return this
        val next = items.toMutableList()
        next.add(to, next.removeAt(from))
        return copy(items = next)
    }

    fun indexOf(id: String): Int = items.indexOfFirst { it.id == id }

    companion object {
        /** Validates user input for the add/edit dialog; returns a stable message key. */
        fun validate(title: String, url: String): String? {
            if (url.isBlank()) return "shortcut_error_url_required"
            val resolution = UrlResolver.resolve(url, SearchEngine.GOOGLE)
            if (resolution is UrlResolver.Resolution.Search) return "shortcut_error_invalid_url"
            if (title.length > 40) return "shortcut_error_title_long"
            return null
        }

        fun newId(existing: List<Shortcut>): String {
            var candidate = "sc-${existing.size + 1}"
            var suffix = 0
            while (existing.any { it.id == candidate }) {
                suffix++
                candidate = "sc-${existing.size + 1}-$suffix"
            }
            return candidate
        }
    }
}
