package com.gamesidebar.core.data

data class Note(
    val id: Long = 0L,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val pinned: Boolean = false,
) {
    val preview: String
        get() = body.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()

    val isEmpty: Boolean get() = title.isBlank() && body.isBlank()
}

/** Quick notes for in-match information: room IDs, callouts, codes. Stored locally only. */
object NoteOps {

    const val TITLE_MAX_CHARS = 60
    const val BODY_MAX_CHARS = 4000

    fun create(title: String, body: String, now: Long, idFactory: () -> Long): Note? {
        val cleanTitle = title.trim().take(TITLE_MAX_CHARS)
        val cleanBody = body.trim().take(BODY_MAX_CHARS)
        if (cleanTitle.isEmpty() && cleanBody.isEmpty()) return null
        return Note(
            id = idFactory(),
            title = cleanTitle,
            body = cleanBody,
            createdAt = now,
            updatedAt = now,
        )
    }

    fun update(items: List<Note>, id: Long, title: String, body: String, now: Long): List<Note> =
        items.map {
            if (it.id == id) {
                it.copy(
                    title = title.trim().take(TITLE_MAX_CHARS),
                    body = body.take(BODY_MAX_CHARS),
                    updatedAt = now,
                )
            } else {
                it
            }
        }

    fun remove(items: List<Note>, id: Long): List<Note> = items.filterNot { it.id == id }

    fun togglePin(items: List<Note>, id: Long): List<Note> =
        items.map { if (it.id == id) it.copy(pinned = !it.pinned) else it }

    fun search(items: List<Note>, query: String): List<Note> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return items
        return items.filter { it.title.lowercase().contains(q) || it.body.lowercase().contains(q) }
    }

    /** Pinned first, then most recently edited. */
    fun sorted(items: List<Note>): List<Note> = items.sortedWith(
        compareByDescending<Note> { it.pinned }
            .thenByDescending { it.updatedAt }
            .thenByDescending { it.id },
    )
}
