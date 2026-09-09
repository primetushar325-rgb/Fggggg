package com.gamesoundpro.app.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.gamesoundpro.app.database.dao.SoundDao
import com.gamesoundpro.app.database.dao.SoundPackDao
import com.gamesoundpro.app.database.entity.SoundEntity
import com.gamesoundpro.app.database.entity.SoundPackEntity
import com.gamesoundpro.app.domain.Category
import com.gamesoundpro.app.domain.StorageStats
import com.gamesoundpro.app.utils.AudioFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/** Metadata collected by the Add-Sound / Record / Edit flows before a row is written. */
data class SoundMeta(
    val name: String,
    val category: Category = Category.DEFAULT,
    val icon: String = "🔊",
    val volume: Float = 1f,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val packId: String? = null,
)

data class PackImportResult(
    val packName: String,
    val imported: Int,
    val skipped: Int,
)

/**
 * Repository: all metadata CRUD plus on-device audio file management. Audio lives in
 * app-private storage (filesDir/sounds, filesDir/recordings) and never leaves the device.
 */
class SoundRepository(
    private val context: Context,
    private val soundDao: SoundDao,
    private val packDao: SoundPackDao,
) {

    private val soundsDir: File get() = File(context.filesDir, "sounds").apply { mkdirs() }
    private val recordingsDir: File get() = File(context.filesDir, "recordings").apply { mkdirs() }

    // ---- Observables ---------------------------------------------------------------

    val sounds: Flow<List<SoundEntity>> = soundDao.observeAll()
    val packs: Flow<List<SoundPackEntity>> = packDao.observeAll()
    val favorites: Flow<List<SoundEntity>> = soundDao.observeFavorites(limit = 500)
    val recents: Flow<List<SoundEntity>> = soundDao.observeRecents(limit = 50)
    val mostPlayed: Flow<List<SoundEntity>> = soundDao.observeMostPlayed(limit = 15)

    /** Favorites first, then recent — the compact overlay's quick buttons. */
    val quickOverlaySounds: Flow<List<SoundEntity>> =
        combine(soundDao.observeFavorites(9), soundDao.observeRecents(9)) { f, r ->
            (f + r).distinctBy { it.id }.take(9)
        }

    fun observePack(packId: String): Flow<List<SoundEntity>> = soundDao.observePack(packId)

    /** Music-player playlist: every sound tagged with the Music category. */
    val musicTracks: Flow<List<SoundEntity>> =
        sounds.map { list -> list.filter { it.category == Category.MUSIC.key }.sortedBy { it.name.lowercase() } }

    // ---- Sounds --------------------------------------------------------------------

    suspend fun soundById(id: String): SoundEntity? = soundDao.byId(id)

    /** Imports a user-picked audio file by copying it into app-private storage. */
    suspend fun addSoundFromUri(uri: Uri, meta: SoundMeta): Result<SoundEntity> =
        withContext(Dispatchers.IO) {
            try {
                val displayName = queryDisplayName(uri) ?: "imported audio"
                val extension = AudioFiles.extensionOf(displayName)
                    .ifBlank { "mp3" }
                if (extension !in AudioFiles.SUPPORTED_EXTENSIONS) {
                    return@withContext Result.failure(
                        IOException("Unsupported format .$extension — use MP3, WAV, M4A, OGG, AAC, FLAC or OPUS")
                    )
                }
                val target = File(soundsDir, "${UUID.randomUUID()}.$extension")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext Result.failure(IOException("Cannot open the selected file"))

                insertImportedFile(target, meta, fallbackName = AudioFiles.baseName(displayName))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Copies a picked file into app-private storage without inserting it yet (Add-Sound form). */
    suspend fun stageImport(uri: Uri): Result<Pair<File, Long>> = withContext(Dispatchers.IO) {
        try {
            val displayName = queryDisplayName(uri) ?: "sound"
            val extension = AudioFiles.extensionOf(displayName).ifBlank { "mp3" }
            if (extension !in AudioFiles.SUPPORTED_EXTENSIONS) {
                return@withContext Result.failure(
                    IOException("Unsupported format .$extension — use MP3, WAV, M4A, OGG, AAC, FLAC or OPUS")
                )
            }
            val target = File(soundsDir, "${UUID.randomUUID()}.$extension")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext Result.failure(IOException("Cannot open the selected file"))
            Result.success(target to AudioFiles.readDurationMs(target.absolutePath))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Registers an already-written recording file (kept where VoiceRecorder wrote it). */
    suspend fun addRecording(file: File, meta: SoundMeta): Result<SoundEntity> =
        withContext(Dispatchers.IO) {
            try {
                if (!file.exists() || file.length() == 0L) {
                    return@withContext Result.failure(IOException("The recording is empty"))
                }
                insertImportedFile(file, meta, fallbackName = "Recording")
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Registers a file staged by [stageImport] once the user saves the metadata form. */
    suspend fun addStagedFile(file: File, meta: SoundMeta): Result<SoundEntity> =
        addRecording(file, meta)

    private suspend fun insertImportedFile(file: File, meta: SoundMeta, fallbackName: String): Result<SoundEntity> {
        val duration = AudioFiles.readDurationMs(file.absolutePath)
        val entity = SoundEntity(
            id = UUID.randomUUID().toString(),
            name = meta.name.ifBlank { fallbackName },
            filePath = file.absolutePath,
            category = meta.category.key,
            packId = meta.packId,
            durationMs = duration,
            sizeBytes = file.length(),
            icon = meta.icon,
            volume = meta.volume.coerceIn(0.1f, 1.5f),
            trimStartMs = meta.trimStartMs.coerceIn(0L, duration),
            trimEndMs = meta.trimEndMs.coerceIn(0L, duration),
            sortIndex = soundDao.maxSortIndex(meta.packId) + 1,
            createdAt = System.currentTimeMillis(),
        )
        soundDao.insert(entity)
        return Result.success(entity)
    }

    suspend fun updateSound(sound: SoundEntity) = withContext(Dispatchers.IO) {
        soundDao.update(sound.copy(volume = sound.volume.coerceIn(0.1f, 1.5f)))
    }

    suspend fun toggleFavorite(sound: SoundEntity) = withContext(Dispatchers.IO) {
        soundDao.setFavorite(sound.id, !sound.isFavorite)
    }

    suspend fun deleteSound(sound: SoundEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Only delete files we own (inside our private dirs).
            val file = File(sound.filePath)
            if (file.absolutePath.startsWith(context.filesDir.absolutePath) && file.exists()) {
                file.delete()
            }
            soundDao.delete(sound)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun duplicateSound(sound: SoundEntity): Result<SoundEntity> = withContext(Dispatchers.IO) {
        try {
            val source = File(sound.filePath)
            val extension = source.extension.ifBlank { "mp3" }
            val copy = File(soundsDir, "${UUID.randomUUID()}.$extension")
            if (source.exists()) source.copyTo(copy, overwrite = true)
            val duplicate = sound.copy(
                id = UUID.randomUUID().toString(),
                name = "${sound.name} (copy)",
                filePath = copy.absolutePath,
                createdAt = System.currentTimeMillis(),
                playCount = 0,
                lastPlayedAt = null,
                isFavorite = false,
                sortIndex = soundDao.maxSortIndex(sound.packId) + 1,
            )
            soundDao.insert(duplicate)
            Result.success(duplicate)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun moveSoundToPack(soundId: String, packId: String?) = withContext(Dispatchers.IO) {
        soundDao.setPack(soundId, packId)
        soundDao.byId(soundId)?.let { soundDao.setSortIndex(soundId, soundDao.maxSortIndex(packId) + 1) }
    }

    suspend fun onSoundPlayed(id: String) = withContext(Dispatchers.IO) {
        soundDao.incrementPlay(id, System.currentTimeMillis())
    }

    /** Reorders a sound within its pack/list by [delta] (-1 up, +1 down). */
    suspend fun reorderSound(sound: SoundEntity, delta: Int) = withContext(Dispatchers.IO) {
        val siblings = soundDao.packMembersOnce(sound.packId)
        val index = siblings.indexOfFirst { it.id == sound.id }
        if (index == -1) return@withContext
        val target = index + delta
        if (target < 0 || target >= siblings.size) return@withContext
        val reordered = siblings.toMutableList().apply {
            val item = removeAt(index)
            add(target, item)
        }
        reordered.forEachIndexed { i, s -> soundDao.setSortIndex(s.id, i) }
    }

    // ---- Packs ---------------------------------------------------------------------

    suspend fun createPack(name: String, icon: String): Result<SoundPackEntity> = withContext(Dispatchers.IO) {
        try {
            val pack = SoundPackEntity(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { "New Pack" },
                icon = icon.ifBlank { "🎮" },
                sortIndex = packDao.maxSortIndex() + 1,
                createdAt = System.currentTimeMillis(),
            )
            packDao.insert(pack)
            Result.success(pack)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renamePack(pack: SoundPackEntity, name: String, icon: String) = withContext(Dispatchers.IO) {
        packDao.rename(pack.id, name.ifBlank { pack.name }, icon.ifBlank { pack.icon })
    }

    /** Deleting a pack keeps its sounds — they simply become packless ("My Sounds"). */
    suspend fun deletePack(pack: SoundPackEntity) = withContext(Dispatchers.IO) {
        packDao.delete(pack)
    }

    suspend fun packById(id: String): SoundPackEntity? = packDao.byId(id)

    fun observePackEntity(id: String): Flow<SoundPackEntity?> = packDao.observeById(id)

    // ---- Search / storage ----------------------------------------------------------

    suspend fun storageStats(): StorageStats = withContext(Dispatchers.IO) {
        val cache = AudioFiles.sizeOf(context.cacheDir) + (context.externalCacheDir?.let { AudioFiles.sizeOf(it) } ?: 0L)
        StorageStats(
            totalSounds = soundDao.count(),
            totalMusic = soundDao.countByCategory(com.gamesoundpro.app.domain.Category.MUSIC.key),
            totalPacks = packDao.count(),
            audioBytes = soundDao.totalBytes(),
            cacheBytes = cache,
        )
    }

    suspend fun unusedSoundCount(): Int = withContext(Dispatchers.IO) { soundDao.unusedSounds().size }

    suspend fun clearCache(): Long = withContext(Dispatchers.IO) {
        val before = AudioFiles.sizeOf(context.cacheDir) + (context.externalCacheDir?.let { AudioFiles.sizeOf(it) } ?: 0L)
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        context.externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
        before
    }

    /** Deletes sounds that have never been played; returns how many were removed. */
    suspend fun deleteUnusedSounds(): Int = withContext(Dispatchers.IO) {
        val unused = soundDao.unusedSounds()
        unused.forEach { sound ->
            val file = File(sound.filePath)
            if (file.absolutePath.startsWith(context.filesDir.absolutePath)) file.delete()
            soundDao.delete(sound)
        }
        unused.size
    }

    // ---- Pack export / import ------------------------------------------------------

    suspend fun exportPack(pack: SoundPackEntity, target: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val members = soundDao.packMembersOnce(pack.id)
            if (members.isEmpty()) return@withContext Result.failure(IOException("This pack has no sounds yet"))
            val manifest = PackCodec.writeManifest(pack, members)
            context.contentResolver.openOutputStream(target)?.use { raw ->
                java.util.zip.ZipOutputStream(raw.buffered()).use { zip ->
                    zip.putNextEntry(java.util.zip.ZipEntry(PackCodec.MANIFEST_ENTRY))
                    zip.write(manifest.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    members.forEach { sound ->
                        val file = File(sound.filePath)
                        if (!file.exists()) return@forEach
                        zip.putNextEntry(java.util.zip.ZipEntry("${PackCodec.AUDIO_DIR}${sound.id}.${file.extension}"))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            } ?: return@withContext Result.failure(IOException("Cannot write to the selected location"))
            Result.success(members.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Imports a previously exported pack zip. Every entry is validated (manifest schema,
     * extension whitelist, size caps, zip-slip protection) before anything is added.
     */
    suspend fun importPack(source: Uri): Result<PackImportResult> = withContext(Dispatchers.IO) {
        try {
            val tempDir = File(context.cacheDir, "pack_import_${System.currentTimeMillis()}").apply { mkdirs() }
            try {
                context.contentResolver.openInputStream(source)?.use { raw ->
                    val extracted = PackCodec.unpackZip(raw, tempDir)
                    if (!extracted) {
                        return@withContext Result.failure(IOException("Invalid or corrupt pack file"))
                    }
                } ?: return@withContext Result.failure(IOException("Cannot open the selected file"))

                val manifestFile = File(tempDir, PackCodec.MANIFEST_ENTRY)
                if (!manifestFile.exists()) {
                    return@withContext Result.failure(IOException("manifest.json missing from pack"))
                }
                val manifest = PackCodec.parseManifest(manifestFile.readText())
                    ?: return@withContext Result.failure(IOException("Invalid pack manifest"))

                val pack = SoundPackEntity(
                    id = UUID.randomUUID().toString(),
                    name = manifest.packName,
                    icon = manifest.packIcon,
                    sortIndex = packDao.maxSortIndex() + 1,
                    createdAt = System.currentTimeMillis(),
                )
                packDao.insert(pack)

                var imported = 0
                var skipped = 0
                manifest.sounds.forEach { entry ->
                    val sourceFile = File(tempDir, entry.file)
                    val safe = sourceFile.canonicalFile
                    if (!safe.path.startsWith(tempDir.canonicalPath) ||
                        AudioFiles.extensionOf(safe.name) !in AudioFiles.SUPPORTED_EXTENSIONS ||
                        !safe.exists()
                    ) {
                        skipped++
                        return@forEach
                    }
                    val destination = File(soundsDir, "${UUID.randomUUID()}.${safe.extension}")
                    safe.copyTo(destination, overwrite = true)
                    val duration = AudioFiles.readDurationMs(destination.absolutePath)
                    soundDao.insert(
                        SoundEntity(
                            id = UUID.randomUUID().toString(),
                            name = entry.name,
                            filePath = destination.absolutePath,
                            category = entry.category,
                            packId = pack.id,
                            durationMs = duration,
                            sizeBytes = destination.length(),
                            icon = entry.icon,
                            volume = entry.volume,
                            trimStartMs = entry.trimStartMs.coerceIn(0L, duration),
                            trimEndMs = entry.trimEndMs.coerceIn(0L, duration),
                            sortIndex = entry.sortIndex,
                            createdAt = System.currentTimeMillis(),
                        )
                    )
                    imported++
                }
                Result.success(PackImportResult(pack.name, imported, skipped))
            } finally {
                tempDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---- Seeding / misc ------------------------------------------------------------

    /** First launch: copy the bundled starter WAVs so the soundboard is never empty. */
    suspend fun seedStarterPack() = withContext(Dispatchers.IO) {
        if (soundDao.count() > 0 || packDao.count() > 0) return@withContext
        val pack = SoundPackEntity(
            id = UUID.randomUUID().toString(),
            name = "Starter Pack",
            icon = "🎮",
            sortIndex = 0,
            createdAt = System.currentTimeMillis(),
        )
        packDao.insert(pack)

        val starter = listOf(
            "starter/laser.wav" to Triple("Laser", "⚡", 1500L),
            "starter/coin.wav" to Triple("Coin", "💰", 900L),
            "starter/powerup.wav" to Triple("Power Up", "🚀", 1400L),
            "starter/alarm.wav" to Triple("Alarm", "🚨", 1600L),
            "starter/explosion.wav" to Triple("Explosion", "💥", 1500L),
            "starter/blip.wav" to Triple("Blip", "🔔", 500L),
        )
        val entities = starter.mapIndexedNotNull { index, (asset, meta) ->
            val (name, icon, duration) = meta
            try {
                val target = File(soundsDir, "${UUID.randomUUID()}.wav")
                context.assets.open(asset).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                SoundEntity(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    filePath = target.absolutePath,
                    category = Category.EFFECTS.key,
                    packId = pack.id,
                    durationMs = duration,
                    sizeBytes = target.length(),
                    icon = icon,
                    sortIndex = index,
                    createdAt = System.currentTimeMillis() + index,
                )
            } catch (_: Exception) {
                null
            }
        }
        soundDao.insertAll(entities)
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        } ?: uri.lastPathSegment
    } catch (_: Exception) {
        uri.lastPathSegment
    }
}
