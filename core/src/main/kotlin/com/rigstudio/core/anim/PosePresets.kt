package com.rigstudio.core.anim

import com.rigstudio.core.json.Json
import com.rigstudio.core.json.JsonValue
import com.rigstudio.core.json.arr
import com.rigstudio.core.json.get
import com.rigstudio.core.json.obj
import com.rigstudio.core.json.str
import com.rigstudio.core.rig.BonePose
import com.rigstudio.core.rig.Pose

/**
 * A named, saveable pose (V6 §47): the bone rotations of one instant. Poses can be applied to
 * the character, mirrored, copied and pasted, and are the atoms user keyframes are built from.
 */
data class PosePreset(
    val id: String,
    val name: String,
    val rotations: Map<String, BonePose>,
) {
    fun toPose(): Pose = Pose(bones = rotations)

    /** Left↔right mirrored pose (V6 §56): bones swap, rotations negate. */
    fun mirrored(): PosePreset {
        val swapped = HashMap<String, BonePose>(rotations.size)
        for ((boneId, bonePose) in rotations) {
            swapped[com.rigstudio.core.model.BoneIds.mirrorOf(boneId)] = bonePose.mirrored()
        }
        return copy(id = id + "_mirrored", name = "$name (mirrored)", rotations = swapped)
    }
}

/**
 * The pose library (V6 §47). Built-ins are sampled from the animation library at a
 * representative instant, so they always stay in sync with the shipped clips; user poses are
 * saved beside the project as versioned JSON.
 */
object PoseLibrary {

    private fun fromClip(id: String, name: String, clipId: String, t: Float): PosePreset {
        val clip = AnimationLibrary.byId(clipId) ?: AnimationLibrary.IDLE
        val pose = clip.sample(t)
        return PosePreset(id, name, pose.bones)
    }

    val BUILT_INS: List<PosePreset> = listOf(
        PosePreset("neutral", "Neutral", emptyMap()),
        fromClip("walking", "Walking", "walk", 0.12f),
        fromClip("running", "Running", "run", 0.22f),
        fromClip("happy", "Happy", "happy", 0.35f),
        fromClip("sad", "Sad", "sad", 0.40f),
        fromClip("angry", "Angry", "angry", 0.40f),
        fromClip("surprised", "Surprised", "surprised", 0.25f),
        fromClip("pointing", "Pointing", "point", 0.45f),
        fromClip("waving", "Waving", "wave", 0.50f),
    )

    fun byId(id: String): PosePreset? = BUILT_INS.firstOrNull { it.id == id }
}

/** Versioned JSON codec for user-saved poses (V6 §39). */
object PosePresetCodec {

    const val SCHEMA_VERSION = 6

    fun encode(poses: List<PosePreset>): JsonValue.Obj = obj(
        "schemaVersion" to JsonValue.Num(SCHEMA_VERSION.toDouble()),
        "poses" to arr(poses.map { preset ->
            obj(
                "id" to str(preset.id),
                "name" to str(preset.name),
                "bones" to JsonValue.Obj(preset.rotations.entries.associate { (bone, pose) ->
                    bone to obj(
                        "rotationDeg" to JsonValue.Num(pose.rotationDeg.toDouble()),
                        "offsetX" to JsonValue.Num(pose.offset.x.toDouble()),
                        "offsetY" to JsonValue.Num(pose.offset.y.toDouble()),
                        "scale" to JsonValue.Num(pose.scale.toDouble()),
                    )
                }),
            )
        }),
    )

    fun encodeJson(poses: List<PosePreset>): String = Json.stringify(encode(poses))

    fun decodeJsonOrNull(json: String): List<PosePreset>? {
        val root = Json.parseOrNull(json) as? JsonValue.Obj ?: return null
        val arr = root.get("poses") as? JsonValue.Arr ?: return emptyList()
        return arr.items.mapNotNull { pv ->
            if (pv !is JsonValue.Obj) return@mapNotNull null
            val id = (pv.get("id") as? JsonValue.Str)?.value ?: return@mapNotNull null
            val name = (pv.get("name") as? JsonValue.Str)?.value ?: id
            val bones = HashMap<String, BonePose>()
            ((pv.get("bones") as? JsonValue.Obj)?.members ?: emptyMap()).forEach { (bone, bv) ->
                if (bv is JsonValue.Obj) {
                    bones[bone] = BonePose(
                        rotationDeg = (bv.get("rotationDeg") as? JsonValue.Num)?.value?.toFloat() ?: 0f,
                        offset = com.rigstudio.core.geom.Vec2(
                            (bv.get("offsetX") as? JsonValue.Num)?.value?.toFloat() ?: 0f,
                            (bv.get("offsetY") as? JsonValue.Num)?.value?.toFloat() ?: 0f,
                        ),
                        scale = (bv.get("scale") as? JsonValue.Num)?.value?.toFloat() ?: 1f,
                    )
                }
            }
            PosePreset(id, name, bones)
        }
    }
}
