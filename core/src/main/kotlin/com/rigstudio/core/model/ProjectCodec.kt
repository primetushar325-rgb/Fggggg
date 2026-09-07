package com.rigstudio.core.model

import com.rigstudio.core.geom.IntRect
import com.rigstudio.core.json.Json
import com.rigstudio.core.json.bool
import com.rigstudio.core.json.JsonValue
import com.rigstudio.core.json.arr
import com.rigstudio.core.json.arrOrNull
import com.rigstudio.core.json.boolean
import com.rigstudio.core.json.float
import com.rigstudio.core.json.get
import com.rigstudio.core.json.int
import com.rigstudio.core.json.long
import com.rigstudio.core.json.num
import com.rigstudio.core.json.obj
import com.rigstudio.core.json.objList
import com.rigstudio.core.json.str
import com.rigstudio.core.json.string
import com.rigstudio.core.json.stringList
import com.rigstudio.core.json.stringOrNull

/**
 * JSON serialization for saved characters (`.rigstudio` project manifest, V6 §39).
 *
 * Enums are stored by name and unknown values are dropped rather than crashing, so a project file
 * written by a future version still opens (minus whatever this build does not understand).
 * Older format versions are migrated forward by [ProjectSchema] before decoding.
 */
object ProjectCodec {

    fun encode(project: CharacterProject): String =
        Json.stringify(encodeProject(project), pretty = true)

    fun encodeProject(project: CharacterProject): JsonValue.Obj = obj(
        "format" to num(ProjectSchema.CURRENT_FORMAT),
        "templateVersion" to num(project.templateVersion),
        "id" to str(project.id),
        "name" to str(project.name),
        "createdAt" to num(project.createdAtEpochMillis),
        "updatedAt" to num(project.updatedAtEpochMillis),
        "lastOpenedAt" to num(project.lastOpenedAtEpochMillis),
        "sheetFileName" to str(project.sheetFileName),
        "sheetWidth" to num(project.sheetWidth),
        "sheetHeight" to num(project.sheetHeight),
        "thumbnailFileName" to project.thumbnailFileName?.let { str(it) },
        "availableViews" to arr(project.availableViews.map { str(it.name) }),
        "mirroredSideView" to bool(project.mirroredSideView),
        "availableExpressions" to arr(project.availableExpressions.map { str(it.name) }),
        "availableMouthShapes" to arr(project.availableMouthShapes.map { str(it.name) }),
        "notes" to arr(project.notes.map { str(it) }),
        "sprites" to arr(project.sprites.map { encodeSprite(it) }),
        "lastClipId" to str(project.lastClipId),
        "lastView" to str(project.lastView.name),
        "lastBackgroundArgb" to project.lastBackgroundArgb?.let { num(it) },
        "lastSpeed" to num(project.lastSpeed),
        "lastCameraZoom" to num(project.lastCameraZoom),
        "lastCameraPanX" to num(project.lastCameraPanX),
        "lastCameraPanY" to num(project.lastCameraPanY),
        "zOrderOverrides" to JsonValue.Obj(
            project.zOrderOverrides.entries
                .sortedBy { it.key }
                .associate { (slotId, z) -> slotId to num(z) },
        ),
        "posePresetIds" to arr(project.posePresetIds.map { str(it) }),
        "userClipIds" to arr(project.userClipIds.map { str(it) }),
    )

    fun decode(json: String): CharacterProject? {
        val root = Json.parseOrNull(json) ?: return null
        return decodeProject(root)
    }

