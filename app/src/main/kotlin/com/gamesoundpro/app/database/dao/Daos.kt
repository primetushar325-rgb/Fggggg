package com.gamesoundpro.app.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gamesoundpro.app.database.entity.SoundEntity
import com.gamesoundpro.app.database.entity.SoundPackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SoundDao {

    @Query("SELECT * FROM sounds")
    fun observeAll(): Flow<List<SoundEntity>>

    @Query("SELECT * FROM sounds WHERE id = :id")
    suspend fun byId(id: String): SoundEntity?

    @Query("SELECT * FROM sounds WHERE packId = :packId ORDER BY sortIndex ASC, createdAt ASC")
    fun observePack(packId: String): Flow<List<SoundEntity>>

    @Query("SELECT * FROM sounds WHERE packId IS :packId ORDER BY sortIndex ASC, createdAt ASC")
    suspend fun packMembersOnce(packId: String?): List<SoundEntity>

    @Query("SELECT * FROM sounds WHERE isFavorite = 1 ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun observeFavorites(limit: Int): Flow<List<SoundEntity>>

    @Query("SELECT * FROM sounds WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun observeRecents(limit: Int): Flow<List<SoundEntity>>

    @Query("SELECT * FROM sounds ORDER BY playCount DESC, lastPlayedAt DESC LIMIT :limit")
    fun observeMostPlayed(limit: Int): Flow<List<SoundEntity>>

    @Query("SELECT * FROM sounds WHERE category = :category ORDER BY name COLLATE NOCASE ASC")
    suspend fun byCategory(category: String): List<SoundEntity>

    @Query("SELECT COUNT(*) FROM sounds")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM sounds WHERE category = :category")
    suspend fun countByCategory(category: String): Int

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM sounds")
    suspend fun totalBytes(): Long

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM sounds WHERE playCount = 0")
    suspend fun unusedBytes(): Long

    @Query("SELECT * FROM sounds WHERE playCount = 0 ORDER BY createdAt ASC")
    suspend fun unusedSounds(): List<SoundEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sound: SoundEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sounds: List<SoundEntity>)

    @Update
    suspend fun update(sound: SoundEntity)

    @Delete
    suspend fun delete(sound: SoundEntity)

    @Query("UPDATE sounds SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE sounds SET playCount = playCount + 1, lastPlayedAt = :now WHERE id = :id")
    suspend fun incrementPlay(id: String, now: Long)

    @Query("UPDATE sounds SET packId = :packId WHERE id = :id")
    suspend fun setPack(id: String, packId: String?)

    @Query("UPDATE sounds SET sortIndex = :index WHERE id = :id")
    suspend fun setSortIndex(id: String, index: Int)

    @Query("SELECT COALESCE(MAX(sortIndex), -1) FROM sounds WHERE packId = :packId")
    suspend fun maxSortIndex(packId: String?): Int
}

@Dao
interface SoundPackDao {

    @Query("SELECT * FROM packs ORDER BY sortIndex ASC, createdAt ASC")
    fun observeAll(): Flow<List<SoundPackEntity>>

    @Query("SELECT * FROM packs WHERE id = :id")
    suspend fun byId(id: String): SoundPackEntity?

    @Query("SELECT * FROM packs WHERE id = :id")
    fun observeById(id: String): Flow<SoundPackEntity?>

    @Query("SELECT COUNT(*) FROM packs")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sortIndex), -1) FROM packs")
    suspend fun maxSortIndex(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pack: SoundPackEntity)

    @Update
    suspend fun update(pack: SoundPackEntity)

    @Delete
    suspend fun delete(pack: SoundPackEntity)

    @Query("UPDATE packs SET name = :name, icon = :icon WHERE id = :id")
    suspend fun rename(id: String, name: String, icon: String)
}
