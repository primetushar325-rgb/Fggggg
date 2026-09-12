package com.gamesidebar.browser.overlay

import com.gamesidebar.core.util.ClipboardItem
import com.gamesidebar.core.util.ClipboardOps

/**
 * In-memory clipboard history for the panel's Clipboard tool.
 *
 * Privacy rules, deliberately strict:
 *  - nothing is persisted: the list dies with the process;
 *  - nothing is read automatically: an entry is added only when the user taps "Capture clipboard",
 *    which is also the only moment the system clipboard is touched;
 *  - entries the system marks sensitive are refused outright (Android 13+ exposes the flag, and on
 *    older releases the capture is still explicit).
 */
object ClipboardHistory {

    private val items = mutableListOf<ClipboardItem>()
    private var nextId = 1L

    fun snapshot(): List<ClipboardItem> = items.toList()

    /** Returns null when the capture was refused (blank or system-marked sensitive). */
    fun capture(text: String, label: String, sensitive: Boolean, now: Long = System.currentTimeMillis()): List<ClipboardItem>? {
        if (sensitive || text.isBlank()) return null
        val updated = ClipboardOps.record(items, text, label, now) { nextId++ }
        items.clear()
        items += updated
        return items.toList()
    }

    fun remove(id: Long): List<ClipboardItem> {
        val updated = ClipboardOps.remove(items, id)
        items.clear()
        items += updated
        return items.toList()
    }

    fun clear(): List<ClipboardItem> {
        items.clear()
        return emptyList()
    }
}