    fun decodeProject(value: JsonValue): CharacterProject? {
        if (value !is JsonValue.Obj) return null
        val migrated = ProjectSchema.migrate(value)
        val id = migrated.string("id")
        val name = migrated.string("name")
        if (id.isBlank()) return null

        return CharacterProject(
            id = id,
            name = name.ifBlank { "Character" },
            createdAtEpochMillis = migrated.long("createdAt"),
            updatedAtEpochMillis = migrated.long("updatedAt", migrated.long("createdAt")),
            lastOpenedAtEpochMillis = migrated.long("lastOpenedAt", migrated.long("updatedAt")),
            sheetFileName = migrated.string("sheetFileName", "sheet.png"),
            sheetWidth = migrated.int("sheetWidth", 2048),
            sheetHeight = migrated.int("sheetHeight", 2048),
            thumbnailFileName = migrated.stringOrNull("thumbnailFileName"),
            sprites = migrated.objList("sprites").mapNotNull { decodeSprite(it) },
            availableViews = migrated.stringList("availableViews").mapNotNull { enumOrNull<ViewKind>(it) },
            mirroredSideView = migrated.boolean("mirroredSideView"),
            availableExpressions = migrated.stringList("availableExpressions")
                .mapNotNull { enumOrNull<Expression>(it) },
            availableMouthShapes = migrated.stringList("availableMouthShapes")
                .mapNotNull { enumOrNull<MouthShape>(it) },
            notes = migrated.stringList("notes"),
            lastClipId = migrated.string("lastClipId", "idle"),
            lastView = enumOrNull<ViewKind>(migrated.string("lastView", ViewKind.FRONT.name))
                ?: ViewKind.FRONT,
            lastBackgroundArgb = (migrated.get("lastBackgroundArgb") as? JsonValue.Num)?.intValue,
            lastSpeed = migrated.float("lastSpeed", 1f),
            templateVersion = migrated.int("templateVersion", 1),
            lastCameraZoom = migrated.float("lastCameraZoom", 1f),
            lastCameraPanX = migrated.float("lastCameraPanX", 0f),
            lastCameraPanY = migrated.float("lastCameraPanY", 0f),
            zOrderOverrides = ((migrated.get("zOrderOverrides") as? JsonValue.Obj)?.members
                ?: emptyMap()).mapNotNull { (slotId, z) ->
                (z as? JsonValue.Num)?.intValue?.let { slotId to it }
            }.toMap(),
            posePresetIds = migrated.stringList("posePresetIds"),
            userClipIds = migrated.stringList("userClipIds"),
        )
    }

    private fun encodeSprite(sprite: SpriteManifestEntry): JsonValue.Obj = obj(
        "slotId" to str(sprite.slotId),
        "fileName" to str(sprite.fileName),
        "width" to num(sprite.width),
        "height" to num(sprite.height),
        "pivotX" to num(sprite.pivotX),
        "pivotY" to num(sprite.pivotY),
        "coverage" to num(sprite.coverage),
        "sourceRect" to encodeRect(sprite.sourceRect),
        "contentRect" to encodeRect(sprite.contentRect),
    )

    private fun decodeSprite(value: JsonValue): SpriteManifestEntry? {
        val slotId = value.string("slotId")
        if (slotId.isBlank()) return null
        val width = value.int("width")
        val height = value.int("height")
        if (width <= 0 || height <= 0) return null
        return SpriteManifestEntry(
            slotId = slotId,
            fileName = value.string("fileName", "$slotId.png"),
            width = width,
            height = height,
            pivotX = value.float("pivotX", 0.5f),
            pivotY = value.float("pivotY", 0.5f),
            coverage = value.float("coverage", 1f),
            sourceRect = decodeRect(value.get("sourceRect")),
            contentRect = decodeRect(value.get("contentRect")),
        )
    }

    private fun encodeRect(rect: IntRect): JsonValue.Arr =
        arr(num(rect.x), num(rect.y), num(rect.width), num(rect.height))

    private fun decodeRect(value: JsonValue?): IntRect {
        val items = value?.arrOrNull()?.items ?: return IntRect.ZERO
        if (items.size < 4) return IntRect.ZERO
        val numbers = items.mapNotNull { (it as? JsonValue.Num)?.intValue }
        if (numbers.size < 4) return IntRect.ZERO
        return IntRect(numbers[0], numbers[1], numbers[2], numbers[3])
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
        enumValues<T>().firstOrNull { it.name == name }
}
