package com.rigstudio.app.data

import android.content.Context
import com.rigstudio.core.model.AppSettings
import com.rigstudio.core.model.AppSettingsCodec
import java.io.File

/**
 * Local, file-backed store for [AppSettings] (V4 §49). One JSON file in the app's private
 * storage — no permissions, no cloud, survives restarts. A corrupted file falls back to
 * defaults instead of taking the app down (V4 §47).
 */
class SettingsStore(context: Context) {

    private val file: File = File(context.filesDir, FILE_NAME)

    /** Reads the settings; returns defaults when the file is missing or unreadable. */
    fun load(): AppSettings = runCatching {
        if (!file.exists()) return AppSettings.DEFAULT
        val text = file.readText(Charsets.UTF_8)
        AppSettingsCodec.decodeJsonOrNull(text) ?: AppSettings.DEFAULT
    }.getOrDefault(AppSettings.DEFAULT)

    /** Writes the settings atomically: temp file + rename, so a crash mid-write cannot corrupt. */
    fun save(settings: AppSettings) {
        runCatching {
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(AppSettingsCodec.encodeJson(settings), Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.writeText(AppSettingsCodec.encodeJson(settings), Charsets.UTF_8)
                tmp.delete()
            }
        }
    }

    companion object {
        private const val FILE_NAME = "settings.json"
    }
}
