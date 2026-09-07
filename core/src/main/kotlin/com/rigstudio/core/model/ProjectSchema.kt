package com.rigstudio.core.model

import com.rigstudio.core.json.JsonValue
import com.rigstudio.core.json.get
import com.rigstudio.core.json.int

/**
 * Versioned `.rigstudio` project format (V6 §39).
 *
 * Every project manifest carries `format: N`. Decoding always migrates the parsed JSON forward
 * through one pure function per version step before [ProjectCodec] reads it, so:
 *
 *  * a file written by any older version of the app still opens — one release later, two
 *    releases later, always (no user ever sees "project was made with an old version");
 *  * each migration is a tiny, unit-testable transformation;
 *  * a file written by a NEWER version (forward compatibility) opens too, because decoding
 *    treats unknown keys as optional.
 *
 * ## History
 *  * **1** — V3/V4/V5 project manifest: identity, sprites, views, last editor state.
 *  * **6** — V6 master spec: stage camera persisted (`lastCameraZoom/PanX/PanY`), manual z-order
 *    edits (`zOrderOverrides`), and ids of user-saved poses/animations whose documents live in
 *    `poses.json` / `animations.json` beside the manifest.
 */
object ProjectSchema {

    /** The format this build writes. */
    const val CURRENT_FORMAT = 6

    /** Migrates a parsed manifest up to [CURRENT_FORMAT]. Pure: input is never mutated. */
    fun migrate(root: JsonValue.Obj): JsonValue.Obj {
        var version = root.int("format", 1)
        var current = root

        // --- step functions, applied in ascending order -------------------------------------
        // format 1 → 6: everything V6 added is optional with defaults, so there is nothing to
        // transform — the step exists so the pipeline is real from day one and future steps
        // (e.g. a rename in format 7) slot in here without touching decoder code.
        if (version < 6) {
            current = step1to6(current)
            version = 6
        }

        // Unknown future formats are NOT migrated; ProjectCodec's optional-field decoding
        // keeps them readable anyway (minus features this build does not know).
        return current
    }

    /** The writer stamps the current format on every save. */
    fun stamp(current: JsonValue.Obj): JsonValue.Obj {
        val members = LinkedHashMap<String, JsonValue>(current.members)
        members["format"] = JsonValue.Num(CURRENT_FORMAT.toDouble())
        return JsonValue.Obj(members)
    }

    private fun step1to6(root: JsonValue.Obj): JsonValue.Obj {
        // Nothing to move — V6 fields default cleanly. Kept explicit for the audit trail.
        val members = LinkedHashMap<String, JsonValue>(root.members)
        if (members["lastCameraZoom"] == null) members["lastCameraZoom"] = JsonValue.Num(1.0)
        if (members["lastCameraPanX"] == null) members["lastCameraPanX"] = JsonValue.Num(0.0)
        if (members["lastCameraPanY"] == null) members["lastCameraPanY"] = JsonValue.Num(0.0)
        if (members["zOrderOverrides"] == null) members["zOrderOverrides"] = JsonValue.Obj(emptyMap())
        members["format"] = JsonValue.Num(6.0)
        return JsonValue.Obj(members)
    }
}
