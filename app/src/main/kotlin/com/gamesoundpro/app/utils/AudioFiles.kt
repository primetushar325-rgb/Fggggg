package com.gamesoundpro.app.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import java.io.File
import java.util.Locale

/** Audio file utilities: extension validation, duration probing and artwork extraction. */
object AudioFiles {

    /** Extensions GameSound Pro accepts on import. */
    val SUPPORTED_EXTENSIONS: Set<String> = setOf("mp3", "wav", "m4a", "ogg", "aac", "flac", "opus")

    /** Formats the add-sound sheet advertises (a superset is accepted). */
    val ADVERTISED_EXTENSIONS: Set<String> = setOf("mp3", "wav", "m4a", "ogg")

    fun extensionOf(fileName: String): String =
        fileName.substringAfterLast('.', "").lowercase(Locale.US)

    fun hasSupportedExtension(fileName: String): Boolean =
        extensionOf(fileName) in SUPPORTED_EXTENSIONS

    /** Best-effort display name: drops the extension, keeps the rest. */
    fun baseName(fileName: String): String {
        val name = fileName.substringBeforeLast('.')
        return if (name.isBlank()) "Sound" else name
    }

    fun safeFileName(name: String, extension: String): String {
        val cleaned = name.map { ch ->
            if (ch.isLetterOrDigit() || ch in " ._-\u2019'()&!,") ch else '_'
        }.joinToString("").trim().ifBlank { "sound" }
        return "$cleaned.$extension"
    }

    /** Reads duration in ms via MediaMetadataRetriever; 0 when unreadable. */
    fun readDurationMs(path: String): Long = try {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        }
    } catch (_: Exception) {
        0L
    }

    /** Extracts embedded artwork (album art), downscaled for low memory use. Null if absent. */
    fun extractArtwork(path: String, maxDim: Int = 256): Bitmap? = try {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(path)
            val bytes = retriever.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxDim && bounds.outHeight / (sample * 2) >= maxDim) {
                sample *= 2
            }
            BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )
        }
    } catch (_: Exception) {
        null
    }

    /** MediaMetadataRetriever implements AutoCloseable from API 29; provide our own close helper. */
    private inline fun <T> MediaMetadataRetriever.use(block: (MediaMetadataRetriever) -> T): T {
        try {
            return block(this)
        } finally {
            try {
                if (android.os.Build.VERSION.SDK_INT >= 29) close() else release()
            } catch (_: Exception) {
                // released best-effort
            }
        }
    }

    /** Recursively sums file sizes under [dir]; 0 when it doesn't exist. */
    fun sizeOf(dir: File): Long = dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
}
