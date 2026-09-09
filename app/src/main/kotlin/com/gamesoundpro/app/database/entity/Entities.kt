package com.gamesoundpro.app.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A soundboard sound. Actual audio stays on disk inside app-private storage; this row only
 * holds metadata. [icon] is an emoji chosen by the user, [volume] a per-sound 0..1 gain and
 * [trimStartMs]/[trimEndMs] a playback window (trimEndMs == 0 means "until the end").
 */
@Entity(
    tableName = "sounds",
    indices = [Index("packId"), Index("category"), Index("isFavorite")],
)
data class SoundEntity(
    @PrimaryKey val id: String,
    val name: String,
    val filePath: String,
    val category: String,
    val packId: String? = null,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val icon: String = "🔊",
    val volume: Float = 1f,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val sortIndex: Int = 0,
    val createdAt: Long,
    val lastPlayedAt: Long? = null,
)

/** A user-created sound pack. A pack holds zero or more sounds ([SoundEntity.packId]). */
@Entity(tableName = "packs")
data class SoundPackEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String = "🎮",
    val sortIndex: Int = 0,
    val createdAt: Long,
)
