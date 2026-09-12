package com.gamesidebar.core.browser

/**
 * Wire format for persisting the shortcut bar in a single DataStore string.
 *
 * One line per shortcut, fields separated by U+0001. That separator cannot appear in a title or
 * URL typed by a user (control characters are stripped on encode), so no escaping pass is needed
 * and decoding cannot be confused by a stray delimiter.
 */
object ShortcutCodec {

    private const val FIELD_SEPARATOR = '\u0001'
    private const val LINE_SEPARATOR = '\n'
    private val CONTROL_CHARS = Regex("[\\u0000-\\u001F\\u007F]")

    fun encode(items: List<Shortcut>): String = items.joinToString(LINE_SEPARATOR.toString()) { item ->
        listOf(item.id, item.title, item.url, item.iconKey)
            .joinToString(FIELD_SEPARATOR.toString()) { clean(it) }
    }

    /**
     * Decodes defensively: a malformed line is skipped rather than crashing the settings screen,
     * and an empty store falls back to the built-in defaults.
     */
    fun decode(stored: String?, fallback: List<Shortcut> = ShortcutCatalog.defaults): List<Shortcut> {
        if (stored.isNullOrBlank()) return fallback
        val items = stored.lineSequence().mapNotNull { line -> decodeLine(line) }.toList()
        return items.ifEmpty { fallback }
    }

    private fun decodeLine(line: String): Shortcut? {
        val parts = line.split(FIELD_SEPARATOR)
        if (parts.size < 3) return null
        val id = parts[0].trim()
        val url = parts[2].trim()
        if (id.isEmpty() || url.isEmpty()) return null
        return Shortcut(
            id = id,
            title = parts[1],
            url = url,
            iconKey = parts.getOrNull(3)?.ifBlank { ShortcutIcon.GENERIC.key } ?: ShortcutIcon.GENERIC.key,
        )
    }

    private fun clean(value: String): String = CONTROL_CHARS.replace(value, " ").trim()
}
