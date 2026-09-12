package com.rigstudio.core.rig

import com.rigstudio.core.geom.IntRect
import com.rigstudio.core.geom.Vec2
import com.rigstudio.core.json.Json
import com.rigstudio.core.json.JsonValue
import com.rigstudio.core.json.arr
import com.rigstudio.core.json.get
import com.rigstudio.core.json.obj
import com.rigstudio.core.json.str

/**
 * One prop/accessory (V6): a user-imported PNG attached to a bone. Hats ride the head, a sword
 * rides a hand — the accessory inherits the bone's whole animated transform, so it follows
 * walk, run and pose-editor drags with zero extra animation data.
 *
 * Geometry works exactly like a bone: [anchorX]/[anchorY] pick the joint **inside the host
 * bone's rest rectangle** (fractions, y down), [pivotX]/[pivotY] pick the joint **inside the
 * accessory artwork**, and the drawn size is [targetHeight] in view units (character height =
 * 1.0). The extra [rotationDeg]/[offset]/[scale] are the user's live adjustments on top.
 */
data class AccessoryDef(
    val id: String,
    val name: String,
    /** PNG file name inside the project folder. */
    val fileName: String,
    val widthPx: Int,
    val heightPx: Int,
    /** Joint inside the accessory artwork, normalised 0..1 (y down). */
    val pivotX: Float = 0.5f,
    val pivotY: Float = 0.5f,
    /** Universal bone id the accessory is attached to. */
    val attachBoneId: String,
    /** Attach point inside the host bone's rest rect, fractions (0.5/0 = top-centre). */
    val anchorX: Float = 0.5f,
    val anchorY: Float = 0f,
    /** Drawn height in view units (1.0 = the whole character). */
    val targetHeight: Float = 0.2f,
    /** Absolute draw layer; body parts live around 3..40, the face at 60/61. */
    val z: Int = 80,
    val rotationDeg: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
) {
    init {
        require(widthPx > 0 && heightPx > 0) { "Accessory '$id' needs positive pixel size" }
        require(targetHeight > 0f) { "Accessory '$id' needs a positive target height" }
    }

    /** The accessory as a rig-grade sprite, so the painter and z-order tools treat it like any part. */
    fun toSpriteAsset(): SpriteAsset = SpriteAsset(
        slotId = id,
        width = widthPx,
        height = heightPx,
        pivot = Vec2(pivotX, pivotY),
        coverage = 1f,
        sourceRect = IntRect(0, 0, widthPx, heightPx),
        contentRect = IntRect(0, 0, widthPx, heightPx),
    )
}

/** Standard attach points offered by the editor (V6: Head / Hand / Torso / Foot / Custom). */
object AttachPoints {
    val STANDARD: List<Pair<String, String>> = listOf(
        "head" to "Head",
        "torso" to "Torso",
        "hand_l" to "Left hand",
        "hand_r" to "Right hand",
        "foot_l" to "Left foot",
        "foot_r" to "Right foot",
    )

    fun labelFor(boneId: String): String =
        STANDARD.firstOrNull { it.first == boneId }?.second ?: boneId
}

/** Versioned JSON codec for a project's accessories (V6 §39: `accessories.json`). */
object AccessoryCodec {

    const val SCHEMA_VERSION = 6

    fun encode(accessories: List<AccessoryDef>): JsonValue.Obj = obj(
        "schemaVersion" to JsonValue.Num(SCHEMA_VERSION.toDouble()),
        "accessories" to arr(accessories.map { a ->
            obj(
                "id" to str(a.id),
                "name" to str(a.name),
                "fileName" to str(a.fileName),
                "widthPx" to JsonValue.Num(a.widthPx.toDouble()),
                "heightPx" to JsonValue.Num(a.heightPx.toDouble()),
                "pivotX" to JsonValue.Num(a.pivotX.toDouble()),
                "pivotY" to JsonValue.Num(a.pivotY.toDouble()),
                "attachBoneId" to str(a.attachBoneId),
                "anchorX" to JsonValue.Num(a.anchorX.toDouble()),
                "anchorY" to JsonValue.Num(a.anchorY.toDouble()),
                "targetHeight" to JsonValue.Num(a.targetHeight.toDouble()),
                "z" to JsonValue.Num(a.z.toDouble()),
                "rotationDeg" to JsonValue.Num(a.rotationDeg.toDouble()),
                "offsetX" to JsonValue.Num(a.offsetX.toDouble()),
                "offsetY" to JsonValue.Num(a.offsetY.toDouble()),
                "scale" to JsonValue.Num(a.scale.toDouble()),
            )
        }),
    )

    fun encodeJson(accessories: List<AccessoryDef>): String =
        Json.stringify(encode(accessories), pretty = true)

    fun decodeJsonOrNull(json: String): List<AccessoryDef>? {
        val root = Json.parseOrNull(json) as? JsonValue.Obj ?: return null
        val arr = root.get("accessories") as? JsonValue.Arr ?: return null
        return arr.items.mapNotNull { value ->
            if (value !is JsonValue.Obj) return@mapNotNull null
            val id = (value.get("id") as? JsonValue.Str)?.value ?: return@mapNotNull null
            val fileName = (value.get("fileName") as? JsonValue.Str)?.value ?: return@mapNotNull null
            val width = (value.get("widthPx") as? JsonValue.Num)?.value?.toInt() ?: return@mapNotNull null
            val height = (value.get("heightPx") as? JsonValue.Num)?.value?.toInt() ?: return@mapNotNull null
            if (width <= 0 || height <= 0) return@mapNotNull null
            val attach = (value.get("attachBoneId") as? JsonValue.Str)?.value ?: return@mapNotNull null
            fun num(key: String, fallback: Float): Float =
                (value.get(key) as? JsonValue.Num)?.value?.toFloat() ?: fallback
            AccessoryDef(
                id = id,
                name = (value.get("name") as? JsonValue.Str)?.value ?: id,
                fileName = fileName,
                widthPx = width,
                heightPx = height,
                pivotX = num("pivotX", 0.5f),
                pivotY = num("pivotY", 0.5f),
                attachBoneId = attach,
                anchorX = num("anchorX", 0.5f),
                anchorY = num("anchorY", 0f),
                targetHeight = num("targetHeight", 0.2f).coerceAtLeast(0.01f),
                z = num("z", 80f).toInt(),
                rotationDeg = num("rotationDeg", 0f),
                offsetX = num("offsetX", 0f),
                offsetY = num("offsetY", 0f),
                scale = num("scale", 1f).coerceAtLeast(0.05f),
            )
        }
    }
}
