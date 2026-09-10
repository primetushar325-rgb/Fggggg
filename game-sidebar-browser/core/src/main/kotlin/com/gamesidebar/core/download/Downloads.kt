package com.gamesidebar.core.download

/**
 * Parsing and policy for web downloads.
 *
 * Files always go through [android.app.DownloadManager] into a public collection
 * (Downloads / Pictures / Movies / Music) via MediaStore, so no storage permission is needed on
 * scoped storage and the user always sees the file in a normal place.
 */
data class DownloadRequest(
    val url: String,
    val fileName: String,
    val mimeType: String,
    val contentLengthBytes: Long = -1L,
    val userAgent: String? = null,
    val referer: String? = null,
) {
    val targetSubdir: String get() = Downloads.subdirFor(mimeType, fileName)
    val sizeLabel: String get() = Downloads.formatBytes(contentLengthBytes)
}

object Downloads {

    private val CONTROL_CHARS = Regex("[\\x00-\\x1F\\x7F]")
    private val ILLEGAL_CHARS = Regex("[\\\\/:*?\"<>|]")
    private val RESERVED_NAMES = setOf(
        "con", "prn", "aux", "nul", "com1", "com2", "com3", "com4", "com5", "com6",
        "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6",
    )
    const val MAX_NAME_LENGTH = 96

    /** `attachment; filename="report.pdf"` / `filename*=UTF-8''a%20b.pdf` -> the bare name. */
    fun fileNameFromContentDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val parts = header.split(';').map { it.trim() }
        var star = ""
        var plain = ""
        for (part in parts) {
            val lower = part.lowercase()
            when {
                lower.startsWith("filename*=") -> star = part.substringAfter('=', "").trim().trim('"')
                lower.startsWith("filename=") -> plain = part.substringAfter('=', "").trim().trim('"')
            }
        }
        val chosen = star.ifBlank { plain }
        if (chosen.isBlank()) return null
        // filename*=charset'lang'encoded
        val decoded = if (star.isNotBlank() && chosen.contains("''")) {
            val encoded = chosen.substringAfter("''")
            percentDecode(encoded)
        } else {
            chosen
        }
        return decoded.ifBlank { null }
    }

    fun percentDecode(value: String): String {
        val bytes = mutableListOf<Byte>()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '%' && i + 2 < value.length) {
                val hex = value.substring(i + 1, i + 3).toIntOrNull(16)
                if (hex != null) {
                    bytes += hex.toByte()
                    i += 3
                    continue
                }
            }
            if (c == '+') {
                bytes += ' '.code.toByte()
            } else {
                bytes += c.code.toByte()
            }
            i++
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }

    /** Last path segment of a URL, or null when the URL carries no usable name. */
    fun fileNameFromUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val withoutQuery = url.substringBefore('?').substringBefore('#')
        val last = withoutQuery.substringAfterLast('/', "")
        if (last.isBlank() || !last.contains('.')) return null
        return percentDecode(last)
    }

    /**
     * Makes a name safe for MediaStore: no path traversal, no control characters, no reserved
     * device names, bounded length, extension preserved.
     */
    fun sanitizeFileName(rawName: String, fallback: String = "download"): String {
        // Defensive: a hostile Content-Disposition must never carry a path into MediaStore.
        val baseName = rawName.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = CONTROL_CHARS.replace(baseName, "")
            .let { ILLEGAL_CHARS.replace(it, "_") }
            .trim()
            .trim('.')
        if (cleaned.isBlank() || cleaned == "." || cleaned == "..") return fallback

        val dot = cleaned.lastIndexOf('.')
        val extension = if (dot > 0 && dot > cleaned.length - 6) cleaned.substring(dot) else ""
        val stem = if (extension.isEmpty()) cleaned else cleaned.substring(0, dot)
        val safeStem = stem.take(MAX_NAME_LENGTH - extension.length)
            .trim()
            .ifBlank { fallback }
        val candidate = "$safeStem$extension"
        val baseWithoutExt = candidate.substringBeforeLast('.', candidate).lowercase()
        return if (baseWithoutExt in RESERVED_NAMES) "${fallback}_$candidate" else candidate
    }

    fun resolveFileName(
        contentDisposition: String?,
        url: String?,
        mimeType: String?,
        urlGuess: String?,
    ): String {
        val candidate = fileNameFromContentDisposition(contentDisposition)
            ?: fileNameFromUrl(url)
            ?: urlGuess
            ?: "download"
        val sanitized = sanitizeFileName(candidate)
        return if (extensionOf(sanitized).isEmpty() && !mimeType.isNullOrBlank()) {
            sanitized + "." + extensionForMime(mimeType)
        } else {
            sanitized
        }
    }

    fun extensionOf(name: String): String =
        if (name.contains('.') && !name.endsWith('.')) name.substringAfterLast('.').lowercase() else ""

    fun guessMimeType(fileName: String): String = MIME_BY_EXT[extensionOf(fileName)] ?: "application/octet-stream"

    fun extensionForMime(mime: String): String {
        val normalized = mime.substringBefore(';').trim().lowercase()
        return EXT_BY_MIME[normalized] ?: "bin"
    }

    fun subdirFor(mimeType: String, fileName: String): String {
        val normalized = mimeType.substringBefore(';').trim().lowercase()
        return when {
            normalized.startsWith("image/") -> "Pictures"
            normalized.startsWith("video/") -> "Movies"
            normalized.startsWith("audio/") -> "Music"
            else -> when (extensionOf(fileName)) {
                in IMAGE_EXTS -> "Pictures"
                in VIDEO_EXTS -> "Movies"
                in AUDIO_EXTS -> "Music"
                else -> "Download"
            }
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "Unknown size"
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return if (unit == 0) {
            "$bytes ${units[0]}"
        } else {
            String.format(java.util.Locale.US, "%.1f %s", value, units[unit])
        }
    }

    private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    private val VIDEO_EXTS = setOf("mp4", "webm", "mkv", "mov", "avi", "m4v")
    private val AUDIO_EXTS = setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac")

    private val MIME_BY_EXT = mapOf(
        "apk" to "application/vnd.android.package-archive",
        "pdf" to "application/pdf",
        "zip" to "application/zip",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "webp" to "image/webp",
        "gif" to "image/gif",
        "mp4" to "video/mp4",
        "webm" to "video/webm",
        "mp3" to "audio/mpeg",
        "m4a" to "audio/mp4",
        "txt" to "text/plain",
        "json" to "application/json",
    )

    private val EXT_BY_MIME = mapOf(
        "application/pdf" to "pdf",
        "application/zip" to "zip",
        "image/jpeg" to "jpg",
        "image/png" to "png",
        "image/webp" to "webp",
        "image/gif" to "gif",
        "video/mp4" to "mp4",
        "video/webm" to "webm",
        "audio/mpeg" to "mp3",
        "text/plain" to "txt",
        "application/json" to "json",
    )
}
