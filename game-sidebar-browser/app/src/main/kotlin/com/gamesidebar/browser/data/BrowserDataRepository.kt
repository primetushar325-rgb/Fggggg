package com.gamesidebar.browser.data

import com.gamesidebar.browser.data.db.AppDatabase
import com.gamesidebar.browser.data.db.BookmarkEntity
import com.gamesidebar.browser.data.db.HistoryEntity
import com.gamesidebar.browser.data.db.NoteEntity
import com.gamesidebar.browser.data.prefs.SettingsRepository
import com.gamesidebar.core.browser.UrlResolver
import com.gamesidebar.core.data.Bookmark
import com.gamesidebar.core.data.HistoryEntry
import com.gamesidebar.core.data.HistoryOps
import com.gamesidebar.core.data.HistoryPolicy
import com.gamesidebar.core.data.Note
import com.gamesidebar.core.data.NoteOps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Bookmarks, browsing history and notes.
 *
 * Every list-level rule (dedupe, ordering, search, caps) lives in `:core` and is unit-tested; this
 * class only maps entities and enforces the privacy policy at write time.
 */
class BrowserDataRepository(
    database: AppDatabase,
    private val settingsRepository: SettingsRepository,
) {
    private val bookmarkDao = database.bookmarkDao()
    private val historyDao = database.historyDao()
    private val noteDao = database.noteDao()

    val bookmarks: Flow<List<Bookmark>> = bookmarkDao.observeAll().map { rows ->
        rows.map { Bookmark(it.id, it.title, it.url, it.createdAt, it.updatedAt) }
    }

    val history: Flow<List<HistoryEntry>> = historyDao.observeAll().map { rows ->
        rows.map { HistoryEntry(it.id, it.url, it.title, it.visitedAt, it.visits) }
    }

    val notes: Flow<List<Note>> = noteDao.observeAll().map { rows ->
        rows.map { Note(it.id, it.title, it.body, it.createdAt, it.updatedAt, it.pinned) }
    }

    suspend fun isBookmarked(url: String): Boolean = findBookmark(url) != null

    /** Adds the bookmark, or removes it when the same page is already saved. */
    suspend fun toggleBookmark(url: String, title: String, now: Long = System.currentTimeMillis()) {
        val canonical = UrlResolver.canonicalKey(url)
        val existing = findBookmark(url)
        if (existing != null) {
            bookmarkDao.deleteById(existing.id)
            return
        }
        bookmarkDao.insert(
            BookmarkEntity(
                title = title.trim().ifBlank { UrlResolver.hostOf(url) ?: url },
                url = canonical,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun renameBookmark(id: Long, title: String, now: Long = System.currentTimeMillis()) {
        val current = bookmarkDao.findById(id) ?: return
        bookmarkDao.update(current.copy(title = title.trim(), updatedAt = now))
    }

    suspend fun moveBookmark(id: Long, url: String, now: Long = System.currentTimeMillis()) {
        val current = bookmarkDao.findById(id) ?: return
        bookmarkDao.update(current.copy(url = UrlResolver.canonicalKey(url), updatedAt = now))
    }

    suspend fun deleteBookmark(id: Long) = bookmarkDao.deleteById(id)

    suspend fun clearBookmarks() = bookmarkDao.clear()

    /**
     * Records a navigation unless the privacy policy says otherwise. Called from WebView callbacks,
     * so it stays cheap and never throws into the page-load path.
     */
    suspend fun recordVisit(url: String, title: String, now: Long = System.currentTimeMillis()) {
        val settings = settingsRepository.currentSettings()
        val policy = HistoryPolicy(saveHistory = settings.saveHistory, incognito = settings.incognito)
        if (!policy.shouldRecord(url)) return

        val canonical = UrlResolver.canonicalKey(url)
        val recent = historyDao.findRecent(canonical, now - HistoryOps.DEDUPE_WINDOW_MS)
        if (recent != null) {
            historyDao.update(
                recent.copy(
                    title = title.trim().ifBlank { recent.title },
                    visitedAt = now,
                    visits = recent.visits + 1,
                ),
            )
        } else {
            historyDao.insert(
                HistoryEntity(
                    url = canonical,
                    title = title.trim().ifBlank { UrlResolver.hostOf(url) ?: url },
                    visitedAt = now,
                ),
            )
            historyDao.trimTo(HistoryOps.MAX_ENTRIES)
        }
    }

    suspend fun deleteHistory(id: Long) = historyDao.deleteById(id)

    suspend fun clearHistory() = historyDao.clear()

    /** Returns the new row id, or null when both title and body are blank. */
    suspend fun createNote(title: String, body: String, now: Long = System.currentTimeMillis()): Long? {
        val note = NoteOps.create(title, body, now) { 0L } ?: return null
        return noteDao.insert(
            NoteEntity(title = note.title, body = note.body, createdAt = now, updatedAt = now),
        )
    }

    suspend fun updateNote(id: Long, title: String, body: String, now: Long = System.currentTimeMillis()) {
        val current = noteDao.findById(id) ?: return
        noteDao.update(
            current.copy(
                title = title.trim().take(NoteOps.TITLE_MAX_CHARS),
                body = body.take(NoteOps.BODY_MAX_CHARS),
                updatedAt = now,
            ),
        )
    }

    suspend fun toggleNotePin(id: Long) {
        val current = noteDao.findById(id) ?: return
        noteDao.update(current.copy(pinned = !current.pinned))
    }

    suspend fun deleteNote(id: Long) = noteDao.deleteById(id)

    /** Bookmarks are matched on the canonical URL, with the raw URL as a fallback. */
    private suspend fun findBookmark(url: String): BookmarkEntity? {
        val canonical = UrlResolver.canonicalKey(url)
        return bookmarkDao.findByUrl(canonical) ?: bookmarkDao.findByUrl(url)
    }
}
