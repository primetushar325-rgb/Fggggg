package com.gamesoundpro.app.repository

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.File

/**
 * (De)serialization of GameSound Pro pack archives: a plain ZIP containing
 * `manifest.json` plus `audio/<soundId>.<ext>` members. Pure JVM code (org.json ships with
 * Android) so the manifest rules can be reasoned about and tested in isolation.
 */
object PackCodec {

    const val MANIFEST_ENTRY = "manifest.json"
    const val AUDIO_DIR = "audio/"
    const val FORMAT_ID = "gamesoundpro-pack"
    const val FORMAT_VERSION = 1

    /** Safety limits applied on import. */
    const val MAX_FILE_BYTES: Long = 50L * 1024 * 1024      // 50 MB per sound
    const val MAX_TOTAL_BYTES: Long = 300L * 1024 * 1024    // 300 MB per pack
    const val MAX_ENTRIES = 500

    data class ManifestSound(
        val name: String,
        val category: String,
        val icon: String,
        val volume: Float,
        val durationMs: Long,
        val trimStartMs: Long,
        val trimEndMs: Long,
        val sortIndex: Int,
        val file: String,
    )

    data class ManifestData(
        val packName: String,
        val packIcon: String,
        val sounds: List<ManifestSound>,
    )

    fun writeManifest(pack: com.gamesoundpro.app.database.entity.SoundPackEntity,
                      sounds: List<com.gamesoundpro.app.database.entity.SoundEntity>): String {
        val arr = JSONArray()
        sounds.forEach { s ->
            arr.put(
                JSONObject()
                    .put("name", s.name)
                    .put("category", s.category)
                    .put("icon", s.icon)
                    .put("volume", s.volume.toDouble())
                    .put("durationMs", s.durationMs)
                    .put("trimStartMs", s.trimStartMs)
                    .put("trimEndMs", s.trimEndMs)
                    .put("sortIndex", s.sortIndex)
                    .put("file", "$AUDIO_DIR${s.id}.${File(s.filePath).extension.ifBlank { "mp3" }}")
            )
        }
        return JSONObject()
            .put("format", FORMAT_ID)
            .put("version", FORMAT_VERSION)
            .put("pack", JSONObject().put("name", pack.name).put("icon", pack.icon))
            .put("sounds", arr)
            .toString(2)
    }

    /** Strict parse + schema validation. Null when the manifest is not ours. */
    fun parseManifest(json: String): ManifestData? = try {
        val root = JSONObject(json)
        if (root.optString("format") != FORMAT_ID) return null
        val version = root.optInt("version", -1)
        if (version != FORMAT_VERSION) return null
        val packObj = root.optJSONObject("pack") ?: return null
        val packName = packObj.optString("name").ifBlank { return null }
        val packIcon = packObj.optString("icon", "🎮").take(8)
        val arr = root.optJSONArray("sounds") ?: return null
        if (arr.length() > MAX_ENTRIES) return null
        val sounds = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val file = o.optString("file")
                if (!file.startsWith(AUDIO_DIR) || file.contains("..") || file.contains('/')) {
                    // Only flat names directly under audio/ are accepted.
                    continue
                }
                add(
                    ManifestSound(
                        name = o.optString("name").ifBlank { "Sound" }.take(80),
                        category = o.optString("category", "custom").take(20),
                        icon = o.optString("icon", "🔊").take(8),
                        volume = o.optDouble("volume", 1.0).toFloat().coerceIn(0.1f, 1.5f),
                        durationMs = o.optLong("durationMs", 0L).coerceIn(0L, 30L * 60 * 1000),
                        trimStartMs = o.optLong("trimStartMs", 0L).coerceAtLeast(0L),
                        trimEndMs = o.optLong("trimEndMs", 0L).coerceAtLeast(0L),
                        sortIndex = o.optInt("sortIndex", 0),
                        file = file,
                    )
                )
            }
        }
        ManifestData(packName, packIcon, sounds)
    } catch (_: Exception) {
        null
    }

    /**
     * Streams a pack zip into [targetDir]. Enforces zip-slip protection, per-entry and total
     * size caps, entry count caps and that the manifest exists. Returns true on success.
     */
    fun unpackZip(input: InputStream, targetDir: File): Boolean {
        var totalBytes = 0L
        var entries = 0
        var manifestFound = false
        try {
            java.util.zip.ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    entries++
                    if (entries > MAX_ENTRIES + 1) return false
                    val name = entry.name
                    if (name.contains("..") || name.startsWith("/") || name.contains('\\')) return false
                    val safeName = if (name == MANIFEST_ENTRY || name.startsWith(AUDIO_DIR)) name else return false
                    if (safeName == MANIFEST_ENTRY) manifestFound = true

                    val target = File(targetDir, safeName)
                    if (!target.canonicalFile.path.startsWith(targetDir.canonicalFile.path)) return false

                    val limit = if (safeName == MANIFEST_ENTRY) 1L * 1024 * 1024 else MAX_FILE_BYTES
                    var written = 0L
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read == -1) break
                            written += read
                            totalBytes += read
                            if (written > limit || totalBytes > MAX_TOTAL_BYTES) return false
                            out.write(buffer, 0, read)
                        }
                    }
                    zip.closeEntry()
                }
            }
        } catch (_: Exception) {
            return false
        }
        return manifestFound
    }
}
