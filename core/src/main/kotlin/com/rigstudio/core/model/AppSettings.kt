package com.rigstudio.core.model

import com.rigstudio.core.export.ExportFrameRate
import com.rigstudio.core.export.ExportResolution
import com.rigstudio.core.export.ExportSettings
import com.rigstudio.core.json.Json
import com.rigstudio.core.json.JsonValue
import com.rigstudio.core.json.get
import com.rigstudio.core.json.obj
import com.rigstudio.core.json.str

/**
 * User-level app settings (V4 §49 Settings screen), persisted locally as JSON.
 *
 * These are *defaults*, never gates: the export screen still lets the user pick any resolution
 * and frame rate per export. `debugOverlays` unlocks the hidden developer view (V4 §52) —
 * slot rectangles, bone pivots, sprite bounds, z-order badges and the FPS counter.
 */
data class AppSettings(
    /** Pre-selected resolution on a fresh export screen (V4 §38 default: 1080p). */
    val defaultResolution: ExportResolution = ExportResolution.FULL_HD_1080,
    /** Pre-selected frame rate on a fresh export screen (V4 §38 default: 30 fps). */
    val defaultFrameRate: ExportFrameRate = ExportFrameRate.FPS_30,
    /** Whether the editor starts clips in loop mode (V4 §14 loops where appropriate). */
    val loopByDefault: Boolean = true,
    /** Hidden developer overlay toggle (V4 §52). Off for normal users. */
    val debugOverlays: Boolean = false,
) {
    companion object {
        val DEFAULT = AppSettings()
    }
}

/** Encodes/decodes [AppSettings]. Unknown or malformed values fall back to spec defaults. */
object AppSettingsCodec {

    fun encode(settings: AppSettings): JsonValue.Obj = obj(
        "version" to JsonValue.Num(1.0),
        "defaultResolution" to str(settings.defaultResolution.name),
        "defaultFrameRate" to str(settings.defaultFrameRate.name),
        "loopByDefault" to JsonValue.Bool(settings.loopByDefault),
        "debugOverlays" to JsonValue.Bool(settings.debugOverlays),
    )

    fun encodeJson(settings: AppSettings): String = Json.stringify(encode(settings))

    fun decode(value: JsonValue): AppSettings {
        if (value !is JsonValue.Obj) return AppSettings.DEFAULT
        return AppSettings(
            defaultResolution = enumEntryOrNull<ExportResolution>(value, "defaultResolution")
                ?: AppSettings.DEFAULT.defaultResolution,
            defaultFrameRate = enumEntryOrNull<ExportFrameRate>(value, "defaultFrameRate")
                ?: AppSettings.DEFAULT.defaultFrameRate,
            loopByDefault = (value.get("loopByDefault") as? JsonValue.Bool)?.value
                ?: AppSettings.DEFAULT.loopByDefault,
            debugOverlays = (value.get("debugOverlays") as? JsonValue.Bool)?.value
                ?: AppSettings.DEFAULT.debugOverlays,
        )
    }

    fun decodeJsonOrNull(json: String): AppSettings? =
        Json.parseOrNull(json)?.let { decode(it) }

    private inline fun <reified E : Enum<E>> enumEntryOrNull(value: JsonValue.Obj, key: String): E? {
        val name = (value.get(key) as? JsonValue.Str)?.value ?: return null
        return enumValues<E>().firstOrNull { it.name == name }
    }
}

/**
 * The seed an export screen should start from when the user enters it without carrying editor
 * state: the app-level defaults (V4 §38 — 1080p / 30 fps unless the user changed them).
 */
fun AppSettings.asExportSeed(): ExportSettings =
    ExportSettings.DEFAULT.copy(
        resolution = defaultResolution,
        frameRate = defaultFrameRate,
    )
