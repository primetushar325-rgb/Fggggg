package com.gamesidebar.browser.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks ORDER BY updated_at DESC, id DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): BookmarkEntity?

    @Query("SELECT * FROM bookmarks WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): BookmarkEntity?

    @Insert
    suspend fun insert(entity: BookmarkEntity): Long

    @Update
    suspend fun update(entity: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bookmarks")
    suspend fun clear()
}

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history ORDER BY visited_at DESC, id DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE url = :url AND visited_at > :since ORDER BY visited_at DESC LIMIT 1")
    suspend fun findRecent(url: String, since: Long): HistoryEntity?

    @Insert
    suspend fun insert(entity: HistoryEntity): Long

    @Update
    suspend fun update(entity: HistoryEntity)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history")
    suspend fun clear()

    /** Keeps the table bounded even if the in-memory trim is ever bypassed. */
    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY visited_at DESC LIMIT :keep)")
    suspend fun trimTo(keep: Int)
}

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes ORDER BY pinned DESC, updated_at DESC, id DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): NoteEntity?

    @Insert
    suspend fun insert(entity: NoteEntity): Long

    @Update
    suspend fun update(entity: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)
}
